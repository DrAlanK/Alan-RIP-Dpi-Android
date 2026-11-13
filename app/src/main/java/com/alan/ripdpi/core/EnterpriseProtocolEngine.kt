package com.alan.ripdpi.core

import com.alan.ripdpi.model.CountryPaletteRegistry
import com.alan.ripdpi.ui.EditableConfigFields
import com.alan.ripdpi.ui.EngineSettings
import com.alan.ripdpi.ui.ProfileItem
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
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
        val clean = proto.trim().lowercase(Locale.US)
        return clean in ENTERPRISE_SCHEMES
    }

    fun isEnterpriseUri(rawUri: String): Boolean {
        val clean = rawUri.trim().lowercase(Locale.US)
        val scheme = clean.substringBefore("://", "")
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

            // Check if the body is Base64-encoded JSON or Base64-encoded user:pass@host:port
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
                "$normProto://$maybeDecoded" + (if (beforeFragment.contains("?")) "?" + beforeFragment.substringAfter("?") else "") + (if (fragmentPart.isNotEmpty()) "#$fragmentPart" else "")
            } else {
                "$normProto://$beforeFragment" + (if (fragmentPart.isNotEmpty()) "#$fragmentPart" else "")
            }

            val u = URI(normalizedUriStr)
            val host = u.host?.trim().orEmpty().ifBlank {
                beforeFragment.substringBefore("?").substringBefore("/").substringAfterLast("@").substringBefore(":")
            }.trim()
            if (host.isEmpty()) return null

            val port = if (u.port in 1..65535) {
                u.port
            } else {
                beforeFragment.substringBefore("?").substringBefore("/").substringAfterLast(":").toIntOrNull()
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

            val sni = (queryMap["sni"] ?: queryMap["peer"] ?: queryMap["domain"] ?: host).trim()
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
        return ProfileItem(
            uid = "ent-${rawUri.trim().hashCode()}",
            name = f.name.ifBlank { "${f.protocol.uppercase(Locale.US)} • ${f.address}" },
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
     * Builds an Xray-compatible outbound JSON for AnyConnect / IKEv2 / L2TP so Xray-core v26
     * routes tunnel traffic through FullDuplexMutatorBridge (127.0.0.1:bridgePort).
     */
    fun buildEnterpriseXrayOutbound(
        spec: EditableConfigFields,
        targetHost: String,
        targetPort: Int,
        settings: EngineSettings
    ): JSONObject {
        val normProto = normalizeProtocolName(spec.protocol)
        val user = spec.uuidOrPassword.substringBefore(":").trim()
        val pass = spec.uuidOrPassword.substringAfter(":", "").trim()

        val serverObj = JSONObject()
            .put("address", targetHost)
            .put("port", targetPort)

        if (user.isNotEmpty()) {
            val userAcc = JSONObject()
                .put("user", user)
                .put("pass", pass)
            serverObj.put("users", JSONArray().put(userAcc))
        }

        val streamSettings = JSONObject().put("network", "tcp")
        if (normProto == "anyconnect") {
            val fp = when (settings.fingerprint.lowercase(Locale.US)) {
                "custom_poly", "auto", "" -> "chrome"
                else -> settings.fingerprint.lowercase(Locale.US)
            }
            val tlsSettings = JSONObject()
                .put("serverName", spec.sni.ifBlank { spec.address })
                .put("allowInsecure", true)
                .put("fingerprint", fp)
                .put("alpn", JSONArray().put("http/1.1"))
            streamSettings.put("security", "tls").put("tlsSettings", tlsSettings)
        } else {
            streamSettings.put("security", "none")
        }

        return JSONObject()
            .put("tag", "proxy-out")
            .put("protocol", "http")
            .put("settings", JSONObject().put("servers", JSONArray().put(serverObj)))
            .put("streamSettings", streamSettings)
    }
}
