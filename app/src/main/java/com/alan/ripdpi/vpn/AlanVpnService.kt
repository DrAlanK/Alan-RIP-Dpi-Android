package com.alan.ripdpi.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.alan.ripdpi.MainActivity
import com.alan.ripdpi.core.AlanAutoPilotEngine
import com.alan.ripdpi.model.APP_NAME
import hev.htproxy.TProxyService
import java.io.File
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AlanVpnService : VpnService() {

    companion object {
        @Volatile var splitTunnelMode: String = "ALL"
        @Volatile var splitTunnelPackages: Set<String> = emptySet()
        const val ACTION_START_TUN = "com.alan.ripdpi.action.START_TUN"
        const val ACTION_STOP_TUN = "com.alan.ripdpi.action.STOP_TUN"
        const val ACTION_DISCONNECT_FROM_NOTIF = "com.alan.ripdpi.action.DISCONNECT_FROM_NOTIF"

        const val EXTRA_SOCKS_PORT = "extra_socks_port"
        const val EXTRA_TUN_MTU = "extra_tun_mtu"
        const val EXTRA_TUN_DNS = "extra_tun_dns"
        const val EXTRA_PROFILE_TITLE = "extra_profile_title"
        const val EXTRA_VECTOR_LABEL = "extra_vector_label"

        private const val CHANNEL_ID = "alan_rip_dpi_vpn_channel"
        private const val NOTIFICATION_ID = 4101

        @Volatile
        var activeInstance: AlanVpnService? = null
            private set

        @Volatile
        var tunEstablishedFlag: Boolean = false
            private set

        @Volatile
        var tunEstablishError: String? = null
            private set

        fun resetTunHandshakeState() {
            tunEstablishedFlag = false
            tunEstablishError = null
        }

        fun protectRawSocket(sock: Socket): Boolean {
            val svc = activeInstance ?: return true
            return try {
                svc.protect(sock)
            } catch (_: Throwable) {
                false
            }
        }

        fun protectRawFd(fd: Int): Boolean {
            val svc = activeInstance ?: return true
            return try {
                svc.protect(fd)
            } catch (_: Throwable) {
                false
            }
        }

        fun startTunService(
            context: Context,
            socksPort: Int,
            tunMtu: Int,
            tunDns: String,
            profileTitle: String,
            vectorLabel: String
        ) {
            try {
                val intent = Intent(context, AlanVpnService::class.java).apply {
                    action = ACTION_START_TUN
                    putExtra(EXTRA_SOCKS_PORT, socksPort)
                    putExtra(EXTRA_TUN_MTU, tunMtu)
                    putExtra(EXTRA_TUN_DNS, tunDns)
                    putExtra(EXTRA_PROFILE_TITLE, profileTitle)
                    putExtra(EXTRA_VECTOR_LABEL, vectorLabel)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                tunEstablishError = "${t.javaClass.simpleName}: ${t.message}"
            }
        }

        fun stopTunService(context: Context) {
            tunEstablishedFlag = false
            try {
                activeInstance?.stopImmediatelyFromEngine()
            } catch (_: Throwable) {
            }
            try {
                context.stopService(Intent(context, AlanVpnService::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    private val tunLock = ReentrantLock()
    private val tproxyStarted = AtomicBoolean(false)
    private var tunInterface: ParcelFileDescriptor? = null
    private val controlExecutor = Executors.newSingleThreadExecutor { r ->
        Thread({
            try {
                r.run()
            } catch (_: Throwable) {
            }
        }, "AlanTunWorker").apply { isDaemon = true }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        TProxyService.ensureLoaded()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_STOP_TUN
        when (action) {
            ACTION_DISCONNECT_FROM_NOTIF -> {
                AlanAutoPilotEngine.getInstance(applicationContext).disconnectTunnel()
                stopImmediatelyFromEngine()
                return START_NOT_STICKY
            }
            ACTION_STOP_TUN -> {
                stopImmediatelyFromEngine()
                return START_NOT_STICKY
            }
            ACTION_START_TUN -> {
                val socksPort = intent?.getIntExtra(EXTRA_SOCKS_PORT, 10808) ?: 10808
                val mtu = (intent?.getIntExtra(EXTRA_TUN_MTU, 1400) ?: 1400).coerceIn(1280, 1500)
                val dns = intent?.getStringExtra(EXTRA_TUN_DNS)?.ifBlank { "1.1.1.1" } ?: "1.1.1.1"
                val title = intent?.getStringExtra(EXTRA_PROFILE_TITLE).orEmpty().ifBlank { APP_NAME }
                val vecLabel = intent?.getStringExtra(EXTRA_VECTOR_LABEL).orEmpty()

                promoteToForegroundSafe(title, vecLabel)

                controlExecutor.execute {
                    val err = establishTunStack(socksPort = socksPort, mtu = mtu, dns = dns)
                    if (err != null) {
                        tunEstablishError = err
                        AlanAutoPilotEngine.getInstance(applicationContext).onTunFatalFailure(err)
                        stopImmediatelyFromEngine()
                    } else {
                        tunEstablishedFlag = true
                    }
                }
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    fun stopImmediatelyFromEngine() {
        tunEstablishedFlag = false
        try {
            teardownTunStack()
        } catch (_: Throwable) {
        }
        try {
            stopSelf()
        } catch (_: Throwable) {
        }
    }

    private fun promoteToForegroundSafe(title: String, vecLabel: String) {
        try {
            val notif = buildForegroundNotification(title, vecLabel)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onRevoke() {
        AlanAutoPilotEngine.getInstance(applicationContext).disconnectTunnel()
        stopImmediatelyFromEngine()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardownTunStack()
        try {
            controlExecutor.shutdownNow()
        } catch (_: Throwable) {
        }
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    fun updateNotificationSummary(profileTitle: String, vectorLabel: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.notify(NOTIFICATION_ID, buildForegroundNotification(profileTitle, vectorLabel))
        } catch (_: Throwable) {
        }
    }

    private fun establishTunStack(socksPort: Int, mtu: Int, dns: String): String? {
        return tunLock.withLock {
            teardownTunStackUnlocked()

            val jniErr = TProxyService.ensureLoaded()
            if (jniErr != null) {
                return@withLock "خطای بارگذاری درایور بومی TUN ($jniErr)"
            }

            val consentCheck = try {
                prepare(this)
            } catch (_: Throwable) {
                null
            }
            if (consentCheck != null) {
                return@withLock "مجوز VPN فعال نیست؛ اگر فیلترشکن دیگری روشن است آن را خاموش کنید."
            }

            val candidateSubnets = listOf(
                "10.26.0.1" to 30,
                "172.19.0.1" to 30,
                "198.18.0.1" to 15
            )
            var lastEx = ""

            for ((tunIp, prefix) in candidateSubnets) {
                try {
                    val builder = Builder()
                        .setSession("AlanTUN")
                        .setMtu(mtu)
                        .addAddress(tunIp, prefix)
                        .addRoute("0.0.0.0", 0)

                    val cleanDns = dns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (cleanDns.isEmpty()) {
                        builder.addDnsServer("1.1.1.1")
                    } else {
                        for (d in cleanDns) {
                            try {
                                builder.addDnsServer(d)
                            } catch (_: Throwable) {
                            }
                        }
                    }

                    try {
                        builder.addDisallowedApplication(packageName)
                    } catch (_: Throwable) {
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        builder.setMetered(false)
                    }

                    val pfd = builder.establish()
                    if (pfd == null) {
                        lastEx = "VpnService.establish() == null ($tunIp/$prefix)"
                        continue
                    }
                    tunInterface = pfd

                    val yamlFile = writeHevTunnelConfig(socksPort = socksPort, mtu = mtu, tunIp = tunIp)
                    TProxyService.TProxyStartService(yamlFile.absolutePath, pfd.fd)
                    tproxyStarted.set(true)
                    return@withLock null
                } catch (t: Throwable) {
                    val root = t.cause ?: t
                    lastEx = "${root.javaClass.simpleName}: ${root.message ?: "error"}"
                    teardownTunStackUnlocked()
                    break
                }
            }

            "خطای راه‌اندازی TUN ($lastEx)"
        }
    }

    private fun writeHevTunnelConfig(socksPort: Int, mtu: Int, tunIp: String): File {
        val yaml = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $mtu")
            appendLine("  ipv4: $tunIp")
            appendLine("")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            appendLine("")
            appendLine("misc:")
            appendLine("  task-stack-size: 81920")
            appendLine("  tcp-buffer-size: 65536")
            appendLine("  connect-timeout: 5000")
            appendLine("  read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }
        val file = File(filesDir, "hev-socks5-tunnel.yaml")
        file.writeText(yaml, Charsets.UTF_8)
        return file
    }

    private fun teardownTunStack() {
        tunLock.withLock {
            teardownTunStackUnlocked()
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {
        }
    }

    private fun teardownTunStackUnlocked() {
        if (tproxyStarted.compareAndSet(true, false)) {
            try {
                TProxyService.TProxyStopService()
            } catch (_: Throwable) {
            }
            try {
                Thread.sleep(70L)
            } catch (_: Throwable) {
            }
        }
        try {
            tunInterface?.close()
        } catch (_: Throwable) {
        }
        tunInterface = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "$APP_NAME VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "وضعیت اتصال امن"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(profileTitle: String, vectorLabel: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlanVpnService::class.java).apply {
            action = ACTION_DISCONNECT_FROM_NOTIF
        }
        val stopPending = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (vectorLabel.isNotBlank()) {
            "$profileTitle  •  $vectorLabel"
        } else {
            profileTitle
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(APP_NAME)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع اتصال", stopPending)
            .build()
    }
}
