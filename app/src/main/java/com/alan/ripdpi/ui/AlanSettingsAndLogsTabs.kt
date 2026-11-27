package com.alan.ripdpi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

@Composable
fun GeneralSettingsScreen(
    settings: EngineSettings,
    vectors: List<VectorSpec>,
    isFindingBestMethod: Boolean,
    autoMethodStatus: String,
    onAutoDetectBestMethod: () -> Unit,
    onReplaceSettings: (EngineSettings) -> Unit,
    onPatchSettings: (Map<String, Any>) -> Unit,
    onOpenSplitTunnelDialog: () -> Unit = {}
) {
    val p = LocalAlanPalette.current

    var tunMtuText by remember(settings.tunMtu) { mutableStateOf(settings.tunMtu.toString()) }
    var tunDnsText by remember(settings.tunDns) { mutableStateOf(settings.tunDns) }
    var socksPortText by remember(settings.socksPort) { mutableStateOf(settings.socksPort.toString()) }
    var httpPortText by remember(settings.httpPort) { mutableStateOf(settings.httpPort.toString()) }
    var subnetsUrlText by remember(settings.subnetsRepoUrl) { mutableStateOf(settings.subnetsRepoUrl) }
    var autoRenameEnabled by remember(settings.autoRenameOnScan) { mutableStateOf(settings.autoRenameOnScan) }
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
            SelectOptionItem("fragment_only", "فقط متدهای فرگمنت", "مناسب سرورهای کلادفلر و CDN")
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .alanGlassCard(cornerRadius = 11.dp)
                    .clickable {
                        onReplaceSettings(EngineSettings(darkTheme = settings.darkTheme))
                        savedMsg = "تنظیمات به حالت پیش‌فرض برگشت."
                    }
                    .padding(horizontal = 13.dp)
            ) {
                Text("بازنشانی پیش‌فرض", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            }
        }

        if (savedMsg.isNotBlank()) {
            Text(savedMsg, color = p.emerald, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }

        SimpleSectionCard("تفکیک هوشمند برنامه‌ها (Per-App Proxy / Split Tunnel)") {
            val selectedCount = settings.splitTunnelPackagesCsv.split(",").count { it.isNotBlank() }
            val modeSummary = when (settings.splitTunnelMode.uppercase(Locale.US)) {
                "INCLUDE" -> "عبور فقط برنامه‌های انتخابی از تونل ($selectedCount برنامه)"
                "EXCLUDE" -> "عبور مستقیم برنامه‌های انتخابی بدون VPN ($selectedCount برنامه)"
                else -> "عبور تمام برنامه‌های دستگاه از تونل"
            }
            Text(modeSummary, color = p.textSecondary, fontSize = 11.5.sp)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .alanGlassCard(highlighted = true, cornerRadius = 12.dp)
                    .clickable { onOpenSplitTunnelDialog() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Outlined.Apps, null, tint = p.accentGold, modifier = Modifier.size(17.dp))
                    Text(
                        text = "مدیریت و انتخاب برنامه‌ها (Per-App Proxy)",
                        color = p.accentGold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        SimpleSectionCard("متد عبور از فیلتر") {
            AlanCustomSelectBox(
                label = "انتخاب متد دستی",
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
                        text = if (isFindingBestMethod) "توقف جستجوی متد" else "پیدا کردن خودکار بهترین متد",
                        color = if (isFindingBestMethod) Color.White else p.onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp
                    )
                }
            }

            if (autoMethodStatus.isNotBlank()) {
                Text(
                    text = autoMethodStatus,
                    color = if (autoMethodStatus.contains("VEC-", true)) p.emerald else p.textSecondary,
                    fontSize = 11.5.sp,
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
                title = "تم تیره نئومورفیک (حالت شب)",
                subtitle = "با خاموش کردن این گزینه، تم روشن نئومورفیک فعال می‌شود",
                checked = settings.darkTheme,
                onChecked = { onPatchSettings(mapOf("darkTheme" to it)) }
            )
        }

        SimpleSectionCard("اتصال هوشمند و مخزن") {
            CleanSwitchItem(
                title = "تزریق خودکار IP تمیز روی کلادفلر",
                subtitle = "جایگزینی خودکار IP تمیز روی کانفیگ‌های کلادفلر و CDN",
                checked = settings.autoCleanIpForCf,
                onChecked = { onPatchSettings(mapOf("autoCleanIpForCf" to it)) }
            )
            CleanSwitchItem(
                title = "اعمال متد ضد DPI در اتصال خودکار کانفیگ شخصی",
                subtitle = "خاموش = اتصال خالص استاندارد v2rayNG به بهترین کانفیگ شما",
                checked = settings.applyDpiOnUserAuto,
                onChecked = { onPatchSettings(mapOf("applyDpiOnUserAuto" to it)) }
            )
            CleanSwitchItem(
                title = "انتخاب خودکار مخزن ابری هنگام اتصال",
                subtitle = "استفاده از مخزن ابری گیت‌هاب در دکمه اصلی اتصال",
                checked = settings.autoPilot,
                onChecked = { onPatchSettings(mapOf("autoPilot" to it)) }
            )
            CleanSwitchItem(
                title = "نام‌گذاری خودکار با پرچم هنگام اسکن",
                subtitle = "ثبت خودکار پرچم و نام کشور پس از تست پینگ واقعی",
                checked = autoRenameEnabled,
                onChecked = {
                    autoRenameEnabled = it
                    setAutoRenameOnScanPreference(it)
                    onPatchSettings(mapOf("autoRenameOnScan" to it))
                }
            )
            CleanSwitchItem(
                title = "تعمیر خودکار اتصال (Watchdog)",
                subtitle = "جایگزینی خودکار متد در صورت افت اینترنت",
                checked = settings.selfHealingWatchdog,
                onChecked = { onPatchSettings(mapOf("selfHealingWatchdog" to it)) }
            )
        }

        SimpleSectionCard("شبکه و پورت‌ها") {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField(
                    label = "MTU تونل",
                    value = tunMtuText,
                    onValueChange = { tunMtuText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                AlanSheetTextField(
                    label = "آدرس DNS",
                    value = tunDnsText,
                    onValueChange = { tunDnsText = it },
                    ltr = true,
                    modifier = Modifier.weight(1.2f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField(
                    label = "پورت SOCKS5",
                    value = socksPortText,
                    onValueChange = { socksPortText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                AlanSheetTextField(
                    label = "پورت HTTP",
                    value = httpPortText,
                    onValueChange = { httpPortText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
            }
            AlanSheetTextField(
                label = "لینک فایل ساب‌نت گیت‌هاب",
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
                        savedMsg = "تنظیمات شبکه ذخیره شد."
                    }
            ) {
                Text("ذخیره تنظیمات شبکه", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun XraySettingsScreen(
    settings: EngineSettings,
    onPatchSettings: (Map<String, Any>) -> Unit,
    onImportBypassFile: (targetType: String, rawText: String, replaceExisting: Boolean) -> Int = { _, _, _ -> 0 }
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
        Text("تنظیمات هسته Xray", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)

        if (savedMsg.isNotBlank()) {
            Text(savedMsg, color = p.emerald, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }

        SimpleSectionCard("تنظیمات TLS و مرورگر") {
            AlanCustomSelectBox(
                label = "شبیه‌سازی مرورگر (Fingerprint)",
                selectedValue = settings.fingerprint,
                options = listOf("custom_poly", "chrome", "firefox", "safari", "ios", "edge", "randomized")
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
            AlanSheetTextField(
                label = "مقدار Early Data وب‌سوکت",
                value = earlyDataText,
                onValueChange = { earlyDataText = it.filter(Char::isDigit) },
                ltr = true,
                numeric = true
            )
        }

        SimpleSectionCard("فرگمنت و برش پکت") {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                AlanSheetTextField(
                    label = "طول گام برش (Bytes)",
                    value = strideText,
                    onValueChange = { strideText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                AlanSheetTextField(
                    label = "نوسان تصادفی (Jitter)",
                    value = jitterText,
                    onValueChange = { jitterText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
            }

            CleanSwitchItem(
                title = "فرگمنت داخلی Xray (در حالت خالص)",
                subtitle = "خرد کردن پکت‌های هندشیک TLS توسط هسته Xray",
                checked = settings.fragmentEnabled,
                onChecked = { onPatchSettings(mapOf("fragmentEnabled" to it)) }
            )

            if (settings.fragmentEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
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

            RoutingAndDnsSettingsSection(
            settings = settings,
            onPatchSettings = onPatchSettings,
            onImportBypassFile = onImportBypassFile
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
            ) {
                Text("ذخیره تنظیمات Xray", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
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
            .alanGlassCard(cornerRadius = 18.dp)
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
            .alanNeuInset(cornerRadius = 13.dp)
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
fun RoutingAndDnsSettingsSection(
    settings: EngineSettings,
    onPatchSettings: (Map<String, Any>) -> Unit,
    onImportBypassFile: (targetType: String, rawText: String, replaceExisting: Boolean) -> Int
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current

    var remoteDns by remember(settings.remoteDns) { mutableStateOf(settings.remoteDns) }
    var domesticDns by remember(settings.domesticDns) { mutableStateOf(settings.domesticDns) }
    var fallbackDns by remember(settings.fallbackDns) { mutableStateOf(settings.fallbackDns) }
    var directDomains by remember(settings.customDirectDomains) { mutableStateOf(settings.customDirectDomains) }
    var proxyDomains by remember(settings.customProxyDomains) { mutableStateOf(settings.customProxyDomains) }
    var blockedDomains by remember(settings.customBlockedDomains) { mutableStateOf(settings.customBlockedDomains) }
    var activeImportTarget by remember { mutableStateOf("direct") }
    var routingFeedback by remember { mutableStateOf("") }

    val bypassFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val rawText = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val added = onImportBypassFile(activeImportTarget, rawText, false)
                routingFeedback = if (added > 0) "✅ $added دامنه/آی‌پی از فایل با موفقیت اضافه شد." else "دامنه یا آی‌پی معتبری در فایل پیدا نشد."
            } catch (t: Throwable) {
                routingFeedback = "خطا در خواندن فایل: ${t.javaClass.simpleName}"
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(cornerRadius = 18.dp)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Outlined.Public, null, tint = p.emerald, modifier = Modifier.size(18.dp))
            Text("تنظیمات پیشرفته DNS و نگهبان حافظه", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AlanSheetTextField("Remote DNS", remoteDns, { remoteDns = it }, ltr = true, modifier = Modifier.weight(1f))
            AlanSheetTextField("DNS داخلی ایران", domesticDns, { domesticDns = it }, ltr = true, modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AlanSheetTextField("Fallback DNS", fallbackDns, { fallbackDns = it }, ltr = true, modifier = Modifier.weight(1f))
            AlanCustomSelectBox(
                label = "استراتژی IP",
                selectedValue = settings.dnsQueryStrategy,
                options = listOf(
                    SelectOptionItem("UseIPv4", "UseIPv4 (پایدار)"),
                    SelectOptionItem("UseIP", "UseIP (دوگانه)"),
                    SelectOptionItem("UseIPv6", "UseIPv6")
                ),
                onSelect = { onPatchSettings(mapOf("dnsQueryStrategy" to it)) },
                modifier = Modifier.weight(1f)
            )
        }

        AlanCustomSelectBox(
            label = "بازه چک خودکار نشتی حافظه (Memory Guard)",
            selectedValue = settings.memoryAuditIntervalMin.toString(),
            options = listOf(
                SelectOptionItem("10", "هر ۱۰ دقیقه"),
                SelectOptionItem("15", "هر ۱۵ دقیقه"),
                SelectOptionItem("20", "هر ۲۰ دقیقه (پیشنهادی)"),
                SelectOptionItem("30", "هر ۳۰ دقیقه"),
                SelectOptionItem("60", "هر ۶۰ دقیقه")
            ),
            onSelect = { onPatchSettings(mapOf("memoryAuditIntervalMin" to (it.toIntOrNull() ?: 20))) }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 12.dp)
                .clickable { onPatchSettings(mapOf("disableDnsCache" to !settings.disableDnsCache)) }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text("غیرفعال کردن کش داخلی DNS", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            AlanPillSwitch(checked = settings.disableDnsCache, onCheckedChange = { onPatchSettings(mapOf("disableDnsCache" to it)) })
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .alanGlassCard(highlighted = true, cornerRadius = 11.dp)
                .clickable {
                    onPatchSettings(
                        mapOf(
                            "remoteDns" to remoteDns.trim().ifBlank { "1.1.1.1" },
                            "domesticDns" to domesticDns.trim().ifBlank { "178.22.122.100" },
                            "fallbackDns" to fallbackDns.trim().ifBlank { "8.8.8.8" }
                        )
                    )
                    routingFeedback = "✅ تنظیمات DNS ذخیره شد."
                }
        ) {
            Text("ذخیره سرورهای DNS", color = p.emerald, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(cornerRadius = 18.dp)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Outlined.Route, null, tint = p.accentGold, modifier = Modifier.size(18.dp))
            Text("مسیریابی (Routing) و بایپس دامنه/IP", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
        }

        AlanCustomSelectBox(
            label = "حالت مسیریابی ترافیک",
            selectedValue = settings.routingMode,
            options = listOf(
                SelectOptionItem("bypass_ir", "دور زدن سایت‌های ایرانی و شبکه محلی (پیشنهادی)"),
                SelectOptionItem("bypass_lan", "فقط دور زدن شبکه محلی (LAN)"),
                SelectOptionItem("global", "سراسری (عبور تمام ترافیک از پروکسی)")
            ),
            onSelect = { onPatchSettings(mapOf("routingMode" to it)) }
        )

        AlanCustomSelectBox(
            label = "استراتژی تطبیق دامنه (DomainStrategy)",
            selectedValue = settings.domainStrategy,
            options = listOf(
                SelectOptionItem("IPIfNonMatch", "IPIfNonMatch (دقیق و متعادل)"),
                SelectOptionItem("AsIs", "AsIs (سریع‌ترین / کم‌مصرف)"),
                SelectOptionItem("IPOnDemand", "IPOnDemand (حل IP در لحظه)")
            ),
            onSelect = { onPatchSettings(mapOf("domainStrategy" to it)) }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 12.dp)
                .clickable { onPatchSettings(mapOf("blockAds" to !settings.blockAds)) }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text("مسدودسازی تبلیغات و ردیاب‌ها (AdBlock)", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            AlanPillSwitch(checked = settings.blockAds, onCheckedChange = { onPatchSettings(mapOf("blockAds" to it)) })
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 12.dp)
                .clickable { onPatchSettings(mapOf("blockQuic" to !settings.blockQuic)) }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text("مسدودسازی QUIC (UDP 443) برای پایداری Anti-DPI", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            AlanPillSwitch(checked = settings.blockQuic, onCheckedChange = { onPatchSettings(mapOf("blockQuic" to it)) })
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 12.dp)
                .clickable { onPatchSettings(mapOf("enableSniffing" to !settings.enableSniffing)) }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text("تشخیص خودکار دامنه از ترافیک (Sniffing)", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            AlanPillSwitch(checked = settings.enableSniffing, onCheckedChange = { onPatchSettings(mapOf("enableSniffing" to it)) })
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 12.dp)
                .clickable { onPatchSettings(mapOf("deepLogEnabled" to !settings.deepLogEnabled)) }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text("لاگ عمیق دیباگ Xray (خاموش = کم‌مصرف‌ترین CPU)", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            AlanPillSwitch(checked = settings.deepLogEnabled, onCheckedChange = { onPatchSettings(mapOf("deepLogEnabled" to it)) })
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("سایت‌ها و IPهای بایپس مستقیم (Direct)", color = p.emerald, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .alanGlassCard(cornerRadius = 9.dp)
                    .clickable {
                        activeImportTarget = "direct"
                        bypassFileLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Icon(Icons.Outlined.UploadFile, null, tint = p.accentGold, modifier = Modifier.size(14.dp))
                Text("ورود از فایل", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
            }
        }
        AlanSheetTextField("هر خط یک دامنه یا IP (مثلاً digikala.com یا 185.143.232.0/22)", directDomains, { directDomains = it }, ltr = true, singleLine = false)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("دامنه‌های اجباری از پروکسی (Proxy)", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .alanGlassCard(cornerRadius = 9.dp)
                    .clickable {
                        activeImportTarget = "proxy"
                        bypassFileLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Icon(Icons.Outlined.UploadFile, null, tint = p.accentGold, modifier = Modifier.size(14.dp))
                Text("ورود از فایل", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
            }
        }
        AlanSheetTextField("دامنه‌ها یا IPهای عبور اجباری از تونل", proxyDomains, { proxyDomains = it }, ltr = true, singleLine = false)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("دامنه‌ها و IPهای مسدود (Block)", color = p.danger, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .alanGlassCard(cornerRadius = 9.dp)
                    .clickable {
                        activeImportTarget = "block"
                        bypassFileLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Icon(Icons.Outlined.UploadFile, null, tint = p.accentGold, modifier = Modifier.size(14.dp))
                Text("ورود از فایل", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
            }
        }
        AlanSheetTextField("دامنه‌ها یا IPهای مسدودشده", blockedDomains, { blockedDomains = it }, ltr = true, singleLine = false)

        if (routingFeedback.isNotBlank()) {
            Text(routingFeedback, color = p.emerald, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .alanGlassCard(highlighted = true, cornerRadius = 11.dp)
                .clickable {
                    onPatchSettings(
                        mapOf(
                            "customDirectDomains" to directDomains.trim(),
                            "customProxyDomains" to proxyDomains.trim(),
                            "customBlockedDomains" to blockedDomains.trim()
                        )
                    )
                    routingFeedback = "✅ قوانین مسیریابی و لیست‌های بایپس ذخیره شدند."
                }
        ) {
            Text("ذخیره قوانین مسیریابی و بایپس", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
    }
}

@Composable
fun LogsTabScreen(
    logs: List<String>,
    onClearLogs: () -> Unit,
    onExportFullLogs: () -> String = { logs.joinToString("\n") }
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current
    var activeFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }

    val saveLogFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            try {
                val fullText = onExportFullLogs()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(fullText.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                feedback = "✅ فایل لاگ ذخیره شد (${fullText.lines().size} خط)."
            } catch (t: Throwable) {
                feedback = "خطا در ذخیره فایل: ${t.javaClass.simpleName}"
            }
        }
    }

    val filtered = remember(logs, activeFilter, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.US)
        logs.asReversed().filter { line ->
            val low = line.lowercase(Locale.US)
            val catOk = when (activeFilter) {
                "NET" -> low.contains("[s#") || low.contains("[bridge]") || low.contains("tcp=") || low.contains("tun") || low.contains("[proxymode]")
                "XRAY" -> low.contains("xray") || low.contains("[core]")
                "DNS" -> low.contains("dns") || low.contains("doh") || low.contains("routing") || low.contains("memguard")
                "LAB" -> low.contains("lab") || low.contains("verifier") || low.contains("vec-") || low.contains("ping")
                "ERR" -> low.contains("err") || low.contains("fail") || low.contains("timeout") || low.contains("خطا") || low.contains("رد شد")
                else -> true
            }
            catOk && (q.isEmpty() || low.contains(q))
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 17.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("کنسول لاگ عمیق مهندسی", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                Text(
                    text = feedback.ifBlank { "${filtered.size} از ${logs.size} رویداد ثبت‌شده" },
                    color = if (feedback.isNotBlank()) p.emerald else p.textMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AlanNeuIconButton(Icons.Outlined.Download, p.emerald, 36.dp) {
                    saveLogFileLauncher.launch("Alan-RIP-Dpi-Logs-${System.currentTimeMillis() / 1000}.log")
                }
                AlanNeuIconButton(Icons.Outlined.Share, p.accentGold, 36.dp) {
                    sharePlainText(context, "Alan-RIP-Dpi Full Logs", onExportFullLogs())
                }
                AlanNeuIconButton(Icons.Outlined.ContentCopy, p.textPrimary, 36.dp) {
                    copyPlainTextToClipboard(context, "AlanLogs", onExportFullLogs())
                    feedback = "کل لاگ‌ها کپی شد."
                }
                AlanNeuIconButton(Icons.Outlined.DeleteOutline, p.danger, 36.dp) {
                    onClearLogs()
                    feedback = "لاگ‌ها پاک شدند."
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf("ALL" to "همه", "NET" to "شبکه/سوکت", "XRAY" to "Xray", "DNS" to "DNS/حافظه", "LAB" to "متد/تست", "ERR" to "خطاها").forEach { (key, title) ->
                val sel = activeFilter == key
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(31.dp)
                        .then(if (sel) Modifier.alanGlassCard(highlighted = true, cornerRadius = 10.dp) else Modifier.alanNeuInset(cornerRadius = 10.dp))
                        .clickable { activeFilter = key }
                        .padding(horizontal = 11.dp)
                ) {
                    Text(title, color = if (sel) p.accentGold else p.textSecondary, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 10.5.sp)
                }
            }
        }

        AlanSheetTextField("جستجو در لاگ‌ها (IP، پورت، VEC، Xray، MemGuard...)", searchQuery, { searchQuery = it }, ltr = false)

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(bottom = 76.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .alanNeuInset(cornerRadius = 16.dp)
                .padding(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text("لاگی در این دسته وجود ندارد.", color = p.textMuted, fontSize = 11.5.sp, modifier = Modifier.padding(12.dp))
                }
            } else {
                itemsIndexed(filtered, key = { idx, _ -> idx }) { _, line ->
                    val low = line.lowercase(Locale.US)
                    val lineCol = when {
                        low.contains("err") || low.contains("fail") || low.contains("timeout") || low.contains("خطا") || low.contains("رد شد") -> p.danger
                        low.contains("تایید") || low.contains("memguard") || low.contains("proxymode") || low.contains("verified") -> p.emerald
                        low.contains("vec-") || low.contains("dpi-lab") -> p.accentGold
                        else -> p.textSecondary
                    }
                    LtrContent {
                        Text(text = line, color = lineCol, fontSize = 10.sp, lineHeight = 14.sp)
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
    var mode by remember(currentMode) {
        mutableStateOf(
            currentMode.uppercase(Locale.US).let {
                if (it in setOf("ALL", "INCLUDE", "EXCLUDE")) it else "ALL"
            }
        )
    }
    var showSystemApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSet by remember(selectedPackagesCsv) {
        mutableStateOf(
            selectedPackagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        )
    }

    LaunchedEffect(showSystemApps) {
        onRequestLoadApps(showSystemApps)
    }

    val filteredApps = remember(installedApps, searchQuery, selectedSet) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        installedApps.filter { app ->
            q.isEmpty() ||
                app.appName.lowercase(Locale.ROOT).contains(q) ||
                app.packageName.lowercase(Locale.ROOT).contains(q)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = p.surfaceBg,
                shadowElevation = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "تفکیک برنامه‌ها (Per-App Proxy)",
                                color = p.accentGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${selectedSet.size} برنامه انتخاب شده",
                                color = p.textMuted,
                                fontSize = 10.5.sp
                            )
                        }
                        AlanNeuIconButton(Icons.Outlined.Close, p.textSecondary, 32.dp, onClick = onDismiss)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanNeuInset(cornerRadius = 13.dp)
                            .padding(4.dp)
                    ) {
                        listOf(
                            "ALL" to "همه برنامه‌ها",
                            "INCLUDE" to "فقط انتخابی‌ها",
                            "EXCLUDE" to "عبور مستقیم"
                        ).forEach { (mKey, mTitle) ->
                            val sel = mode == mKey
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .then(
                                        if (sel) {
                                            Modifier.alanGlassCard(highlighted = true, cornerRadius = 10.dp)
                                        } else {
                                            Modifier.clip(RoundedCornerShape(10.dp))
                                        }
                                    )
                                    .clickable { mode = mKey }
                            ) {
                                Text(
                                    text = mTitle,
                                    color = if (sel) p.emerald else p.textSecondary,
                                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanNeuInset(cornerRadius = 11.dp)
                            .clickable { showSystemApps = !showSystemApps }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "نمایش برنامه‌های سیستمی اندروید",
                            color = p.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        AlanPillSwitch(checked = showSystemApps, onCheckedChange = { showSystemApps = it })
                    }

                    AlanSheetTextField(
                        label = "جستجوی نام برنامه یا پکیج...",
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        ltr = false
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        itemsIndexed(filteredApps, key = { _, item -> item.packageName }) { _, app ->
                            val isChecked = app.packageName in selectedSet
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isChecked) {
                                            Modifier.alanNeuInset(cornerRadius = 12.dp, accented = true)
                                        } else {
                                            Modifier.alanGlassCard(cornerRadius = 12.dp)
                                        }
                                    )
                                    .clickable {
                                        selectedSet = if (isChecked) selectedSet - app.packageName else selectedSet + app.packageName
                                    }
                                    .padding(horizontal = 11.dp, vertical = 9.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(21.dp)
                                            .then(
                                                if (isChecked) {
                                                    Modifier.clip(RoundedCornerShape(6.dp)).background(p.emerald)
                                                } else {
                                                    Modifier.alanNeuInset(cornerRadius = 6.dp)
                                                }
                                            )
                                    ) {
                                        if (isChecked) {
                                            Icon(Icons.Outlined.Check, null, tint = Color(0xFF07130E), modifier = Modifier.size(14.dp))
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            color = p.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                        LtrContent {
                                            Text(
                                                text = app.packageName,
                                                color = p.textMuted,
                                                fontSize = 9.5.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .alanNeuInset(cornerRadius = 11.dp)
                                .clickable { onDismiss() }
                        ) {
                            Text("انصراف", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.35f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.emerald)
                                .clickable { onSave(mode, selectedSet) }
                        ) {
                            Text(
                                text = "ذخیره و اعمال (${selectedSet.size})",
                                color = Color(0xFF07130E),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
