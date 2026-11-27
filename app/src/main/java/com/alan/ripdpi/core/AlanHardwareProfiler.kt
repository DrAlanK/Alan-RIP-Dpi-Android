package com.alan.ripdpi.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HardwareHealthSnapshot(
    val javaHeapUsedMb: Float = 0f,
    val javaHeapMaxMb: Float = 128f,
    val nativeHeapMb: Float = 0f,
    val totalRssMb: Float = 0f,
    val cpuUsagePercent: Float = 0f,
    val activeThreads: Int = 0,
    val openFds: Int = -1,
    val activeBridgeSessions: Int = 0,
    val batteryLevelPct: Int = -1,
    val batteryTempC: Float = 0f,
    val batteryCurrentMa: Float = 0f,
    val isCharging: Boolean = false,
    val peakHeapMb: Float = 0f,
    val peakRssMb: Float = 0f,
    val peakThreads: Int = 0,
    val peakFds: Int = 0,
    val leakGuardStatus: String = "HEALTHY",
    val auditIntervalMinutes: Int = 20,
    val lastAuditTime: String = "—",
    val nextAuditMinutesLeft: Int = 20,
    val totalAutoHeals: Int = 0,
    val lastHealSummary: String = "سیستم پایدار است؛ نشتی شناسایی نشده"
)

object AlanHardwareProfiler {
    private var prevCpuTicks: Long = -1L
    private var prevWallMs: Long = -1L
    @Volatile private var lastCpuPct: Float = 0f

    @Volatile private var peakHeapMb: Float = 0f
    @Volatile private var peakRssMb: Float = 0f
    @Volatile private var peakThreads: Int = 0
    @Volatile private var peakFds: Int = 0

    @Volatile var leakGuardStatus: String = "HEALTHY"
    @Volatile var auditIntervalMinutes: Int = 20
    @Volatile var lastAuditElapsedMs: Long = SystemClock.elapsedRealtime()
    @Volatile var lastAuditClockLabel: String = "—"
    @Volatile var totalAutoHeals: Int = 0
    @Volatile var lastHealSummary: String = "سیستم پایدار است؛ نشتی شناسایی نشده"

    fun resetPeaks() {
        peakHeapMb = 0f
        peakRssMb = 0f
        peakThreads = 0
        peakFds = 0
    }

    fun forceGc() {
        try {
            Runtime.getRuntime().gc()
        } catch (_: Throwable) {
        }
    }

    fun markAuditCompleted(healed: Boolean, summary: String, status: String) {
        lastAuditElapsedMs = SystemClock.elapsedRealtime()
        lastAuditClockLabel = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        if (healed) totalAutoHeals++
        lastHealSummary = summary
        leakGuardStatus = status
    }

    private fun readProcessCpuPercent(): Float {
        return try {
            val statText = File("/proc/self/stat").readText(Charsets.US_ASCII)
            val rparen = statText.lastIndexOf(')')
            if (rparen <= 0) return lastCpuPct
            val fields = statText.substring(rparen + 2).trim().split(Regex("\\s+"))
            if (fields.size < 14) return lastCpuPct
            val utime = fields[11].toLongOrNull() ?: 0L
            val stime = fields[12].toLongOrNull() ?: 0L
            val totalTicks = utime + stime
            val nowMs = SystemClock.elapsedRealtime()

            val prevT = prevCpuTicks
            val prevW = prevWallMs
            if (prevT < 0L || prevW < 0L) {
                prevCpuTicks = totalTicks
                prevWallMs = nowMs
                return lastCpuPct
            }
            val deltaWallMs = nowMs - prevW
            if (deltaWallMs < 900L) return lastCpuPct

            prevCpuTicks = totalTicks
            prevWallMs = nowMs

            val deltaTicks = (totalTicks - prevT).coerceAtLeast(0L)
            val deltaWallSec = deltaWallMs / 1000.0
            val cpuSec = deltaTicks / 100.0
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val pct = ((cpuSec / (deltaWallSec * cores)) * 100.0).toFloat().coerceIn(0f, 100f)
            lastCpuPct = pct
            pct
        } catch (_: Throwable) {
            lastCpuPct
        }
    }

    private fun readLightweightRssMb(): Float {
        return try {
            val parts = File("/proc/self/statm").readText(Charsets.US_ASCII).trim().split(Regex("\\s+"))
            val rssPages = parts.getOrNull(1)?.toLongOrNull() ?: return 0f
            (rssPages * 4096f) / (1024f * 1024f)
        } catch (_: Throwable) {
            0f
        }
    }

    private fun countOpenFileDescriptors(): Int {
        return try {
            File("/proc/self/fd").list()?.size ?: -1
        } catch (_: Throwable) {
            -1
        }
    }

    fun captureSnapshot(context: Context, activeBridgeSessions: Int): HardwareHealthSnapshot {
        val rt = Runtime.getRuntime()
        val javaUsedMb = ((rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)).coerceAtLeast(0f)
        val javaMaxMb = (rt.maxMemory() / (1024f * 1024f)).coerceAtLeast(1f)
        val nativeMb = (Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)).coerceAtLeast(0f)
        val rssMb = readLightweightRssMb()

        val threads = Thread.activeCount()
        val fds = countOpenFileDescriptors()
        val cpuPct = readProcessCpuPercent()

        if (javaUsedMb > peakHeapMb) peakHeapMb = javaUsedMb
        if (rssMb > peakRssMb) peakRssMb = rssMb
        if (threads > peakThreads) peakThreads = threads
        if (fds > peakFds) peakFds = fds

        var battPct = -1
        var battTempC = 0f
        var charging = false
        var currentMa = 0f

        try {
            val intent = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                if (lvl >= 0) battPct = (lvl * 100) / scale
                battTempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
            val bm = context.applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                val ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                if (ua != Int.MIN_VALUE) currentMa = kotlin.math.abs(ua) / 1000f
            }
        } catch (_: Throwable) {
        }

        val elapsedSinceAuditMin = ((SystemClock.elapsedRealtime() - lastAuditElapsedMs) / 60_000L).toInt().coerceAtLeast(0)
        val minsLeft = (auditIntervalMinutes - elapsedSinceAuditMin).coerceIn(0, auditIntervalMinutes)

        return HardwareHealthSnapshot(
            javaHeapUsedMb = javaUsedMb,
            javaHeapMaxMb = javaMaxMb,
            nativeHeapMb = nativeMb,
            totalRssMb = rssMb,
            cpuUsagePercent = cpuPct,
            activeThreads = threads,
            openFds = fds,
            activeBridgeSessions = activeBridgeSessions,
            batteryLevelPct = battPct,
            batteryTempC = battTempC,
            batteryCurrentMa = currentMa,
            isCharging = charging,
            peakHeapMb = peakHeapMb,
            peakRssMb = peakRssMb,
            peakThreads = peakThreads,
            peakFds = peakFds,
            leakGuardStatus = leakGuardStatus,
            auditIntervalMinutes = auditIntervalMinutes,
            lastAuditTime = lastAuditClockLabel,
            nextAuditMinutesLeft = minsLeft,
            totalAutoHeals = totalAutoHeals,
            lastHealSummary = lastHealSummary
        )
    }
}
