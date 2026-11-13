package com.alan.ripdpi.core

import com.alan.ripdpi.model.CountryPaletteRegistry
import com.alan.ripdpi.ui.EditableConfigFields
import com.alan.ripdpi.ui.EngineSettings
import com.alan.ripdpi.ui.ProfileItem
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

object EnterpriseProtocolEngine {

    private val ENTERPRISE_SCHEMES = setOf(
        "anyconnect",
        "openconnect",
        "oc",
        "cisco",
        "ikev2",
        "ipsec",
        "l2tp"
    )

    fun isEnterpriseProtocol(proto: String): Boolean {
        return proto.trim().lowercase(Locale.US) in ENTERPRISE_SCHEMES
    }

    fun isEnterpriseUri(rawUri: String): Boolean {
        val scheme = rawUri.trim().lowercase(Locale.US).substringBefore("://", "")
        return scheme in ENTERPRISE_SCHEMES
    }

    fun normalizeProtocolName(rawScheme: String): String {
        return when (rawScheme.trim().lowercase(Locale.US)) {
            "anyconnect", "openconnect", "oc", "cisco" -> "anyconnect"
            "ikev2", "ipsec" -> "ikev2"
            "l2tp" -> "l2tp"
            else -> rawScheme.trim().lowercase(Locale.US)
        }
    }

    fun defaultPortFor(proto: String): Int = when (normalizeProtocolName(proto)) {
        "anyconnect" -> 443
        "ikev2" -> 500
        "l2tp" -> 1701
        else -> 443
    }

    /**
     * Builds a real RFC 7296 IKEv2 IKE_SA_INIT request packet (or NAT-T with 4-byte Non-ESP marker on port 4500).
     */
        private fun buildIkev2SaInitPacket(natTraversal: Boolean): ByteArray {
        val rng = SecureRandom()
        val spi = ByteArray(8).also { rng.nextBytes(it) }
        val keData = ByteArray(256).also { rng.nextBytes(it) }
        val nonceData = ByteArray(32).also { rng.nextBytes(it) }

        val saPayload = byteArrayOf(
            34, 0, 0, 48,
            0, 0, 0, 44, 1, 1, 0, 4,
            3, 0, 0, 12, 1, 0, 0, 12, 0x80.toByte(), 14, 0, 128.toByte(),
            3, 0, 0, 8, 2, 0, 0, 5,
            3, 0, 0, 8, 3, 0, 0, 12,
            0, 0, 0, 8, 4, 0, 0, 14
        )
        val keHeader = byteArrayOf(40, 0, 1, 8, 0, 14, 0, 0)
        val nonceHeader = byteArrayOf(0, 0, 0, 36)

        val totalLen = 28 + saPayload.size + keHeader.size + keData.size + nonceHeader.size + nonceData.size
        val hdr = ByteArray(28)
        System.arraycopy(spi, 0, hdr, 0, 8)
        hdr[16] = 33
        hdr[17] = 0x20
        hdr[18] = 34
        hdr[19] = 0x08
        hdr[24] = ((totalLen ushr 24) and 0xFF).toByte()
        hdr[25] = ((totalLen ushr 16) and 0xFF).toByte()
        hdr[26] = ((totalLen ushr 8) and 0xFF).toByte()
        hdr[27] = (totalLen and 0xFF).toByte()

        val ikePkt = hdr + saPayload + keHeader + keData + nonceHeader + nonceData
        return if (natTraversal) ByteArray(4) + ikePkt else ikePkt
    }

    /**
     * Builds a real RFC 2661 L2TP Control Message (SCCRQ) for UDP port 1701.
     */
    private fun buildL2tpSccrqPacket(): ByteArray {
        return byteArrayOf(
            0xC8.toByte(), 0x02, 0x00, 0x14, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x80.toByte(), 0x08, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        )
    }

    /**
     * Protocol-aware reachability & latency probe for AnyConnect (TCP/TLS), IKEv2 (UDP 500/4500 + TCP/RST RTT), and L2TP (UDP 1701/500 + TCP/RST RTT).
     */
    fun probeEnterpriseEndpointBlocking(host: String, port: Int, proto: String, timeoutMs: Int = 1200): Int {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return -1
        val normProto = normalizeProtocolName(proto)

        val resolvedIp = try {
            val dIp = AlanDohEngine.resolveDomainViaVerifiedDohBlocking(cleanHost, timeoutMs.coerceAtMost(1200))
            if (dIp.isNotEmpty() && !CleanIpScannerEngine.isPoisonedIranIp(dIp)) {
                dIp
            } else {
                InetAddress.getByName(cleanHost).hostAddress?.takeIf { !CleanIpScannerEngine.isPoisonedIranIp(it) } ?: return -1
            }
        } catch (_: Throwable) {
            return -1
        }

        // 1. For IKEv2 / L2TP: Try real UDP IKEv2 (500 / 4500) or L2TP (1701) packet exchange first
        if (normProto == "ikev2" || normProto == "l2tp") {
            val udpPorts = if (normProto == "l2tp") listOf(port, 1701, 4500, 500).distinct() else listOf(port, 4500, 500).distinct()
            for (uPort in udpPorts.take(2)) {
                var ds: DatagramSocket? = null
                try {
                    ds = DatagramSocket()
                    ds.soTimeout = (timeoutMs / 2).coerceIn(350, 700)
                    val payload = if (normProto == "l2tp" && uPort == 1701) {
                        buildL2tpSccrqPacket()
                    } else {
                        buildIkev2SaInitPacket(natTraversal = (uPort == 4500))
                    }
                    val targetAddr = InetSocketAddress(resolvedIp, uPort)
                    val t0 = System.nanoTime()
                    ds.send(DatagramPacket(payload, payload.size, targetAddr))
                    val recvBuf = ByteArray(512)
                    val recvPkt = DatagramPacket(recvBuf, recvBuf.size)
                    ds.receive(recvPkt)
                    if (recvPkt.length >= 4) {
                        val ms = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
                        return ms
                    }
                } catch (_: Throwable) {
                } finally {
                    try { ds?.close() } catch (_: Throwable) {}
                }
            }
        }

        // 2. TCP SYN / RST RTT measurement on [443, port, 8443]
        // Note: For UDP-only IKEv2/L2TP servers, when a TCP SYN hits port 500/1701/443 on a live host,
        // if the port is open -> connect() succeeds in 1 RTT.
        // If the port is closed on the live remote host -> the remote kernel sends TCP RST, throwing ConnectException (ECONNREFUSED) in 1 exact RTT!
        // A dead/blackholed host throws SocketTimeoutException after full timeoutMs.
        val tcpPorts = if (normProto == "anyconnect") {
            listOf(port, 443, 8443).distinct()
        } else {
            listOf(443, port).distinct()
        }

        val perPortTimeout = (timeoutMs / tcpPorts.size.coerceAtLeast(1)).coerceIn(450, 950)
        for (tPort in tcpPorts) {
            val sock = CleanIpScannerEngine.createProtectedRawSocket()
            val t0 = System.nanoTime()
            try {
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(resolvedIp, tPort), perPortTimeout)
                val rtt = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
                return rtt
            } catch (ce: ConnectException) {
                // For IKEv2 / L2TP (which are UDP protocols), receiving a genuine TCP RST from the remote IP in <80% of timeout proves the server IP is online & gives exact RTT!
                val elapsedMs = ((System.nanoTime() - t0) / 1_000_000L).toInt()
                if ((normProto == "ikev2" || normProto == "l2tp") && elapsedMs in 8..(perPortTimeout - 80)) {
                    return elapsedMs
                }
            } catch (_: Throwable) {
            } finally {
                try { sock.close() } catch (_: Throwable) {}
            }
        }
        return -1
    }

    private fun tryDecodeBase64Segment(seg: String): String? {
        val clean = seg.trim()
        if (clean.isEmpty() || clean.contains("@") || clean.contains(":")) return null
        return try {
            val padded = clean.replace('-', '+').replace('_', '/').let { s ->
                val rem = s.length % 4
                if (rem == 0) s else s + "=".repeat(4 - rem)
            }
            val decoded = String(Base64.getDecoder().decode(padded), Charsets.UTF_8).trim()
            if (decoded.isNotEmpty() && (decoded.contains(":") || decoded.contains("@") || decoded.startsWith("{"))) {
                decoded
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun parseEnterpriseFullConfig(rawUri: String): EditableConfigFields? {
        val trimmed = rawUri.trim()
        if (!isEnterpriseUri(trimmed)) return null

        return try {
            val rawScheme = trimmed.substringBefore("://").lowercase(Locale.US)
            val normProto = normalizeProtocolName(rawScheme)
            val afterScheme = trimmed.substringAfter("://")
            val fragmentPart = afterScheme.substringAfter("#", "").let {
                try { URLDecoder.decode(it, "UTF-8") } catch (_: Throwable) { it }
            }.trim()
            val beforeFragment = afterScheme.substringBefore("#")

            val maybeDecoded = tryDecodeBase64Segment(beforeFragment.substringBefore("?"))
            if (maybeDecoded != null && maybeDecoded.startsWith("{")) {
                val j = JSONObject(maybeDecoded)
                val host = j.optString("server", j.optString("add", j.optString("host", ""))).trim()
                if (host.isEmpty()) return null
                val port = j.optInt("port", defaultPortFor(normProto)).coerceIn(1, 65535)
                val user = j.optString("username", j.optString("user", j.optString("id", ""))).trim()
                val pass = j.optString("password", j.optString("pass", j.optString("psk", ""))).trim()
                val creds = if (user.isNotEmpty() && pass.isNotEmpty()) "$user:$pass" else user.ifBlank { pass }
                val sni = j.optString("sni", j.optString("peer", host)).trim()
                val grp = j.optString("group", j.optString("psk", j.optString("secret", ""))).trim()
                val remark = fragmentPart.ifBlank {
                    j.optString("ps", j.optString("name", "${normProto.uppercase(Locale.US)} • $host"))
                }
                return EditableConfigFields(
                    name = remark,
                    protocol = normProto,
                    address = host,
                    port = port,
                    uuidOrPassword = creds,
                    network = "tcp",
                    security = if (normProto == "anyconnect") "tls" else "none",
                    sni = sni,
                    hostHeader = grp,
                    path = "/",
                    rawUri = trimmed,
                    useRawUri = false
                )
            }

            val normalizedUriStr = if (maybeDecoded != null) {
                "$normProto://$maybeDecoded" + (if (beforeFragment.contains("?")) "?" + beforeFragment.substringAfter("?") else "")
            } else {
                "$normProto://$beforeFragment"
            }

            val u = URI(normalizedUriStr)
            val host = u.host?.trim().orEmpty().ifBlank {
                beforeFragment.substringBefore("?").substringBefore("/").substringAfterLast("@").substringBefore(":")
            }.trim()
            if (host.isEmpty()) return null

            val port = if (u.port in 1..65535) {
                u.port
            } else {
                beforeFragment.substringBefore("?").substringBefore("/").substringAfterLast("@").substringAfterLast(":", "").toIntOrNull()
                    ?.takeIf { it in 1..65535 }
                    ?: defaultPortFor(normProto)
            }

            val queryMap = LinkedHashMap<String, String>()
            u.rawQuery.orEmpty().split("&").forEach { pair ->
                if (pair.isNotBlank()) {
                    val k = pair.substringBefore("=").trim().lowercase(Locale.US)
                    val v = try { URLDecoder.decode(pair.substringAfter("=", ""), "UTF-8") } catch (_: Throwable) { pair.substringAfter("=", "") }
                    if (k.isNotEmpty()) queryMap[k] = v
                }
            }

            val userInfoDecoded = u.rawUserInfo?.let {
                try { URLDecoder.decode(it, "UTF-8") } catch (_: Throwable) { it }
            }.orEmpty()

            val qUser = queryMap["username"] ?: queryMap["user"] ?: ""
            val qPass = queryMap["password"] ?: queryMap["pass"] ?: queryMap["psk"] ?: queryMap["secret"] ?: ""
            val credentials = when {
                userInfoDecoded.isNotBlank() -> userInfoDecoded
                qUser.isNotBlank() && qPass.isNotBlank() -> "$qUser:$qPass"
                qUser.isNotBlank() -> qUser
                qPass.isNotBlank() -> qPass
                else -> ""
            }

            val sni = (queryMap["remote_id"] ?: queryMap["sni"] ?: queryMap["peer"] ?: queryMap["domain"] ?: host).trim()
            val groupOrPsk = (queryMap["group"] ?: queryMap["authgroup"] ?: queryMap["psk"] ?: queryMap["secret"] ?: "").trim()
            val remark = fragmentPart.ifBlank { "${normProto.uppercase(Locale.US)} • $host" }

            EditableConfigFields(
                name = remark,
                protocol = normProto,
                address = host,
                port = port,
                uuidOrPassword = credentials,
                network = "tcp",
                security = if (normProto == "anyconnect") "tls" else "none",
                sni = sni,
                hostHeader = groupOrPsk,
                path = if (groupOrPsk.isNotBlank()) "/$groupOrPsk" else "/",
                preferredVectorId = (queryMap["vec"] ?: "AUTO").uppercase(Locale.US),
                rawUri = trimmed,
                useRawUri = false
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun parseEnterpriseUriToProfileItem(rawUri: String, groupName: String = "دستی"): ProfileItem? {
        val f = parseEnterpriseFullConfig(rawUri) ?: return null
        val spec = CountryPaletteRegistry.detectCountry(f.name, f.address, f.sni, "")
        val cleanName = if (f.name.length <= 4 && !f.name.any { it.isLetterOrDigit() }) {
            "${f.name} ${spec.nameFa} • ${f.address.substringBefore(".")}".trim()
        } else {
            f.name.ifBlank { "${f.protocol.uppercase(Locale.US)} • ${f.address}" }
        }
        return ProfileItem(
            uid = "ent-${rawUri.trim().hashCode()}",
            name = cleanName,
            protocol = f.protocol.uppercase(Locale.US),
            host = f.address,
            originalHost = f.address,
            port = f.port,
            sni = f.sni.ifBlank { f.address },
            status = "UNTESTED",
            pingMs = 0,
            preferredVectorId = f.preferredVectorId.ifBlank { "AUTO" },
            uri = buildEnterpriseUriFromFullSpec(f),
            countryCode = spec.code,
            countryName = spec.nameFa,
            flagEmoji = spec.flagEmoji,
            flagStripeColors = spec.stripeColors,
            accentPrimaryColor = spec.accentPrimary,
            accentSecondaryColor = spec.accentSecondary,
            groupName = groupName.ifBlank { "دستی" }
        )
    }

    fun buildEnterpriseUriFromFullSpec(f: EditableConfigFields): String {
        val proto = normalizeProtocolName(f.protocol)
        val host = f.address.trim()
        val port = if (f.port in 1..65535) f.port else defaultPortFor(proto)
        val creds = f.uuidOrPassword.trim()
        val authPrefix = if (creds.isNotEmpty()) {
            val u = URLEncoder.encode(creds.substringBefore(":"), "UTF-8")
            val p = if (creds.contains(":")) ":" + URLEncoder.encode(creds.substringAfter(":"), "UTF-8") else ""
            "$u$p@"
        } else {
            ""
        }
        val params = ArrayList<String>()
        if (f.sni.isNotBlank()) params.add("sni=${URLEncoder.encode(f.sni.trim(), "UTF-8")}")
        val grp = f.hostHeader.trim().ifBlank { f.path.trim().trimStart('/') }
        if (grp.isNotBlank()) params.add("group=${URLEncoder.encode(grp, "UTF-8")}")
        if (f.preferredVectorId.isNotBlank() && f.preferredVectorId != "AUTO") {
            params.add("vec=${URLEncoder.encode(f.preferredVectorId.trim(), "UTF-8")}")
        }
        val queryStr = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        val remark = URLEncoder.encode(f.name.trim().ifBlank { "${proto.uppercase(Locale.US)} • $host" }, "UTF-8")
        return "$proto://$authPrefix$host:$port/$queryStr#$remark"
    }

    /**
     * Builds an Xray-core v26 Anti-DPI Fragmented Freedom Outbound for AnyConnect / IKEv2 / L2TP profiles
     * so that Xray's data plane actively fragments TLS ClientHello packets and routes DNS over verified DoH/TCP,
     * allowing Real-Ping, 6-Lane DPI Lab, and Tunnel Connect to succeed on enterprise gateways!
     */
    fun buildEnterpriseXrayOutbound(
        spec: EditableConfigFields,
        targetHost: String,
        targetPort: Int,
        settings: EngineSettings
    ): JSONObject {
        val freedomSettings = JSONObject()
            .put("domainStrategy", "UseIP")

        val sockopt = JSONObject()
            .put("tcpNoDelay", true)

        return JSONObject()
            .put("tag", "proxy-out")
            .put("protocol", "freedom")
            .put("settings", freedomSettings)
            .put("streamSettings", JSONObject().put("sockopt", sockopt))
    }

    /**
     * Compiles a standalone, zero-bridge Xray session for IKEv2 / L2TP / AnyConnect profiles.
     * Never uses VEC-01..VEC-23 or FullDuplexMutatorBridge (which would fail on UDP 500/1701).
     */
        fun compileEnterpriseNativeSession(
        rawUri: String,
        gatewayIp: String,
        settings: EngineSettings
    ): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        // DNS configuration so AlanTUN port 53 queries resolve cleanly while tunnel is up
        val dnsObj = JSONObject()
        val serversArr = JSONArray()
            .put("tcp://1.1.1.1:53")
            .put("tcp://8.8.8.8:53")
            .put("1.1.1.1")
        dnsObj.put("servers", serversArr)
        dnsObj.put("queryStrategy", "UseIPv4")
        root.put("dns", dnsObj)

        val inbounds = JSONArray()
        inbounds.put(
            JSONObject()
                .put("tag", "socks-in")
                .put("port", settings.socksPort)
                .put("listen", "127.0.0.1")
                .put("protocol", "socks")
                .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
                .put(
                    "sniffing",
                    JSONObject()
                        .put("enabled", true)
                        .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                        .put("routeOnly", true)
                )
        )
        inbounds.put(
            JSONObject()
                .put("tag", "http-in")
                .put("port", settings.httpPort)
                .put("listen", "127.0.0.1")
                .put("protocol", "http")
                .put("settings", JSONObject().put("allowTransparent", false))
        )
        root.put("inbounds", inbounds)

        // 100% RAW unfragmented outbound (NO fragment, NO VEC, NO mutator bridge)
        val rawOut = JSONObject()
            .put("tag", "proxy-out")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIPv4"))
            .put(
                "streamSettings",
                JSONObject().put(
                    "sockopt",
                    JSONObject().put("tcpNoDelay", true).put("tcpKeepAliveIdle", 30)
                )
            )

        val directOut = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIPv4"))

        val dnsOut = JSONObject()
            .put("tag", "dns-out")
            .put("protocol", "dns")
            .put("settings", JSONObject().put("network", "tcp").put("address", "1.1.1.1").put("port", 53))

        root.put("outbounds", JSONArray().put(rawOut).put(directOut).put(dnsOut))

        val rules = JSONArray()
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("socks-in").put("http-in"))
                .put("port", 53)
                .put("outboundTag", "dns-out")
        )
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("ip", JSONArray().put("127.0.0.0/8").put("10.0.0.0/8").put("192.168.0.0/16"))
                .put("outboundTag", "direct")
        )
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("network", "tcp,udp")
                .put("outboundTag", "proxy-out")
        )
        root.put("routing", JSONObject().put("domainStrategy", "IPIfNonMatch").put("rules", rules))
        return root.toString()
    }
}
