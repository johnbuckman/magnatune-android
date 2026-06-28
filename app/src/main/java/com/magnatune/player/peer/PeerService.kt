package com.magnatune.player.peer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * LAN peer sync (mirrors the iOS PeerService). Discovers other Magnatune instances via NSD
 * (`_magnatune._tcp`), keeps one TCP connection per peer, broadcasts our now-playing snapshot, and
 * relays transport commands. Exactly one connection per pair: the lower instance id dials, the
 * higher listens. Peers exchange only a songId (the catalog is shared).
 *
 * NOTE: built + non-crashing, but NOT yet verified across two devices (needs a second Android
 * device on the same LAN). Wiring to the controller + the settings toggle is in place.
 */
class PeerService(
    private val context: Context,
    private val deviceName: String,
    private val scope: CoroutineScope,
) {
    companion object { const val SERVICE_TYPE = "_magnatune._tcp" }

    data class Snapshot(val state: String, val songId: Long?, val position: Double, val startedAt: Long?)
    data class PeerInfo(val id: String, val name: String, val snapshot: Snapshot)

    /** Invoked when a peer sends us a transport command ("playPause" | "next" | "prev"). */
    var onControl: ((String) -> Unit)? = null
    val peers = MutableStateFlow<List<PeerInfo>>(emptyList())

    private val instanceId = UUID.randomUUID().toString()
    @Volatile private var registeredName: String = instanceId
    @Volatile private var enabled = false

    private val nsd by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var serverSocket: ServerSocket? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private val links = ConcurrentHashMap<String, Link>()   // peerId -> link
    @Volatile private var lastSnapshot = Snapshot("idle", null, 0.0, null)

    private inner class Link(val socket: Socket, var remoteId: String?, var remoteName: String?) {
        val out: OutputStream = socket.getOutputStream()
        @Volatile var snapshot = Snapshot("idle", null, 0.0, null)
    }

    fun start() {
        if (enabled) return
        enabled = true
        scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(0)
                serverSocket = server
                registerService(server.localPort)
                startDiscovery()
                acceptLoop(server)
            } catch (_: Exception) { }
        }
    }

    fun stop() {
        if (!enabled) return
        enabled = false
        runCatching { regListener?.let { nsd.unregisterService(it) } }
        runCatching { discListener?.let { nsd.stopServiceDiscovery(it) } }
        runCatching { serverSocket?.close() }
        links.values.forEach { runCatching { it.socket.close() } }
        links.clear()
        peers.value = emptyList()
    }

    fun updateLocalState(snapshot: Snapshot) {
        lastSnapshot = snapshot
        val line = encodeNowPlaying(snapshot)
        links.values.forEach { l -> if (l.remoteId != null) send(l, line) }
    }

    fun sendControl(peerId: String, cmd: String) {
        links[peerId]?.let { send(it, JSONObject().put("t", "ctl").put("cmd", cmd).toString()) }
    }

    // ---- NSD register / discover ----
    private fun registerService(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = instanceId; serviceType = SERVICE_TYPE; setPort(port)
        }
        regListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) { registeredName = s.serviceName }
            override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener) }
    }

    private fun startDiscovery() {
        discListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(s: NsdServiceInfo) { dropPeer(s.serviceName) }
            override fun onServiceFound(s: NsdServiceInfo) {
                val peerId = s.serviceName
                if (peerId == registeredName || peerId == instanceId) return
                // Tie-break: only the lower id dials.
                if (registeredName < peerId && !links.containsKey(peerId)) resolveAndDial(s)
            }
        }
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener) }
    }

    private fun resolveAndDial(s: NsdServiceInfo) {
        nsd.resolveService(s, object : NsdManager.ResolveListener {
            override fun onResolveFailed(si: NsdServiceInfo, e: Int) {}
            override fun onServiceResolved(si: NsdServiceInfo) {
                val peerId = si.serviceName
                if (links.containsKey(peerId)) return
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val sock = Socket()
                        sock.connect(InetSocketAddress(si.host, si.port), 5000)
                        adopt(Link(sock, peerId, si.serviceName))
                    }
                }
            }
        })
    }

    private fun acceptLoop(server: ServerSocket) {
        while (enabled && !server.isClosed) {
            val sock = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch(Dispatchers.IO) { adopt(Link(sock, null, null)) }
        }
    }

    // ---- per-link ----
    private fun adopt(link: Link) {
        link.remoteId?.let { links[it] = link }
        // Introduce ourselves + share current state.
        send(link, JSONObject().put("t", "hello").put("id", registeredName)
            .put("name", deviceName).put("platform", "android").toString())
        send(link, encodeNowPlaying(lastSnapshot))
        readLoop(link)
    }

    private fun readLoop(link: Link) {
        runCatching {
            val reader = BufferedReader(InputStreamReader(link.socket.getInputStream()))
            while (enabled) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank()) handle(link, line)
            }
        }
        // disconnected
        link.remoteId?.let { links.remove(it) }
        runCatching { link.socket.close() }
        publishPeers()
    }

    private fun handle(link: Link, line: String) {
        val o = runCatching { JSONObject(line) }.getOrNull() ?: return
        when (o.optString("t")) {
            "hello" -> {
                val id = o.optString("id"); link.remoteId = id; link.remoteName = o.optString("name")
                if (id.isNotEmpty()) links[id] = link
                publishPeers()
            }
            "np" -> {
                link.snapshot = Snapshot(
                    o.optString("state", "idle"),
                    if (o.isNull("songID")) null else o.optLong("songID"),
                    o.optDouble("position", 0.0),
                    if (o.isNull("startedAt")) null else o.optLong("startedAt"),
                )
                publishPeers()
            }
            "ctl" -> onControl?.invoke(o.optString("cmd"))
        }
    }

    private fun send(link: Link, json: String) {
        scope.launch(Dispatchers.IO) {
            runCatching { link.out.write((json + "\n").toByteArray()); link.out.flush() }
        }
    }

    private fun encodeNowPlaying(s: Snapshot): String = JSONObject().put("t", "np")
        .put("state", s.state)
        .put("songID", s.songId ?: JSONObject.NULL)
        .put("position", s.position)
        .put("startedAt", s.startedAt ?: JSONObject.NULL).toString()

    private fun dropPeer(peerId: String) {
        links.remove(peerId)?.let { runCatching { it.socket.close() } }
        publishPeers()
    }

    private fun publishPeers() {
        peers.value = links.values.mapNotNull { l -> l.remoteId?.let { PeerInfo(it, l.remoteName ?: "Magnatune", l.snapshot) } }
            .distinctBy { it.id }
    }
}
