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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
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
    var tunMtuText by remember(settings.tunMtu) { mutableStateOf(settings.tunMtu.toString()) }
    var tunDnsText by remember(settings.tunDns) { mutableStateOf(settings.tunDns) }
    var socksPortText by remember(settings.socksPort) { mutableStateOf(settings.socksPort.toString()) }
    var httpPortText by remember(settings.httpPort) { mutableStateOf(settings.httpPort.toString()) }
    var subnetsUrlText by remember(settings.subnetsRepoUrl) { mutableStateOf(settings.subnetsRepoUrl) }
    var savedMsg by remember { mutableStateOf("") }

    val methodOptions = remember(vectors) {
        vectors.map { spec -> SelectOptionItem(spec.id, spec.id, spec.name.substringAfter("—").trim()) }
    }

    val modeOptions = remember {
        listOf(
            SelectOptionItem("auto_hybrid", "ترکیبی هوشمند (پیشنهادی)", "استفاده هم‌زمان از تمام متدها"),
            SelectOptionItem("proprietary_only", "فقط متدهای تزریق پکت", "مخصوص عبور از فیلترینگ شدید SNI"),
            SelectOptionItem("fragment_only", "فقط متدهای فرگمنت", "مناسب سرورهای کلودفلر و CDN")
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("تنظیمات عمومی", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .alanGlassCard(cornerRadius = 12.dp)
                    .clickable {
                        onReplaceSettings(EngineSettings())
                        savedMsg = "تنظیمات به حالت پیش‌فرض برگشت."
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("پیش‌فرض", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        if (savedMsg.isNotBlank()) {
            Text(savedMsg, color = p.emerald, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        SimpleSectionCard("متد عبور از فیلتر") {
            AlanCustomSelectBox("انتخاب متد", settings.manualVectorId, methodOptions, { onPatchSettings(mapOf("manualVectorId" to it)) })

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .alanGlassCard(
                        cornerRadius = 14.dp,
                        highlighted = !isFindingBestMethod,
                        accentBorderColor = if(isFindingBestMethod) p.danger else p.accentGold
                    )
                    .clickable { onAutoDetectBestMethod() }
                    .padding(vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.AutoFixHigh, null, tint = if (isFindingBestMethod) p.danger else p.accentGold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isFindingBestMethod) "توقف جستجوی متد" else "پیدا کردن خودکار بهترین متد",
                        color = if(isFindingBestMethod) p.danger else p.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }

            if (autoMethodStatus.isNotBlank()) {
                Text(autoMethodStatus, color = if (autoMethodStatus.contains("انتخاب شد")) p.emerald else p.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            AlanCustomSelectBox("حالت کاری موتور", settings.engineMode, modeOptions, { onPatchSettings(mapOf("engineMode" to it)) })
        }

        SimpleSectionCard("ظاهر برنامه") {
            CleanSwitchItem("تم تیره (حالت شب)", "با خاموش کردن این گزینه، تم سفید فعال می‌شود", settings.darkTheme, { onPatchSettings(mapOf("darkTheme" to it)) })
        }

        SimpleSectionCard("اتصال هوشمند") {
            CleanSwitchItem("تزریق خودکار IP تمیز روی کلادفلر", "جایگزینی خودکار IP تمیز", settings.autoCleanIpForCf, { onPatchSettings(mapOf("autoCleanIpForCf" to it)) })
            CleanSwitchItem("انتخاب خودکار متد هنگام اتصال", "تست خودکار متدها", settings.autoPilot, { onPatchSettings(mapOf("autoPilot" to it)) })
            CleanSwitchItem("تست پایداری یوتیوب", "اطمینان از قطع نشدن ارتباط", settings.requireYoutube6s, { onPatchSettings(mapOf("requireYoutube6s" to it)) })
            CleanSwitchItem("تست سلامت آپلود", "بررسی باز بودن مسیر ارسال داده", settings.verifyUploadStream, { onPatchSettings(mapOf("verifyUploadStream" to it)) })
            CleanSwitchItem("تعمیر خودکار اتصال", "جایگزینی خودکار متد در صورت افت اینترنت", settings.selfHealingWatchdog, { onPatchSettings(mapOf("selfHealingWatchdog" to it)) })
        }

        SimpleSectionCard("شبکه و پورت‌ها") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField("MTU تونل", tunMtuText, { tunMtuText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
                AlanSheetTextField("آدرس DNS", tunDnsText, { tunDnsText = it }, ltr = true, modifier = Modifier.weight(1.2f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField("پورت SOCKS5", socksPortText, { socksPortText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
                AlanSheetTextField("پورت HTTP", httpPortText, { httpPortText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
            }
            AlanSheetTextField("لینک فایل ساب‌نت", subnetsUrlText, { subnetsUrlText = it }, ltr = true)
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .alanGlassCard(cornerRadius = 14.dp, highlighted = true)
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
                        savedMsg = "تنظیمات شبکه ذخیره شد."
                    }
                    .padding(vertical = 14.dp)
            ) {
                Text("ذخیره تنظیمات شبکه", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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
    var strideText by remember(settings.forwardStrideBytes) { mutableStateOf(settings.forwardStrideBytes.toString()) }
    var jitterText by remember(settings.strideJitterBytes) { mutableStateOf(settings.strideJitterBytes.toString()) }
    var fragLenText by remember(settings.fragmentLength) { mutableStateOf(settings.fragmentLength) }
    var fragIntText by remember(settings.fragmentInterval) { mutableStateOf(settings.fragmentInterval) }
    var earlyDataText by remember(settings.wsEarlyData) { mutableStateOf(settings.wsEarlyData.toString()) }
    var muxText by remember(settings.muxConcurrency) { mutableStateOf(settings.muxConcurrency.toString()) }
    var savedMsg by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text("تنظیمات Xray", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        if (savedMsg.isNotBlank()) {
            Text(savedMsg, color = p.emerald, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        SimpleSectionCard("تنظیمات TLS و مرورگر") {
            AlanCustomSelectBox("شبیه‌سازی مرورگر", settings.fingerprint, listOf("chrome", "firefox", "safari", "ios", "edge", "randomized").map { SelectOptionItem(it, it) }, { onPatchSettings(mapOf("fingerprint" to it)) })
            AlanCustomSelectBox("تنظیم ALPN", settings.alpnOverride, listOf("auto", "h2,http/1.1", "http/1.1", "h2").map { SelectOptionItem(it, it) }, { onPatchSettings(mapOf("alpnOverride" to it)) })
            AlanSheetTextField("مقدار Early Data وب‌سوکت", earlyDataText, { earlyDataText = it.filter(Char::isDigit) }, ltr = true, numeric = true)
        }

        SimpleSectionCard("فرگمنت و برش پکت") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField("طول گام برش (Bytes)", strideText, { strideText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
                AlanSheetTextField("نوسان تصادفی (Jitter)", jitterText, { jitterText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
            }

            CleanSwitchItem("فرگمنت داخلی Xray (در حالت دستی)", "خرد کردن پکت‌های هندشیک توسط هسته Xray", settings.fragmentEnabled, { onPatchSettings(mapOf("fragmentEnabled" to it)) })

            if (settings.fragmentEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    AlanSheetTextField("طول تکه (Length)", fragLenText, { fragLenText = it }, ltr = true, modifier = Modifier.weight(1f))
                    AlanSheetTextField("وقفه (ms)", fragIntText, { fragIntText = it }, ltr = true, modifier = Modifier.weight(1f))
                }
            }

            CleanSwitchItem("ترکیب کانکشن‌ها (Mux)", "ارسال هم‌زمان چند درخواست در یک تونل", settings.muxEnabled, { onPatchSettings(mapOf("muxEnabled" to it)) })
            if (settings.muxEnabled) {
                AlanSheetTextField("تعداد هم‌زمانی Mux", muxText, { muxText = it.filter(Char::isDigit) }, ltr = true, numeric = true)
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .alanGlassCard(cornerRadius = 14.dp, highlighted = true)
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
                        savedMsg = "تنظیمات Xray ذخیره شد."
                    }
                    .padding(vertical = 14.dp)
            ) {
                Text("ذخیره تنظیمات Xray", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(cornerRadius = 20.dp)
            .padding(18.dp)
    ) {
        Text(title, color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
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
            .alanNeuInset(cornerRadius = 16.dp)
            .clickable { onChecked(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = p.textMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
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

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("لاگ‌ها", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(
                    text = if (copied) "در کلیپ‌بورد کپی شد." else "${logs.size} خط ثبت شده",
                    color = if (copied) p.emerald else p.textMuted,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .alanGlassCard(cornerRadius = 12.dp)
                        .clickable {
                            if (logs.isNotEmpty()) {
                                copyPlainTextToClipboard(context, "Logs", logs.joinToString("\n"))
                                copied = true
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = p.textPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("کپی", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .alanGlassCard(cornerRadius = 12.dp)
                        .clickable {
                            copied = false
                            onClearLogs()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DeleteOutline, null, tint = p.danger, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("پاک کردن", color = p.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .alanNeuInset(cornerRadius = 18.dp)
        ) {
            if (logs.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("لاگی ثبت نشده است.", color = p.textMuted, fontSize = 13.sp)
                }
            } else {
                LtrContent {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(logs, key = { idx, _ -> idx }) { _, line ->
                            val low = line.lowercase(Locale.ROOT)
                            val col = when {
                                low.contains("error") || low.contains("fail") || low.contains("blocked") || low.contains("timeout") -> Color(0xFFF87171)
                                low.contains("up:") || low.contains("verified") || low.contains("qualified") || low.contains("winner") -> Color(0xFF34D399)
                                low.contains("vec-") || low.contains("stage") -> Color(0xFFF5C451)
                                else -> Color(0xFFCBD5E1) // Default Log Color in Dark
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                )
                                Text(
                                    text = line,
                                    color = col,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}