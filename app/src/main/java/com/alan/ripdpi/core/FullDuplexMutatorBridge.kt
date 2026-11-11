package com.alan.ripdpi.core

import android.os.ParcelFileDescriptor
import com.alan.ripdpi.model.BypassVector
import com.alan.ripdpi.model.VectorCatalog
import com.alan.ripdpi.vpn.AlanVpnService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

object BridgeDeepDiagnostics {
    @Volatile
    var globalDeepLogger: ((String) -> Unit)? = null
    fun log(msg: String) {
        globalDeepLogger?.invoke(msg)
    }
}

class FullDuplexMutatorBridge(
    val listenPort: Int = 20809,
    initialRemoteHost: String = "",
    initialRemotePort: Int = 443,
    initialVector: BypassVector = VectorCatalog.VECTOR_REGISTRY.first(),
    initialCustomStride: Int = 400,
    initialJitterBytes: Int = 20,
    initialRawPassthrough: Boolean = false,
    private val protectSocket: ((Socket) -> Boolean)? = null,
    private val onLog: ((String) -> Unit)? = null
) {

    companion object {
        private const val SOCKET_BUFFER_SIZE = 4 * 1024 * 1024
        private const val STREAM_CHUNK_SIZE = 65536
        private const val UPLINK_SHIELD_WINDOW_BYTES = 16384
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val HANDSHAKE_READ_TIMEOUT_MS = 6500

        private val dohResolutionCache = ConcurrentHashMap<String, InetAddress>()

        private val dohSocketFactory = object : SocketFactory() {
            private val base = getDefault()
            override fun createSocket(): Socket = base.createSocket().also { s ->
                try { s.bind(InetSocketAddress(0)) } catch (_: Throwable) {}
                AlanVpnService.protectRawSocket(s)
            }
            override fun createSocket(h: String?, p: Int): Socket = createSocket().apply { connect(InetSocketAddress(h, p), 2500) }
            override fun createSocket(h: String?, p: Int, lh: InetAddress?, lp: Int): Socket = base.createSocket(h, p, lh, lp)
            override fun createSocket(h: InetAddress?, p: Int): Socket = createSocket().apply { connect(InetSocketAddress(h, p), 2500) }
            override fun createSocket(a: InetAddress?, p: Int, la: InetAddress?, lp: Int): Socket = base.createSocket(a, p, la, lp)
        }

        private val dohClient = OkHttpClient.Builder()
            .socketFactory(dohSocketFactory)
            .connectTimeout(2000, TimeUnit.MILLISECONDS)
            .readTimeout(2000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()

        fun isPoisonedIp(ip: String): Boolean {
            val v = ip.trim()
            return v.isEmpty() ||
                v.startsWith("10.10.34.") ||
                v.startsWith("10.10.") ||
                v == "0.0.0.0" ||
                v.startsWith("127.") ||
                v.startsWith("198.18.") ||
                v == "::" ||
                v == "::1"
        }
    }

    private data class RouteConfig(
        val remoteHost: String,
        val remotePort: Int,
        val vector: BypassVector,
        val customStride: Int,
        val jitterBytes: Int,
        val rawPassthrough: Boolean
    )

    private val routeRef = AtomicReference(
        RouteConfig(
            remoteHost = initialRemoteHost.trim().removePrefix("[").removeSuffix("]"),
            remotePort = initialRemotePort,
            vector = initialVector,
            customStride = initialCustomStride,
            jitterBytes = initialJitterBytes,
            rawPassthrough = initialRawPassthrough || initialVector.family == "DIRECT"
        )
    )
    private val cachedAddressRef = AtomicReference<Pair<String, InetAddress>?>(null)
    private val running = AtomicBoolean(false)
    private val sessionSeq = AtomicInteger(0)
    private var serverSocket: ServerSocket? = null

    private val liveSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    val txBytesTotal: AtomicLong = AtomicLong(0L)
    val rxBytesTotal: AtomicLong = AtomicLong(0L)

    private val ioPool = Executors.newCachedThreadPool { r ->
        Thread(r, "AlanMutatorBridge").apply { isDaemon = true }
    }

    @Volatile
    var boundPort: Int = listenPort
        private set

    val totalTxBytes: Long get() = txBytesTotal.get()
    val totalRxBytes: Long get() = rxBytesTotal.get()
    val txBytes: Long get() = txBytesTotal.get()
    val rxBytes: Long get() = rxBytesTotal.get()
    val activeSessionCount: Int get() = (liveSockets.size / 2).coerceAtLeast(0)
    val activeSessions: Int get() = activeSessionCount

    fun getCurrentVector(): BypassVector = routeRef.get().vector

    fun resetCounters() {
        txBytesTotal.set(0L)
        rxBytesTotal.set(0L)
    }

    fun updateVector(
        vector: BypassVector,
        customStride: Int = 400,
        jitterBytes: Int = 20,
        rawPassthrough: Boolean = false
    ) {
        val cur = routeRef.get()
        routeRef.set(
            cur.copy(
                vector = vector,
                customStride = customStride,
                jitterBytes = jitterBytes,
                rawPassthrough = rawPassthrough || vector.family == "DIRECT"
            )
        )
    }

    fun updateRoute(
        remoteHost: String,
        remotePort: Int,
        vector: BypassVector,
        customStride: Int = 400,
        jitterBytes: Int = 20,
        rawPassthrough: Boolean = false,
        flushExistingPool: Boolean = false
    ) {
        BridgeDeepDiagnostics.log("[DPI-BRIDGE] Route -> $remoteHost:$remotePort | Vector=${vector.vectorId} (${vector.codeName})")
        val cleanHost = remoteHost.trim().removePrefix("[").removeSuffix("]")
        val prev = routeRef.get()
        if (prev.remoteHost != cleanHost) {
            cachedAddressRef.set(null)
        }
        val effectivePass = rawPassthrough || vector.family == "DIRECT"
        routeRef.set(
            RouteConfig(
                remoteHost = cleanHost,
                remotePort = remotePort,
                vector = vector,
                customStride = customStride,
                jitterBytes = jitterBytes,
                rawPassthrough = effectivePass
            )
        )
        if (flushExistingPool) {
            dropActiveConnections()
        }
    }

    fun updateEndpoint(remoteHost: String, remotePort: Int, flushExisting: Boolean = true) {
        val cur = routeRef.get()
        updateRoute(remoteHost, remotePort, cur.vector, cur.customStride, cur.jitterBytes, cur.rawPassthrough, flushExisting)
    }

    fun dropActiveConnections() {
        val snapshot = liveSockets.toList()
        liveSockets.clear()
        for (s in snapshot) {
            try { s.shutdownInput() } catch (_: Throwable) {}
            try { s.shutdownOutput() } catch (_: Throwable) {}
            try { s.close() } catch (_: Throwable) {}
        }
    }

    fun start(): Int {
        if (!running.compareAndSet(false, true)) return boundPort
        AlanNativeSocket.ensureLoaded()
        val srv = ServerSocket()
        srv.reuseAddress = true
        srv.bind(InetSocketAddress("127.0.0.1", listenPort), 256)
        boundPort = srv.localPort
        serverSocket = srv
        ioPool.execute { acceptLoop(srv) }
        return boundPort
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Throwable) {}
        serverSocket = null
        dropActiveConnections()
        try { ioPool.shutdownNow() } catch (_: Throwable) {}
    }

    private fun acceptLoop(srv: ServerSocket) {
        while (running.get() && !srv.isClosed) {
            try {
                val clientSock = srv.accept()
                val sid = sessionSeq.incrementAndGet() % 10000
                ioPool.execute { handleSession(clientSock, sid) }
            } catch (_: Throwable) {
                if (!running.get()) break
            }
        }
    }

    private fun resolveTargetAddress(host: String): InetAddress {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        cachedAddressRef.get()?.let { (h, addr) ->
            if (h == clean) return addr
        }
        dohResolutionCache[clean]?.let { cached ->
            cachedAddressRef.set(clean to cached)
            return cached
        }

        if (clean.all { it.isDigit() || it == '.' } || clean.contains(":")) {
            val numeric = InetAddress.getByName(clean)
            cachedAddressRef.set(clean to numeric)
            return numeric
        }

        try {
            val all = InetAddress.getAllByName(clean)
            val valid = all.filter { !isPoisonedIp(it.hostAddress.orEmpty()) }
            val picked = valid.firstOrNull { it is Inet4Address } ?: valid.firstOrNull()
            if (picked != null) {
                dohResolutionCache[clean] = picked
                cachedAddressRef.set(clean to picked)
                return picked
            }
        } catch (_: Throwable) {
        }

        for (dohUrl in listOf(
            "https://1.1.1.1/dns-query?name=$clean&type=A",
            "https://8.8.8.8/resolve?name=$clean&type=A",
            "https://9.9.9.9:5053/dns-query?name=$clean&type=A"
        )) {
            try {
                val req = Request.Builder().url(dohUrl).header("Accept", "application/dns-json").get().build()
                dohClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val ans = JSONObject(resp.body?.string().orEmpty()).optJSONArray("Answer")
                        if (ans != null) {
                            for (i in 0 until ans.length()) {
                                val rec = ans.getJSONObject(i)
                                if (rec.optInt("type") == 1) {
                                    val ipStr = rec.optString("data", "").trim()
                                    if (!isPoisonedIp(ipStr)) {
                                        val resolved = InetAddress.getByName(ipStr)
                                        dohResolutionCache[clean] = resolved
                                        cachedAddressRef.set(clean to resolved)
                                        return resolved
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        return InetAddress.getByName(clean)
    }

    private fun readExactInitialFlight(sock: Socket, timeoutMs: Int = HANDSHAKE_READ_TIMEOUT_MS): ByteArray {
        sock.soTimeout = timeoutMs
        val ins = sock.getInputStream()
        val hdr = ByteArray(5)
        var hdrRead = 0
        while (hdrRead < 5) {
            val n = ins.read(hdr, hdrRead, 5 - hdrRead)
            if (n <= 0) {
                return if (hdrRead > 0) hdr.copyOf(hdrRead) else ByteArray(0)
            }
            hdrRead += n
        }

        val recLen = ((hdr[3].toInt() and 0xFF) shl 8) or (hdr[4].toInt() and 0xFF)
        if ((hdr[0].toInt() and 0xFF) != 0x16 || recLen <= 0 || recLen > 16384) {
            val out = ByteArrayOutputStream(1024)
            out.write(hdr)
            sock.soTimeout = 45
            val tmp = ByteArray(16384)
            try {
                val extra = ins.read(tmp)
                if (extra > 0) out.write(tmp, 0, extra)
            } catch (_: SocketTimeoutException) {
            } catch (_: Throwable) {
            }
            return out.toByteArray()
        }

        val full = ByteArray(5 + recLen)
        System.arraycopy(hdr, 0, full, 0, 5)
        var bodyRead = 0
        while (bodyRead < recLen) {
            val n = ins.read(full, 5 + bodyRead, minOf(16384, recLen - bodyRead))
            if (n <= 0) break
            bodyRead += n
        }
        return if (bodyRead == recLen) full else full.copyOf(5 + bodyRead)
    }

    private fun handleSession(clientSock: Socket, sid: Int) {
        val cfg = routeRef.get()
        if (cfg.remoteHost.isBlank()) {
            try { clientSock.close() } catch (_: Throwable) {}
            return
        }

        val remoteSock = Socket()
        liveSockets.add(clientSock)
        liveSockets.add(remoteSock)

        var clientPfd: ParcelFileDescriptor? = null
        var remotePfd: ParcelFileDescriptor? = null

        try {
            clientSock.tcpNoDelay = true
            clientSock.keepAlive = true

            val targetAddr = resolveTargetAddress(cfg.remoteHost)
            if (targetAddr is Inet4Address) {
                try { remoteSock.bind(InetSocketAddress(0)) } catch (_: Throwable) {}
            }
            try { protectSocket?.invoke(remoteSock) } catch (_: Throwable) {}
            AlanVpnService.protectRawSocket(remoteSock)
            remoteSock.tcpNoDelay = true
            remoteSock.keepAlive = true

            val t0 = System.nanoTime()
            remoteSock.connect(InetSocketAddress(targetAddr, cfg.remotePort), CONNECT_TIMEOUT_MS)
            val tcpMs = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)

            // Re-affirm fwmark protection after connect in case IPv6/dual-stack created FD during connect
            AlanVpnService.protectRawSocket(remoteSock)

            clientPfd = try { ParcelFileDescriptor.fromSocket(clientSock) } catch (_: Throwable) { null }
            remotePfd = try { ParcelFileDescriptor.fromSocket(remoteSock) } catch (_: Throwable) { null }
            val clientFd = clientPfd?.fd ?: -1
            val remoteFd = remotePfd?.fd ?: -1

            AlanNativeSocket.tuneTcpSocket(clientFd, SOCKET_BUFFER_SIZE)
            AlanNativeSocket.tuneTcpSocket(remoteFd, SOCKET_BUFFER_SIZE)

            val remoteOut = remoteSock.getOutputStream()

            if (!cfg.rawPassthrough) {
                val firstData = readExactInitialFlight(clientSock, timeoutMs = HANDSHAKE_READ_TIMEOUT_MS)
                if (firstData.isEmpty()) return

                val isTlsHello = firstData.size >= 44 &&
                    (firstData[0].toInt() and 0xFF) == 0x16 &&
                    (firstData[5].toInt() and 0xFF) == 0x01

                val isHttpReq = firstData.size >= 5 && (
                    startsWithAscii(firstData, "GET ") ||
                        startsWithAscii(firstData, "POST ") ||
                        startsWithAscii(firstData, "HEAD ") ||
                        startsWithAscii(firstData, "PUT ") ||
                        startsWithAscii(firstData, "OPTIONS ") ||
                        startsWithAscii(firstData, "DELETE ")
                    )

                val sentBytes = when {
                    isTlsHello -> {
                        val (sent, trace) = TlsByteMutator.transmitClientHello(
                            sock = remoteSock,
                            out = remoteOut,
                            nativeFd = remoteFd,
                            chRecord = firstData,
                            vector = cfg.vector,
                            customStride = cfg.customStride,
                            jitterBytes = cfg.jitterBytes
                        )
                        onLog?.invoke("[S#%04d] TCP=%dms | %s: %s".format(sid, tcpMs, cfg.vector.vectorId, trace))
                        sent
                    }
                    isHttpReq -> {
                        val (sent, trace) = TlsByteMutator.transmitPlainHttp(
                            sock = remoteSock,
                            out = remoteOut,
                            nativeFd = remoteFd,
                            httpPayload = firstData,
                            vector = cfg.vector,
                            customStride = cfg.customStride,
                            jitterBytes = cfg.jitterBytes
                        )
                        onLog?.invoke("[S#%04d] TCP=%dms | %s: %s".format(sid, tcpMs, cfg.vector.vectorId, trace))
                        sent
                    }
                    else -> {
                        remoteOut.write(firstData)
                        remoteOut.flush()
                        firstData.size
                    }
                }
                txBytesTotal.addAndGet(sentBytes.toLong())
            }

            remoteSock.soTimeout = 0
            clientSock.soTimeout = 0

            val downDone = AtomicBoolean(false)
            ioPool.execute {
                val buf = ByteArray(STREAM_CHUNK_SIZE)
                try {
                    val rIn = remoteSock.getInputStream()
                    val cOut = clientSock.getOutputStream()
                    while (running.get()) {
                        val n = rIn.read(buf)
                        if (n <= 0) break
                        cOut.write(buf, 0, n)
                        cOut.flush()
                        rxBytesTotal.addAndGet(n.toLong())
                    }
                } catch (_: Throwable) {
                } finally {
                    try { clientSock.shutdownOutput() } catch (_: Throwable) {}
                    downDone.set(true)
                }
            }

            var flightNum = if (cfg.rawPassthrough) 0 else 1
            var uplinkAppBytes = 0
            var oobShieldDone = false
            val upBuf = ByteArray(STREAM_CHUNK_SIZE)
            val cIn: InputStream = clientSock.getInputStream()

            try {
                while (running.get()) {
                    val n = cIn.read(upBuf)
                    if (n <= 0) break
                    flightNum++

                    var dataArr = upBuf
                    var dataLen = n

                    if (!cfg.rawPassthrough && flightNum == 2 && cfg.vector.coalesceFlight2 && n <= 180) {
                        if (AlanNativeSocket.waitReadable(clientFd, 18)) {
                            val extraBuf = ByteArray(STREAM_CHUNK_SIZE)
                            val extraN = try { cIn.read(extraBuf) } catch (_: Throwable) { -1 }
                            if (extraN > 0) {
                                val merged = ByteArray(n + extraN)
                                System.arraycopy(upBuf, 0, merged, 0, n)
                                System.arraycopy(extraBuf, 0, merged, n, extraN)
                                dataArr = merged
                                dataLen = merged.size
                            }
                        }
                    }

                    val vec = cfg.vector
                    val isMultiFrag = !cfg.rawPassthrough &&
                        vec.family == "FRAGMENT" &&
                        vec.fragFlights > 1 &&
                        flightNum <= vec.fragFlights &&
                        vec.fragSliceMax > 0

                    val useShield = !cfg.rawPassthrough &&
                        ((vec.uplinkOobShield && !oobShieldDone) || vec.uplinkMssClamp > 0) &&
                        uplinkAppBytes < UPLINK_SHIELD_WINDOW_BYTES

                    if (isMultiFrag) {
                        val sentFrag = TlsByteMutator.transmitFragmentedBuffer(
                            out = remoteOut,
                            payload = dataArr,
                            payloadLen = dataLen,
                            minLen = vec.fragSliceMin,
                            maxLen = vec.fragSliceMax,
                            minDelayMs = vec.fragDelayMinMs,
                            maxDelayMs = vec.fragDelayMaxMs
                        )
                        txBytesTotal.addAndGet(sentFrag.toLong())
                        uplinkAppBytes += dataLen
                    } else if (useShield) {
                        val wantOob = vec.uplinkOobShield && !oobShieldDone && (dataArr[0].toInt() and 0xFF) == 0x17
                        if (wantOob) oobShieldDone = true
                        val sentSh = TlsByteMutator.transmitUplinkShieldFrame(
                            sock = remoteSock,
                            out = remoteOut,
                            nativeFd = remoteFd,
                            payload = dataArr,
                            payloadLen = dataLen,
                            injectOob = wantOob,
                            mssClamp = vec.uplinkMssClamp
                        )
                        txBytesTotal.addAndGet(sentSh.toLong())
                        uplinkAppBytes += dataLen
                    } else {
                        remoteOut.write(dataArr, 0, dataLen)
                        remoteOut.flush()
                        txBytesTotal.addAndGet(dataLen.toLong())
                        uplinkAppBytes += dataLen
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { remoteSock.shutdownOutput() } catch (_: Throwable) {}
            }

            var waitedMs = 0
            while (!downDone.get() && waitedMs < 18000 && running.get()) {
                try { Thread.sleep(50L) } catch (_: InterruptedException) { break }
                waitedMs += 50
            }
        } catch (_: Throwable) {
        } finally {
            liveSockets.remove(clientSock)
            liveSockets.remove(remoteSock)
            try { clientPfd?.close() } catch (_: Throwable) {}
            try { remotePfd?.close() } catch (_: Throwable) {}
            try { clientSock.close() } catch (_: Throwable) {}
            try { remoteSock.close() } catch (_: Throwable) {}
        }
    }

    private fun startsWithAscii(buf: ByteArray, prefix: String): Boolean {
        if (buf.size < prefix.length) return false
        for (i in prefix.indices) {
            if (buf[i] != prefix[i].code.toByte()) return false
        }
        return true
    }

    private fun readCompleteTlsClientHello(input: java.io.InputStream, buffer: ByteArray): Int {
        val firstRead = input.read(buffer, 0, buffer.size)
        if (firstRead <= 0) return firstRead
        if (firstRead >= 5 && (buffer[0].toInt() and 0xFF) == 0x16) {
            val recordLen = ((buffer[3].toInt() and 0xFF) shl 8) or (buffer[4].toInt() and 0xFF)
            val totalExpected = (5 + recordLen).coerceAtMost(buffer.size)
            var totalRead = firstRead
            while (totalRead < totalExpected) {
                val r = input.read(buffer, totalRead, totalExpected - totalRead)
                if (r <= 0) break
                totalRead += r
            }
            BridgeDeepDiagnostics.log("[TLS-HELLO] Read complete ClientHello (${totalRead}B, recordLen=${recordLen}B)")
            return totalRead
        }
        return firstRead
    }

}
