package com.alan.ripdpi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Surface
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

    val modeOptions = remember {
        listOf(
            SelectOptionItem("auto_hybrid", "ترکیبی هوشمند (پیشنهادی)", "استفاده هم‌زمان از تمام متدها"),
            SelectOptionItem("proprietary_only", "فقط متدهای تزریق پکت", "مخصوص عبور از فیلترینگ شدید SNI"),
            SelectOptionItem("fragment_only", "فقط متدهای فرگمنت", "مناسب سرورهای کلودفلر و CDN")
        )
    }

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
            Text("تنظیمات عمومی", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Surface(
                onClick = {
                    onReplaceSettings(EngineSettings())
                    savedMsg = "تنظیمات به حالت پیش‌فرض برگشت."
                },
                shape = RoundedCornerShape(9.dp),
                color = p.subtleBg,
                border = BorderStroke(1.dp, p.cardBorder)
            ) {
                Text("پیش‌فرض", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
            }
        }

        if (savedMsg.isNotBlank()) {
            Text(savedMsg, color = p.emerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        SimpleSectionCard("متد عبور از فیلتر") {
            AlanCustomSelectBox(
                label = "انتخاب متد",
                selectedValue = settings.manualVectorId,
                options = methodOptions,
                onSelect = { onPatchSettings(mapOf("manualVectorId" to it)) }
            )

            Surface(
                onClick = onAutoDetectBestMethod,
                shape = RoundedCornerShape(11.dp),
                color = if (isFindingBestMethod) p.danger else p.accentGold,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 11.dp, horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = if (isFindingBestMethod) Color.White else p.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (isFindingBestMethod) "توقف جستجوی متد" else "پیدا کردن خودکار بهترین متد",
                        color = if (isFindingBestMethod) Color.White else p.onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }

            if (autoMethodStatus.isNotBlank()) {
                Text(
                    text = autoMethodStatus,
                    color = if (autoMethodStatus.contains("انتخاب شد")) p.emerald else p.accentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AlanCustomSelectBox(
                label = "حالت کاری موتور",
                selectedValue = settings.engineMode,
                options = modeOptions,
                onSelect = { onPatchSettings(mapOf("engineMode" to it)) }
            )
        }

        SimpleSectionCard("ظاهر برنامه") {
            CleanSwitchItem(
                title = "تم تیره (حالت شب)",
                subtitle = "با خاموش کردن این گزینه، تم سفید فعال و برای همیشه ذخیره می‌شود",
                checked = settings.darkTheme,
                onChecked = { onPatchSettings(mapOf("darkTheme" to it)) }
            )
        }

        SimpleSectionCard("اتصال هوشمند") {
            CleanSwitchItem(
                title = "انتخاب خودکار متد هنگام اتصال",
                subtitle = "تست خودکار متدها در زمان زدن دکمه اتصال",
                checked = settings.autoPilot,
                onChecked = { onPatchSettings(mapOf("autoPilot" to it)) }
            )
            CleanSwitchItem(
                title = "تست پایداری یوتیوب",
                subtitle = "اطمینان از قطع نشدن ارتباط بعد از چند ثانیه",
                checked = settings.requireYoutube6s,
                onChecked = { onPatchSettings(mapOf("requireYoutube6s" to it)) }
            )
            CleanSwitchItem(
                title = "تست سلامت آپلود",
                subtitle = "بررسی باز بودن مسیر ارسال داده",
                checked = settings.verifyUploadStream,
                onChecked = { onPatchSettings(mapOf("verifyUploadStream" to it)) }
            )
            CleanSwitchItem(
                title = "تعمیر خودکار اتصال",
                subtitle = "جایگزینی خودکار متد در صورت افت اینترنت",
                checked = settings.selfHealingWatchdog,
                onChecked = { onPatchSettings(mapOf("selfHealingWatchdog" to it)) }
            )
        }

        SimpleSectionCard("شبکه و پورت‌ها") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField("MTU تونل", tunMtuText, { tunMtuText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
                AlanSheetTextField("آدرس DNS", tunDnsText, { tunDnsText = it }, ltr = true, modifier = Modifier.weight(1.2f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField("پورت SOCKS5", socksPortText, { socksPortText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
                AlanSheetTextField("پورت HTTP", httpPortText, { httpPortText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
            }
            Surface(
                onClick = {
                    onPatchSettings(
                        mapOf(
                            "tunMtu" to (tunMtuText.toIntOrNull()?.coerceIn(1280, 9000) ?: settings.tunMtu),
                            "tunDns" to tunDnsText.trim().ifBlank { "1.1.1.1" },
                            "socksPort" to (socksPortText.toIntOrNull()?.coerceIn(1025, 65534) ?: settings.socksPort),
                            "httpPort" to (httpPortText.toIntOrNull()?.coerceIn(1025, 65534) ?: settings.httpPort)
                        )
                    )
                    savedMsg = "تنظیمات شبکه ذخیره شد."
                },
                shape = RoundedCornerShape(11.dp),
                color = p.accentGold,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                    Text("ذخیره تنظیمات شبکه", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text("تنظیمات Xray", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        if (savedMsg.isNotBlank()) {
            Text(savedMsg, color = p.emerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        SimpleSectionCard("تنظیمات TLS و مرورگر") {
            AlanCustomSelectBox(
                label = "شبیه‌سازی مرورگر (Fingerprint)",
                selectedValue = settings.fingerprint,
                options = listOf("chrome", "firefox", "safari", "ios", "edge", "randomized")
                    .map { SelectOptionItem(it, it) },
                onSelect = { onPatchSettings(mapOf("fingerprint" to it)) }
            )
            AlanCustomSelectBox(
                label = "تنظیم ALPN",
                selectedValue = settings.alpnOverride,
                options = listOf("auto", "h2,http/1.1", "http/1.1", "h2")
                    .map { SelectOptionItem(it, it) },
                onSelect = { onPatchSettings(mapOf("alpnOverride" to it)) }
            )
            AlanSheetTextField("مقدار Early Data وب‌سوکت", earlyDataText, { earlyDataText = it.filter(Char::isDigit) }, ltr = true, numeric = true)
        }

        SimpleSectionCard("فرگمنت و برش پکت") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField("طول گام برش (Bytes)", strideText, { strideText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
                AlanSheetTextField("نوسان تصادفی (Jitter)", jitterText, { jitterText = it.filter(Char::isDigit) }, ltr = true, numeric = true, modifier = Modifier.weight(1f))
            }

            CleanSwitchItem(
                title = "فرگمنت داخلی Xray (در حالت دستی)",
                subtitle = "خرد کردن پکت‌های هندشیک توسط هسته Xray",
                checked = settings.fragmentEnabled,
                onChecked = { onPatchSettings(mapOf("fragmentEnabled" to it)) }
            )

            if (settings.fragmentEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AlanSheetTextField("طول تکه (Length)", fragLenText, { fragLenText = it }, ltr = true, modifier = Modifier.weight(1f))
                    AlanSheetTextField("وقفه (ms)", fragIntText, { fragIntText = it }, ltr = true, modifier = Modifier.weight(1f))
                }
            }

            CleanSwitchItem(
                title = "ترکیب کانکشن‌ها (Mux)",
                subtitle = "ارسال هم‌زمان چند درخواست در یک تونل",
                checked = settings.muxEnabled,
                onChecked = { onPatchSettings(mapOf("muxEnabled" to it)) }
            )
            if (settings.muxEnabled) {
                AlanSheetTextField("تعداد هم‌زمانی Mux", muxText, { muxText = it.filter(Char::isDigit) }, ltr = true, numeric = true)
            }

            Surface(
                onClick = {
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
                },
                shape = RoundedCornerShape(11.dp),
                color = p.accentGold,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                    Text("ذخیره تنظیمات Xray", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(cornerRadius = 15.dp)
            .padding(14.dp)
    ) {
        Text(title, color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
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
            .clip(RoundedCornerShape(11.dp))
            .background(p.subtleBg)
            .border(1.dp, p.cardBorder, RoundedCornerShape(11.dp))
            .clickable { onChecked(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(subtitle, color = p.textMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.width(8.dp))
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
                Text("لاگ‌ها", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    text = if (copied) "در کلیپ‌بورد کپی شد." else "${logs.size} خط ثبت شده",
                    color = if (copied) p.emerald else p.textMuted,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = {
                        if (logs.isNotEmpty()) {
                            copyPlainTextToClipboard(context, "Logs", logs.joinToString("\n"))
                            copied = true
                        }
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = p.subtleBg,
                    border = BorderStroke(1.dp, p.cardBorder)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("کپی", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Surface(
                    onClick = {
                        copied = false
                        onClearLogs()
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = p.danger.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, p.danger.copy(alpha = 0.4f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, null, tint = p.danger, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("پاک کردن", color = p.danger, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF05080E))
                .border(1.dp, p.cardBorder, RoundedCornerShape(14.dp))
        ) {
            if (logs.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("لاگی ثبت نشده است.", color = p.textMuted, fontSize = 12.sp)
                }
            } else {
                LtrContent {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(logs, key = { idx, _ -> idx }) { _, line ->
                            val low = line.lowercase(Locale.ROOT)
                            val col = when {
                                low.contains("error") || low.contains("fail") || low.contains("blocked") || low.contains("timeout") -> Color(0xFFF87171)
                                low.contains("up:") || low.contains("verified") || low.contains("qualified") || low.contains("winner") -> Color(0xFF34D399)
                                low.contains("vec-") || low.contains("stage") -> Color(0xFFF5C451)
                                else -> Color(0xFFCBD5E1)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                )
                                Text(line, color = col, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
