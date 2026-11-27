package com.alan.ripdpi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onRunMemoryAuditNow: () -> Unit,
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

    val cpuHealthy = if (isConnected) health.cpuUsagePercent <= 10f else health.cpuUsagePercent <= 3f
    val heapRatio = (health.javaHeapUsedMb / health.javaHeapMaxMb.coerceAtLeast(1f))
    val heapHealthy = heapRatio < 0.65f
    val rssHealthy = health.totalRssMb <= 210f
    val fdHealthy = health.openFds in 0..240
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Outlined.Memory, null, tint = p.accentGold, modifier = Modifier.size(20.dp))
                            Column {
                                Text("مدیریت سخت‌افزار و نگهبان حافظه", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        HwMetricCard(
                            title = "CPU پردازنده",
                            value = String.format(Locale.US, "%.1f%%", health.cpuUsagePercent),
                            sub = if (isConnected) "مجاز: <10%" else "بیکار: <3%",
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
                            value = String.format(Locale.US, "%.1f MB", health.totalRssMb),
                            sub = String.format(Locale.US, "Native: %.1f • اوج: %.0f", health.nativeHeapMb, health.peakRssMb),
                            healthy = rssHealthy,
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

                    // 20-Minute Automatic Anti-Leak Self-Healing Guard Card
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanNeuInset(cornerRadius = 14.dp, accented = true)
                            .padding(11.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Shield, null, tint = p.emerald, modifier = Modifier.size(16.dp))
                                Text("نگهبان خودکار ضد نشتی (${health.auditIntervalMinutes} دقیقه‌ای)", color = p.emerald, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }
                            LtrContent {
                                Text(
                                    text = health.leakGuardStatus,
                                    color = if (health.leakGuardStatus == "HEALTHY" || health.leakGuardStatus == "HEALED") p.emerald else p.accentGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Text(
                            text = "آخرین چک: ${health.lastAuditTime}  •  چک بعدی: ~${health.nextAuditMinutesLeft} دقیقه  •  خودترمیمی: ${health.totalAutoHeals} بار",
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = health.lastHealSummary,
                            color = p.textSecondary,
                            fontSize = 9.8.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Reference Thresholds
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanGlassCard(cornerRadius = 14.dp)
                            .padding(10.dp)
                    ) {
                        Text("مکانیزم محافظت خودکار بدون کرش:", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp)
                        Text("• هر ${health.auditIntervalMinutes} دقیقه (یا هنگام فشار بالا) کش‌ها، بافرپول و لاگ‌های اضافه هرس می‌شوند", color = p.textSecondary, fontSize = 9.6.sp)
                        Text("• با عبور Heap از 72% یا FDs از 260، سوکت‌های مرده بدون قطع VPN پاکسازی می‌شوند", color = p.textSecondary, fontSize = 9.6.sp)
                        Text("• معیار سلامت: Heap زیر 55MB، FDs زیر 220 و Threads در حالت قطع زیر 40", color = p.textSecondary, fontSize = 9.6.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.25f)
                                .height(38.dp)
                                .alanGlassCard(highlighted = true, cornerRadius = 11.dp)
                                .clickable { onRunMemoryAuditNow() }
                        ) {
                            Text("پاکسازی و GC فوری", color = p.emerald, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(0.95f)
                                .height(38.dp)
                                .alanGlassCard(cornerRadius = 11.dp)
                                .clickable { onResetPeaks() }
                        ) {
                            Text("ریست اوج‌ها", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(0.8f)
                                .height(38.dp)
                                .alanNeuInset(cornerRadius = 11.dp)
                                .clickable { onRefresh() }
                        ) {
                            Text("بروزرسانی", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
