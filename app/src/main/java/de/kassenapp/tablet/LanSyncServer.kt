package de.kassenapp.tablet

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal local-network receiver for handover rows.
 *
 * This is a transport foundation, not a complete secure production server:
 * keep the tablets on a trusted Wi-Fi network. Future hardening should add
 * pairing/authentication, TLS or a per-event secret, bounded request parsing,
 * and sync acknowledgements/outbox state.
 */
class LanSyncServer(
    private val dao: KassenDao,
    private val pairingKey: String,
    private val prefs: android.content.SharedPreferences,
    private val port: Int = LanDiscovery.DEFAULT_PORT,
    private val onStatus: (String) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                onStatus("LAN-Empfang aktiv auf Port $port")
                while (running.get()) {
                    val socket = serverSocket?.accept() ?: break
                    scope.launch { handle(socket) }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    Log.e("LanSyncServer", "Server error", e)
                    onStatus("LAN-Empfang fehlgeschlagen: ${e.message ?: "unbekannt"}")
                }
            } finally {
                running.set(false)
                runCatching { serverSocket?.close() }
                serverSocket = null
            }
        }
    }

    private suspend fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            try {
                val headerBytes = readUntilHeaderEnd(input, 16 * 1024)
                val headerText = headerBytes.toString(Charsets.UTF_8)
                val firstLine = headerText.lineSequence().firstOrNull().orEmpty()
                val contentLength = Regex("(?im)^Content-Length:\\s*(\\d+)\\s*$")
                    .find(headerText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (firstLine.startsWith("GET /health")) {
                    respond(output, 200, JSONObject().put("ok", true).put("service", "kassenapp"))
                    return
                }
                if (firstLine.startsWith("GET /articles") || firstLine.startsWith("POST /handover") || firstLine.startsWith("POST /cash-count")) {
                    val suppliedKey = Regex("(?im)^X-Kassenapp-Key:\\s*(.+)\\s*$").find(headerText)?.groupValues?.get(1)?.trim().orEmpty()
                    if (pairingKey.isBlank() || suppliedKey != pairingKey) {
                        respond(output, 401, JSONObject().put("ok", false).put("error", "unauthorized"))
                        return
                    }
                }
                if (firstLine.startsWith("GET /articles")) {
                    val articles = org.json.JSONArray()
                    dao.getArticles().forEach { article ->
                        articles.put(JSONObject()
                            .put("id", article.id)
                            .put("name", article.name)
                            .put("price", article.price)
                            .put("enabled", article.enabled))
                    }
                    respond(output, 200, JSONObject().put("ok", true).put("articles", articles))
                    return
                }
                if (firstLine.startsWith("POST /cash-count")) {
                    if (contentLength !in 1..16384) {
                        respond(output, 400, JSONObject().put("ok", false).put("error", "invalid_length"))
                        return
                    }
                    val body = ByteArray(contentLength)
                    var offset = 0
                    while (offset < contentLength) {
                        val count = input.read(body, offset, contentLength - offset)
                        if (count < 0) break
                        offset += count
                    }
                    if (offset != contentLength) {
                        respond(output, 400, JSONObject().put("ok", false).put("error", "short_body"))
                        return
                    }
                    val json = JSONObject(String(body, Charsets.UTF_8))
                    val role = json.optString("registerRole", "")
                    if (role !in listOf("Zusatzkasse 1", "Zusatzkasse 2")) {
                        respond(output, 400, JSONObject().put("ok", false).put("error", "invalid_role"))
                        return
                    }
                    prefs.edit()
                        .putInt("remote_${role}_cash_count_change", json.optInt("change", -1))
                        .putLong("remote_${role}_cash_count_change_timestamp", json.optLong("changeTimestamp", -1L))
                        .putInt("remote_${role}_cash_count_settlement", json.optInt("settlement", -1))
                        .putLong("remote_${role}_cash_count_settlement_timestamp", json.optLong("settlementTimestamp", -1L))
                        .apply()
                    respond(output, 200, JSONObject().put("ok", true).put("registerRole", role))
                    return
                }
                if (!firstLine.startsWith("POST /handover")) {
                    respond(output, 404, JSONObject().put("ok", false).put("error", "not_found"))
                    return
                }
                if (contentLength !in 1..65536) {
                    respond(output, 400, JSONObject().put("ok", false).put("error", "invalid_length"))
                    return
                }
                val body = ByteArray(contentLength)
                var offset = 0
                while (offset < contentLength) {
                    val count = input.read(body, offset, contentLength - offset)
                    if (count < 0) break
                    offset += count
                }
                if (offset != contentLength) {
                    respond(output, 400, JSONObject().put("ok", false).put("error", "short_body"))
                    return
                }
                val json = JSONObject(String(body, Charsets.UTF_8))
                val role = json.optString("registerRole", "")
                val syncId = json.optString("syncId", "")
                if (role !in listOf("Masterkasse", "Zusatzkasse 1", "Zusatzkasse 2") || syncId.isBlank()) {
                    respond(output, 400, JSONObject().put("ok", false).put("error", "invalid_record"))
                    return
                }
                val row = HandoverEntity(
                    id = 0,
                    timestamp = json.getLong("timestamp"),
                    articleId = json.getInt("articleId"),
                    articleName = json.getString("articleName"),
                    unitPrice = json.getDouble("unitPrice"),
                    quantity = json.getInt("quantity"),
                    totalPrice = json.getDouble("totalPrice"),
                    registerRole = role,
                    syncId = syncId
                )
                val inserted = dao.receiveRemoteHandover(row)
                respond(output, 200, JSONObject().put("ok", true).put("inserted", inserted).put("syncId", syncId))
            } catch (e: Exception) {
                Log.w("LanSyncServer", "Request rejected", e)
                runCatching { respond(output, 400, JSONObject().put("ok", false).put("error", "bad_request")) }
            }
        }
    }

    private fun readUntilHeaderEnd(input: BufferedInputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var matched = 0
        while (out.size() < maxBytes) {
            val b = input.read()
            if (b < 0) break
            out.write(b)
            matched = when {
                matched == 0 && b == 13 -> 1
                matched == 1 && b == 10 -> 2
                matched == 2 && b == 13 -> 3
                matched == 3 && b == 10 -> 4
                b == 13 -> 1
                else -> 0
            }
            if (matched == 4) return out.toByteArray()
        }
        throw IllegalArgumentException("HTTP header too large or incomplete")
    }

    private fun respond(out: BufferedOutputStream, status: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val reason = if (status == 200) "OK" else if (status == 404) "Not Found" else if (status == 401) "Unauthorized" else "Bad Request"
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
