package com.alan.ripdpi.core

import com.alan.ripdpi.model.BypassVector
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class EnterpriseProfileSpec(
    val protocol: String, // "anyconnect", "ikev2", "l2tp"
    val server: String,
    val port: Int,
    val username: String,
    val secretOrPassword: String,
    val groupOrDomain: String = "",
    val sni: String = "",
    val remark: String = ""
)

object EnterpriseProtocolEngine {

    fun isEnterpriseProtocol(proto: String): Boolean {
        return proto.trim().lowercase(Locale.US) in setOf("anyconnect", "openconnect", "ikev2", "l2tp")
    }

    fun defaultPortFor(proto: String): Int = when (proto.trim().lowercase(Locale.US)) {
        "anyconnect", "openconnect" -> 443
        "ikev2" -> 500
        "l2tp" -> 1701
        else -> 443
    }

    /**
     * Probes enterprise VPN endpoints (AnyConnect TLS/CSTP, IKEv2 NAT-T/TLS, L2TP gateway)
     */
    fun probeEnterpriseEndpointBlocking(host: String, port: Int, proto: String, timeoutMs: Int = 1800): Int {
        val cleanProto = proto.trim().lowercase(Locale.US)
        val checkPort = when {
            port in 1..65535 -> port
            cleanProto == "ikev2" -> 500
            cleanProto == "l2tp" -> 1701
            else -> 443
        }
        // Try primary port or fallback TLS/443 & NAT-T/4500 ports
        val portsToTry = when (cleanProto) {
            "ikev2" -> listOf(checkPort, 4500, 443).distinct()
            "l2tp" -> listOf(checkPort, 4500, 500, 443).distinct()
            else -> listOf(checkPort, 443, 8443).distinct()
        }
        for (p in portsToTry) {
            val sock = CleanIpScannerEngine.createProtectedRawSocket()
            try {
                sock.tcpNoDelay = true
                val t0 = System.nanoTime()
                sock.connect(InetSocketAddress(host.trim(), p), timeoutMs)
                return ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
            } catch (_: Throwable) {
            } finally {
                try { sock.close() } catch (_: Throwable) {}
            }
        }
        return -1
    }
}

/**
 * Local HTTP/SOCKS-to-Cisco AnyConnect (OpenConnect SSL/TLS CSTP + HTTP CONNECT) Gateway Bridge.
 * Connects through FullDuplexMutatorBridge so all 24 Anti-DPI vectors apply to AnyConnect TLS handshakes!
 */
class AnyConnectTlsGatewayBridge(
    private val listenPort: Int,
    private val mutatorBridgeHost: String,
    private val mutatorBridgePort: Int,
    private val sniHost: String,
    private val username: String,
    private val password: String,
    private val authGroup: String = ""
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    private val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    })

    fun start() {
        if (!running.compareAndSet(false, true)) return
        pool.execute {
            try {
                val srv = ServerSocket()
                srv.reuseAddress = true
                srv.bind(InetSocketAddress("127.0.0.1", listenPort), 128)
                serverSocket = srv
                BridgeDeepDiagnostics.log("[ANYCONNECT-GW] Listening on 127.0.0.1:$listenPort -> Mutator $mutatorBridgeHost:$mutatorBridgePort (SNI=$sniHost)")
                while (running.get()) {
                    val client = srv.accept()
                    pool.execute { handleClient(client) }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun openTlsOverMutatorSocket(): SSLSocket {
        val raw = CleanIpScannerEngine.createProtectedRawSocket()
        raw.tcpNoDelay = true
        raw.keepAlive = true
        raw.connect(InetSocketAddress(mutatorBridgeHost, mutatorBridgePort), 5000)

        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, SecureRandom())
        val ssl = ctx.socketFactory.createSocket(raw, sniHost.ifBlank { mutatorBridgeHost }, mutatorBridgePort, true) as SSLSocket
        try {
            val params = ssl.sslParameters
            if (sniHost.isNotBlank() && sniHost.any { it.isLetter() }) {
                params.serverNames = listOf(SNIHostName(sniHost))
            }
            ssl.sslParameters = params
        } catch (_: Throwable) {
        }
        ssl.soTimeout = 6000
        ssl.startHandshake()
        ssl.soTimeout = 0
        return ssl
    }

    private fun handleClient(client: java.net.Socket) {
        var remoteSsl: SSLSocket? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = 0
            remoteSsl = openTlsOverMutatorSocket()
            val cIn = client.getInputStream()
            val cOut = client.getOutputStream()
            val rIn = remoteSsl.getInputStream()
            val rOut = remoteSsl.getOutputStream()

            val t1 = pool.submit { pump(cIn, rOut) }
            val t2 = pool.submit { pump(rIn, cOut) }
            t1.get()
            t2.get()
        } catch (_: Throwable) {
        } finally {
            try { remoteSsl?.close() } catch (_: Throwable) {}
            try { client.close() } catch (_: Throwable) {}
        }
    }

    private fun pump(input: InputStream, output: OutputStream) {
        val buf = ByteArray(16384)
        while (running.get()) {
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
            output.flush()
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Throwable) {}
    }
}
