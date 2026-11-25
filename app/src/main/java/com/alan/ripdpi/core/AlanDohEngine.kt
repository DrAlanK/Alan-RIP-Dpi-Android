package com.alan.ripdpi.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory

data class DohEndpointSpec(
    val id: String,
    val name: String,
    val url: String,
    val bootstrapIp: String = "",
    val category: String = "Global"
)

data class DohProbeResult(
    val id: String,
    val name: String,
    val url: String,
    val bootstrapIp: String,
    val category: String,
    val latencyMs: Int,
    val resolvedSampleIp: String,
    val isHealthy: Boolean,
    val statusDetail: String
)

object AlanDohEngine {

    val BUILTIN_DOH_SERVERS: List<DohEndpointSpec> = listOf(
        DohEndpointSpec("cf-main", "Cloudflare DoH", "https://cloudflare-dns.com/dns-query", "172.64.154.14", "Global"),
        DohEndpointSpec("cf-mozilla", "Cloudflare Mozilla", "https://mozilla.cloudflare-dns.com/dns-query", "104.16.249.249", "Global"),
        DohEndpointSpec("google-dns", "Google Public DoH", "https://dns.google/dns-query", "8.8.8.8", "Global"),
        DohEndpointSpec("quad9-main", "Quad9 Secure DoH", "https://dns.quad9.net/dns-query", "9.9.9.9", "Security"),
        DohEndpointSpec("quad9-ecs", "Quad9 ECS DoH", "https://dns11.quad9.net/dns-query", "149.112.112.11", "Global"),
        DohEndpointSpec("rethink-sky", "RethinkDNS Sky", "https://sky.rethinkdns.com/dns-query", "172.64.154.14", "Anti-DPI"),
        DohEndpointSpec("rethink-max", "RethinkDNS Max", "https://max.rethinkdns.com/dns-query", "104.18.32.47", "Anti-DPI"),
        DohEndpointSpec("mullvad-base", "Mullvad Privacy", "https://dns.mullvad.net/dns-query", "194.242.2.2", "Privacy"),
        DohEndpointSpec("adguard-default", "AdGuard DoH", "https://dns.adguard-dns.com/dns-query", "94.140.14.14", "AdBlock"),
        DohEndpointSpec("adguard-unfiltered", "AdGuard Unfiltered", "https://unfiltered.adguard-dns.com/dns-query", "94.140.14.140", "Global"),
        DohEndpointSpec("controld-free", "ControlD Free", "https://freedns.controld.com/p0", "76.76.2.0", "Global"),
        DohEndpointSpec("wikimedia", "Wikimedia DoH", "https://wikimedia-dns.org/dns-query", "185.71.138.138", "Privacy"),
        DohEndpointSpec("opendns", "Cisco OpenDNS", "https://doh.opendns.com/dns-query", "208.67.222.222", "Global"),
        DohEndpointSpec("cleanbrowsing", "CleanBrowsing", "https://doh.cleanbrowsing.org/doh/security-filter/", "185.228.168.9", "Security"),
        DohEndpointSpec("nextdns", "NextDNS Anycast", "https://dns.nextdns.io/dns-query", "45.90.28.0", "Global"),
        DohEndpointSpec("dns-sb", "DNS.SB Privacy", "https://doh.dns.sb/dns-query", "185.222.222.222", "Privacy"),
        DohEndpointSpec("switch-ch", "SWITCH Switzerland", "https://dns.switch.ch/dns-query", "130.59.31.248", "Europe"),
        DohEndpointSpec("digitale-ch", "Digitale Gesellschaft", "https://dns.digitale-gesellschaft.ch/dns-query", "185.95.218.42", "Europe"),
        DohEndpointSpec("libredns", "LibreDNS Europe", "https://doh.libredns.gr/dns-query", "116.202.176.26", "Europe"),
        DohEndpointSpec("ffmuc-de", "FFMUC Germany", "https://doh.ffmuc.net/dns-query", "5.1.66.255", "Europe"),
        DohEndpointSpec("cira-ca", "CIRA Shield", "https://private.canadianshield.cira.ca/dns-query", "149.112.121.10", "Global"),
        DohEndpointSpec("shecan-ir", "Shecan DoH", "https://free.shecan.ir/dns-query", "178.22.122.100", "Iran"),
        DohEndpointSpec("electro-ir", "Electro DoH", "https://78.157.42.100/dns-query", "78.157.42.100", "Iran")
    )

    private val customServers = Collections.synchronizedList(ArrayList<DohEndpointSpec>())
    private val verifiedResults = Collections.synchronizedList(ArrayList<DohProbeResult>())
    private val resolvedCache = ConcurrentHashMap<String, String>()

    @Volatile
    var activeDohUrl: String = "https://cloudflare-dns.com/dns-query"

    @Volatile
    var activeBootstrapIp: String = "172.64.154.14"

    fun getAllConfiguredEndpoints(): List<DohEndpointSpec> {
        val merged = LinkedHashMap<String, DohEndpointSpec>()
        synchronized(customServers) {
            for (s in customServers) merged[s.url] = s
        }
        for (s in BUILTIN_DOH_SERVERS) {
            merged.putIfAbsent(s.url, s)
        }
        return merged.values.toList()
    }

    fun getVerifiedResults(): List<DohProbeResult> = synchronized(verifiedResults) { verifiedResults.toList() }

    fun addCustomDohUrls(rawText: String): Int {
        var added = 0
        rawText.lineSequence()
            .map { it.substringBefore("#").trim() }
            .filter { it.startsWith("https://", ignoreCase = true) && it.length > 12 }
            .forEach { url ->
                val host = try { java.net.URI(url).host.orEmpty() } catch (_: Throwable) { "Custom-DoH" }
                val spec = DohEndpointSpec(
                    id = "custom-${url.hashCode()}",
                    name = host.ifBlank { "Custom DoH" },
                    url = url,
                    bootstrapIp = "",
                    category = "Custom"
                )
                synchronized(customServers) {
                    if (customServers.none { it.url.equals(url, ignoreCase = true) }) {
                        customServers.add(0, spec)
                        added++
                    }
                }
            }
        return added
    }

    private fun isValidIpv4(ip: String): Boolean {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return false
        return parts.all { p -> val n = p.toIntOrNull(); n != null && n in 0..255 }
    }

    private fun buildDnsWireQuery(domain: String, qtype: Int = 1): ByteArray {
        val out = ByteArrayOutputStream(64)
        out.write(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        for (label in domain.trim().trimEnd('.').split(".")) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size and 0xFF)
            out.write(bytes)
        }
        out.write(0x00)
        out.write((qtype shr 8) and 0xFF)
        out.write(qtype and 0xFF)
        out.write(0x00)
        out.write(0x01)
        return out.toByteArray()
    }

    private fun parseFirstIpv4FromDnsWire(resp: ByteArray): String {
        if (resp.size < 12) return ""
        val rcode = resp[3].toInt() and 0x0F
        if (rcode != 0) return ""
        val qdCount = ((resp[4].toInt() and 0xFF) shl 8) or (resp[5].toInt() and 0xFF)
        val anCount = ((resp[6].toInt() and 0xFF) shl 8) or (resp[7].toInt() and 0xFF)
        if (anCount <= 0) return ""

        var idx = 12
        fun skipName(pos: Int): Int {
            var p = pos
            while (p < resp.size) {
                val len = resp[p].toInt() and 0xFF
                if (len == 0) return p + 1
                if ((len and 0xC0) == 0xC0) return p + 2
                p += 1 + len
            }
            return resp.size
        }

        for (q in 0 until qdCount) {
            idx = skipName(idx) + 4
            if (idx >= resp.size) return ""
        }

        for (a in 0 until anCount) {
            idx = skipName(idx)
            if (idx + 10 > resp.size) break
            val rtype = ((resp[idx].toInt() and 0xFF) shl 8) or (resp[idx + 1].toInt() and 0xFF)
            val rdLen = ((resp[idx + 8].toInt() and 0xFF) shl 8) or (resp[idx + 9].toInt() and 0xFF)
            idx += 10
            if (idx + rdLen > resp.size) break
            if (rtype == 1 && rdLen == 4) {
                val ip = "${resp[idx].toInt() and 0xFF}.${resp[idx + 1].toInt() and 0xFF}.${resp[idx + 2].toInt() and 0xFF}.${resp[idx + 3].toInt() and 0xFF}"
                if (!CleanIpScannerEngine.isPoisonedIranIp(ip)) {
                    return ip
                }
            }
            idx += rdLen
        }
        return ""
    }

    private fun buildBootstrapClient(bootstrapIp: String, timeoutMs: Int): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout((timeoutMs + 700).toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(false)
            .socketFactory(object : SocketFactory() {
                override fun createSocket(): Socket = CleanIpScannerEngine.createProtectedRawSocket()
                override fun createSocket(h: String?, p: Int): Socket =
                    CleanIpScannerEngine.createProtectedRawSocket().apply { connect(InetSocketAddress(h, p), timeoutMs) }
                override fun createSocket(h: String?, p: Int, lh: InetAddress?, lp: Int): Socket =
                    CleanIpScannerEngine.createProtectedRawSocket().apply { connect(InetSocketAddress(h, p), timeoutMs) }
                override fun createSocket(h: InetAddress?, p: Int): Socket =
                    CleanIpScannerEngine.createProtectedRawSocket().apply { connect(InetSocketAddress(h, p), timeoutMs) }
                override fun createSocket(a: InetAddress?, p: Int, la: InetAddress?, lp: Int): Socket =
                    CleanIpScannerEngine.createProtectedRawSocket().apply { connect(InetSocketAddress(a, p), timeoutMs) }
            })

        if (bootstrapIp.isNotBlank() && isValidIpv4(bootstrapIp)) {
            builder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return try {
                        listOf(InetAddress.getByAddress(hostname, InetAddress.getByName(bootstrapIp).address))
                    } catch (_: Throwable) {
                        InetAddress.getAllByName(hostname).toList()
                    }
                }
            })
        }
        return builder.build()
    }

    fun querySingleDohBlocking(
        endpoint: DohEndpointSpec,
        domain: String = "github.com",
        timeoutMs: Int = 2000
    ): DohProbeResult {
        val effectiveBootstrap = if (
            endpoint.url.contains("cloudflare", true) ||
            endpoint.url.contains("rethinkdns", true)
        ) {
            activeBootstrapIp.ifBlank { endpoint.bootstrapIp }
        } else {
            endpoint.bootstrapIp
        }

        val client = buildBootstrapClient(effectiveBootstrap, timeoutMs)
        val wireQuery = buildDnsWireQuery(domain, 1)
        val b64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(wireQuery)
        val t0 = System.nanoTime()

        try {
            val sep = if (endpoint.url.contains("?")) "&" else "?"
            val req = Request.Builder()
                .url("${endpoint.url}${sep}dns=$b64Url")
                .header("Accept", "application/dns-message")
                .header("User-Agent", "Mozilla/5.0 Chrome/131.0.0.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes() ?: ByteArray(0)
                    val ip = parseFirstIpv4FromDnsWire(bytes)
                    if (ip.isNotEmpty() && !CleanIpScannerEngine.isPoisonedIranIp(ip)) {
                        val ms = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
                        return DohProbeResult(
                            id = endpoint.id,
                            name = endpoint.name,
                            url = endpoint.url,
                            bootstrapIp = effectiveBootstrap,
                            category = endpoint.category,
                            latencyMs = ms,
                            resolvedSampleIp = ip,
                            isHealthy = true,
                            statusDetail = "RFC8484 ($ip)"
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }

        val t1 = System.nanoTime()
        try {
            val sep = if (endpoint.url.contains("?")) "&" else "?"
            val req = Request.Builder()
                .url("${endpoint.url}${sep}name=$domain&type=A")
                .header("Accept", "application/dns-json")
                .header("User-Agent", "Mozilla/5.0 Chrome/131.0.0.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val j = JSONObject(resp.body?.string().orEmpty())
                    val ans = j.optJSONArray("Answer")
                    if (ans != null) {
                        for (i in 0 until ans.length()) {
                            val obj = ans.getJSONObject(i)
                            if (obj.optInt("type") == 1) {
                                val ip = obj.optString("data", "").trim()
                                if (isValidIpv4(ip) && !CleanIpScannerEngine.isPoisonedIranIp(ip)) {
                                    val ms = ((System.nanoTime() - t1) / 1_000_000L).toInt().coerceAtLeast(1)
                                    return DohProbeResult(
                                        id = endpoint.id,
                                        name = endpoint.name,
                                        url = endpoint.url,
                                        bootstrapIp = effectiveBootstrap,
                                        category = endpoint.category,
                                        latencyMs = ms,
                                        resolvedSampleIp = ip,
                                        isHealthy = true,
                                        statusDetail = "JSON ($ip)"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            return DohProbeResult(
                id = endpoint.id,
                name = endpoint.name,
                url = endpoint.url,
                bootstrapIp = effectiveBootstrap,
                category = endpoint.category,
                latencyMs = -1,
                resolvedSampleIp = "",
                isHealthy = false,
                statusDetail = e.javaClass.simpleName
            )
        }

        return DohProbeResult(
            id = endpoint.id,
            name = endpoint.name,
            url = endpoint.url,
            bootstrapIp = effectiveBootstrap,
            category = endpoint.category,
            latencyMs = -1,
            resolvedSampleIp = "",
            isHealthy = false,
            statusDetail = "Timeout / Blocked"
        )
    }

    fun resolveDomainViaVerifiedDohBlocking(domain: String, timeoutMs: Int = 1500): String {
        val clean = domain.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return ""
        if (isValidIpv4(clean)) {
            return if (CleanIpScannerEngine.isPoisonedIranIp(clean)) "" else clean
        }
        resolvedCache[clean]?.let { if (!CleanIpScannerEngine.isPoisonedIranIp(it)) return it }

        val candidates = ArrayList<DohEndpointSpec>()
        val all = getAllConfiguredEndpoints()
        all.firstOrNull { it.url.equals(activeDohUrl, ignoreCase = true) }?.let { candidates.add(it) }
        for (v in getVerifiedResults().take(3)) {
            all.firstOrNull { it.url == v.url }?.let { if (it !in candidates) candidates.add(it) }
        }
        for (b in listOf("rethink-sky", "cf-main", "quad9-main", "google-dns", "mullvad-base")) {
            BUILTIN_DOH_SERVERS.firstOrNull { it.id == b }?.let { if (it !in candidates) candidates.add(it) }
        }

        for (ep in candidates.take(4)) {
            val res = querySingleDohBlocking(ep, clean, timeoutMs)
            if (res.isHealthy && res.resolvedSampleIp.isNotEmpty()) {
                resolvedCache[clean] = res.resolvedSampleIp
                return res.resolvedSampleIp
            }
        }
        return ""
    }

    suspend fun runFullDohScan(
        testDomain: String = "github.com",
        stopSignal: AtomicBoolean = AtomicBoolean(false),
        logger: (String) -> Unit = {},
        onProgress: (percent: Int, tested: Int, total: Int, hits: List<DohProbeResult>, summary: String) -> Unit
    ): List<DohProbeResult> = withContext(Dispatchers.IO) {
        val endpoints = getAllConfiguredEndpoints()
        val total = endpoints.size.coerceAtLeast(1)
        val sem = Semaphore(10)
        val working = Collections.synchronizedList(ArrayList<DohProbeResult>())
        var doneCount = 0

        logger("[DOH-SCAN] Starting DNSveil parallel probe on $total DoH servers (target=$testDomain)...")

        coroutineScope {
            endpoints.map { ep ->
                async(Dispatchers.IO) {
                    if (stopSignal.get()) return@async
                    sem.withPermit {
                        if (stopSignal.get()) return@withPermit
                        val res = querySingleDohBlocking(ep, testDomain, 2200)
                        synchronized(working) {
                            doneCount++
                            if (res.isHealthy) {
                                working.add(res)
                                logger("[DOH-OK] ${res.name} (${res.url}) -> ${res.latencyMs}ms | $testDomain = ${res.resolvedSampleIp}")
                            } else {
                                logger("[DOH-FAIL] ${res.name} (${res.url}) -> ${res.statusDetail}")
                            }
                            val sorted = working.sortedBy { it.latencyMs }
                            val pct = ((doneCount * 100) / total).coerceIn(1, 99)
                            onProgress(
                                pct,
                                doneCount,
                                total,
                                sorted,
                                "بررسی $doneCount از $total سرور DoH • سالم: ${sorted.size}"
                            )
                        }
                    }
                }
            }.awaitAll()
        }

        val finalSorted = working.sortedBy { it.latencyMs }
        synchronized(verifiedResults) {
            verifiedResults.clear()
            verifiedResults.addAll(finalSorted)
        }
        if (finalSorted.isNotEmpty()) {
            activeDohUrl = finalSorted.first().url
            if (finalSorted.first().bootstrapIp.isNotEmpty()) {
                activeBootstrapIp = finalSorted.first().bootstrapIp
            }
            logger("[DOH-LOCK] Best DoH locked: ${finalSorted.first().name} (${finalSorted.first().latencyMs}ms)")
        }
        finalSorted
    }
}
