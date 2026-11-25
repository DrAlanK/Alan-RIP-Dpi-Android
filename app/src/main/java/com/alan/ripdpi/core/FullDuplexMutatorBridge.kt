package com.alan.ripdpi.core

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.alan.ripdpi.model.BypassVector
import com.alan.ripdpi.model.VectorCatalog
import com.alan.ripdpi.vpn.AlanVpnService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import kotlin.random.Random

class FullDuplexMutatorBridge(
    val listenPort: Int = 20809,
    initialRemoteHost: String = "",
    initialRemotePort: Int = 443,
    initialVector: BypassVector = VectorCatalog.VECTOR_REGISTRY.first(),
    initialCustomStride: Int = 400,
    initialJitterBytes: Int = 20,
    initialRawPassthrough: Boolean = false,
    val socks5DynamicMode: Boolean = false,
    private val verboseSessionLogs: Boolean = false,
    private val protectSocket: ((Socket) -> Boolean)? = null,
    private val onLog: ((String) -> Unit)? = null
) {

    companion object {
        // Mobile-optimized asymmetric buffers: 512KB RX / 96KB TX prevents 200MB+ RAM spikes & GC battery drain
        private const val RX_SOCK_BUF = 512 * 1024
        private const val TX_ASYMMETRIC_BUF = 98304
        private const val TX_BULK_FASTPATH_BUF = 393216
        private const val TCP_MAXSEG_OPT = 2
        private const val TCP_MAXSEG_VALUE = 1220
        private const val STREAM_CHUNK_SIZE = 32768
        private const val UPLINK_SHIELD_WINDOW_BYTES = 16384
        private const val CONNECT_TIMEOUT_MS = 6500
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

        private val dohClient: OkHttpClient by lazy {
            AlanCaSanitizer.applyTo(
                OkHttpClient.Builder()
                    .socketFactory(dohSocketFactory)
                    .connectTimeout(2000, TimeUnit.MILLISECONDS)
                    .readTimeout(2000, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(false)
            ).build()
        }

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

    // Bounded self-shrinking thread pool: idle threads terminate after 15s (zero idle CPU/battery!)
    private val ioPool = ThreadPoolExecutor(
        2,
        128,
        15L,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        ThreadFactory { r -> Thread(r, "AlanBridgeIO").apply { isDaemon = true } }
    ).apply {
        allowCoreThreadTimeOut(true)
        setRejectedExecutionHandler { r, _ ->
            if (running.get()) {
                Thread(r, "AlanBridgeOverflow").apply { isDaemon = true }.start()
            }
        }
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
        try {
            srv.bind(InetSocketAddress("127.0.0.1", listenPort), 128)
        } catch (_: BindException) {
            onLog?.invoke("[Bridge] Port $listenPort busy (EADDRINUSE) -> auto-allocating ephemeral port")
            srv.bind(InetSocketAddress("127.0.0.1", 0), 128)
        }
        boundPort = srv.localPort
        serverSocket = srv
        onLog?.invoke("[Bridge] Active 127.0.0.1:$boundPort (RX=512KB, TX=96KB, MSS=$TCP_MAXSEG_VALUE)")
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
                                        onLog?.invoke("[DoH] $clean -> $ipStr")
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
            if (n <= 0) return if (hdrRead > 0) hdr.copyOf(hdrRead) else ByteArray(0)
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

    private fun negotiateSocks5Target(clientSock: Socket): Pair<String, Int>? {
        return try {
            clientSock.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
            val ins = clientSock.getInputStream()
            val out = clientSock.getOutputStream()

            val ver = ins.read()
            if (ver != 0x05) return null
            val nMethods = ins.read()
            if (nMethods <= 0) return null
            val methods = ByteArray(nMethods)
            var rm = 0
            while (rm < nMethods) {
                val n = ins.read(methods, rm, nMethods - rm)
                if (n <= 0) return null
                rm += n
            }
            out.write(byteArrayOf(0x05, 0x00))
            out.flush()

            val hdr = ByteArray(4)
            var rh = 0
            while (rh < 4) {
                val n = ins.read(hdr, rh, 4 - rh)
                if (n <= 0) return null
                rh += n
            }
            if ((hdr[0].toInt() and 0xFF) != 0x05) return null

            val atyp = hdr[3].toInt() and 0xFF
            val targetHost = when (atyp) {
                0x01 -> {
                    val ip4 = ByteArray(4)
                    var i = 0
                    while (i < 4) {
                        val n = ins.read(ip4, i, 4 - i)
                        if (n <= 0) return null
                        i += n
                    }
                    InetAddress.getByAddress(ip4).hostAddress ?: return null
                }
                0x03 -> {
                    val len = ins.read()
                    if (len <= 0) return null
                    val dom = ByteArray(len)
                    var i = 0
                    while (i < len) {
                        val n = ins.read(dom, i, len - i)
                        if (n <= 0) return null
                        i += n
                    }
                    String(dom, Charsets.UTF_8).trim()
                }
                0x04 -> {
                    val ip6 = ByteArray(16)
                    var i = 0
                    while (i < 16) {
                        val n = ins.read(ip6, i, 16 - i)
                        if (n <= 0) return null
                        i += n
                    }
                    InetAddress.getByAddress(ip6).hostAddress ?: return null
                }
                else -> return null
            }

            val pHi = ins.read()
            val pLo = ins.read()
            if (pHi < 0 || pLo < 0) return null
            val targetPort = ((pHi and 0xFF) shl 8) or (pLo and 0xFF)
            targetHost to targetPort
        } catch (_: Throwable) {
            null
        }
    }

    private fun transmitUplinkStream(
        remoteSock: Socket,
        remoteOut: OutputStream,
        dataArr: ByteArray,
        dataLen: Int,
        vec: BypassVector,
        flightNum: Int,
        sessionTxBytes: Long,
        stride: Int,
        jitter: Int,
        fastpathUnlocked: Boolean
    ): Boolean {
        if (dataLen <= 0) return fastpathUnlocked

        if (vec.hybridMode == "regional_pmtu_1180" || vec.hybridMode == "anti_16kb_flow_stall") {
            val maxCap = if (vec.hybridMode == "regional_pmtu_1180") 1140 else 880
            if (dataLen > maxCap) {
                var pos = 0
                var cIdx = 0
                while (pos < dataLen) {
                    val jit = Random.nextInt(-40, 21)
                    val step = (maxCap + jit).coerceIn(360, maxCap).coerceAtMost(dataLen - pos)
                    remoteOut.write(dataArr, pos, step)
                    remoteOut.flush()
                    pos += step
                    cIdx++
                    if (pos < dataLen && cIdx % 4 == 0) {
                        try { Thread.sleep(0L, 400_000) } catch (_: Throwable) {}
                    }
                }
            } else {
                remoteOut.write(dataArr, 0, dataLen)
                remoteOut.flush()
            }
            txBytesTotal.addAndGet(dataLen.toLong())
            return fastpathUnlocked
        }

        if (vec.hybridMode == "fastpath_nic_offload_bypass") {
            var unlocked = fastpathUnlocked
            if (!unlocked && (sessionTxBytes >= 16384L || flightNum >= 4)) {
                try { remoteSock.sendBufferSize = TX_BULK_FASTPATH_BUF } catch (_: Throwable) {}
                unlocked = true
            }
            if (unlocked) {
                var pos = 0
                val step = 32768
                while (pos < dataLen) {
                    val sz = minOf(step, dataLen - pos)
                    remoteOut.write(dataArr, pos, sz)
                    remoteOut.flush()
                    pos += sz
                }
                txBytesTotal.addAndGet(dataLen.toLong())
                return true
            }
        }

        if (vec.hybridMode == "cwnd_token_bucket") {
            if (sessionTxBytes <= 49152L && dataLen > 900) {
                var pos = 0
                var segCnt = 0
                while (pos < dataLen) {
                    val step = Random.nextInt(880, 1121).coerceAtMost(dataLen - pos)
                    remoteOut.write(dataArr, pos, step)
                    remoteOut.flush()
                    pos += step
                    segCnt++
                    if (pos < dataLen && segCnt % 3 == 0) {
                        try { Thread.sleep(0L, 500_000) } catch (_: Throwable) {}
                    }
                }
                txBytesTotal.addAndGet(dataLen.toLong())
                return fastpathUnlocked
            }
        }

        val clamp = vec.uplinkMssClamp
        if (flightNum in 2..5 && dataLen > 360 && sessionTxBytes <= 32768L && clamp > 0) {
            val baseStep = minOf(clamp, maxOf(280, stride * 2))
            var pos = 0
            var segIdx = 0
            while (pos < dataLen) {
                val jb = minOf(40, jitter)
                val jit = if (jb > 0) Random.nextInt(-jb, jb + 1) else 0
                val step = (baseStep + jit).coerceIn(200, 1280).coerceAtMost(dataLen - pos)
                remoteOut.write(dataArr, pos, step)
                remoteOut.flush()
                pos += step
                segIdx++
                if (pos < dataLen && segIdx % 3 == 0) {
                    try { Thread.sleep(0L, 600_000) } catch (_: Throwable) {}
                }
            }
            txBytesTotal.addAndGet(dataLen.toLong())
            return fastpathUnlocked
        }

        if (dataLen > 1420 && clamp > 0) {
            val maxSeg = clamp.coerceIn(512, 1380)
            var pos = 0
            var burstChunks = 0
            while (pos < dataLen) {
                val jit = Random.nextInt(-28, 29)
                val step = (maxSeg + jit).coerceIn(420, 1380).coerceAtMost(dataLen - pos)
                remoteOut.write(dataArr, pos, step)
                remoteOut.flush()
                pos += step
                burstChunks++
                if (pos < dataLen && burstChunks % 8 == 0) {
                    try { Thread.sleep(0L, 400_000) } catch (_: Throwable) {}
                }
            }
            txBytesTotal.addAndGet(dataLen.toLong())
            return fastpathUnlocked
        }

        remoteOut.write(dataArr, 0, dataLen)
        remoteOut.flush()
        txBytesTotal.addAndGet(dataLen.toLong())
        return fastpathUnlocked
    }

    private fun handleSession(clientSock: Socket, sid: Int) {
        val cfg = routeRef.get()
        if (cfg.remoteHost.isBlank() && !socks5DynamicMode) {
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
            try {
                clientSock.receiveBufferSize = RX_SOCK_BUF
                clientSock.sendBufferSize = RX_SOCK_BUF
            } catch (_: Throwable) {}

            val (effectiveHost, effectivePort) = if (socks5DynamicMode) {
                negotiateSocks5Target(clientSock) ?: return
            } else {
                cfg.remoteHost to cfg.remotePort
            }

            val targetAddr = resolveTargetAddress(effectiveHost)
            if (targetAddr is Inet4Address) {
                try { remoteSock.bind(InetSocketAddress(0)) } catch (_: Throwable) {}
            }
            try { protectSocket?.invoke(remoteSock) } catch (_: Throwable) {}
            AlanVpnService.protectRawSocket(remoteSock)
            remoteSock.tcpNoDelay = true
            remoteSock.keepAlive = true
            try {
                remoteSock.receiveBufferSize = RX_SOCK_BUF
                remoteSock.sendBufferSize = TX_ASYMMETRIC_BUF
            } catch (_: Throwable) {}

            val t0 = System.nanoTime()
            remoteSock.connect(InetSocketAddress(targetAddr, effectivePort), CONNECT_TIMEOUT_MS)
            val tcpMs = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)

            if (socks5DynamicMode) {
                val cOut = clientSock.getOutputStream()
                cOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                cOut.flush()
            }

            AlanVpnService.protectRawSocket(remoteSock)

            // Only duplicate FDs when needed for native OOB / coalesce / TCP_MAXSEG
            val needClientFd = !cfg.rawPassthrough && cfg.vector.coalesceFlight2
            val needRemoteFd = !cfg.rawPassthrough
            if (needClientFd) {
                clientPfd = try { ParcelFileDescriptor.fromSocket(clientSock) } catch (_: Throwable) { null }
            }
            if (needRemoteFd) {
                remotePfd = try { ParcelFileDescriptor.fromSocket(remoteSock) } catch (_: Throwable) { null }
                try {
                    remotePfd?.fileDescriptor?.let { fdesc ->
                        Os.setsockoptInt(fdesc, OsConstants.IPPROTO_TCP, TCP_MAXSEG_OPT, TCP_MAXSEG_VALUE)
                    }
                } catch (_: Throwable) {}
            }
            val clientFd = clientPfd?.fd ?: -1
            val remoteFd = remotePfd?.fd ?: -1

            if (remoteFd >= 0) {
                AlanNativeSocket.tuneTcpSocket(remoteFd, TX_ASYMMETRIC_BUF)
            }

            val remoteOut = remoteSock.getOutputStream()
            var sessionTxBytes = 0L

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
                        if (verboseSessionLogs || sid <= 3) {
                            onLog?.invoke("[S#%04d] %s:%d TCP=%dms | %s: %s".format(sid, targetAddr.hostAddress, effectivePort, tcpMs, cfg.vector.vectorId, trace))
                        }
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
                        if (verboseSessionLogs || sid <= 3) {
                            onLog?.invoke("[S#%04d] %s:%d TCP=%dms | %s: %s".format(sid, targetAddr.hostAddress, effectivePort, tcpMs, cfg.vector.vectorId, trace))
                        }
                        sent
                    }
                    else -> {
                        remoteOut.write(firstData)
                        remoteOut.flush()
                        firstData.size
                    }
                }
                txBytesTotal.addAndGet(sentBytes.toLong())
                sessionTxBytes += sentBytes.toLong()
            }

            // Free duplicated ParcelFileDescriptors right after Flight 1/2 if not needed to avoid FD accumulation!
            if (cfg.rawPassthrough || (!cfg.vector.uplinkOobShield && !cfg.vector.coalesceFlight2)) {
                try { clientPfd?.close() } catch (_: Throwable) {}
                clientPfd = null
                try { remotePfd?.close() } catch (_: Throwable) {}
                remotePfd = null
            }

            remoteSock.soTimeout = 0
            clientSock.soTimeout = 0

            val downLatch = CountDownLatch(1)
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
                    downLatch.countDown()
                }
            }

            var flightNum = if (cfg.rawPassthrough) 0 else 1
            var uplinkAppBytes = 0
            var oobShieldDone = false
            var fastpathUnlocked = false
            val upBuf = ByteArray(STREAM_CHUNK_SIZE)
            val cIn: InputStream = clientSock.getInputStream()

            try {
                while (running.get()) {
                    val n = cIn.read(upBuf)
                    if (n <= 0) break
                    flightNum++
                    sessionTxBytes += n.toLong()

                    var dataArr = upBuf
                    var dataLen = n

                    if (!cfg.rawPassthrough && flightNum == 2 && cfg.vector.coalesceFlight2 && n <= 180 && clientPfd != null) {
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
                        try { clientPfd?.close() } catch (_: Throwable) {}
                        clientPfd = null
                    }

                    val vec = cfg.vector
                    val isAsymMultiFlight = !cfg.rawPassthrough &&
                        flightNum <= vec.fragFlights &&
                        (vec.hybridMode == "multiflight_asymmetric_0x17" || vec.hybridMode == "anti_16kb_flow_stall")

                    val isMultiFrag = !cfg.rawPassthrough &&
                        vec.family == "FRAGMENT" &&
                        vec.fragFlights > 1 &&
                        flightNum <= vec.fragFlights &&
                        vec.fragSliceMax > 0

                    val useShield = !cfg.rawPassthrough &&
                        ((vec.uplinkOobShield && !oobShieldDone) || (vec.uplinkMssClamp > 0 && vec.hybridMode == "none")) &&
                        uplinkAppBytes < UPLINK_SHIELD_WINDOW_BYTES

                    when {
                        cfg.rawPassthrough || vec.family == "DIRECT" -> {
                            remoteOut.write(dataArr, 0, dataLen)
                            remoteOut.flush()
                            txBytesTotal.addAndGet(dataLen.toLong())
                            uplinkAppBytes += dataLen
                        }
                        isAsymMultiFlight -> {
                            val sentAsym = TlsByteMutator.transmitAsymmetricAppFlight(
                                out = remoteOut,
                                payload = dataArr,
                                payloadLen = dataLen,
                                flightIndex = flightNum,
                                stride = cfg.customStride,
                                jitter = cfg.jitterBytes
                            )
                            txBytesTotal.addAndGet(sentAsym.toLong())
                            uplinkAppBytes += dataLen
                        }
                        isMultiFrag -> {
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
                        }
                        useShield -> {
                            val wantOob = vec.uplinkOobShield && !oobShieldDone && (dataArr[0].toInt() and 0xFF) == 0x17
                            if (wantOob) oobShieldDone = true
                            val sentSh = TlsByteMutator.transmitUplinkShieldFrame(
                                sock = remoteSock,
                                out = remoteOut,
                                nativeFd = if (remotePfd != null) remoteFd else -1,
                                payload = dataArr,
                                payloadLen = dataLen,
                                injectOob = wantOob,
                                mssClamp = vec.uplinkMssClamp
                            )
                            if (oobShieldDone && remotePfd != null) {
                                try { remotePfd?.close() } catch (_: Throwable) {}
                                remotePfd = null
                            }
                            txBytesTotal.addAndGet(sentSh.toLong())
                            uplinkAppBytes += dataLen
                        }
                        else -> {
                            fastpathUnlocked = transmitUplinkStream(
                                remoteSock = remoteSock,
                                remoteOut = remoteOut,
                                dataArr = dataArr,
                                dataLen = dataLen,
                                vec = vec,
                                flightNum = flightNum,
                                sessionTxBytes = sessionTxBytes,
                                stride = cfg.customStride,
                                jitter = cfg.jitterBytes,
                                fastpathUnlocked = fastpathUnlocked
                            )
                            uplinkAppBytes += dataLen
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { remoteSock.shutdownOutput() } catch (_: Throwable) {}
            }

            // Zero-CPU wait for remote downlink shutdown (replaces 50ms busy-polling loop!)
            try {
                downLatch.await(8L, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
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
}
