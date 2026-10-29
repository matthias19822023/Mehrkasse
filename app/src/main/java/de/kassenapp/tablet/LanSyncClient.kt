package de.kassenapp.tablet

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LanSyncClient {
    fun downloadArticles(host: String, port: Int, key: String): List<ArticleEntity>? {
        val connection = (URL("http://$host:$port/articles").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("X-Kassenapp-Key", key)
            connectTimeout = 3000
            readTimeout = 5000
        }
        return try {
            if (connection.responseCode != 200) return null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (!json.optBoolean("ok", false)) return null
            val array = json.getJSONArray("articles")
            (0 until array.length()).map { i ->
                val a = array.getJSONObject(i)
                ArticleEntity(a.getInt("id"), a.getString("name"), a.getDouble("price"), a.getBoolean("enabled"))
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun downloadMasterSettings(host: String, port: Int, key: String): JSONObject? {
        val connection = (URL("http://$host:$port/settings").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("X-Kassenapp-Key", key)
            connectTimeout = 3000
            readTimeout = 5000
        }
        return try {
            if (connection.responseCode != 200) return null
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (response.optBoolean("ok", false)) response else null
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun uploadHandover(host: String, port: Int, row: HandoverEntity, key: String): Boolean {
        val connection = (URL("http://$host:$port/handover").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Kassenapp-Key", key)
        }
        return try {
            val json = JSONObject()
                .put("timestamp", row.timestamp)
                .put("articleId", row.articleId)
                .put("articleName", row.articleName)
                .put("unitPrice", row.unitPrice)
                .put("quantity", row.quantity)
                .put("totalPrice", row.totalPrice)
                .put("registerRole", row.registerRole)
                .put("syncId", row.syncId)
            connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            code == 200 && JSONObject(response).optBoolean("ok", false)
        } finally {
            connection.disconnect()
        }
    }
    fun uploadCashCount(host: String, port: Int, key: String, role: String, prefs: android.content.SharedPreferences): Boolean {
        val connection = (URL("http://$host:$port/cash-count").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Kassenapp-Key", key)
        }
        return try {
            val json = JSONObject()
                .put("registerRole", role)
                .put("change", prefs.getInt("cash_count_change", -1))
                .put("changeTimestamp", prefs.getLong("cash_count_change_timestamp", -1L))
                .put("settlement", prefs.getInt("cash_count_settlement", -1))
                .put("settlementTimestamp", prefs.getLong("cash_count_settlement_timestamp", -1L))
            connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            code == 200 && JSONObject(response).optBoolean("ok", false)
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

}
