package de.kassenapp.tablet

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Local-network service discovery foundation.
 *
 * The master advertises a service on the local Wi-Fi network; additional registers
 * discover it without needing its IP address typed in. This class only discovers
 * the endpoint; transaction transfer is intentionally handled by the LAN sync layer.
 */
class LanDiscovery(
    context: Context,
    private val onMasterFound: (host: String, port: Int) -> Unit,
    private val onStatus: (String) -> Unit = {}
) {
    companion object {
        const val SERVICE_TYPE = "_kassenapp._tcp."
        const val SERVICE_NAME = "Kassenapp-Master"
        const val DEFAULT_PORT = 47831
        private const val TAG = "LanDiscovery"
    }

    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var stopped = false

    fun advertiseMaster(port: Int = DEFAULT_PORT) {
        stopped = false
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                onStatus("Masterkasse im WLAN sichtbar")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onStatus("WLAN-Anmeldung fehlgeschlagen ($errorCode)")
                Log.w(TAG, "Service registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                onStatus("Masterkasse nicht mehr sichtbar")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Service unregistration failed: $errorCode")
            }
        }
        registration = listener
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun discoverMaster() {
        stopped = false
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                onStatus("Suche Masterkasse im WLAN …")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.trimEnd('.') != SERVICE_TYPE.trimEnd('.')) return
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Resolve failed: $errorCode")
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (!stopped && info.host != null && info.port > 0) {
                            onMasterFound(info.host.hostAddress ?: return, info.port)
                            onStatus("Masterkasse gefunden")
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                onStatus("Masterkasse derzeit nicht erreichbar")
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onStatus("WLAN-Suche fehlgeschlagen ($errorCode)")
                runCatching { manager.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Stop discovery failed: $errorCode")
            }
        }
        discovery = listener
        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        stopped = true
        discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }
        registration?.let { runCatching { manager.unregisterService(it) } }
        discovery = null
        registration = null
    }
}
