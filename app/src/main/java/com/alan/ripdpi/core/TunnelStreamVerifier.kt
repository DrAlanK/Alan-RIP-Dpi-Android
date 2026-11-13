package com.alan.ripdpi.core

import kotlinx.coroutines.delay
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class ProbeVerdict(
    val ok: Boolean,
    val latencyMs: Long,
    val httpCode: Int = 0,
    val fatalUpstreamError: Boolean = false,
    val reason: String = ""
)

data class QualificationVerdict(
    val fullyQualified: Boolean,
    val softReachable: Boolean,
    val latencyMs: Long,
    val uploadPassed: Boolean,
    val endurancePassed: Boolean,
    val reason: String = ""
) {
        val ok: Boolean get() = fullyQualified
    }

data class EgressGeoInfo(
    val ip: String,
    val countryCode: String
)

data class LaneGeoPingResult(
    val ok: Boolean,
    val latencyMs: Int,
    val egressIp: String = "",
    val countryCode: String = "UN",
    val detail: String = ""
)

object TunnelStreamVerifier {

    private const val URL_GSTATIC_204 = "https://www.gstatic.com/generate_204"
    private const val URL_YOUTUBE_204 = "https://www.youtube.com/generate_204"
    private const val URL_CF_HTTP_204 = "http://cp.cloudflare.com/generate_204"
    private const val URL_CF_TRACE = "https://cloudflare.com/cdn-cgi/trace"
    private const val URL_IPWHO = "https://ipwho.is/"
    private const val URL_IPIFY_JSON = "http://api.ipify.org?format=json"

    fun tcpReachabilityCheck(
        host: String,
        port: Int,
        timeoutMs: Int = 1800,
        protectSocket: ((Socket) -> Boolean)? = null
    ): Long {
        val sock = Socket()
        val t0 = System.nanoTime()
        return try {
            protectSocket?.invoke(sock)
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Throwable) {
            -1L
        } finally {
            try {
                sock.close()
            } catch (_: Throwable) {
            }
        }
    }

    fun multiSampleTcpPreflight(
        host: String,
        port: Int,
        attempts: Int = 2,
        timeoutMs: Int = 1600,
        protectSocket: ((Socket) -> Boolean)? = null
    ): Long {
        var bestRtt = Long.MAX_VALUE
        val count = attempts.coerceIn(1, 3)
        for (i in 0 until count) {
            val rtt = tcpReachabilityCheck(host, port, timeoutMs, protectSocket)
            if (rtt in 1 until bestRtt) {
                bestRtt = rtt
                if (rtt < 220L) break
            }
        }
        return if (bestRtt == Long.MAX_VALUE) -1L else bestRtt
    }

    private fun buildIsolatedProxyClient(
        httpPort: Int,
        timeoutMs: Long,
        keepAliveConnections: Int = 2
    ): OkHttpClient {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", httpPort))
        return OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs + 600L, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectionPool(ConnectionPool(keepAliveConnections, 15, TimeUnit.SECONDS))
            .build()
    }

    private fun shutdownClient(client: OkHttpClient) {
        try {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        } catch (_: Throwable) {
        }
    }

            fun fastProbe(httpPort: Int, timeoutMs: Long = 3400L): ProbeVerdict {
        val client = okhttp3.OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", httpPort)))
            .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs + 600L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
        for (u in listOf("http://cp.cloudflare.com/generate_204", "http://connectivitycheck.gstatic.com/generate_204", "https://www.gstatic.com/generate_204")) {
            val t0 = System.nanoTime()
            try {
                val req = okhttp3.Request.Builder().url(u).header("User-Agent", "Mozilla/5.0 Chrome/131.0.0.0").get().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 204 || resp.code == 200) {
                        return ProbeVerdict(true, ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L), resp.code)
                    }
                }
            } catch (_: Throwable) {}
        }
        return ProbeVerdict(false, -1L, 0)
    }

    fun checkGeminiAccess(httpPort: Int, timeoutMs: Long = 4500L): Pair<Boolean, String> {
        val client = okhttp3.OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", httpPort)))
            .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        return try {
            val apiReq = okhttp3.Request.Builder()
                .url("https://generativelanguage.googleapis.com/$\$discovery/rest?version=v1beta")
                .header("User-Agent", "Mozilla/5.0 Chrome/131.0.0.0")
                .get()
                .build()
            var apiCode = 0
            var blocked = false
            client.newCall(apiReq).execute().use { resp ->
                apiCode = resp.code
                val bodyLow = resp.body?.string().orEmpty().take(800).lowercase(java.util.Locale.US)
                if (bodyLow.contains("user location is not supported") || apiCode == 403 || apiCode == 451) {
                    blocked = true
                }
            }
            if (blocked) return false to "BLOCKED_REGION"

            val webReq = okhttp3.Request.Builder()
                .url("https://gemini.google.com/app")
                .header("User-Agent", "Mozilla/5.0 Chrome/131.0.0.0")
                .get()
                .build()
            client.newCall(webReq).execute().use { resp ->
                val loc = resp.header("Location").orEmpty().lowercase(java.util.Locale.US)
                if (loc.contains("faq") || loc.contains("unsupported") || resp.code == 451) {
                    return false to "BLOCKED_REGION"
                }
                if (apiCode == 200) return true to "OK"
                false to "HTTP_${resp.code}"
            }
        } catch (_: Throwable) {
            false to "UNREACHABLE"
        }
    }

    suspend fun runFullQualification(
        httpPort: Int,
        verifyUploadStream: Boolean,
        requireYoutube6s: Boolean,
        isCancelled: () -> Boolean = { false }
    ): QualificationVerdict {
        val client = buildIsolatedProxyClient(httpPort, timeoutMs = 4200L, keepAliveConnections = 4)
        try {
            if (isCancelled()) {
                return QualificationVerdict(false, false, -1L, false, false, "Cancelled")
            }

            var initial = singleGet(client, URL_YOUTUBE_204)
            if (!initial.ok && !initial.fatalUpstreamError && !requireYoutube6s) {
                initial = singleGet(client, URL_GSTATIC_204)
            }
            if (!initial.ok) {
                return QualificationVerdict(
                    fullyQualified = false,
                    softReachable = false,
                    latencyMs = -1L,
                    uploadPassed = false,
                    endurancePassed = false,
                    reason = initial.reason.ifBlank { "Initial TLS handshake failed" }
                )
            }

            val baseLatency = initial.latencyMs
            var uploadOk = true

            if (verifyUploadStream) {
                for (round in 1..3) {
                    if (isCancelled()) {
                        return QualificationVerdict(false, true, baseLatency, false, false, "Cancelled")
                    }
                    val pad4k = generateRandomHex4KB()
                    val upRes = singleGet(
                        client = client,
                        url = URL_GSTATIC_204,
                        extraHeaders = mapOf(
                            "X-Stream-Pad" to pad4k,
                            "X-Stream-Round" to round.toString()
                        )
                    )
                    if (!upRes.ok) {
                        uploadOk = false
                        return QualificationVerdict(
                            fullyQualified = false,
                            softReachable = true,
                            latencyMs = baseLatency,
                            uploadPassed = false,
                            endurancePassed = false,
                            reason = "Upload stream stalled at round $round"
                        )
                    }
                }
            }

            var enduranceOk = true
            if (requireYoutube6s) {
                val startMark = System.currentTimeMillis()
                val waitTo25 = (2500L - (System.currentTimeMillis() - startMark)).coerceAtLeast(100L)
                delay(waitTo25)
                if (isCancelled()) {
                    return QualificationVerdict(false, true, baseLatency, uploadOk, false, "Cancelled")
                }

                val midCheck = singleGet(client, URL_YOUTUBE_204)
                if (!midCheck.ok) {
                    enduranceOk = false
                    return QualificationVerdict(
                        fullyQualified = false,
                        softReachable = true,
                        latencyMs = baseLatency,
                        uploadPassed = uploadOk,
                        endurancePassed = false,
                        reason = "Mid-stream reset at 2.5s"
                    )
                }

                val waitTo54 = (5400L - (System.currentTimeMillis() - startMark)).coerceAtLeast(100L)
                delay(waitTo54)
                if (isCancelled()) {
                    return QualificationVerdict(false, true, baseLatency, uploadOk, false, "Cancelled")
                }

                val endCheck = singleGet(client, URL_YOUTUBE_204)
                if (!endCheck.ok) {
                    enduranceOk = false
                    return QualificationVerdict(
                        fullyQualified = false,
                        softReachable = true,
                        latencyMs = baseLatency,
                        uploadPassed = uploadOk,
                        endurancePassed = false,
                        reason = "Secondary DPI reset at 5.4s"
                    )
                }
            }

            return QualificationVerdict(
                fullyQualified = true,
                softReachable = true,
                latencyMs = baseLatency,
                uploadPassed = uploadOk,
                endurancePassed = enduranceOk,
                reason = "Verified"
            )
        } finally {
            shutdownClient(client)
        }
    }

    fun resolveEgressEndpoint(httpPort: Int): EgressGeoInfo {
        val client = okhttp3.OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", httpPort)))
            .connectTimeout(4000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(4000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .build()

        fun buildGeo(ipRaw: String, ccRaw: String): EgressGeoInfo {
            val ipClean = ipRaw.trim().ifBlank { "—" }
            var cc = ccRaw.trim().uppercase(java.util.Locale.US)
            if (cc == "UK") cc = "GB"
            if (cc.length != 2 || cc == "XX") cc = "UN"
            val spec = com.alan.ripdpi.model.CountryPaletteRegistry.resolveByCode(cc)
            return EgressGeoInfo(ip = ipClean, countryCode = cc)
        }

        // 1. api.country.is
        try {
            val r1 = okhttp3.Request.Builder().url("https://api.country.is/").header("User-Agent", "Mozilla/5.0").get().build()
            client.newCall(r1).execute().use { resp ->
                if (resp.isSuccessful) {
                    val j = org.json.JSONObject(resp.body?.string().orEmpty())
                    val ip = j.optString("ip", "").trim()
                    val cc = j.optString("country", "").trim()
                    if (ip.isNotEmpty() && cc.isNotEmpty()) return buildGeo(ip, cc)
                }
            }
        } catch (_: Throwable) {}

        // 2. ipwho.is
        try {
            val r2 = okhttp3.Request.Builder().url("https://ipwho.is/").header("User-Agent", "Mozilla/5.0").get().build()
            client.newCall(r2).execute().use { resp ->
                if (resp.isSuccessful) {
                    val j = org.json.JSONObject(resp.body?.string().orEmpty())
                    val ip = j.optString("ip", "").trim()
                    if (j.optBoolean("success", true) && ip.isNotEmpty()) {
                        return buildGeo(ip, j.optString("country_code", ""))
                    }
                }
            }
        } catch (_: Throwable) {}

        // 3. ipinfo.io
        try {
            val r3 = okhttp3.Request.Builder().url("https://ipinfo.io/json").header("User-Agent", "Mozilla/5.0").get().build()
            client.newCall(r3).execute().use { resp ->
                if (resp.isSuccessful) {
                    val j = org.json.JSONObject(resp.body?.string().orEmpty())
                    val ip = j.optString("ip", "").trim()
                    if (ip.isNotEmpty()) return buildGeo(ip, j.optString("country", ""))
                }
            }
        } catch (_: Throwable) {}

        // 4. ip-api.com
        try {
            val r4 = okhttp3.Request.Builder().url("http://ip-api.com/json/?fields=status,country,countryCode,query").header("User-Agent", "Mozilla/5.0").get().build()
            client.newCall(r4).execute().use { resp ->
                if (resp.isSuccessful) {
                    val j = org.json.JSONObject(resp.body?.string().orEmpty())
                    val ip = j.optString("query", "").trim()
                    if (ip.isNotEmpty()) return buildGeo(ip, j.optString("countryCode", ""))
                }
            }
        } catch (_: Throwable) {}

        // 5. cloudflare cdn-cgi/trace
        try {
            val r5 = okhttp3.Request.Builder().url("https://www.cloudflare.com/cdn-cgi/trace").header("User-Agent", "Mozilla/5.0").get().build()
            client.newCall(r5).execute().use { resp ->
                if (resp.isSuccessful) {
                    val kv = HashMap<String, String>()
                    resp.body?.string().orEmpty().lineSequence().forEach { ln ->
                        if (ln.contains("=")) {
                            val k = ln.substringBefore("=").trim()
                            val v = ln.substringAfter("=").trim()
                            kv[k] = v
                        }
                    }
                    val ip = kv["ip"].orEmpty()
                    if (ip.isNotEmpty()) return buildGeo(ip, kv["loc"].orEmpty())
                }
            }
        } catch (_: Throwable) {}

        // 6. api.ipify.org
        try {
            val r6 = okhttp3.Request.Builder().url("https://api.ipify.org?format=json").header("User-Agent", "Mozilla/5.0").get().build()
            client.newCall(r6).execute().use { resp ->
                if (resp.isSuccessful) {
                    val ip = org.json.JSONObject(resp.body?.string().orEmpty()).optString("ip", "").trim()
                    if (ip.isNotEmpty()) return buildGeo(ip, "UN")
                }
            }
        } catch (_: Throwable) {}

        return EgressGeoInfo(ip = "—", countryCode = "UN")
    }

    fun resolveEgressIp(httpPort: Int): String = resolveEgressEndpoint(httpPort).ip

    private fun singleGet(
        client: OkHttpClient,
        url: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): ProbeVerdict {
        val t0 = System.nanoTime()
        return try {
            val rb = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                .header("Cache-Control", "no-cache")
                .get()
            for ((k, v) in extraHeaders) {
                rb.header(k, v)
            }
            client.newCall(rb.build()).execute().use { resp ->
                val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
                val code = resp.code
                if (false) {
                    return ProbeVerdict(
                        ok = false,
                        latencyMs = ms,
                        httpCode = code,
                        fatalUpstreamError = false,
                        reason = "HTTP $code upstream error"
                    )
                }
                val valid = code in 200..299
                ProbeVerdict(
                    ok = valid,
                    latencyMs = ms,
                    httpCode = code,
                    fatalUpstreamError = false,
                    reason = if (valid) "HTTP $code" else "Unexpected HTTP $code"
                )
            }
        } catch (e: Throwable) {
            val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
            ProbeVerdict(
                ok = false,
                latencyMs = ms,
                httpCode = 0,
                fatalUpstreamError = false,
                reason = e.javaClass.simpleName
            )
        }
    }

    private fun generateRandomHex4KB(): String {
        val raw = Random.nextBytes(2048)
        val hexChars = "0123456789abcdef"
        val out = CharArray(4096)
        for (i in raw.indices) {
            val b = raw[i].toInt() and 0xFF
            out[i * 2] = hexChars[b ushr 4]
            out[i * 2 + 1] = hexChars[b and 0x0F]
        }
        return String(out)
    }

    fun quickUploadCheck(httpPort: Int, timeoutMs: Long = 1800L): Pair<Boolean, Double> {
        val client = okhttp3.OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", httpPort)))
            .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs + 500L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        return try {
            val payload = ByteArray(4096).also { kotlin.random.Random.nextBytes(it) }
            val req = okhttp3.Request.Builder()
                .url("https://www.gstatic.com/generate_204")
                .header("User-Agent", "Mozilla/5.0 Chrome/131.0.0.0")
                .header("Content-Type", "application/octet-stream")
                .post(okhttp3.RequestBody.create(null, payload))
                .build()
            val t0 = System.nanoTime()
            client.newCall(req).execute().use { resp ->
                val ok = resp.code in listOf(200, 204, 405)
                val dt = (((System.nanoTime() - t0) / 1_000_000_000.0)).coerceAtLeast(0.02)
                ok to if (ok) ((payload.size / 1024.0) / dt) else 0.0
            }
        } catch (_: Throwable) {
            false to 0.0
        }
    }

    fun probeTunnelAndGeo(httpPort: Int, timeoutMs: Long = 3200L, fetchGeo: Boolean = true): LaneGeoPingResult {
        val client = okhttp3.OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", httpPort)))
            .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs + 900L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

        var pingMs = -1
        for (url in listOf("https://www.gstatic.com/generate_204", "http://connectivitycheck.gstatic.com/generate_204")) {
            val t0 = System.nanoTime()
            try {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 Chrome/131.0.0.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 200 || resp.code == 204) {
                        pingMs = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
                    }
                }
                if (pingMs > 0) break
            } catch (_: Throwable) {
            }
        }

        if (pingMs <= 0) {
            return LaneGeoPingResult(ok = false, latencyMs = -1, detail = "Timeout")
        }
        if (!fetchGeo) {
            return LaneGeoPingResult(ok = true, latencyMs = pingMs, detail = "OK")
        }

        // Reuse warm proxy connection to grab exit IP & ISO-2 country code rapidly!
        try {
            val traceReq = okhttp3.Request.Builder()
                .url("https://www.cloudflare.com/cdn-cgi/trace")
                .header("User-Agent", "Mozilla/5.0 Chrome/131.0.0.0")
                .get()
                .build()
            client.newCall(traceReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    var ipVal = ""
                    var locVal = ""
                    resp.body?.string().orEmpty().lineSequence().forEach { ln ->
                        if (ln.startsWith("ip=")) ipVal = ln.substringAfter("=").trim()
                        else if (ln.startsWith("loc=")) locVal = ln.substringAfter("=").trim().uppercase(java.util.Locale.US)
                    }
                    if (locVal == "UK") locVal = "GB"
                    if (locVal.length == 2 && locVal != "XX") {
                        return LaneGeoPingResult(ok = true, latencyMs = pingMs, egressIp = ipVal, countryCode = locVal, detail = "OK")
                    }
                }
            }
        } catch (_: Throwable) {
        }

        try {
            val geoReq = okhttp3.Request.Builder()
                .url("https://api.country.is/")
                .header("User-Agent", "Mozilla/5.0 Chrome/131.0.0.0")
                .get()
                .build()
            client.newCall(geoReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val j = org.json.JSONObject(resp.body?.string().orEmpty())
                    val ipVal = j.optString("ip", "").trim()
                    var cc = j.optString("country", "").trim().uppercase(java.util.Locale.US)
                    if (cc == "UK") cc = "GB"
                    if (cc.length == 2) {
                        return LaneGeoPingResult(ok = true, latencyMs = pingMs, egressIp = ipVal, countryCode = cc, detail = "OK")
                    }
                }
            }
        } catch (_: Throwable) {
        }

        return LaneGeoPingResult(ok = true, latencyMs = pingMs, detail = "OK")
    }
}
