package de.kassenapp.tablet

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Persistent retry path for handovers that remain in Room's pending_sync outbox.
 * WorkManager can resume these attempts after process/app restarts, subject to Android
 * background-execution and battery policies. It does not guarantee immediate delivery.
 */
class LanSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("kassen_app_settings", Context.MODE_PRIVATE)
        val role = prefs.getString("register_role", "Masterkasse") ?: "Masterkasse"
        if (role == "Masterkasse") return@withContext Result.success()

        val host = prefs.getString("lan_master_host", null)
            ?: return@withContext Result.retry()
        val port = prefs.getInt("lan_master_port", LanDiscovery.DEFAULT_PORT)
        val key = LanAuth.getKey(applicationContext)
        if (key.isBlank()) return@withContext Result.retry()

        val dao = KassenDb.get(applicationContext).dao()
        LanSyncClient.uploadCashCount(host, port, key, role, prefs)
        // Keep the shared article catalogue reasonably fresh when the master is reachable.
        val articles = LanSyncClient.downloadArticles(host, port, key)
        if (articles == null) return@withContext Result.retry()
        runCatching {
            dao.clearArticles()
            dao.saveArticles(articles)
        }.getOrElse { return@withContext Result.retry() }
        runCatching {
            val settings = LanSyncClient.downloadMasterSettings(host, port, key)
            if (settings != null) {
                val editor = prefs.edit()
                val symbols = settings.optJSONObject("symbols")
                if (symbols != null) for (id in 1..12) if (symbols.has(id.toString())) editor.putString("article_symbol_$id", symbols.optString(id.toString(), "🛒"))
                val enabled = settings.optJSONArray("enabledSymbols")
                if (enabled != null) {
                    val values = mutableSetOf<String>()
                    for (i in 0 until enabled.length()) values.add(enabled.optString(i))
                    editor.putStringSet("enabled_article_symbols", values)
                }
                val org = settings.optJSONObject("organization")
                if (org != null) editor.putString("org_name", org.optString("name", ""))
                    .putString("org_address", org.optString("address", ""))
                    .putString("org_contact", org.optString("contact", ""))
                    .putString("org_extra", org.optString("extra", ""))
                    .putString("org_event", org.optString("event", ""))
                editor.apply()
            }
        }

        var failed = false
        val pending = runCatching { dao.getPendingSync() }.getOrElse { return@withContext Result.retry() }
        for (p in pending) {
            val row = HandoverEntity(
                id = 0,
                timestamp = p.timestamp,
                articleId = p.articleId,
                articleName = p.articleName,
                unitPrice = p.unitPrice,
                quantity = p.quantity,
                totalPrice = p.totalPrice,
                registerRole = p.registerRole,
                syncId = p.syncId
            )
            val sent = runCatching { LanSyncClient.uploadHandover(host, port, row, key) }.getOrDefault(false)
            if (sent) {
                runCatching { dao.removePendingSync(p.syncId) }.onFailure { failed = true }
            } else {
                failed = true
                runCatching { dao.recordSyncFailure(p.syncId, "Hintergrund-Upload fehlgeschlagen") }
            }
        }
        if (failed) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "kassenapp-lan-sync-periodic"
        private const val ONCE_NAME = "kassenapp-lan-sync-now"

        fun schedule(context: Context, role: String) {
            val manager = WorkManager.getInstance(context.applicationContext)
            if (role == "Masterkasse") {
                manager.cancelUniqueWork(ONCE_NAME)
                manager.cancelUniqueWork(PERIODIC_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val once = OneTimeWorkRequestBuilder<LanSyncWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putString("role", role).build())
                .build()
            manager.enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.REPLACE, once)
            val periodic = PeriodicWorkRequestBuilder<LanSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(Data.Builder().putString("role", role).build())
                .build()
            manager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        }
    }
}
