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
)

data class EgressGeoInfo(
    val ip: String,
    val countryCode: String
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

    fun fastProbe(httpPort: Int, timeoutMs: Long = 2600L): ProbeVerdict {
        val client = buildIsolatedProxyClient(httpPort, timeoutMs, keepAliveConnections = 1)
        try {
            val gstaticRes = singleGet(client, URL_GSTATIC_204)
            if (gstaticRes.ok || gstaticRes.fatalUpstreamError) {
                return gstaticRes
            }
            val cfRes = singleGet(client, URL_CF_HTTP_204)
            if (cfRes.ok) {
                return cfRes
            }
            return gstaticRes
        } finally {
            shutdownClient(client)
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
        val client = buildIsolatedProxyClient(httpPort, timeoutMs = 3600L, keepAliveConnections = 1)
        try {
            var resolvedIp = ""
            var resolvedLoc = ""

            try {
                val req = Request.Builder()
                    .url(URL_CF_TRACE)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        for (line in body.lineSequence()) {
                            if (line.startsWith("ip=")) {
                                resolvedIp = line.substringAfter("ip=").trim()
                            } else if (line.startsWith("loc=")) {
                                resolvedLoc = line.substringAfter("loc=").trim().uppercase()
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }

            if (resolvedIp.isNotEmpty() && resolvedLoc.length == 2 && resolvedLoc != "XX") {
                return EgressGeoInfo(resolvedIp, resolvedLoc)
            }

            try {
                val req = Request.Builder()
                    .url(URL_IPWHO)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = JSONObject(resp.body?.string().orEmpty())
                        if (obj.optBoolean("success", true)) {
                            val ip = obj.optString("ip", "").trim()
                            val cc = obj.optString("country_code", "").trim().uppercase()
                            if (ip.isNotEmpty()) {
                                return EgressGeoInfo(
                                    ip = ip,
                                    countryCode = if (cc.length == 2) cc else resolvedLoc.ifBlank { "UN" }
                                )
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }

            if (resolvedIp.isNotEmpty()) {
                return EgressGeoInfo(resolvedIp, resolvedLoc.ifBlank { "UN" })
            }

            try {
                val req = Request.Builder()
                    .url(URL_IPIFY_JSON)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val ip = JSONObject(resp.body?.string().orEmpty()).optString("ip", "").trim()
                        if (ip.isNotEmpty()) {
                            return EgressGeoInfo(ip, "UN")
                        }
                    }
                }
            } catch (_: Throwable) {
            }

            return EgressGeoInfo("—", "UN")
        } finally {
            shutdownClient(client)
        }
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
                if (code == 502 || code == 503) {
                    return ProbeVerdict(
                        ok = false,
                        latencyMs = ms,
                        httpCode = code,
                        fatalUpstreamError = true,
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
}
