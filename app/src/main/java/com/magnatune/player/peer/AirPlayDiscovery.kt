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

    /** The currently chosen AirPlay device (UI selection; streaming not yet wired). */
    val selected = MutableStateFlow<Device?>(null)
    fun select(device: Device?) { selected.value = device }

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val found = ConcurrentHashMap<String, Device>()
    private var listener: NsdManager.DiscoveryListener? = null

    // NsdManager (pre-API-34) allows only ONE resolveService at a time; concurrent resolves fail
    // with FAILURE_ALREADY_ACTIVE and leave host=null. Serialize them through a queue, with a few
    // retries per service so every discovered device ends up with a resolved host/port.
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val attempts = ConcurrentHashMap<String, Int>()

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
                // Add as soon as we know the name; queue a (serialized) resolve for host/port.
                found[s.serviceName] = Device(s.serviceName, friendly(s.serviceName), null, 0)
                publish()
                enqueueResolve(s)
            }
        }
        listener = l
        runCatching { nsd.discoverServices("_raop._tcp", NsdManager.PROTOCOL_DNS_SD, l) }
    }

    private fun enqueueResolve(s: NsdServiceInfo) {
        synchronized(resolveQueue) { resolveQueue.addLast(s) }
        pumpResolve()
    }

    private fun pumpResolve() {
        val next: NsdServiceInfo
        synchronized(resolveQueue) {
            if (resolving || resolveQueue.isEmpty()) return
            resolving = true
            next = resolveQueue.removeFirst()
        }
        val done = { synchronized(resolveQueue) { resolving = false }; pumpResolve() }
        runCatching {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo, e: Int) {
                    val n = (attempts[si.serviceName] ?: 0) + 1
                    attempts[si.serviceName] = n
                    if (n <= 3) synchronized(resolveQueue) { resolveQueue.addLast(si) }  // retry later
                    done()
                }
                override fun onServiceResolved(si: NsdServiceInfo) {
                    attempts.remove(si.serviceName)
                    val txt = runCatching {
                        si.attributes.entries.joinToString(" ") { (k, v) -> "$k=${v?.let { String(it) }}" }
                    }.getOrDefault("")
                    android.util.Log.i("RAOP", "resolved ${si.serviceName} ${si.host?.hostAddress}:${si.port} TXT[$txt]")
                    found[si.serviceName] = Device(si.serviceName, friendly(si.serviceName),
                        si.host?.hostAddress, si.port)
                    publish()
                    done()
                }
            })
        }.onFailure { done() }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        synchronized(resolveQueue) { resolveQueue.clear(); resolving = false }
        attempts.clear()
        found.clear(); publish()
    }

    private fun publish() {
        devices.value = found.values.sortedBy { it.name.lowercase() }
    }
}
