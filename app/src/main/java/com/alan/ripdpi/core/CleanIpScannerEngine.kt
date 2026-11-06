package com.alan.ripdpi.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.alan.ripdpi.model.CountryPaletteRegistry
import com.alan.ripdpi.ui.CleanIpHit
import com.alan.ripdpi.ui.ProfileItem
import com.alan.ripdpi.vpn.AlanVpnService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.abs
import kotlin.random.Random

class CleanIpScannerEngine(context: Context) {

    companion object {
        const val DEFAULT_GITHUB_SUBNETS_URL =
            "https://raw.githubusercontent.com/DrAlanK/Alan-RIP-Dpi-Android/main/subnets.txt"

        // Updated 12 SEED_CLEAN_IPS from clean_ip_3.py
        val SEED_CLEAN_IPS: List<String> = listOf(
            "172.64.154.159",
            "172.64.155.88",
            "172.66.40.212",
            "172.66.43.118",
            "172.66.44.110",
            "104.18.32.47",
            "104.18.40.122",
            "104.19.144.33",
            "162.159.135.42",
            "162.159.140.98",
            "188.114.96.3",
            "188.114.97.3"
        )

        // Updated 23 ACTIVE_EDGE_SUBNETS (/24) from clean_ip_3.py
        val DEFAULT_CF_SUBNETS: List<String> = listOf(
            "172.64.148.0/24",
            "172.64.150.0/24",
            "172.64.152.0/24",
            "172.64.154.0/24",
            "172.64.155.0/24",
            "172.66.40.0/24",
            "172.66.43.0/24",
            "172.66.44.0/24",
            "172.66.47.0/24",
            "104.16.132.0/24",
            "104.17.147.0/24",
            "104.18.32.0/24",
            "104.18.38.0/24",
            "104.18.40.0/24",
            "104.19.144.0/24",
            "104.21.48.0/24",
            "104.24.112.0/24",
            "162.159.135.0/24",
            "162.159.137.0/24",
            "162.159.140.0/24",
            "188.114.96.0/24",
            "188.114.97.0/24",
            "188.114.98.0/24"
        )

        val CLOUDFLARE_HTTP_PORTS: Set<Int> = setOf(80, 8080, 8880, 2052, 2082, 2086, 2095)
        val CLOUDFLARE_HTTPS_PORTS: Set<Int> = setOf(443, 2053, 2083, 2087, 2096, 8443)
        val CF_COMPATIBLE_PORTS: Set<Int> = CLOUDFLARE_HTTPS_PORTS + CLOUDFLARE_HTTP_PORTS

        private val CDN_DOMAIN_SUFFIXES = listOf(
            ".pages.dev",
            ".workers.dev",
            ".trycloudflare.com",
            ".cfargotunnel.com"
        )

        fun isPoisonedIranIp(ip: String): Boolean {
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

        fun isCloudflareIpv4Static(ip: String): Boolean {
            val parts = ip.trim().split(".")
            if (parts.size != 4) return false
            val b0 = parts[0].toIntOrNull() ?: return false
            val b1 = parts[1].toIntOrNull() ?: return false
            val b2 = parts[2].toIntOrNull() ?: return false
            val b3 = parts[3].toIntOrNull() ?: return false
            if (b0 !in 0..255 || b1 !in 0..255 || b2 !in 0..255 || b3 !in 0..255) return false
            val v = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3

            fun inCidr(a: Int, b: Int, c: Int, d: Int, prefix: Int): Boolean {
                val base = (a shl 24) or (b shl 16) or (c shl 8) or d
                val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
                return (v and mask) == (base and mask)
            }

            return inCidr(172, 64, 0, 0, 13) ||
                inCidr(104, 16, 0, 0, 13) ||
                inCidr(104, 24, 0, 0, 14) ||
                inCidr(162, 159, 0, 0, 16) ||
                inCidr(162, 158, 0, 0, 15) ||
                inCidr(188, 114, 96, 0, 20) ||
                inCidr(108, 162, 192, 0, 18) ||
                inCidr(141, 101, 64, 0, 18) ||
                inCidr(198, 41, 128, 0, 17) ||
                inCidr(190, 93, 240, 0, 20)
        }

        fun createProtectedRawSocket(): Socket {
            val sock = Socket()
            try { sock.bind(InetSocketAddress(0)) } catch (_: Throwable) {}
            AlanVpnService.protectRawSocket(sock)
            try { sock.tcpNoDelay = true } catch (_: Throwable) {}
            return sock
        }

        private val trustAllManagers: Array<TrustManager> = arrayOf(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )

        private val sharedTlsContext: SSLContext by lazy {
            SSLContext.getInstance("TLS").apply {
                init(null, trustAllManagers, SecureRandom())
            }
        }
    }

    private val appContext = context.applicationContext
    private val githubCacheFile = File(appContext.filesDir, "github_subnets_v120.txt")
    private val verifiedHitsCacheFile = File(appContext.filesDir, "clean_ips_v120.json")
    private val dohCache = ConcurrentHashMap<String, String>()
    private val verifiedPool = Collections.synchronizedList(ArrayList<CleanIpHit>())

    private val protectedSocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = createProtectedRawSocket()
        override fun createSocket(host: String?, port: Int): Socket =
            createProtectedRawSocket().apply { connect(InetSocketAddress(host, port), 2500) }
        override fun createSocket(host: String?, port: Int, lh: InetAddress?, lp: Int): Socket =
            createProtectedRawSocket().apply { connect(InetSocketAddress(host, port), 2500) }
        override fun createSocket(host: InetAddress?, port: Int): Socket =
            createProtectedRawSocket().apply { connect(InetSocketAddress(host, port), 2500) }
        override fun createSocket(a: InetAddress?, p: Int, la: InetAddress?, lp: Int): Socket =
            createProtectedRawSocket().apply { connect(InetSocketAddress(a, p), 2500) }
    }

    private val httpClient = OkHttpClient.Builder()
        .socketFactory(protectedSocketFactory)
        .connectTimeout(2600, TimeUnit.MILLISECONDS)
        .readTimeout(2800, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    init {
        loadCachedCleanHits()
    }

    fun getCachedHits(): List<CleanIpHit> = synchronized(verifiedPool) { verifiedPool.toList() }

    fun loadCachedOrDefaultSubnets(): List<String> {
        if (githubCacheFile.exists()) {
            try {
                val parsed = parseSubnetsText(githubCacheFile.readText(Charsets.UTF_8))
                if (parsed.isNotEmpty()) return parsed
            } catch (_: Throwable) {
            }
        }
        return SEED_CLEAN_IPS + DEFAULT_CF_SUBNETS
    }

    suspend fun fetchSubnetsFromGithub(repoUrl: String = DEFAULT_GITHUB_SUBNETS_URL): List<String> =
        withContext(Dispatchers.IO) {
            val cleanUrl = repoUrl.trim().ifBlank { DEFAULT_GITHUB_SUBNETS_URL }
            try {
                val req = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "Alan-RIP-DPI-Android/1.2.0")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val parsed = parseSubnetsText(body)
                        if (parsed.isNotEmpty()) {
                            githubCacheFile.writeText(parsed.joinToString("\n"), Charsets.UTF_8)
                            return@withContext parsed
                        }
                    }
                }
            } catch (_: Throwable) {
            }
            loadCachedOrDefaultSubnets()
        }

    fun parseSubnetsText(raw: String): List<String> {
        return raw.lineSequence()
            .flatMap { it.split(",", ";", " ").asSequence() }
            .map { it.trim() }
            .filter { token ->
                token.isNotEmpty() &&
                    !token.startsWith("#") &&
                    !token.startsWith("//") &&
                    token.any { ch -> ch.isDigit() } &&
                    token.contains(".")
            }
            .distinct()
            .toList()
    }

    fun isDomesticOrDirectTunnel(profile: ProfileItem): Boolean {
        val proto = profile.protocol.uppercase(Locale.US)
        if (proto == "SHADOWSOCKS" || proto == "SS" || proto == "SOCKS") return true
        val h = profile.originalHost.ifBlank { profile.host }.lowercase(Locale.US).trim()
        return h.endsWith(".ir")
    }

    fun isCloudflareEdgeProfile(profile: ProfileItem): Boolean {
        val proto = profile.protocol.uppercase(Locale.US)
        val uriLower = profile.uri.lowercase(Locale.US)
        if (proto == "SHADOWSOCKS" || proto == "SS" || proto == "SOCKS") return false
        if (uriLower.contains("security=reality")) return false
        if (uriLower.contains("headertype=http") && uriLower.contains("type=tcp")) return false
        if (profile.port !in CF_COMPATIBLE_PORTS) return false

        val hostLower = profile.originalHost.ifBlank { profile.host }.lowercase(Locale.US).trim()
        val sniLower = profile.sni.lowercase(Locale.US).trim()
        if (hostLower.endsWith(".ir")) return false

        // Explicitly exclude non-Cloudflare PaaS / cloud providers (e.g. Railway, Render, Fly, Vercel, Fastly, AWS)
        val nonCfSuffixes = listOf(
            ".railway.app", ".onrender.com", ".fly.dev", ".vercel.app",
            ".netlify.app", ".herokuapp.com", ".koyeb.app", ".hf.space",
            ".cloudfront.net", ".fastly.net", ".googleapis.com", ".azurewebsites.net"
        )
        if (nonCfSuffixes.any { hostLower.endsWith(it) || sniLower.endsWith(it) }) {
            return false
        }

        for (sfx in CDN_DOMAIN_SUFFIXES) {
            if (sniLower.endsWith(sfx) || hostLower.endsWith(sfx)) return true
        }

        if (isCloudflareIpv4Static(hostLower) || isCloudflareIpv4Static(profile.host)) {
            return true
        }

        val resolved = resolveUnpoisonedIpv4Blocking(hostLower, 1300)
        if (resolved.isNotEmpty() && isCloudflareIpv4Static(resolved)) {
            return true
        }
        return false
    }

    fun resolveUnpoisonedIpv4Blocking(host: String, timeoutMs: Int = 1800): String {
        val clean = host.trim().removePrefix("[").removeSuffix("]").lowercase(Locale.US)
        if (clean.isEmpty()) return ""
        if (clean.all { it.isDigit() || it == '.' }) {
            return if (isPoisonedIranIp(clean)) "" else clean
        }
        dohCache[clean]?.let { return it }

        try {
            val all = InetAddress.getAllByName(clean)
            val v4 = all.firstOrNull { it is Inet4Address && !isPoisonedIranIp(it.hostAddress.orEmpty()) }
            if (v4 != null) {
                val ip = v4.hostAddress.orEmpty().trim()
                if (ip.isNotEmpty()) {
                    dohCache[clean] = ip
                    return ip
                }
            }
        } catch (_: Throwable) {
        }

        val dohUrls = listOf(
            "https://1.1.1.1/dns-query?name=$clean&type=A",
            "https://8.8.8.8/resolve?name=$clean&type=A",
            "https://9.9.9.9:5053/dns-query?name=$clean&type=A"
        )
        for (url in dohUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val ans = JSONObject(resp.body?.string().orEmpty()).optJSONArray("Answer") ?: return@use
                    for (i in 0 until ans.length()) {
                        val rec = ans.getJSONObject(i)
                        if (rec.optInt("type") == 1) {
                            val ip = rec.optString("data", "").trim()
                            if (ip.isNotEmpty() && !isPoisonedIranIp(ip)) {
                                dohCache[clean] = ip
                                return ip
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return ""
    }

    suspend fun resolveDomainViaDoh(domain: String): String? = withContext(Dispatchers.IO) {
        resolveUnpoisonedIpv4Blocking(domain, 1800).ifEmpty { null }
    }

    fun fastTcpPingBlocking(ipOrHost: String, port: Int = 443, timeoutMs: Int = 1300): Int {
        val resolved = resolveUnpoisonedIpv4Blocking(ipOrHost, 1200)
        if (resolved.isEmpty()) return -1
        val sock = createProtectedRawSocket()
        return try {
            val t0 = System.nanoTime()
            sock.connect(InetSocketAddress(resolved, port), timeoutMs)
            ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
        } catch (_: Throwable) {
            -1
        } finally {
            try { sock.close() } catch (_: Throwable) {}
        }
    }

    fun sampleCandidateIps(ranges: List<String>, totalCount: Int): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()
        val inputRanges = if (ranges.isEmpty()) (SEED_CLEAN_IPS + DEFAULT_CF_SUBNETS) else ranges

        val cidrEntries = ArrayList<String>()
        for (r in inputRanges) {
            if (!r.contains("/") && !r.contains("-") && r.count { it == '.' } == 3) {
                out[r] = r.substringBeforeLast(".") + ".0/24"
            } else {
                cidrEntries.add(r)
            }
        }
        if (ranges === DEFAULT_CF_SUBNETS) {
            for (seed in SEED_CLEAN_IPS) {
                out[seed] = seed.substringBeforeLast(".") + ".0/24"
            }
        }

        if (cidrEntries.isEmpty()) {
            return out.entries.take(totalCount).map { it.key to it.value }
        }

        val remainingWanted = (totalCount - out.size).coerceAtLeast(16)
        val perRange = maxOf(2, (remainingWanted / cidrEntries.size.coerceAtLeast(1)) + 1)

        for (entry in cidrEntries.shuffled()) {
            if (out.size >= totalCount) break
            if (entry.contains("-") && !entry.contains("/")) {
                val left = entry.substringBefore("-").trim()
                val right = entry.substringAfter("-").trim()
                val startLong = ipv4ToLong(left)
                val endLong = if (right.contains(".")) {
                    ipv4ToLong(right)
                } else {
                    ipv4ToLong("${left.substringBeforeLast(".")}.$right")
                }
                if (startLong != null && endLong != null && endLong >= startLong) {
                    val span = (endLong - startLong + 1L).coerceAtLeast(1L)
                    val cnt = minOf(perRange.toLong(), span).toInt().coerceAtLeast(1)
                    repeat(cnt) {
                        val pick = startLong + Random.nextLong(span)
                        out[longToIpv4(pick)] = entry
                    }
                }
                continue
            }

            val ipPart = entry.substringBefore("/").trim()
            val prefix = entry.substringAfter("/", "24").toIntOrNull()?.coerceIn(8, 32) ?: 24
            val baseLong = ipv4ToLong(ipPart) ?: continue
            if (prefix == 32) {
                out[ipPart] = entry
                continue
            }
            if (prefix == 24) {
                val subPrefix = ipPart.substringBeforeLast(".")
                val octets = (2..252).shuffled().take(minOf(perRange, 12))
                for (lastOctet in octets) {
                    out["$subPrefix.$lastOctet"] = entry
                    if (out.size >= totalCount) break
                }
                continue
            }

            val hostBits = (32 - prefix).coerceIn(1, 20)
            val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val netBase = baseLong and mask
            val maxOffset = (1L shl hostBits) - 2L
            repeat(perRange) {
                val randOffset = if (maxOffset > 2L) Random.nextLong(2L, maxOffset) else 1L
                val candidateLong = netBase or randOffset
                val lastOctet = candidateLong and 0xFFL
                val safeLong = if (lastOctet < 2L || lastOctet > 252L) {
                    (candidateLong and 0xFFFFFF00L) or Random.nextLong(2L, 252L)
                } else {
                    candidateLong
                }
                out[longToIpv4(safeLong)] = entry
            }
        }

        return out.entries.take(totalCount).map { it.key to it.value }
    }

    // Exact Android equivalent of _tls_bio_trace_probe from clean_ip_3.py using SSLEngine + [37, 24, 32] record reframing!
    private fun tlsBioTraceProbe(
        rawSock: Socket,
        probeSni: String,
        port: Int,
        reqBytes: ByteArray
    ): ByteArray {
        val engine: SSLEngine = sharedTlsContext.createSSLEngine(probeSni, port)
        engine.useClientMode = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val params = engine.sslParameters
                params.serverNames = listOf(SNIHostName(probeSni))
                engine.sslParameters = params
            } catch (_: Throwable) {
            }
        }

        var netBufCap = maxOf(16384, engine.session.packetBufferSize)
        var appBufCap = maxOf(16384, engine.session.applicationBufferSize)
        var netOut = ByteBuffer.allocate(netBufCap * 2)
        var netIn = ByteBuffer.allocate(netBufCap * 2).apply { flip() }
        var appIn = ByteBuffer.allocate(appBufCap * 2)

        val sockOut = rawSock.getOutputStream()
        val sockIn = rawSock.getInputStream()
        val wireTmp = ByteArray(16384)
        val emptyBuf = ByteBuffer.allocate(0)
        var firstFlight = true

        fun flushPendingNetOut() {
            netOut.flip()
            val rem = netOut.remaining()
            if (rem > 0) {
                val outBytes = ByteArray(rem)
                netOut.get(outBytes)
                if (firstFlight && outBytes.size > 48 && (outBytes[0].toInt() and 0xFF) == 0x16) {
                    firstFlight = false
                    val recLen = ((outBytes[3].toInt() and 0xFF) shl 8) or (outBytes[4].toInt() and 0xFF)
                    val hsBody = if (5 + recLen <= outBytes.size) {
                        outBytes.copyOfRange(5, 5 + recLen)
                    } else {
                        outBytes.copyOfRange(5, outBytes.size)
                    }
                    val frames = TlsByteMutator.reframeHandshakeChunks(hsBody, listOf(37, 24, 32), 0x0303)
                    val splitAt = minOf(2, frames.size)
                    val burst1 = ByteArrayOutputStream()
                    for (i in 0 until splitAt) burst1.write(frames[i])
                    sockOut.write(burst1.toByteArray())
                    sockOut.flush()

                    if (splitAt < frames.size) {
                        val burst2 = ByteArrayOutputStream()
                        for (i in splitAt until frames.size) burst2.write(frames[i])
                        if (5 + recLen < outBytes.size) {
                            burst2.write(outBytes, 5 + recLen, outBytes.size - (5 + recLen))
                        }
                        sockOut.write(burst2.toByteArray())
                        sockOut.flush()
                    }
                } else {
                    sockOut.write(outBytes)
                    sockOut.flush()
                }
            }
            netOut.clear()
        }

        engine.beginHandshake()
        var hs = engine.handshakeStatus
        var guard = 0
        while (hs != SSLEngineResult.HandshakeStatus.FINISHED &&
            hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING &&
            guard++ < 48
        ) {
            when (hs) {
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    val res = engine.wrap(emptyBuf, netOut)
                    hs = res.handshakeStatus
                    if (res.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        netBufCap *= 2
                        val bigger = ByteBuffer.allocate(netBufCap * 2)
                        netOut.flip()
                        bigger.put(netOut)
                        netOut = bigger
                    } else {
                        flushPendingNetOut()
                    }
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    if (!netIn.hasRemaining()) {
                        netIn.clear()
                        val n = sockIn.read(wireTmp)
                        if (n <= 0) return ByteArray(0)
                        netIn.put(wireTmp, 0, n)
                        netIn.flip()
                    }
                    val res = engine.unwrap(netIn, appIn)
                    hs = res.handshakeStatus
                    when (res.status) {
                        SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                            netIn.compact()
                            val n = sockIn.read(wireTmp)
                            if (n <= 0) return ByteArray(0)
                            if (netIn.remaining() < n) {
                                val bigger = ByteBuffer.allocate(netIn.capacity() + n + 8192)
                                netIn.flip()
                                bigger.put(netIn)
                                netIn = bigger
                            }
                            netIn.put(wireTmp, 0, n)
                            netIn.flip()
                        }
                        SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                            appBufCap *= 2
                            appIn = ByteBuffer.allocate(appBufCap * 2)
                        }
                        SSLEngineResult.Status.CLOSED -> return ByteArray(0)
                        else -> {}
                    }
                }
                SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                    var task = engine.delegatedTask
                    while (task != null) {
                        task.run()
                        task = engine.delegatedTask
                    }
                    hs = engine.handshakeStatus
                }
                else -> break
            }
        }

        val reqBuf = ByteBuffer.wrap(reqBytes)
        while (reqBuf.hasRemaining()) {
            netOut.clear()
            val res = engine.wrap(reqBuf, netOut)
            if (res.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                netOut = ByteBuffer.allocate(netOut.capacity() * 2)
                continue
            }
            flushPendingNetOut()
        }

        val plainBuf = ByteArrayOutputStream(1024)
        for (round in 0 until 4) {
            if (!netIn.hasRemaining()) {
                netIn.clear()
                val n = try { sockIn.read(wireTmp) } catch (_: Throwable) { -1 }
                if (n <= 0) break
                netIn.put(wireTmp, 0, n)
                netIn.flip()
            }
            while (netIn.hasRemaining()) {
                appIn.clear()
                val res = engine.unwrap(netIn, appIn)
                appIn.flip()
                if (appIn.remaining() > 0) {
                    val dec = ByteArray(appIn.remaining())
                    appIn.get(dec)
                    plainBuf.write(dec)
                }
                if (res.status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    netIn.compact()
                    val n = try { sockIn.read(wireTmp) } catch (_: Throwable) { -1 }
                    if (n <= 0) {
                        netIn.flip()
                        break
                    }
                    if (netIn.remaining() < n) {
                        val bigger = ByteBuffer.allocate(netIn.capacity() + n + 8192)
                        netIn.flip()
                        bigger.put(netIn)
                        netIn = bigger
                    }
                    netIn.put(wireTmp, 0, n)
                    netIn.flip()
                } else if (res.status == SSLEngineResult.Status.CLOSED) {
                    break
                }
            }
            if (plainBuf.toString("ISO-8859-1").contains("fl=")) break
        }
        return plainBuf.toByteArray()
    }

    private data class EdgeVerifyOutcome(
        val score: Int,
        val avgRttMs: Int,
        val jitterMs: Int,
        val colo: String
    )

    private fun deepEdgeVerify(
        ip: String,
        port: Int = 443,
        probeSni: String = "speed.cloudflare.com",
        timeoutMs: Int = 2400
    ): EdgeVerifyOutcome? {
        val isPlainHttp = port in CLOUDFLARE_HTTP_PORTS
        val sniHost = probeSni.ifBlank { "speed.cloudflare.com" }
        val crlf = "\r\n"
        val traceReq = (
            "GET /cdn-cgi/trace HTTP/1.1" + crlf +
                "Host: " + sniHost + crlf +
                "User-Agent: Mozilla/5.0" + crlf +
                "Connection: close" + crlf + crlf
            ).toByteArray(Charsets.US_ASCII)

        val samples = ArrayList<Int>(2)
        var detectedColo = "CF"

        for (attempt in 0 until 2) {
            val rawSock = createProtectedRawSocket()
            rawSock.soTimeout = timeoutMs
            try {
                val t0 = System.nanoTime()
                rawSock.connect(InetSocketAddress(ip, port), timeoutMs)
                val rxBytes: ByteArray = if (isPlainHttp) {
                    val out = rawSock.getOutputStream()
                    out.write(traceReq)
                    out.flush()
                    val buf = ByteArray(1024)
                    val n = rawSock.getInputStream().read(buf)
                    if (n > 0) buf.copyOf(n) else ByteArray(0)
                } else {
                    tlsBioTraceProbe(rawSock, sniHost, port, traceReq)
                }

                if (rxBytes.isEmpty()) return null
                val text = String(rxBytes, Charsets.ISO_8859_1)
                if (!text.contains("fl=")) return null

                detectedColo = extractColo(text)
                val elapsed = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
                samples.add(elapsed)
            } catch (_: Throwable) {
                return null
            } finally {
                try { rawSock.close() } catch (_: Throwable) {}
            }
        }

        if (samples.size < 2) return null
        val avg = (samples[0] + samples[1]) / 2
        val jitter = abs(samples[1] - samples[0])
        return EdgeVerifyOutcome(avg + jitter * 2, avg, jitter, detectedColo)
    }

    suspend fun scanCleanIps(
        subnets: List<String>,
        port: Int = 443,
        maxResults: Int = 20,
        totalCandidates: Int = 64,
        onProgress: (percent: Int, tested: Int, total: Int, hitsCount: Int) -> Unit,
        onHitUpdate: (List<CleanIpHit>) -> Unit
    ): List<CleanIpHit> = withContext(Dispatchers.IO) {
        val checkPort = if (port in CF_COMPATIBLE_PORTS) port else 443
        val candidates = sampleCandidateIps(subnets, totalCandidates.coerceIn(28, 220))
        if (candidates.isEmpty()) return@withContext emptyList()

        val tcpSem = Semaphore(22)
        val tcpWinners = Collections.synchronizedList(ArrayList<Triple<Int, String, String>>())
        var tcpDone = 0
        val totalPool = candidates.size

        try {
            coroutineScope {
                candidates.map { (ip, cidr) ->
                    async {
                        if (!isActive) return@async
                        tcpSem.withPermit {
                            if (!isActive) return@withPermit
                            val rtt = fastTcpPingBlocking(ip, checkPort, 1300)
                            synchronized(tcpWinners) {
                                tcpDone++
                                if (rtt >= 0) {
                                    tcpWinners.add(Triple(rtt, ip, cidr))
                                }
                                val pct = (10 + ((tcpDone * 50) / totalPool)).coerceIn(10, 60)
                                onProgress(pct, tcpDone, totalPool, tcpWinners.size)
                            }
                        }
                    }
                }.awaitAll()
            }

            if (tcpWinners.isEmpty()) {
                return@withContext emptyList()
            }

            val shortlist = synchronized(tcpWinners) {
                tcpWinners.sortedBy { it.first }.take(minOf(18, maxResults + 6))
            }
            val verifySem = Semaphore(8)
            val verifiedHits = Collections.synchronizedList(ArrayList<Pair<Int, CleanIpHit>>())
            var vDone = 0

            coroutineScope {
                shortlist.map { (_, ip, cidr) ->
                    async {
                        if (!isActive) return@async
                        verifySem.withPermit {
                            if (!isActive) return@withPermit
                            val v = deepEdgeVerify(ip, checkPort, "speed.cloudflare.com", 2400)
                            synchronized(verifiedHits) {
                                vDone++
                                if (v != null) {
                                    val hit = CleanIpHit(
                                        ip = ip,
                                        port = checkPort,
                                        latencyMs = v.avgRttMs,
                                        subnet = cidr,
                                        colo = v.colo
                                    )
                                    verifiedHits.add(v.score to hit)
                                    val live = verifiedHits.sortedBy { it.first }.map { it.second }.take(maxResults)
                                    onHitUpdate(live)
                                }
                                val pct = (60 + ((vDone * 40) / shortlist.size.coerceAtLeast(1))).coerceIn(60, 100)
                                onProgress(pct, vDone, shortlist.size, verifiedHits.size)
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalResults: List<CleanIpHit> = synchronized(verifiedHits) {
                if (verifiedHits.isNotEmpty()) {
                    verifiedHits.sortedBy { it.first }.map { it.second }.take(maxResults)
                } else {
                    shortlist.take(maxResults).map { (tcpMs, ip, cidr) ->
                        CleanIpHit(ip = ip, port = checkPort, latencyMs = tcpMs, subnet = cidr, colo = "CF")
                    }
                }
            }
            onHitUpdate(finalResults)

            if (finalResults.isNotEmpty()) {
                synchronized(verifiedPool) {
                    for (h in finalResults) {
                        verifiedPool.removeAll { it.ip == h.ip }
                        verifiedPool.add(h)
                    }
                    verifiedPool.sortBy { it.latencyMs }
                    while (verifiedPool.size > 40) verifiedPool.removeAt(verifiedPool.lastIndex)
                }
                saveCachedCleanHits()
            }
            return@withContext finalResults
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            return@withContext emptyList()
        }
    }

    suspend fun quickFindBestCleanIps(port: Int = 443, neededCount: Int = 3, sampleCount: Int = 40): List<CleanIpHit> =
        withContext(Dispatchers.IO) {
            val safePort = if (port in CF_COMPATIBLE_PORTS) port else 443
            val cachedSnapshot = synchronized(verifiedPool) { verifiedPool.take(8) }
            val aliveCached = ArrayList<CleanIpHit>()

            if (cachedSnapshot.isNotEmpty()) {
                coroutineScope {
                    cachedSnapshot.map { c ->
                        async {
                            val rtt = fastTcpPingBlocking(c.ip, safePort, 1000)
                            if (rtt > 0) c.copy(port = safePort, latencyMs = rtt) else null
                        }
                    }.awaitAll().filterNotNull().sortedBy { it.latencyMs }.forEach { aliveCached.add(it) }
                }
                if (aliveCached.size >= neededCount) {
                    return@withContext aliveCached.take(neededCount)
                }
            }

            val subnets = loadCachedOrDefaultSubnets()
            val fresh = scanCleanIps(
                subnets = subnets,
                port = safePort,
                maxResults = neededCount.coerceAtLeast(4),
                totalCandidates = sampleCount.coerceAtLeast(32),
                onProgress = { _, _, _, _ -> },
                onHitUpdate = {}
            )
            (aliveCached + fresh).distinctBy { it.ip }.sortedBy { it.latencyMs }.take(neededCount)
        }

    fun buildProfileWithCleanIp(
        original: ProfileItem,
        cleanHit: CleanIpHit,
        cloneTagIndex: Int = 0
    ): ProfileItem? {
        val spec = UriConfigCodec.parseFullConfig(original.uri) ?: return null
        val edgeDomain = listOf(
            original.originalHost,
            spec.sni,
            spec.hostHeader,
            spec.address,
            original.sni
        ).firstOrNull { cand ->
            val t = cand.trim()
            t.isNotEmpty() && !t.all { it.isDigit() || it == '.' }
        } ?: spec.sni.ifBlank { spec.hostHeader.ifBlank { spec.address } }

        val coloTag = cleanHit.colo.ifBlank { "CF" }
        val baseTitle = original.name
            .substringBefore("[⚡")
            .substringBefore("[Edge-")
            .trim()
        val newTitle = if (cloneTagIndex > 0) {
            "$baseTitle [Edge-$cloneTagIndex | ${cleanHit.latencyMs}ms]"
        } else {
            "$baseTitle [⚡$coloTag]"
        }

        val updatedSpec = spec.copy(
            name = newTitle,
            address = cleanHit.ip,
            port = if (original.port in 1..65535) original.port else cleanHit.port,
            hostHeader = spec.hostHeader.ifBlank { edgeDomain },
            sni = if (spec.security.equals("tls", true) || spec.security.equals("reality", true)) {
                spec.sni.ifBlank { edgeDomain }
            } else {
                spec.sni
            }
        )

        val rebuiltUri = UriConfigCodec.buildUriFromFullSpec(updatedSpec)
        if (rebuiltUri.isBlank()) return null
        val parsed = UriConfigCodec.parseUriToProfileItem(rebuiltUri) ?: return null
        val country = if (original.countryCode != "UN") {
            CountryPaletteRegistry.resolveByCode(original.countryCode)
        } else {
            CountryPaletteRegistry.detectCountry(newTitle, edgeDomain, edgeDomain)
        }

        return parsed.copy(
            originalHost = edgeDomain.ifBlank { original.originalHost },
            sni = updatedSpec.sni.ifBlank { edgeDomain },
            pingMs = cleanHit.latencyMs,
            status = "VERIFIED",
            preferredVectorId = original.preferredVectorId,
            cachedVectorId = original.cachedVectorId,
            countryCode = country.code,
            countryName = country.nameFa,
            flagEmoji = country.flagEmoji,
            flagStripeColors = country.stripeColors,
            accentPrimaryColor = country.accentPrimary,
            accentSecondaryColor = country.accentSecondary
        )
    }

    private fun extractColo(resp: String): String {
        for (line in resp.lines()) {
            val t = line.trim()
            if (t.startsWith("colo=", ignoreCase = true)) {
                return t.substringAfter("=").trim().uppercase(Locale.US).take(4).ifBlank { "CF" }
            }
            if (t.startsWith("cf-ray:", ignoreCase = true) && t.contains("-")) {
                return t.substringAfterLast("-").trim().uppercase(Locale.US).take(4).ifBlank { "CF" }
            }
        }
        return "CF"
    }

    private fun ipv4ToLong(ip: String): Long? {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return null
        var acc = 0L
        for (p in parts) {
            val n = p.toLongOrNull() ?: return null
            if (n !in 0L..255L) return null
            acc = (acc shl 8) or n
        }
        return acc and 0xFFFFFFFFL
    }

    private fun longToIpv4(v: Long): String {
        val u = v and 0xFFFFFFFFL
        return "${(u ushr 24) and 0xFFL}.${(u ushr 16) and 0xFFL}.${(u ushr 8) and 0xFFL}.${u and 0xFFL}"
    }

    private fun loadCachedCleanHits() {
        if (!verifiedHitsCacheFile.exists()) return
        try {
            val arr = JSONArray(verifiedHitsCacheFile.readText(Charsets.UTF_8))
            synchronized(verifiedPool) {
                verifiedPool.clear()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ip = o.optString("ip", "")
                    if (ip.isNotEmpty()) {
                        verifiedPool.add(
                            CleanIpHit(
                                ip = ip,
                                port = o.optInt("port", 443),
                                latencyMs = o.optInt("latencyMs", 120),
                                subnet = o.optString("subnet", ""),
                                colo = o.optString("colo", "CF")
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun saveCachedCleanHits() {
        try {
            val arr = JSONArray()
            val snap = synchronized(verifiedPool) { verifiedPool.toList() }
            for (h in snap) {
                arr.put(
                    JSONObject().apply {
                        put("ip", h.ip)
                        put("port", h.port)
                        put("latencyMs", h.latencyMs)
                        put("subnet", h.subnet)
                        put("colo", h.colo)
                    }
                )
            }
            verifiedHitsCacheFile.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }
}
