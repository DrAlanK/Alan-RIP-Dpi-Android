package com.alan.ripdpi.core

import android.content.Context
import android.system.Os
import android.util.Base64
import okhttp3.OkHttpClient
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class CaAuditSummary(
    val initialized: Boolean = false,
    val totalSystemCas: Int = 0,
    val trustedCleanCas: Int = 0,
    val blockedCasCount: Int = 0,
    val blockedCaNames: List<String> = emptyList(),
    val pemBundlePath: String = ""
)

/**
 * Strips untrusted / state-linked OEM root CAs (e.g. BEIJING, GUANG DONG, iTrusChina,
 * TrustAsia, UniTrust, CFCA, CNNIC, WoSign, StartCom, SHECA, GDCA, vTrus, etc.) from
 * AndroidCAStore for both OkHttp/JVM TLS and Go/Xray-core via SSL_CERT_FILE.
 */
object AlanCaSanitizer {

    private val DISTRUSTED_CA_PATTERNS = listOf(
        "BEIJING",
        "BJCA",
        "GUANG DONG",
        "GUANGDONG",
        "GDCA",
        "ITRUSCHINA",
        "ITRUS",
        "TRUSTASIA",
        "UNITRUST",
        "SHECA",
        "CFCA",
        "CNNIC",
        "WOSIGN",
        "WO SIGN",
        "STARTCOM",
        "VTRUS",
        "CHINA FINANCIAL",
        "CHINA INTERNET NETWORK",
        "SHANGHAI CA",
        "SHANGHAI ELECTRONIC",
        "NETCA",
        "SZCA",
        "HNCA",
        "HBCA",
        "ZJCA",
        "JSCA",
        "FJCA",
        "SCCA",
        "CQCA",
        "TJCA",
        "SDCA",
        "AHCA",
        "LNCA",
        "XJCA",
        "HONGKONG POST",
        "ZHONG GUO",
        "ZHONGGUO"
    )

    private val initLock = Any()

    @Volatile
    var auditSummary: CaAuditSummary = CaAuditSummary()
        private set

    @Volatile
    private var cleanTrustManager: X509TrustManager? = null

    @Volatile
    private var cleanSocketFactory: SSLSocketFactory? = null

    private fun isDistrustedCertificate(cert: X509Certificate): Boolean {
        val subj = cert.subjectX500Principal?.name.orEmpty().uppercase(Locale.US)
        val iss = cert.issuerX500Principal?.name.orEmpty().uppercase(Locale.US)
        val combined = "$subj | $iss"
        return DISTRUSTED_CA_PATTERNS.any { pat -> combined.contains(pat) }
    }

    private fun extractShortCn(cert: X509Certificate): String {
        val dn = cert.subjectX500Principal?.name.orEmpty()
        val cnMatch = Regex("""(?:^|,\s*)(?:CN|O)=([^,]+)""", RegexOption.IGNORE_CASE).find(dn)
        return cnMatch?.groupValues?.getOrNull(1)?.trim()?.take(48) ?: dn.take(48)
    }

    fun initAndExportCleanCaBundle(
        context: Context,
        logger: ((String) -> Unit)? = null
    ): CaAuditSummary {
        synchronized(initLock) {
            if (auditSummary.initialized && cleanTrustManager != null) {
                return auditSummary
            }
            return try {
                val androidStore = KeyStore.getInstance("AndroidCAStore")
                androidStore.load(null)

                val cleanStore = KeyStore.getInstance(KeyStore.getDefaultType())
                cleanStore.load(null, null)

                val pemBuilder = StringBuilder(262144)
                val blockedNames = ArrayList<String>()
                var totalCount = 0
                var keptCount = 0

                val aliases = androidStore.aliases()
                while (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    val cert = androidStore.getCertificate(alias) as? X509Certificate ?: continue
                    totalCount++
                    if (isDistrustedCertificate(cert)) {
                        blockedNames.add(extractShortCn(cert))
                        continue
                    }
                    cleanStore.setCertificateEntry("clean_$keptCount", cert)
                    val b64 = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
                    pemBuilder.append("-----BEGIN CERTIFICATE-----\n")
                    var i = 0
                    while (i < b64.length) {
                        val end = minOf(i + 64, b64.length)
                        pemBuilder.append(b64, i, end).append('\n')
                        i = end
                    }
                    pemBuilder.append("-----END CERTIFICATE-----\n")
                    keptCount++
                }

                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(cleanStore)
                val x509Tm = tmf.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
                if (x509Tm != null) {
                    val sslCtx = SSLContext.getInstance("TLS")
                    sslCtx.init(null, arrayOf(x509Tm), SecureRandom())
                    cleanTrustManager = x509Tm
                    cleanSocketFactory = sslCtx.socketFactory
                }

                val pemFile = File(context.applicationContext.filesDir, "sanitized_cacerts.pem")
                pemFile.writeText(pemBuilder.toString(), Charsets.UTF_8)

                // Direct Go crypto/x509 (libv2ray / Xray-core) to load ONLY our sanitized root CA bundle!
                try {
                    Os.setenv("SSL_CERT_FILE", pemFile.absolutePath, true)
                    Os.setenv("SSL_CERT_DIR", "", true)
                } catch (_: Throwable) {
                }

                val summary = CaAuditSummary(
                    initialized = true,
                    totalSystemCas = totalCount,
                    trustedCleanCas = keptCount,
                    blockedCasCount = blockedNames.size,
                    blockedCaNames = blockedNames.distinct(),
                    pemBundlePath = pemFile.absolutePath
                )
                auditSummary = summary
                logger?.invoke(
                    "[CA-Shield] Sanitized AndroidCAStore: kept=$keptCount/$totalCount | blocked=${blockedNames.size}" +
                        if (blockedNames.isNotEmpty()) " (${blockedNames.distinct().take(6).joinToString(", ")})" else ""
                )
                summary
            } catch (t: Throwable) {
                logger?.invoke("[CA-Shield] Init fallback: ${t.javaClass.simpleName}: ${t.message}")
                auditSummary
            }
        }
    }

    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val sf = cleanSocketFactory
        val tm = cleanTrustManager
        if (sf != null && tm != null) {
            builder.sslSocketFactory(sf, tm)
        }
        return builder
    }
}
