package de.kassenapp.tablet

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * LAN sync coordinator. The host app should create this while its activity is
 * active and stop it when no longer needed. The persistent Room outbox survives
 * process death and retries while the app is running.
 */
class LanSyncCoordinator(
    context: Context,
    private val dao: KassenDao,
    private val role: String,
    private val onStatus: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var discovery: LanDiscovery? = null
    private var server: LanSyncServer? = null
    private var masterHost: String? = null
    private var masterPort: Int = LanDiscovery.DEFAULT_PORT
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        if (job != null) return
        LanSyncWorker.schedule(appContext, role)
        job = scope.launch {
            if (role == "Masterkasse") {
                val key = LanAuth.getOrCreateMasterKey(appContext)
                server = LanSyncServer(dao, pairingKey = key, onStatus = onStatus).also { it.start() }
                discovery = LanDiscovery(appContext, { _, _ -> }, onStatus).also { it.advertiseMaster() }
            } else {
                val wm = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wm.createMulticastLock("kassenapp-nsd").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                discovery = LanDiscovery(appContext, { host, port ->
                    masterHost = host
                    masterPort = port
                    appContext.getSharedPreferences("kassen_app_settings", Context.MODE_PRIVATE).edit()
                        .putString("lan_master_host", host).putInt("lan_master_port", port).apply()
                }, onStatus).also { it.discoverMaster() }
            }
            while (isActive) {
                if (role != "Masterkasse") {
                    val saved = appContext.getSharedPreferences("kassen_app_settings", Context.MODE_PRIVATE)
                    val host = masterHost ?: saved.getString("lan_master_host", null)
                    if (host != null) masterHost = host
                    masterPort = if (masterPort == LanDiscovery.DEFAULT_PORT) saved.getInt("lan_master_port", masterPort) else masterPort
                    val key = LanAuth.getKey(appContext)
                    if (host != null) {
                        val masterArticles = runCatching { LanSyncClient.downloadArticles(host, masterPort, key) }.getOrNull()
                        if (masterArticles != null) {
                            runCatching {
                                dao.clearArticles()
                                dao.saveArticles(masterArticles)
                            }
                        }
                        val pending = runCatching { dao.getPendingSync() }.getOrDefault(emptyList())
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
                            val sent = runCatching { LanSyncClient.uploadHandover(host, masterPort, row, key) }.getOrDefault(false)
                            if (sent) {
                                dao.removePendingSync(p.syncId)
                            } else {
                                dao.recordSyncFailure(p.syncId, "Upload fehlgeschlagen")
                            }
                        }
                    }
                }
                delay(5000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        discovery?.stop()
        discovery = null
        server?.stop()
        server = null
        multicastLock?.let { runCatching { if (it.isHeld) it.release() } }
        multicastLock = null
    }
}
