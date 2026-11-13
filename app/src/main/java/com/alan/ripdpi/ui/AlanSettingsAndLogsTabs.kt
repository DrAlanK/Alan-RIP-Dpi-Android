package com.alan.ripdpi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun GeneralSettingsScreen(
    settings: EngineSettings,
    vectors: List<VectorSpec>,
    isFindingBestMethod: Boolean,
    autoMethodStatus: String,
    onAutoDetectBestMethod: () -> Unit,
    onReplaceSettings: (EngineSettings) -> Unit,
    onPatchSettings: (Map<String, Any>) -> Unit
) {
    val p = LocalAlanPalette.current
    val isFa = LocalAlanLang.current == AlanLang.FA

    var tunMtuText by remember(settings.tunMtu) { mutableStateOf(settings.tunMtu.toString()) }
    var tunDnsText by remember(settings.tunDns) { mutableStateOf(settings.tunDns) }
    var socksPortText by remember(settings.socksPort) { mutableStateOf(settings.socksPort.toString()) }
    var httpPortText by remember(settings.httpPort) { mutableStateOf(settings.httpPort.toString()) }
    var subnetsUrlText by remember(settings.subnetsRepoUrl) { mutableStateOf(settings.subnetsRepoUrl) }
    var savedMsg by remember { mutableStateOf("") }

    val methodOptions = remember(vectors) {
        vectors.map { spec ->
            SelectOptionItem(
                value = spec.id,
                label = spec.id,
                subtitle = spec.name.substringAfter("—").trim()
            )
        }
    }

    val modeOptions = listOf(
        SelectOptionItem(
            "auto_hybrid",
            tr("ترکیبی هوشمند (پیشنهادی)", "Smart Hybrid (Recommended)"),
            tr("استفاده ترکیبی از تمام متدها", "All proprietary & fragment vectors")
        ),
        SelectOptionItem(
            "proprietary_only",
            tr("فقط متدهای ساختاری پکت", "Proprietary Desync Only"),
            tr("مخصوص عبور از فیلترینگ شدید SNI", "Deep SNI stream desynchronization")
        ),
        SelectOptionItem(
            "fragment_only",
            tr("فقط متدهای فرگمنت", "TLS Fragment Only"),
            tr("مناسب سرورهای کلادفلر و CDN", "Optimized for Cloudflare & CDN edges")
        )
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = tr("تنظیمات عمومی", "General Settings"),
                color = p.textPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(34.dp)
                    .alanGlassCard(cornerRadius = 10.dp)
                    .clickable {
                        onReplaceSettings(EngineSettings(darkTheme = settings.darkTheme))
                        savedMsg = if (isFa) "تنظیمات به حالت پیش‌فرض برگشت." else "Settings restored to defaults."
                    }
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = tr("پیش‌فرض", "Reset Defaults"),
                    color = p.accentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }

        if (savedMsg.isNotBlank()) {
            Text(savedMsg, color = p.emerald, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }

        SimpleSectionCard(tr("متد عبور از فیلتر", "Anti-DPI Bypass Method")) {
            AlanCustomSelectBox(
                label = tr("متد دستی پیش‌فرض", "Manual Vector"),
                selectedValue = settings.manualVectorId,
                options = methodOptions,
                onSelect = { onPatchSettings(mapOf("manualVectorId" to it)) }
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isFindingBestMethod) p.danger else p.accentGold)
                    .clickable { onAutoDetectBestMethod() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = if (isFindingBestMethod) Color.White else p.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (isFindingBestMethod) {
                            tr("توقف جستجوی متد", "Stop Method Detection")
                        } else {
                            tr("پیدا کردن خودکار بهترین متد", "Auto-Detect Best Method")
                        },
                        color = if (isFindingBestMethod) Color.White else p.onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp
                    )
                }
            }

            if (autoMethodStatus.isNotBlank()) {
                Text(
                    text = autoMethodStatus,
                    color = if (autoMethodStatus.contains("انتخاب") || autoMethodStatus.contains("VEC-", true)) {
                        p.emerald
                    } else {
                        p.textSecondary
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AlanCustomSelectBox(
                label = tr("حالت کاری موتور", "Engine Mode"),
                selectedValue = settings.engineMode,
                options = modeOptions,
                onSelect = { onPatchSettings(mapOf("engineMode" to it)) }
            )
        }

        SimpleSectionCard(tr("ظاهر برنامه", "Appearance")) {
            CleanSwitchItem(
                title = tr("تم تیره نئومورفیک", "Dark Neumorphic Theme"),
                subtitle = tr("با خاموش کردن این گزینه تم روشن فعال می‌شود", "Disable to switch to clean Light Neumorphic surface"),
                checked = settings.darkTheme,
                onChecked = { onPatchSettings(mapOf("darkTheme" to it)) }
            )
        }

        SimpleSectionCard(tr("اتصال هوشمند", "Smart Automation")) {
            CleanSwitchItem(
                title = tr("تزریق خودکار IP تمیز روی کلادفلر", "Auto Clean-IP for Cloudflare"),
                subtitle = tr("جایگزینی خودکار IP تمیز روی سرورهای کلادفلر", "Automatically inject clean edge IPs on CF configs"),
                checked = settings.autoCleanIpForCf,
                onChecked = { onPatchSettings(mapOf("autoCleanIpForCf" to it)) }
            )
            CleanSwitchItem(
                title = tr("انتخاب خودکار متد هنگام اتصال", "Smart Auto-Pilot"),
                subtitle = tr("اسکن و قفل بهترین متد هنگام زدن دکمه اتصال", "Probe and lock fastest working vector on connect"),
                checked = settings.autoPilot,
                onChecked = { onPatchSettings(mapOf("autoPilot" to it)) }
            )
            CleanSwitchItem(
                title = tr("تست پایداری یوتیوب", "YouTube 6s Stability Check"),
                subtitle = tr("اطمینان از قطع نشدن استریم پس از چند ثانیه", "Verify sustained stream throughput during probe"),
                checked = settings.requireYoutube6s,
                onChecked = { onPatchSettings(mapOf("requireYoutube6s" to it)) }
            )
            CleanSwitchItem(
                title = tr("تست سلامت آپلود", "Verify Upload Stream"),
                subtitle = tr("بررسی باز بودن مسیر ارسال داده (Uplink)", "Ensure outbound uplink frames are not throttled"),
                checked = settings.verifyUploadStream,
                onChecked = { onPatchSettings(mapOf("verifyUploadStream" to it)) }
            )
            CleanSwitchItem(
                title = tr("تعمیر خودکار اتصال (Watchdog)", "Self-Healing Watchdog"),
                subtitle = tr("سوییچ خودکار متد در صورت افت یا قطعی تونل", "Automatically recover tunnel if network drops"),
                checked = settings.selfHealingWatchdog,
                onChecked = { onPatchSettings(mapOf("selfHealingWatchdog" to it)) }
            )
        }

        SimpleSectionCard(tr("شبکه و پورت‌ها", "Network & Local Ports")) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField(
                    label = tr("MTU تونل", "TUN MTU"),
                    value = tunMtuText,
                    onValueChange = { tunMtuText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                AlanSheetTextField(
                    label = tr("آدرس DNS", "TUN DNS"),
                    value = tunDnsText,
                    onValueChange = { tunDnsText = it },
                    ltr = true,
                    modifier = Modifier.weight(1.2f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField(
                    label = "SOCKS5 Port",
                    value = socksPortText,
                    onValueChange = { socksPortText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                AlanSheetTextField(
                    label = "HTTP Port",
                    value = httpPortText,
                    onValueChange = { httpPortText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
            }
            AlanSheetTextField(
                label = tr("لینک فایل ساب‌نت گیت‌هاب", "GitHub Subnets URL"),
                value = subnetsUrlText,
                onValueChange = { subnetsUrlText = it },
                ltr = true
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.accentGold)
                    .clickable {
                        onPatchSettings(
                            mapOf(
                                "tunMtu" to (tunMtuText.toIntOrNull()?.coerceIn(1280, 9000) ?: settings.tunMtu),
                                "tunDns" to tunDnsText.trim().ifBlank { "1.1.1.1" },
                                "socksPort" to (socksPortText.toIntOrNull()?.coerceIn(1025, 65534) ?: settings.socksPort),
                                "httpPort" to (httpPortText.toIntOrNull()?.coerceIn(1025, 65534) ?: settings.httpPort),
                                "subnetsRepoUrl" to subnetsUrlText.trim()
                            )
                        )
                        savedMsg = if (isFa) "تنظیمات شبکه ذخیره شد." else "Network settings saved."
                    }
            ) {
                Text(
                    text = tr("ذخیره تنظیمات شبکه", "Save Network Settings"),
                    color = p.onAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun XraySettingsScreen(
    settings: EngineSettings,
    onPatchSettings: (Map<String, Any>) -> Unit
) {
    val p = LocalAlanPalette.current
    val isFa = LocalAlanLang.current == AlanLang.FA

    var strideText by remember(settings.forwardStrideBytes) { mutableStateOf(settings.forwardStrideBytes.toString()) }
    var jitterText by remember(settings.strideJitterBytes) { mutableStateOf(settings.strideJitterBytes.toString()) }
    var fragLenText by remember(settings.fragmentLength) { mutableStateOf(settings.fragmentLength) }
    var fragIntText by remember(settings.fragmentInterval) { mutableStateOf(settings.fragmentInterval) }
    var earlyDataText by remember(settings.wsEarlyData) { mutableStateOf(settings.wsEarlyData.toString()) }
    var muxText by remember(settings.muxConcurrency) { mutableStateOf(settings.muxConcurrency.toString()) }
    var savedMsg by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = tr("تنظیمات Xray و فرگمنت", "Xray & Fragment Settings"),
            color = p.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )

        if (savedMsg.isNotBlank()) {
            Text(savedMsg, color = p.emerald, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }

        SimpleSectionCard(tr("تنظیمات TLS و مرورگر", "TLS & uTLS Fingerprint")) {
            AlanCustomSelectBox(
                label = tr("شبیه‌سازی مرورگر (Fingerprint)", "uTLS Fingerprint"),
                selectedValue = settings.fingerprint,
                options = listOf("custom_poly", "chrome", "firefox", "safari", "ios", "edge", "randomized")
                    .map { SelectOptionItem(it, it) },
                onSelect = { onPatchSettings(mapOf("fingerprint" to it)) }
            )
            AlanCustomSelectBox(
                label = tr("تنظیم ALPN", "ALPN Override"),
                selectedValue = settings.alpnOverride,
                options = listOf("auto", "h2,http/1.1", "http/1.1", "h2")
                    .map { SelectOptionItem(it, it) },
                onSelect = { onPatchSettings(mapOf("alpnOverride" to it)) }
            )
            AlanSheetTextField(
                label = tr("مقدار Early Data وب‌سوکت", "WebSocket Early Data (Bytes)"),
                value = earlyDataText,
                onValueChange = { earlyDataText = it.filter(Char::isDigit) },
                ltr = true,
                numeric = true
            )
        }

        SimpleSectionCard(tr("فرگمنت و برش پکت", "Packet Slicing & Fragment")) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField(
                    label = tr("طول گام برش (Bytes)", "Stride Bytes"),
                    value = strideText,
                    onValueChange = { strideText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                AlanSheetTextField(
                    label = tr("نوسان تصادفی (Jitter)", "Stride Jitter"),
                    value = jitterText,
                    onValueChange = { jitterText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
            }

            CleanSwitchItem(
                title = tr("فرگمنت داخلی هسته Xray", "Xray Core TLS Fragment"),
                subtitle = tr("خرد کردن پکت‌های هندشیک TLS در حالت دستی", "Slice outbound TLS ClientHello packets"),
                checked = settings.fragmentEnabled,
                onChecked = { onPatchSettings(mapOf("fragmentEnabled" to it)) }
            )

            if (settings.fragmentEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    AlanSheetTextField(
                        label = tr("طول تکه (Length)", "Fragment Length"),
                        value = fragLenText,
                        onValueChange = { fragLenText = it },
                        ltr = true,
                        modifier = Modifier.weight(1f)
                    )
                    AlanSheetTextField(
                        label = tr("وقفه (ms)", "Interval (ms)"),
                        value = fragIntText,
                        onValueChange = { fragIntText = it },
                        ltr = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            CleanSwitchItem(
                title = tr("ترکیب کانکشن‌ها (Mux)", "Multiplexing (Mux)"),
                subtitle = tr("ارسال هم‌زمان چند نشست روی یک کانکشن", "Bundle multiple TCP streams over one tunnel"),
                checked = settings.muxEnabled,
                onChecked = { onPatchSettings(mapOf("muxEnabled" to it)) }
            )

            if (settings.muxEnabled) {
                AlanSheetTextField(
                    label = tr("تعداد هم‌زمانی Mux", "Mux Concurrency"),
                    value = muxText,
                    onValueChange = { muxText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.accentGold)
                    .clickable {
                        onPatchSettings(
                            mapOf(
                                "forwardStrideBytes" to (strideText.toIntOrNull()?.coerceIn(0, 4096) ?: settings.forwardStrideBytes),
                                "strideJitterBytes" to (jitterText.toIntOrNull()?.coerceIn(0, 512) ?: settings.strideJitterBytes),
                                "wsEarlyData" to (earlyDataText.toIntOrNull()?.coerceIn(0, 8192) ?: settings.wsEarlyData),
                                "fragmentLength" to fragLenText.trim().ifBlank { "5-16" },
                                "fragmentInterval" to fragIntText.trim().ifBlank { "2-3" },
                                "muxConcurrency" to (muxText.toIntOrNull()?.coerceIn(1, 64) ?: settings.muxConcurrency)
                            )
                        )
                        savedMsg = if (isFa) "تنظیمات Xray ذخیره شد." else "Xray settings saved."
                    }
            ) {
                Text(
                    text = tr("ذخیره تنظیمات Xray", "Save Xray Settings"),
                    color = p.onAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SimpleSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val p = LocalAlanPalette.current
    Column(
        verticalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(cornerRadius = 16.dp)
            .padding(14.dp)
    ) {
        Text(title, color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
        content()
    }
}

@Composable
private fun CleanSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    val p = LocalAlanPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .alanNeuInset(cornerRadius = 12.dp)
            .clickable { onChecked(!checked) }
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            Text(subtitle, color = p.textMuted, fontSize = 10.5.sp)
        }
        Spacer(Modifier.width(10.dp))
        AlanPillSwitch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun LogsTabScreen(
    logs: List<String>,
    onClearLogs: () -> Unit
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var copied by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(logs, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) logs else logs.filter { it.lowercase(Locale.ROOT).contains(q) }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = tr("لاگ‌های زنده", "Live Engine Logs"),
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (copied) {
                        tr("در کلیپ‌بورد کپی شد.", "Copied to clipboard.")
                    } else {
                        tr("${filteredLogs.size} خط نمایش داده‌شده", "${filteredLogs.size} lines")
                    },
                    color = if (copied) p.emerald else p.textSecondary,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(36.dp)
                        .alanGlassCard(cornerRadius = 10.dp)
                        .clickable {
                            if (filteredLogs.isNotEmpty()) {
                                copyPlainTextToClipboard(context, "Logs", filteredLogs.joinToString("\n"))
                                copied = true
                            }
                        }
                        .padding(horizontal = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(tr("کپی", "Copy"), color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(36.dp)
                        .alanGlassCard(cornerRadius = 10.dp)
                        .clickable {
                            copied = false
                            onClearLogs()
                        }
                        .padding(horizontal = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DeleteOutline, null, tint = p.danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("پاک کردن", "Clear"), color = p.danger, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
        }

        AlanSheetTextField(
            label = tr("فیلتر و جستجو در لاگ‌ها...", "Filter logs..."),
            value = searchQuery,
            onValueChange = { searchQuery = it },
            ltr = true
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .alanNeuInset(cornerRadius = 16.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = tr("لاگی برای نمایش وجود ندارد.", "No logs to display."),
                        color = p.textMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                LtrContent {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(13.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredLogs, key = { idx, _ -> idx }) { _, line ->
                            val low = line.lowercase(Locale.ROOT)
                            // High-contrast theme-aware log colors for both Dark & Light modes
                            val col: Color = when {
                                low.contains("error") || low.contains("fail") || low.contains("blocked") || low.contains("timeout") -> p.danger
                                low.contains("up:") || low.contains("verified") || low.contains("qualified") || low.contains("winner") || low.contains("ready") -> p.emerald
                                low.contains("vec-") || low.contains("stage") -> p.accentGold
                                else -> p.textPrimary
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                )
                                Text(
                                    text = line,
                                    color = col,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun PerAppProxySplitTunnelDialog(
    currentMode: String,
    selectedPackagesCsv: String,
    installedApps: List<InstalledAppUiItem>,
    onRequestLoadApps: (includeSystem: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (mode: String, packages: Set<String>) -> Unit
) {
    val p = LocalAlanPalette.current
    var mode by remember(currentMode) { mutableStateOf(currentMode.uppercase(Locale.US).let { if (it in setOf("ALL", "INCLUDE", "EXCLUDE")) it else "ALL" }) }
    var showSys by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedSet by remember(selectedPackagesCsv) { mutableStateOf(selectedPackagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()) }

    LaunchedEffect(showSys) { onRequestLoadApps(showSys) }
    val filtered = remember(installedApps, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        installedApps.filter { q.isEmpty() || it.appName.lowercase(Locale.ROOT).contains(q) || it.packageName.lowercase(Locale.ROOT).contains(q) }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(shape = RoundedCornerShape(22.dp), color = p.surfaceBg, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("تفکیک برنامه‌ها (Per-App Proxy)", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        AlanNeuIconButton(Icons.Outlined.Close, p.textSecondary, 32.dp, onClick = onDismiss)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().alanNeuInset(cornerRadius = 12.dp).padding(4.dp)) {
                        listOf("ALL" to "همه برنامه‌ها", "INCLUDE" to "فقط انتخابی‌ها", "EXCLUDE" to "عبور مستقیم").forEach { (k, t) ->
                            val sel = mode == k
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).height(34.dp).then(if (sel) Modifier.alanGlassCard(highlighted = true, cornerRadius = 9.dp) else Modifier.clip(RoundedCornerShape(9.dp))).clickable { mode = k }) {
                                Text(t, color = if (sel) p.emerald else p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                    AlanSheetTextField("جستجوی برنامه...", query, { query = it }, ltr = false)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().height(240.dp)) {
                        itemsIndexed(filtered, key = { _, item -> item.packageName }) { _, app ->
                            val chk = app.packageName in selectedSet
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().then(if (chk) Modifier.alanNeuInset(cornerRadius = 11.dp, accented = true) else Modifier.alanGlassCard(cornerRadius = 11.dp)).clickable {
                                selectedSet = if (chk) selectedSet - app.packageName else selectedSet + app.packageName
                            }.padding(horizontal = 11.dp, vertical = 8.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp).then(if (chk) Modifier.clip(RoundedCornerShape(5.dp)).background(p.emerald) else Modifier.alanNeuInset(cornerRadius = 5.dp))) {
                                    if (chk) Icon(Icons.Outlined.Check, null, tint = Color(0xFF07130E), modifier = Modifier.size(13.dp))
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                    LtrContent { Text(app.packageName, color = p.textMuted, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, maxLines = 1) }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).height(40.dp).alanNeuInset(cornerRadius = 11.dp).clickable { onDismiss() }) {
                            Text("انصراف", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1.3f).height(40.dp).clip(RoundedCornerShape(11.dp)).background(p.emerald).clickable { onSave(mode, selectedSet) }) {
                            Text("ذخیره (${selectedSet.size})", color = Color(0xFF07130E), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
