package com.alan.ripdpi.core

import java.util.Locale

import android.net.Uri
import android.util.Base64
import com.alan.ripdpi.model.BypassVector
import com.alan.ripdpi.model.CountryPaletteRegistry
import com.alan.ripdpi.model.VectorCatalog
import com.alan.ripdpi.ui.EditableConfigFields
import com.alan.ripdpi.ui.EngineSettings
import com.alan.ripdpi.ui.ProfileItem
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

data class FullParsedConfig(
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val uuidOrPassword: String,
    val cipherOrEncryption: String,
    val flow: String,
    val network: String,
    val headerType: String,
    val hostHeader: String,
    val path: String,
    val earlyData: Int,
    val security: String,
    val sni: String,
    val fingerprint: String,
    val alpn: String,
    val publicKey: String,
    val shortId: String,
    val pinnedCertSha256: String = "",
    val verifyCertByName: String = ""
)

data class CompiledXraySession(
    val configJson: String,
    val socksPort: Int,
    val httpPort: Int,
    val bridgePort: Int,
    val remoteHost: String,
    val remotePort: Int
)

data class MultiLaneItem(
    val laneIndex: Int,
    val profile: ProfileItem,
    val httpPort: Int,
    val bridgePort: Int,
    val remoteHost: String,
    val remotePort: Int
)

data class MultiLaneXraySession(
    val configJson: String,
    val lanes: List<MultiLaneItem>
)

object UriConfigCodec {

    fun allocateSafePort(preferredPort: Int, exclude: Set<Int> = emptySet()): Int {
        if (preferredPort in 1025..65534 && preferredPort !in exclude && isLocalPortAvailable(preferredPort)) {
            return preferredPort
        }
        return ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }

    fun isLocalPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port, 16, InetAddress.getByName("127.0.0.1")).use { true }
        } catch (_: Throwable) {
            false
        }
    }

    fun extractConfigLinesFromSubscriptionBody(rawText: String): List<String> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()
        val decoded = if (!trimmed.contains("://") && trimmed.length >= 16) {
            decodeBase64Flexible(trimmed) ?: trimmed
        } else {
            trimmed
        }

        return decoded
            .lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.startsWith("vless://", ignoreCase = true) ||
                    line.startsWith("vmess://", ignoreCase = true) ||
                    line.startsWith("trojan://", ignoreCase = true) ||
                    line.startsWith("ss://", ignoreCase = true)
            }
            .distinct()
            .toList()
    }

    fun parseUriToProfileItem(rawUri: String): ProfileItem? {
        if (EnterpriseProtocolEngine.isEnterpriseUri(rawUri)) return EnterpriseProtocolEngine.parseEnterpriseUriToProfileItem(rawUri)
        val uri = rawUri.trim()
        if (uri.isEmpty()) return null
        val spec = parseFullConfig(uri) ?: return null
        val uid = UUID.nameUUIDFromBytes(uri.toByteArray(Charsets.UTF_8))
            .toString()
            .replace("-", "")
            .take(12)
        val displaySni = spec.sni.ifBlank { spec.hostHeader.ifBlank { spec.address } }
        val cleanTitle = spec.name.ifBlank { "${spec.protocol}-${spec.address}:${spec.port}" }
        val country = CountryPaletteRegistry.detectCountry(
            name = cleanTitle,
            host = spec.address,
            sni = displaySni
        )

        return ProfileItem(
            uid = uid,
            name = cleanTitle,
            protocol = spec.protocol,
            host = spec.address,
            originalHost = spec.address,
            port = spec.port,
            sni = displaySni,
            status = "UNTESTED",
            pingMs = 0,
            cachedVectorId = "",
            preferredVectorId = "AUTO",
            isSelected = false,
            uri = uri,
            countryCode = country.code,
            countryName = country.nameFa,
            flagEmoji = country.flagEmoji,
            flagStripeColors = country.stripeColors,
            accentPrimaryColor = country.accentPrimary,
            accentSecondaryColor = country.accentSecondary
        )
    }

    fun parseFullConfig(rawUri: String): FullParsedConfig? {
        val clean = rawUri.trim()
        return when {
            clean.startsWith("vless://", ignoreCase = true) -> parseVlessOrTrojan(clean, "VLESS")
            clean.startsWith("trojan://", ignoreCase = true) -> parseVlessOrTrojan(clean, "TROJAN")
            clean.startsWith("vmess://", ignoreCase = true) -> parseVmess(clean)
            clean.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(clean)
            else -> null
        }
    }

    private fun parseVlessOrTrojan(rawUri: String, proto: String): FullParsedConfig? {
        return try {
            val parsed = Uri.parse(rawUri)
            val userInfo = parsed.encodedUserInfo?.let { urlDecode(it) } ?: return null
            val host = parsed.host?.trim()?.removePrefix("[")?.removeSuffix("]") ?: return null
            if (host.isEmpty()) return null
            val port = if (parsed.port > 0) parsed.port else 443
            val remark = parsed.encodedFragment?.let { urlDecode(it) } ?: "$proto-$host"
            val q = parseQueryMap(parsed.encodedQuery)

            val network = (q["type"] ?: "tcp").lowercase()
            val rawPath = q["path"] ?: q["serviceName"] ?: "/"
            val (cleanPath, pathEd) = splitEarlyDataFromPath(rawPath)
            val edVal = (q["ed"]?.toIntOrNull() ?: pathEd.toIntOrNull() ?: 0).coerceAtLeast(0)

            FullParsedConfig(
                name = remark,
                protocol = proto,
                address = host,
                port = port,
                uuidOrPassword = userInfo,
                cipherOrEncryption = q["encryption"] ?: if (proto == "VLESS") "none" else "",
                flow = q["flow"] ?: "",
                network = network,
                headerType = q["headerType"] ?: "none",
                hostHeader = q["host"] ?: q["authority"] ?: "",
                path = cleanPath,
                earlyData = edVal,
                security = (q["security"] ?: if (proto == "TROJAN") "tls" else "none").lowercase(),
                sni = q["sni"] ?: "",
                fingerprint = q["fp"] ?: "chrome",
                alpn = q["alpn"] ?: "",
                publicKey = q["pbk"] ?: "",
                shortId = q["sid"] ?: "",
                pinnedCertSha256 = q["pcs"] ?: q["pinnedPeerCertSha256"] ?: "",
                verifyCertByName = q["vcn"] ?: q["verifyPeerCertByName"] ?: ""
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseVmess(rawUri: String): FullParsedConfig? {
        return try {
            val payload = rawUri.substringAfter("://").substringBefore("#").trim()
            val fragmentRemark = if (rawUri.contains("#")) urlDecode(rawUri.substringAfter("#")) else ""
            val jsonStr = decodeBase64Flexible(payload) ?: return null
            val obj = JSONObject(jsonStr)
            val add = obj.optString("add", "").trim()
            if (add.isEmpty()) return null
            val port = obj.optString("port", "443").toIntOrNull() ?: obj.optInt("port", 443)
            val id = obj.optString("id", "").trim()
            if (id.isEmpty()) return null
            val net = obj.optString("net", "tcp").lowercase()
            val rawPath = obj.optString("path", "/")
            val (cleanPath, pathEd) = splitEarlyDataFromPath(rawPath)
            val tlsVal = obj.optString("tls", "").lowercase()
            val sec = if (tlsVal == "tls" || tlsVal == "reality") tlsVal else "none"

            FullParsedConfig(
                name = obj.optString("ps", fragmentRemark).ifBlank { "VMESS-$add" },
                protocol = "VMESS",
                address = add,
                port = port.coerceIn(1, 65535),
                uuidOrPassword = id,
                cipherOrEncryption = obj.optString("scy", "auto").ifBlank { "auto" },
                flow = "",
                network = net,
                headerType = obj.optString("type", "none").ifBlank { "none" },
                hostHeader = obj.optString("host", ""),
                path = cleanPath,
                earlyData = pathEd.toIntOrNull() ?: 0,
                security = sec,
                sni = obj.optString("sni", ""),
                fingerprint = obj.optString("fp", "chrome").ifBlank { "chrome" },
                alpn = obj.optString("alpn", ""),
                publicKey = "",
                shortId = "",
                pinnedCertSha256 = obj.optString("pcs", ""),
                verifyCertByName = obj.optString("vcn", "")
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseShadowsocks(rawUri: String): FullParsedConfig? {
        return try {
            val withoutScheme = rawUri.substringAfter("://")
            val remark = if (withoutScheme.contains("#")) {
                urlDecode(withoutScheme.substringAfter("#"))
            } else {
                ""
            }
            val mainPart = withoutScheme.substringBefore("#").substringBefore("?")

            val method: String
            val password: String
            val host: String
            val port: Int

            if (mainPart.contains("@")) {
                val userPartRaw = mainPart.substringBeforeLast("@")
                val hostPort = mainPart.substringAfterLast("@")
                val decodedUser = if (userPartRaw.contains(":")) {
                    urlDecode(userPartRaw)
                } else {
                    decodeBase64Flexible(userPartRaw) ?: urlDecode(userPartRaw)
                }
                method = decodedUser.substringBefore(":").trim()
                password = decodedUser.substringAfter(":", "").trim()
                val hp = splitHostPort(hostPort) ?: return null
                host = hp.first
                port = hp.second.toIntOrNull() ?: 443
            } else {
                val decodedAll = decodeBase64Flexible(mainPart) ?: return null
                if (!decodedAll.contains("@")) return null
                val userPart = decodedAll.substringBeforeLast("@")
                val hostPort = decodedAll.substringAfterLast("@")
                method = userPart.substringBefore(":").trim()
                password = userPart.substringAfter(":", "").trim()
                val hp = splitHostPort(hostPort) ?: return null
                host = hp.first
                port = hp.second.toIntOrNull() ?: 443
            }

            if (host.isEmpty() || method.isEmpty()) return null

            FullParsedConfig(
                name = remark.ifBlank { "SS-$host" },
                protocol = "SHADOWSOCKS",
                address = host,
                port = port.coerceIn(1, 65535),
                uuidOrPassword = password,
                cipherOrEncryption = method,
                flow = "",
                network = "tcp",
                headerType = "none",
                hostHeader = "",
                path = "",
                earlyData = 0,
                security = "none",
                sni = "",
                fingerprint = "",
                alpn = "",
                publicKey = "",
                shortId = ""
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun toEditableFields(profile: ProfileItem): EditableConfigFields {
        val spec = parseFullConfig(profile.uri)
        if (spec == null) {
            return EditableConfigFields(
                name = profile.name,
                address = profile.host,
                port = profile.port,
                preferredVectorId = profile.preferredVectorId,
                rawUri = profile.uri,
                useRawUri = true
            )
        }

        val protoLower = when (spec.protocol.uppercase()) {
            "SHADOWSOCKS" -> "ss"
            else -> spec.protocol.lowercase()
        }
        val credentialValue = if (spec.protocol.uppercase() == "SHADOWSOCKS") {
            "${spec.cipherOrEncryption}:${spec.uuidOrPassword}"
        } else {
            spec.uuidOrPassword
        }

        return EditableConfigFields(
            name = spec.name,
            protocol = protoLower,
            address = spec.address,
            port = spec.port,
            uuidOrPassword = credentialValue,
            network = spec.network,
            security = spec.security,
            sni = spec.sni,
            hostHeader = spec.hostHeader,
            path = composePathWithEarlyData(spec.path, spec.earlyData, spec.network),
            alpn = spec.alpn,
            fingerprint = spec.fingerprint,
            flow = spec.flow,
            publicKey = spec.publicKey,
            shortId = spec.shortId,
            preferredVectorId = profile.preferredVectorId.ifBlank { "AUTO" },
            rawUri = profile.uri,
            useRawUri = false
        )
    }

    fun buildUriFromEditableFields(fields: EditableConfigFields, fallbackRawUri: String = ""): String {
        if (fields.useRawUri && fields.rawUri.isNotBlank()) {
            return fields.rawUri.trim()
        }

        val prevSpec = fallbackRawUri.takeIf { it.isNotBlank() }?.let { parseFullConfig(it) }
        val normalizedProto = when (fields.protocol.trim().uppercase()) {
            "SS", "SHADOWSOCKS" -> "SHADOWSOCKS"
            "VMESS" -> "VMESS"
            "TROJAN" -> "TROJAN"
            else -> "VLESS"
        }

        val (cleanPath, pathEdStr) = splitEarlyDataFromPath(fields.path)
        val edVal = pathEdStr.toIntOrNull() ?: prevSpec?.earlyData ?: 0

        val ssMethod: String
        val ssPassword: String
        if (normalizedProto == "SHADOWSOCKS") {
            if (fields.uuidOrPassword.contains(":")) {
                ssMethod = fields.uuidOrPassword.substringBefore(":").trim()
                ssPassword = fields.uuidOrPassword.substringAfter(":")
            } else {
                ssMethod = prevSpec?.cipherOrEncryption?.ifBlank { "chacha20-ietf-poly1305" }
                    ?: "chacha20-ietf-poly1305"
                ssPassword = fields.uuidOrPassword
            }
        } else {
            ssMethod = prevSpec?.cipherOrEncryption ?: if (normalizedProto == "VMESS") "auto" else "none"
            ssPassword = fields.uuidOrPassword.trim()
        }

        val full = FullParsedConfig(
            name = fields.name.ifBlank { "$normalizedProto-${fields.address}" },
            protocol = normalizedProto,
            address = fields.address.trim(),
            port = fields.port.coerceIn(1, 65535),
            uuidOrPassword = ssPassword,
            cipherOrEncryption = ssMethod,
            flow = fields.flow.trim(),
            network = fields.network.trim().lowercase().ifBlank { "tcp" },
            headerType = if (prevSpec?.network == fields.network.trim().lowercase()) prevSpec.headerType else "none",
            hostHeader = fields.hostHeader.trim(),
            path = cleanPath,
            earlyData = edVal,
            security = fields.security.trim().lowercase().ifBlank { "none" },
            sni = fields.sni.trim(),
            fingerprint = fields.fingerprint.trim(),
            alpn = fields.alpn.trim(),
            publicKey = fields.publicKey.trim(),
            shortId = fields.shortId.trim(),
            pinnedCertSha256 = prevSpec?.pinnedCertSha256.orEmpty(),
            verifyCertByName = prevSpec?.verifyCertByName.orEmpty()
        )

        return buildUriFromFullSpec(full)
    }

    fun injectEarlyDataIntoUri(rawUri: String, earlyDataBytes: Int): String {
        if (earlyDataBytes <= 0) return rawUri
        val spec = parseFullConfig(rawUri) ?: return rawUri
        if (!spec.network.equals("ws", ignoreCase = true)) return rawUri
        return buildUriFromFullSpec(spec.copy(earlyData = earlyDataBytes))
    }

    fun buildUriFromFullSpec(spec: FullParsedConfig): String {
        if (spec.address.isBlank()) return ""
        val proto = spec.protocol.uppercase()
        val hostFormatted = if (spec.address.contains(":") && !spec.address.startsWith("[")) {
            "[${spec.address}]"
        } else {
            spec.address
        }
        val portNum = spec.port.coerceIn(1, 65535)
        val encodedRemark = urlEncode(spec.name.ifBlank { "$proto-${spec.address}" })
        val finalPath = composePathWithEarlyData(spec.path, spec.earlyData, spec.network)

        return when (proto) {
            "VLESS", "TROJAN" -> {
                val scheme = proto.lowercase()
                val params = LinkedHashMap<String, String>()
                params["type"] = spec.network.ifBlank { "tcp" }
                params["security"] = spec.security.ifBlank { if (proto == "TROJAN") "tls" else "none" }
                if (proto == "VLESS") {
                    params["encryption"] = spec.cipherOrEncryption.ifBlank { "none" }
                    if (spec.flow.isNotBlank()) params["flow"] = spec.flow
                }
                if (spec.headerType.isNotBlank() && spec.headerType != "none") {
                    params["headerType"] = spec.headerType
                }
                if (spec.hostHeader.isNotBlank()) {
                    if (spec.network.equals("grpc", true)) {
                        params["authority"] = spec.hostHeader
                    } else {
                        params["host"] = spec.hostHeader
                    }
                }
                if (finalPath.isNotBlank()) {
                    if (spec.network.equals("grpc", true)) {
                        params["serviceName"] = spec.path
                    } else {
                        params["path"] = finalPath
                    }
                }
                if (spec.sni.isNotBlank()) params["sni"] = spec.sni
                if (spec.fingerprint.isNotBlank()) params["fp"] = spec.fingerprint
                if (spec.alpn.isNotBlank()) params["alpn"] = spec.alpn
                if (spec.publicKey.isNotBlank()) params["pbk"] = spec.publicKey
                if (spec.shortId.isNotBlank()) params["sid"] = spec.shortId
                if (spec.pinnedCertSha256.isNotBlank()) params["pcs"] = spec.pinnedCertSha256
                if (spec.verifyCertByName.isNotBlank()) params["vcn"] = spec.verifyCertByName

                val queryString = params.entries.joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
                "$scheme://${urlEncode(spec.uuidOrPassword)}@$hostFormatted:$portNum?$queryString#$encodedRemark"
            }
            "VMESS" -> {
                val obj = JSONObject().apply {
                    put("v", "2")
                    put("ps", spec.name.ifBlank { "VMESS-${spec.address}" })
                    put("add", spec.address)
                    put("port", portNum.toString())
                    put("id", spec.uuidOrPassword)
                    put("scy", spec.cipherOrEncryption.ifBlank { "auto" })
                    put("net", spec.network.ifBlank { "tcp" })
                    put("type", spec.headerType.ifBlank { "none" })
                    put("host", spec.hostHeader)
                    put("path", finalPath)
                    put("tls", if (spec.security.equals("tls", true)) "tls" else "")
                    put("sni", spec.sni)
                    put("alpn", spec.alpn)
                    put("fp", spec.fingerprint.ifBlank { "chrome" })
                }
                val b64 = Base64.encodeToString(obj.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "vmess://$b64"
            }
            "SHADOWSOCKS" -> {
                val method = spec.cipherOrEncryption.ifBlank { "chacha20-ietf-poly1305" }
                val userCred = "$method:${spec.uuidOrPassword}"
                val userB64 = Base64.encodeToString(
                    userCred.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
                )
                "ss://$userB64@$hostFormatted:$portNum#$encodedRemark"
            }
            else -> ""
        }
    }

    fun buildVectorPlan(profile: ProfileItem, settings: EngineSettings): List<BypassVector> {
        val baseVectors = VectorCatalog.filterVectorsByMode(settings.engineMode).filter { it.family != "DIRECT" }
        val directVec = VectorCatalog.findVector("VEC-00")
        val uriLow = profile.uri.lowercase(java.util.Locale.US)
        val protoUp = profile.protocol.uppercase(java.util.Locale.US)
        val hostLow = profile.originalHost.ifBlank { profile.host }.lowercase(java.util.Locale.US).trim()

        if (protoUp in setOf("SHADOWSOCKS", "SS", "SOCKS")) {
            return listOf(directVec)
        }

        if (uriLow.contains("security=reality")) {
            val fragSubset = baseVectors.filter { it.family in setOf("COALESCE", "FRAGMENT") }.take(3)
            return listOf(directVec) + fragSubset
        }

        val isTunnelOrPlain = hostLow.endsWith(".ir") ||
            uriLow.contains("headertype=http") ||
            uriLow.contains("security=none")
        if (isTunnelOrPlain) {
            return listOf(directVec) + baseVectors
        }

        val ordered = baseVectors.toMutableList()
        if (profile.cachedVectorId.isNotBlank() && !profile.cachedVectorId.equals("AUTO", true)) {
            val cv = VectorCatalog.findVector(profile.cachedVectorId)
            ordered.remove(cv)
            ordered.add(0, cv)
        }
        if (profile.preferredVectorId.isNotBlank() && !profile.preferredVectorId.equals("AUTO", true)) {
            val pv = VectorCatalog.findVector(profile.preferredVectorId)
            ordered.remove(pv)
            ordered.add(0, pv)
        }
        return ordered + directVec
    }

    fun compileXraySession(
        profile: ProfileItem,
        settings: EngineSettings,
        preferredSocksPort: Int = settings.socksPort,
        preferredHttpPort: Int = settings.httpPort,
        preferredBridgePort: Int = 20809,
        forceLogSilent: Boolean = false
    ): CompiledXraySession {
        if (EnterpriseProtocolEngine.isEnterpriseProtocol(profile.protocol) || EnterpriseProtocolEngine.isEnterpriseUri(profile.uri)) {
            val used = mutableSetOf<Int>()
            val socksPort = allocateSafePort(preferredSocksPort, used).also { used.add(it) }
            val httpPort = allocateSafePort(preferredHttpPort, used).also { used.add(it) }
            val bridgePort = allocateSafePort(preferredBridgePort.coerceAtLeast(1025), used).also { used.add(it) }
            val remoteHost = profile.host.ifBlank { profile.originalHost }.trim().ifBlank { "1.1.1.1" }
            val remotePort = (if (profile.port > 0) profile.port else EnterpriseProtocolEngine.defaultPortFor(profile.protocol)).coerceIn(1, 65535)
            val rawJson = EnterpriseProtocolEngine.compileEnterpriseNativeSession(
                rawUri = profile.uri,
                gatewayIp = remoteHost,
                settings = settings.copy(socksPort = socksPort, httpPort = httpPort),
                bridgePort = bridgePort
            )
            return CompiledXraySession(
                configJson = rawJson,
                socksPort = socksPort,
                httpPort = httpPort,
                bridgePort = bridgePort,
                remoteHost = remoteHost,
                remotePort = remotePort
            )
        }
        val spec = parseFullConfig(profile.uri)
            ?: throw IllegalArgumentException("Unsupported config URI: ${profile.name}")

        val used = mutableSetOf<Int>()
        val socksPort = allocateSafePort(preferredSocksPort, used).also { used.add(it) }
        val httpPort = allocateSafePort(preferredHttpPort, used).also { used.add(it) }
        val bridgePort = if (preferredBridgePort < 0) -1 else allocateSafePort(preferredBridgePort, used).also { used.add(it) }

        val remoteHost = profile.host.ifBlank { spec.address }.trim()
        val remotePort = (if (profile.port > 0) profile.port else spec.port).coerceIn(1, 65535)

        val useNativeXrayFragment = !settings.autoPilot && settings.fragmentEnabled &&
            spec.protocol.uppercase() != "SHADOWSOCKS"

        val root = JSONObject()
        root.put(
            "log",
            JSONObject().apply {
                put("loglevel", if (forceLogSilent) "none" else "warning")
            }
        )
        root.put("stats", JSONObject())
        root.put(
            "policy",
            JSONObject().put(
                "system",
                JSONObject().apply {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                }
            )
        )

        val inbounds = JSONArray()
        inbounds.put(
            JSONObject().apply {
                put("tag", "socks-in")
                put("port", socksPort)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put(
                    "settings",
                    JSONObject().apply {
                        put("auth", "noauth")
                        put("udp", true)
                    }
                )
                put(
                    "sniffing",
                    JSONObject().apply {
                        put("enabled", true)
                        put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                        put("routeOnly", true)
                    }
                )
            }
        )
        inbounds.put(
            JSONObject().apply {
                put("tag", "http-in")
                put("port", httpPort)
                put("listen", "127.0.0.1")
                put("protocol", "http")
                put("settings", JSONObject().put("allowTransparent", false))
            }
        )
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(spec, bridgePort, settings, useNativeXrayFragment))

        if (useNativeXrayFragment) {
            outbounds.put(
                JSONObject().apply {
                    put("tag", "fragment-out")
                    put("protocol", "freedom")
                    put(
                        "settings",
                        JSONObject().apply {
                            put(
                                "fragment",
                                JSONObject().apply {
                                    put("packets", settings.fragmentPackets.ifBlank { "tlshello" })
                                    put("length", settings.fragmentLength.ifBlank { "5-16" })
                                    put("interval", settings.fragmentInterval.ifBlank { "2-3" })
                                }
                            )
                        }
                    )
                    put(
                        "streamSettings",
                        JSONObject().apply {
                            put("sockopt", JSONObject().put("tcpNoDelay", true))
                        }
                    )
                }
            )
        }

        outbounds.put(
            JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put("settings", JSONObject())
            }
        )
        outbounds.put(
            JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
                put("settings", JSONObject())
            }
        )
        root.put("outbounds", outbounds)

        val privateCidrs = JSONArray()
            .put("127.0.0.0/8")
            .put("10.0.0.0/8")
            .put("172.16.0.0/12")
            .put("192.168.0.0/16")
            .put("169.254.0.0/16")
            .put("::1/128")
            .put("fc00::/7")
            .put("fe80::/10")

        root.put(
            "routing",
            JSONObject().apply {
                put("domainStrategy", "AsIs")
                put(
                    "rules",
                    JSONArray().put(
                        JSONObject().apply {
                            put("type", "field")
                            put("ip", privateCidrs)
                            put("outboundTag", "direct")
                        }
                    )
                )
            }
        )

        return CompiledXraySession(
            configJson = root.toString(),
            socksPort = socksPort,
            httpPort = httpPort,
            bridgePort = bridgePort,
            remoteHost = remoteHost,
            remotePort = remotePort
        )
    }

    private fun buildProxyOutbound(
        spec: FullParsedConfig,
        bridgePort: Int,
        settings: EngineSettings,
        useNativeXrayFragment: Boolean
    ): JSONObject {
        val proto = spec.protocol.uppercase()
        val outbound = JSONObject()
        outbound.put("tag", "proxy")

        when (proto) {
            "VLESS" -> {
                outbound.put("protocol", "vless")
                val userObj = JSONObject().apply {
                    put("id", spec.uuidOrPassword)
                    put("encryption", spec.cipherOrEncryption.ifBlank { "none" })
                    if (spec.flow.isNotBlank()) put("flow", spec.flow)
                }
                val vnextObj = JSONObject().apply {
                    put("address", "127.0.0.1")
                    put("port", bridgePort)
                    put("users", JSONArray().put(userObj))
                }
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(vnextObj)))
            }
            "VMESS" -> {
                outbound.put("protocol", "vmess")
                val userObj = JSONObject().apply {
                    put("id", spec.uuidOrPassword)
                    put("security", spec.cipherOrEncryption.ifBlank { "auto" })
                }
                val vnextObj = JSONObject().apply {
                    put("address", "127.0.0.1")
                    put("port", bridgePort)
                    put("users", JSONArray().put(userObj))
                }
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(vnextObj)))
            }
            "TROJAN" -> {
                outbound.put("protocol", "trojan")
                val srvObj = JSONObject().apply {
                    put("address", "127.0.0.1")
                    put("port", bridgePort)
                    put("password", spec.uuidOrPassword)
                }
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(srvObj)))
            }
            "SHADOWSOCKS" -> {
                outbound.put("protocol", "shadowsocks")
                val srvObj = JSONObject().apply {
                    put("address", "127.0.0.1")
                    put("port", bridgePort)
                    put("method", spec.cipherOrEncryption.ifBlank { "chacha20-ietf-poly1305" })
                    put("password", spec.uuidOrPassword)
                    put("uot", true)
                }
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(srvObj)))
            }
        }

        outbound.put("streamSettings", buildStreamSettings(spec, settings, useNativeXrayFragment))

        if (settings.muxEnabled && proto != "SHADOWSOCKS" && spec.flow.isBlank()) {
            outbound.put(
                "mux",
                JSONObject().apply {
                    put("enabled", true)
                    put("concurrency", settings.muxConcurrency.coerceIn(1, 64))
                }
            )
        }

        return outbound
    }

    private fun buildStreamSettings(
        spec: FullParsedConfig,
        settings: EngineSettings,
        useNativeXrayFragment: Boolean
    ): JSONObject {
        val stream = JSONObject()
        val net = spec.network.lowercase().ifBlank { "tcp" }
        val finalNet = when (net) {
            "splithttp", "h2", "http", "xhttp" -> "xhttp"
            "raw", "tcp" -> "tcp"
            else -> net
        }
        stream.put("network", finalNet)

        val effectiveSni = spec.sni.ifBlank {
            spec.hostHeader.ifBlank { spec.address }
        }
        val effectiveEd = if (spec.earlyData > 0) spec.earlyData else settings.wsEarlyData
        val wsPath = composePathWithEarlyData(spec.path, effectiveEd, finalNet)

        when (finalNet) {
            "tcp" -> {
                if (spec.headerType.equals("http", ignoreCase = true)) {
                    val hostArr = JSONArray()
                    if (spec.hostHeader.isNotBlank()) {
                        spec.hostHeader.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { hostArr.put(it) }
                    } else {
                        hostArr.put(spec.address)
                    }
                    val pathArr = JSONArray().put(spec.path.ifBlank { "/" })
                    stream.put(
                        "tcpSettings",
                        JSONObject().apply {
                            put(
                                "header",
                                JSONObject().apply {
                                    put("type", "http")
                                    put(
                                        "request",
                                        JSONObject().apply {
                                            put("version", "1.1")
                                            put("method", "GET")
                                            put("path", pathArr)
                                            put(
                                                "headers",
                                                JSONObject().apply {
                                                    put("Host", hostArr)
                                                    put(
                                                        "User-Agent",
                                                        JSONArray().put("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                                    )
                                                    put("Accept-Encoding", JSONArray().put("gzip, deflate"))
                                                    put("Connection", JSONArray().put("keep-alive"))
                                                    put("Pragma", "no-cache")
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            }
            "ws" -> {
                stream.put(
                    "wsSettings",
                    JSONObject().apply {
                        put("path", wsPath.ifBlank { "/" })
                        if (spec.hostHeader.isNotBlank()) {
                            put("host", spec.hostHeader)
                        }
                    }
                )
            }
            "httpupgrade" -> {
                stream.put(
                    "httpupgradeSettings",
                    JSONObject().apply {
                        put("path", spec.path.ifBlank { "/" })
                        if (spec.hostHeader.isNotBlank()) {
                            put("host", spec.hostHeader)
                        }
                    }
                )
            }
            "xhttp" -> {
                stream.put(
                    "xhttpSettings",
                    JSONObject().apply {
                        put("path", spec.path.ifBlank { "/" })
                        if (spec.hostHeader.isNotBlank()) {
                            put("host", spec.hostHeader)
                        }
                    }
                )
            }
            "grpc" -> {
                stream.put(
                    "grpcSettings",
                    JSONObject().apply {
                        put("serviceName", spec.path)
                        if (spec.hostHeader.isNotBlank()) {
                            put("authority", spec.hostHeader)
                        }
                        put("multiMode", false)
                    }
                )
            }
        }

        when (spec.security.lowercase()) {
            "tls" -> {
                stream.put("security", "tls")
                val effectiveFp = spec.fingerprint.ifBlank { settings.fingerprint.ifBlank { "chrome" } }
                val effectiveAlpn = if (
                    settings.alpnOverride.isNotBlank() &&
                    !settings.alpnOverride.equals("auto", ignoreCase = true)
                ) {
                    settings.alpnOverride
                } else {
                    spec.alpn
                }
                stream.put(
                    "tlsSettings",
                    JSONObject().apply {
                        put("serverName", effectiveSni)
                        if (effectiveFp.isNotBlank()) {
                            put("fingerprint", effectiveFp)
                        }
                        if (effectiveAlpn.isNotBlank()) {
                            val alpnArr = JSONArray()
                            effectiveAlpn.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .forEach { alpnArr.put(it) }
                            if (alpnArr.length() > 0) put("alpn", alpnArr)
                        }
                        if (spec.pinnedCertSha256.isNotBlank()) {
                            put("pinnedPeerCertSha256", spec.pinnedCertSha256)
                        }
                        if (spec.verifyCertByName.isNotBlank()) {
                            put("verifyPeerCertByName", spec.verifyCertByName)
                        }
                    }
                )
            }
            "reality" -> {
                stream.put("security", "reality")
                val effectiveFp = spec.fingerprint.ifBlank { settings.fingerprint.ifBlank { "chrome" } }
                stream.put(
                    "realitySettings",
                    JSONObject().apply {
                        put("serverName", effectiveSni)
                        put("fingerprint", effectiveFp)
                        put("publicKey", spec.publicKey)
                        put("shortId", spec.shortId)
                        put("spiderX", "/")
                    }
                )
            }
            else -> {
                stream.put("security", "none")
            }
        }

        stream.put(
            "sockopt",
            JSONObject().apply {
                put("tcpNoDelay", true)
                if (useNativeXrayFragment) {
                    put("dialerProxy", "fragment-out")
                }
            }
        )

        return stream
    }

    private fun splitEarlyDataFromPath(rawPath: String): Pair<String, String> {
        if (!rawPath.contains("ed=")) return rawPath to ""
        val qIdx = rawPath.indexOf('?')
        if (qIdx < 0) return rawPath to ""
        val basePath = rawPath.substring(0, qIdx)
        val query = rawPath.substring(qIdx + 1)
        var edValue = ""
        val kept = mutableListOf<String>()
        for (part in query.split("&")) {
            if (part.startsWith("ed=")) {
                edValue = part.substringAfter("ed=")
            } else if (part.isNotBlank()) {
                kept.add(part)
            }
        }
        val rebuilt = if (kept.isEmpty()) basePath else "$basePath?${kept.joinToString("&")}"
        return rebuilt to edValue
    }

    private fun composePathWithEarlyData(path: String, earlyData: Int, network: String): String {
        val base = path.trim()
        if (earlyData <= 0 || !network.equals("ws", ignoreCase = true) || base.contains("ed=")) {
            return base
        }
        val normalizedBase = base.ifEmpty { "/" }
        val sep = if (normalizedBase.contains("?")) "&" else "?"
        return "${normalizedBase}${sep}ed=$earlyData"
    }

    private fun splitHostPort(hostPort: String): Pair<String, String>? {
        val trimmed = hostPort.trim().substringBefore("/")
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf(']')
            if (closeIdx <= 1) return null
            val h = trimmed.substring(1, closeIdx)
            val p = trimmed.substring(closeIdx + 1).removePrefix(":").ifBlank { "443" }
            h to p
        } else {
            val colonIdx = trimmed.lastIndexOf(':')
            if (colonIdx <= 0) return trimmed to "443"
            trimmed.substring(0, colonIdx) to trimmed.substring(colonIdx + 1)
        }
    }

    private fun parseQueryMap(encodedQuery: String?): Map<String, String> {
        if (encodedQuery.isNullOrBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (pair in encodedQuery.split("&")) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val k = urlDecode(pair.substring(0, idx))
                val v = urlDecode(pair.substring(idx + 1))
                map[k] = v
            } else {
                map[urlDecode(pair)] = ""
            }
        }
        return map
    }

    private fun decodeBase64Flexible(input: String): String? {
        val sanitized = input.trim().replace("\r", "").replace("\n", "").replace(" ", "")
        if (sanitized.isEmpty()) return null
        val padded = when (sanitized.length % 4) {
            2 -> "$sanitized=="
            3 -> "$sanitized="
            else -> sanitized
        }
        val flagsToTry = intArrayOf(
            Base64.DEFAULT,
            Base64.URL_SAFE or Base64.NO_WRAP,
            Base64.NO_WRAP
        )
        for (flag in flagsToTry) {
            try {
                val bytes = Base64.decode(padded, flag)
                if (bytes.isNotEmpty()) {
                    return String(bytes, Charsets.UTF_8)
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun urlDecode(s: String): String {
        return try {
            URLDecoder.decode(s, "UTF-8")
        } catch (_: Throwable) {
            s
        }
    }

    private fun urlEncode(s: String): String {
        return try {
            URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        } catch (_: Throwable) {
            s
        }
    }

    // Interleaved OOB + Zero-OOB Vector Wave Plan: guarantees Railway/Envoy/Nginx AND Cloudflare both succeed in Wave 1!
    fun buildBalancedRacePlan(profile: ProfileItem, settings: EngineSettings): List<BypassVector> {
        val protoUp = profile.protocol.uppercase(java.util.Locale.US)
        val uriLow = profile.uri.lowercase(java.util.Locale.US)
        val hostLow = profile.originalHost.ifBlank { profile.host }.lowercase(java.util.Locale.US).trim()
        val directVec = VectorCatalog.findVector("VEC-00")

        if (protoUp in setOf("SHADOWSOCKS", "SS", "SOCKS")) {
            return listOf(directVec)
        }
        if (uriLow.contains("security=reality")) {
            return listOf(
                directVec,
                VectorCatalog.findVector("VEC-17"),
                VectorCatalog.findVector("VEC-11"),
                VectorCatalog.findVector("VEC-23")
            ).distinctBy { it.vectorId }
        }
        if (hostLow.endsWith(".ir") || uriLow.contains("security=none")) {
            return (listOf(directVec) + buildVectorPlan(profile, settings)).distinctBy { it.vectorId }
        }

        // Wave 1 mixes 3 Zero-OOB framing/fragment vectors (VEC-17, VEC-11, VEC-23) + 3 OOB vectors (VEC-21, VEC-01, VEC-18)!
        val wavePriorityIds = listOf(
            "VEC-21", // Hybrid OOB (Top on Cloudflare Edge)
            "VEC-17", // Zero-OOB 3-Record DotSplit (Top on Railway / Envoy / TLS 1.3)
            "VEC-11", // Zero-OOB Micro-Fragment 5-16B (Universal DPI SNI bypass)
            "VEC-01", // Core Alpha SNI-Tag OOB
            "VEC-23", // Zero-OOB Chameleon Interleave
            "VEC-18", // Cipher-Pivot OOB
            "VEC-20", // Zero-OOB Dot-Fibonacci
            "VEC-13", // Zero-OOB Rapid Fragment 12-28B
            "VEC-22", // Microcell Pincer OOB
            "VEC-19", // Stream Window Balance
            "VEC-09", // Zero-OOB Atomic Mid-SNI Split
            "VEC-04", // Preamble Fusion OOB
            "VEC-08", // Zero-OOB Dual Preamble
            "VEC-14", // Zero-OOB Multi-Flight Fragment
            "VEC-02", "VEC-03", "VEC-05", "VEC-06", "VEC-07", "VEC-10", "VEC-15", "VEC-16", "VEC-12", "VEC-00"
        )

        val allowedSet = VectorCatalog.filterVectorsByMode(settings.engineMode).map { it.vectorId }.toSet()
        val ordered = ArrayList<BypassVector>(24)

        for (pinned in listOf(profile.preferredVectorId, profile.cachedVectorId)) {
            if (pinned.isNotBlank() && !pinned.equals("AUTO", true)) {
                val v = VectorCatalog.findVector(pinned)
                if (ordered.none { it.vectorId == v.vectorId }) ordered.add(v)
            }
        }
        for (vid in wavePriorityIds) {
            if (vid in allowedSet) {
                val v = VectorCatalog.findVector(vid)
                if (ordered.none { it.vectorId == v.vectorId }) ordered.add(v)
            }
        }
        return ordered
    }

    fun updateUriRemarkName(rawUri: String, newRemark: String): String {
        val cleanName = newRemark.trim()
        if (cleanName.isEmpty()) return rawUri
        val spec = parseFullConfig(rawUri) ?: return rawUri
        return buildUriFromFullSpec(spec.copy(name = cleanName))
    }

    fun compileMultiLaneProbeSession(
        laneSpecs: List<Pair<ProfileItem, String>>,
        settings: EngineSettings
    ): MultiLaneXraySession {
        val usedPorts = LinkedHashSet<Int>()
        usedPorts.add(settings.socksPort)
        usedPorts.add(settings.httpPort)
        usedPorts.add(20809)

        val inboundsArr = org.json.JSONArray()
        val outboundsArr = org.json.JSONArray()
        val rulesArr = org.json.JSONArray()
        val compiledLanes = ArrayList<MultiLaneItem>(laneSpecs.size)

        for ((idx, pair) in laneSpecs.withIndex()) {
            val (prof, epOverride) = pair
            val spec = parseFullConfig(prof.uri) ?: continue

            val httpPortVal = allocateSafePort(0, usedPorts)
            usedPorts.add(httpPortVal)
            val bridgePortVal = allocateSafePort(0, usedPorts)
            usedPorts.add(bridgePortVal)

            val remoteHostVal = epOverride.ifBlank { prof.host.ifBlank { spec.address } }.trim()
            val remotePortVal = (if (prof.port in 1..65535) prof.port else spec.port).coerceIn(1, 65535)

            val inTag = "http-lane-$idx"
            val outTag = "proxy-lane-$idx"

            inboundsArr.put(
                org.json.JSONObject()
                    .put("tag", inTag)
                    .put("listen", "127.0.0.1")
                    .put("port", httpPortVal)
                    .put("protocol", "http")
                    .put("settings", org.json.JSONObject().put("allowTransparent", false))
                    .put("sniffing", org.json.JSONObject().put("enabled", false))
            )

            // Compile outbound cleanly via single-lane helper & retag
            val single = compileXraySession(
                profile = prof,
                settings = settings,
                preferredSocksPort = 0,
                preferredHttpPort = httpPortVal,
                preferredBridgePort = bridgePortVal,
                forceLogSilent = true
            )
            val singleJson = org.json.JSONObject(single.configJson)
            val singleOut = singleJson.getJSONArray("outbounds").getJSONObject(0)
            singleOut.put("tag", outTag)
            singleOut.optJSONObject("streamSettings")?.optJSONObject("sockopt")?.remove("dialerProxy")
            outboundsArr.put(singleOut)

            rulesArr.put(
                org.json.JSONObject()
                    .put("type", "field")
                    .put("inboundTag", org.json.JSONArray().put(inTag))
                    .put("outboundTag", outTag)
            )

            compiledLanes.add(
                MultiLaneItem(
                    laneIndex = idx,
                    profile = prof,
                    httpPort = httpPortVal,
                    bridgePort = bridgePortVal,
                    remoteHost = remoteHostVal,
                    remotePort = remotePortVal
                )
            )
        }

        outboundsArr.put(org.json.JSONObject().put("tag", "direct-out").put("protocol", "freedom"))

        val root = org.json.JSONObject()
            .put("log", org.json.JSONObject().put("loglevel", "none"))
            .put("inbounds", inboundsArr)
            .put("outbounds", outboundsArr)
            .put("routing", org.json.JSONObject().put("domainStrategy", "AsIs").put("rules", rulesArr))

        return MultiLaneXraySession(configJson = root.toString(), lanes = compiledLanes)
    }

    fun extractExplicitIsoFromTitle(title: String): String {
        // Matches [DE-01] or [DE] produced by Alan Cloudflare Worker!
        val bracketMatch = Regex("\\[([A-Za-z]{2})(?:-\\d+)?\\]").find(title)
        if (bracketMatch != null) {
            val code = bracketMatch.groupValues[1].uppercase(java.util.Locale.US)
            if (code != "UN" && code != "CF") return if (code == "UK") "GB" else code
        }
        return ""
    }

    fun extractConfigUrisFromText(rawText: String): List<String> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = LinkedHashSet<String>()
        val schemeRegex = Regex(
            """(?i)\b(?:vless|vmess|trojan|ss|socks|anyconnect|openconnect|oc|cisco|ikev2|ipsec|l2tp|hy2|hysteria2|tuic)://[^\r\n"'<>]+"""
        )

        fun scanChunk(chunk: String) {
            schemeRegex.findAll(chunk).forEach { m ->
                val uri = m.value.trim().trimEnd(',', ';', ')', ']', '}')
                if (uri.length > 10) {
                    results.add(uri)
                }
            }
        }

        scanChunk(trimmed)

        val compact = trimmed.replace("\\s+".toRegex(), "")
        for (candidateB64 in listOf(compact) + trimmed.lines().map { it.trim() }.filter { it.length > 16 && !it.contains("://") }) {
            try {
                val normalized = candidateB64.replace('-', '+').replace('_', '/').let { s ->
                    val rem = s.length % 4
                    if (rem == 0) s else s + "=".repeat(4 - rem)
                }
                val decoded = String(java.util.Base64.getDecoder().decode(normalized), Charsets.UTF_8)
                if (decoded.isNotBlank()) {
                    scanChunk(decoded)
                }
            } catch (_: Throwable) {
            }
        }

        return results.toList()
    }
}
