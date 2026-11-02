package com.alan.ripdpi.core

import android.content.Context
import com.alan.ripdpi.vpn.AlanVpnService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class GeoIpQuickResolver(context: Context) {

    private val cacheFile = File(context.applicationContext.filesDir, "geoip_cache.json")
    private val cacheMap = ConcurrentHashMap<String, String>()
    private val diskLock = Any()

    private val protectedSocketFactory = object : SocketFactory() {
        private val base = getDefault()
        override fun createSocket(): Socket = base.createSocket().also { AlanVpnService.protectRawSocket(it) }
        override fun createSocket(host: String?, port: Int): Socket = base.createSocket(host, port)
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            base.createSocket(host, port, localHost, localPort)
        override fun createSocket(host: InetAddress?, port: Int): Socket = base.createSocket(host, port)
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            base.createSocket(address, port, localAddress, localPort)
    }

    private val httpClient = OkHttpClient.Builder()
        .socketFactory(protectedSocketFactory)
        .connectTimeout(2400, TimeUnit.MILLISECONDS)
        .readTimeout(2400, TimeUnit.MILLISECONDS)
        .callTimeout(3200, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    init {
        loadCache()
    }

    data class GeoProbeResult(
        val countryCode: String,
        val isCloudflareAnycast: Boolean
    )

    fun resolveHostCountryFast(rawHost: String): GeoProbeResult {
        val cleanHost = rawHost.trim().removePrefix("[").removeSuffix("]").lowercase(Locale.US)
        if (cleanHost.isEmpty() || cleanHost == "127.0.0.1" || cleanHost == "localhost") {
            return GeoProbeResult("UN", false)
        }

        cacheMap[cleanHost]?.let { cached ->
            if (isValidIso(cached)) return GeoProbeResult(cached, false)
        }

        val resolvedIp = try {
            val all = InetAddress.getAllByName(cleanHost)
            val v4 = all.firstOrNull { it is Inet4Address } ?: all.firstOrNull()
            v4?.hostAddress?.trim().orEmpty()
        } catch (_: Throwable) {
            if (cleanHost.all { it.isDigit() || it == '.' || it == ':' }) cleanHost else ""
        }

        if (resolvedIp.isEmpty()) {
            return GeoProbeResult("UN", false)
        }

        if (isCloudflareIpv4(resolvedIp)) {
            return GeoProbeResult("UN", true)
        }

        cacheMap[resolvedIp]?.let { cachedIpCc ->
            if (isValidIso(cachedIpCc)) {
                cacheMap[cleanHost] = cachedIpCc
                return GeoProbeResult(cachedIpCc, false)
            }
        }

        val queried = queryCountryIs(resolvedIp)
            ?: queryIpWhoIs(resolvedIp)
            ?: queryIpApi(resolvedIp)

        if (queried != null && isValidIso(queried)) {
            cacheMap[resolvedIp] = queried
            cacheMap[cleanHost] = queried
            saveCache()
            return GeoProbeResult(queried, false)
        }

        return GeoProbeResult("UN", false)
    }

    fun rememberResolvedCountry(host: String, isoCode: String) {
        val cleanHost = host.trim().lowercase(Locale.US)
        val cc = isoCode.trim().uppercase(Locale.US)
        if (cleanHost.isNotEmpty() && isValidIso(cc)) {
            cacheMap[cleanHost] = cc
            saveCache()
        }
    }

    private fun queryCountryIs(ip: String): String? {
        return try {
            val req = Request.Builder()
                .url("https://api.country.is/$ip")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val cc = JSONObject(resp.body?.string().orEmpty())
                    .optString("country", "")
                    .trim()
                    .uppercase(Locale.US)
                cc.takeIf { isValidIso(it) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun queryIpWhoIs(ip: String): String? {
        return try {
            val req = Request.Builder()
                .url("https://ipwho.is/$ip?fields=success,country_code")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val obj = JSONObject(resp.body?.string().orEmpty())
                if (!obj.optBoolean("success", true)) return null
                val cc = obj.optString("country_code", "").trim().uppercase(Locale.US)
                cc.takeIf { isValidIso(it) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun queryIpApi(ip: String): String? {
        return try {
            val req = Request.Builder()
                .url("http://ip-api.com/json/$ip?fields=status,countryCode")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val obj = JSONObject(resp.body?.string().orEmpty())
                if (!obj.optString("status").equals("success", true)) return null
                val cc = obj.optString("countryCode", "").trim().uppercase(Locale.US)
                cc.takeIf { isValidIso(it) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isCloudflareIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val b0 = parts[0].toIntOrNull() ?: return false
        val b1 = parts[1].toIntOrNull() ?: return false
        val ipv4Int = (b0 shl 24) or (b1 shl 16) or
            ((parts[2].toIntOrNull() ?: return false) shl 8) or
            (parts[3].toIntOrNull() ?: return false)

        fun inCidr(baseIp: Int, prefixLen: Int): Boolean {
            val mask = if (prefixLen == 0) 0 else (-1 shl (32 - prefixLen))
            return (ipv4Int and mask) == (baseIp and mask)
        }

        fun pack(a: Int, b: Int, c: Int, d: Int): Int = (a shl 24) or (b shl 16) or (c shl 8) or d

        return inCidr(pack(104, 16, 0, 0), 12) ||
            inCidr(pack(172, 64, 0, 0), 13) ||
            inCidr(pack(162, 158, 0, 0), 15) ||
            inCidr(pack(188, 114, 96, 0), 20) ||
            inCidr(pack(198, 41, 128, 0), 17) ||
            inCidr(pack(141, 101, 64, 0), 18) ||
            inCidr(pack(108, 162, 192, 0), 18) ||
            inCidr(pack(190, 93, 240, 0), 20)
    }

    private fun isValidIso(cc: String): Boolean {
        return cc.length == 2 && cc != "UN" && cc != "XX" && cc[0] in 'A'..'Z' && cc[1] in 'A'..'Z'
    }

    private fun loadCache() {
        synchronized(diskLock) {
            if (!cacheFile.exists()) return
            try {
                val obj = JSONObject(cacheFile.readText(Charsets.UTF_8))
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optString(k, "")
                    if (isValidIso(v)) {
                        cacheMap[k] = v
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun saveCache() {
        synchronized(diskLock) {
            try {
                val obj = JSONObject()
                for ((k, v) in cacheMap) {
                    obj.put(k, v)
                }
                cacheFile.writeText(obj.toString(), Charsets.UTF_8)
            } catch (_: Throwable) {
            }
        }
    }
}
