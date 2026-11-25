package com.alan.ripdpi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alan.ripdpi.core.HardwareHealthSnapshot
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun AlanHardwareDiagnosticsDialog(
    health: HardwareHealthSnapshot,
    isConnected: Boolean,
    tunnelMode: String,
    onRefresh: () -> Unit,
    onForceGc: () -> Unit,
    onResetPeaks: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalAlanPalette.current

    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            delay(3000L)
        }
    }

    val cpuHealthy = if (isConnected) health.cpuUsagePercent <= 10f else health.cpuUsagePercent <= 2f
    val heapHealthy = health.javaHeapUsedMb <= 65f
    val pssHealthy = health.totalPssMb <= 185f
    val fdHealthy = health.openFds in 0..220
    val threadHealthy = health.activeThreads <= 75

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = p.surfaceBg,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Outlined.Memory, null, tint = p.accentGold, modifier = Modifier.size(20.dp))
                            Column {
                                Text("پنل مخفی سخت‌افزار و نشتی حافظه", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                LtrContent {
                                    Text(
                                        text = "Mode: $tunnelMode • ${if (isConnected) "ACTIVE" else "IDLE"} • Auto 3s",
                                        color = p.textMuted,
                                        fontSize = 9.5.sp
                                    )
                                }
                            }
                        }
                        AlanNeuIconButton(Icons.Outlined.Close, p.textSecondary, 32.dp, onClick = onDismiss)
                    }

                    // Live Hardware Metrics Grid
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        HwMetricCard(
                            title = "CPU پردازنده",
                            value = String.format(Locale.US, "%.1f%%", health.cpuUsagePercent),
                            sub = if (isConnected) "مجاز: <8%" else "بیکار: <1%",
                            healthy = cpuHealthy,
                            modifier = Modifier.weight(1f)
                        )
                        HwMetricCard(
                            title = "Java Heap",
                            value = String.format(Locale.US, "%.1f MB", health.javaHeapUsedMb),
                            sub = String.format(Locale.US, "اوج: %.1f / %.0fMB", health.peakHeapMb, health.javaHeapMaxMb),
                            healthy = heapHealthy,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        HwMetricCard(
                            title = "Total RSS (رم کل)",
                            value = String.format(Locale.US, "%.1f MB", health.totalPssMb),
                            sub = String.format(Locale.US, "Native: %.1f • اوج: %.0f", health.nativeHeapMb, health.peakPssMb),
                            healthy = pssHealthy,
                            modifier = Modifier.weight(1f)
                        )
                        HwMetricCard(
                            title = "File Descriptors",
                            value = if (health.openFds >= 0) "${health.openFds}" else "N/A",
                            sub = "اوج: ${health.peakFds} • نشست: ${health.activeBridgeSessions}",
                            healthy = fdHealthy,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        HwMetricCard(
                            title = "تردهای فعال",
                            value = "${health.activeThreads}",
                            sub = "اوج: ${health.peakThreads} (مجاز <75)",
                            healthy = threadHealthy,
                            modifier = Modifier.weight(1f)
                        )
                        HwMetricCard(
                            title = "باتری و دما",
                            value = if (health.batteryLevelPct >= 0) "${health.batteryLevelPct}% • ${String.format(Locale.US, "%.1f°C", health.batteryTempC)}" else "—",
                            sub = String.format(Locale.US, "جریان: %.0f mA %s", health.batteryCurrentMa, if (health.isCharging) "(شارژ)" else ""),
                            healthy = health.batteryTempC < 41f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Reference Thresholds Guide for Proving Zero Leaks
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanGlassCard(cornerRadius = 14.dp)
                            .padding(10.dp)
                    ) {
                        Text("معیارهای اثبات نبودن نشتی (بعد از ۱۰ دقیقه استفاده + GC):", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp)
                        Text("• CPU در حالت بیکار زیر 1% و حین وب‌گردی 1% تا 6%", color = p.textSecondary, fontSize = 9.8.sp)
                        Text("• Java Heap پس از Force GC زیر 55MB ثابت بماند (رشد خطی نکند)", color = p.textSecondary, fontSize = 9.8.sp)
                        Text("• Open FDs در حالت قطع زیر 130 و حین کار زیر 220 بماند", color = p.textSecondary, fontSize = 9.8.sp)
                        Text("• Threads در حالت قطع به زیر 40 برگردد و بالای 90 نرود", color = p.textSecondary, fontSize = 9.8.sp)
                    }

                    // Actions Row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .alanGlassCard(cornerRadius = 11.dp)
                                .clickable { onForceGc() }
                        ) {
                            Text("Force GC", color = p.emerald, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .alanGlassCard(cornerRadius = 11.dp)
                                .clickable { onResetPeaks() }
                        ) {
                            Text("ریست اوج‌ها", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(0.9f)
                                .height(38.dp)
                                .alanNeuInset(cornerRadius = 11.dp)
                                .clickable { onRefresh() }
                        ) {
                            Text("بروزرسانی", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HwMetricCard(
    title: String,
    value: String,
    sub: String,
    healthy: Boolean,
    modifier: Modifier = Modifier
) {
    val p = LocalAlanPalette.current
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .alanNeuInset(cornerRadius = 13.dp, accented = !healthy)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(title, color = p.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
        LtrContent {
            Text(
                text = value,
                color = if (healthy) p.emerald else p.danger,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp,
                maxLines = 1
            )
        }
        LtrContent {
            Text(sub, color = p.textSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
