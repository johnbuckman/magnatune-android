package com.magnatune.player.peer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Discovers AirPlay audio receivers on the LAN via NSD (the RAOP service, `_raop._tcp`). Used only to
 * decide whether to show the AirPlay icon and to populate the device picker — the AirPlay icon stays
 * hidden whenever no receiver is found.
 *
 * NOTE: device discovery only. Actually streaming audio to AirPlay (RAOP: RTSP handshake + RSA/AES
 * key exchange + RTP/ALAC) is a separate, larger piece and is not yet implemented.
 */
class AirPlayDiscovery(context: Context) {

    data class Device(val id: String, val name: String, val host: String?, val port: Int)

    val devices = MutableStateFlow<List<Device>>(emptyList())

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val found = ConcurrentHashMap<String, Device>()
    private var listener: NsdManager.DiscoveryListener? = null

    /** RAOP advertises as "<MAC>@<Speaker Name>"; show the friendly part. */
    private fun friendly(serviceName: String): String =
        serviceName.substringAfter('@', serviceName)

    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(s: NsdServiceInfo) {
                found.remove(s.serviceName); publish()
            }
            override fun onServiceFound(s: NsdServiceInfo) {
                // Resolve to get host/port; add as soon as we know the name.
                found[s.serviceName] = Device(s.serviceName, friendly(s.serviceName), null, 0)
                publish()
                runCatching {
                    nsd.resolveService(s, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, e: Int) {}
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            found[si.serviceName] = Device(si.serviceName, friendly(si.serviceName),
                                si.host?.hostAddress, si.port)
                            publish()
                        }
                    })
                }
            }
        }
        listener = l
        runCatching { nsd.discoverServices("_raop._tcp", NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        found.clear(); publish()
    }

    private fun publish() {
        devices.value = found.values.sortedBy { it.name.lowercase() }
    }
}
