package com.alan.ripdpi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

@Composable
fun CleanIpScannerScreen(
    profiles: List<ProfileItem>,
    settings: EngineSettings,
    cleanIpHits: List<CleanIpHit>,
    cleanIpState: CleanIpScanState,
    onStartScan: (sourceMode: String, customRanges: String, port: Int, maxResults: Int) -> Unit,
    onStopScan: () -> Unit,
    onRefreshGithubSubnets: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onUpdateSubnetsUrl: (String) -> Unit,
    onApplyOrCloneIps: (
        ips: List<String>,
        targetMode: String,
        selectedIndices: List<Int>,
        cloneMode: Boolean,
        clonesPerConfig: Int
    ) -> Unit
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current

    var sourceMode by rememberSaveable { mutableStateOf("github") }
    var customRangesText by rememberSaveable {
        mutableStateOf("172.64.154.0/24\n104.18.32.0/24\n188.114.96.0/24")
    }
    var portText by rememberSaveable { mutableStateOf("443") }
    var maxHitsText by rememberSaveable { mutableStateOf("20") }
    var feedback by remember { mutableStateOf("") }
    var showApplyModal by remember { mutableStateOf(false) }
    var singleQuickIp by remember { mutableStateOf<String?>(null) }

    val checkedIps = remember { mutableStateListOf<String>() }
    val animPct by animateFloatAsState(
        targetValue = (cleanIpState.percent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(220),
        label = "cleanIpPct"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 13.dp)
                .padding(4.dp)
        ) {
            listOf(
                "github" to "فایل گیت‌هاب",
                "default" to "ساب‌نت پیش‌فرض",
                "custom" to "رنج دلخواه"
            ).forEach { (key, title) ->
                val sel = sourceMode == key
                Surface(
                    onClick = { sourceMode = key },
                    shape = RoundedCornerShape(9.dp),
                    color = if (sel) p.accentGold else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = title,
                            color = if (sel) p.onAccent else p.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 15.dp)
                .padding(12.dp)
        ) {
            when (sourceMode) {
                "github" -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "دریافت خودکار از فایل ساب‌نت گیت‌هاب (${cleanIpState.loadedSubnetsCount} رنج)",
                                color = p.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            LtrContent {
                                Text(
                                    text = settings.subnetsRepoUrl,
                                    color = p.textMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            onClick = onRefreshGithubSubnets,
                            shape = RoundedCornerShape(9.dp),
                            color = p.subtleBg,
                            border = BorderStroke(1.dp, p.cardBorder)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Outlined.Refresh, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("بروزرسانی", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }
                "custom" -> {
                    AlanSheetTextField(
                        label = "رنج‌های CIDR یا بازه IP (هر خط یک رنج)",
                        value = customRangesText,
                        onValueChange = { customRangesText = it },
                        ltr = true,
                        singleLine = false
                    )
                }
                else -> {
                    Text(
                        text = "استفاده از ساب‌نت‌های منتخب لبه کلادفلر (/24) با تست ضد فیلتر TLS-BIO",
                        color = p.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AlanSheetTextField(
                    label = "پورت (443/8443)",
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                AlanSheetTextField(
                    label = "تعداد هدف",
                    value = maxHitsText,
                    onValueChange = { maxHitsText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(0.9f)
                )
                Surface(
                    onClick = {
                        if (cleanIpState.isRunning) {
                            onStopScan()
                        } else {
                            checkedIps.clear()
                            onStartScan(
                                sourceMode,
                                customRangesText,
                                portText.toIntOrNull()?.coerceIn(1, 65535) ?: 443,
                                maxHitsText.toIntOrNull()?.coerceIn(5, 50) ?: 20
                            )
                        }
                    },
                    shape = RoundedCornerShape(11.dp),
                    color = if (cleanIpState.isRunning) p.danger else p.accentGold,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = if (cleanIpState.isRunning) "توقف" else "شروع اسکن",
                        color = if (cleanIpState.isRunning) Color.White else p.onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }

            if (cleanIpState.isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(p.subtleBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animPct.coerceAtLeast(0.05f))
                            .height(4.dp)
                            .background(p.accentGold)
                    )
                }
            }

            Text(
                text = feedback.ifBlank { cleanIpState.summary },
                color = if (feedback.isNotBlank()) p.emerald else p.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (cleanIpHits.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    onClick = {
                        val targetList = if (checkedIps.isNotEmpty()) checkedIps.toList() else cleanIpHits.map { it.ip }
                        copyPlainTextToClipboard(context, "CleanIPs", targetList.joinToString("\n"))
                        feedback = "${targetList.size} آی‌پی تمیز در کلیپ‌بورد کپی شد."
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = p.subtleBg,
                    border = BorderStroke(1.dp, p.cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 9.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (checkedIps.isNotEmpty()) "کپی انتخابی (${checkedIps.size})" else "کپی همه (${cleanIpHits.size})",
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    onClick = {
                        singleQuickIp = null
                        showApplyModal = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = p.accentGold,
                    modifier = Modifier.weight(1.3f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 9.dp)
                    ) {
                        Icon(Icons.Outlined.Bolt, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "اعمال و کلون روی کانفیگ‌ها",
                            color = p.onAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(cleanIpHits, key = { _, item -> item.ip }) { rankIdx, hit ->
                val isChecked = hit.ip in checkedIps
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alanGlassCard(highlighted = rankIdx == 0, cornerRadius = 13.dp)
                        .clickable {
                            if (isChecked) checkedIps.remove(hit.ip) else checkedIps.add(hit.ip)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                if (it) {
                                    if (hit.ip !in checkedIps) checkedIps.add(hit.ip)
                                } else {
                                    checkedIps.remove(hit.ip)
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = p.accentGold,
                                checkmarkColor = p.onAccent,
                                uncheckedColor = p.textMuted
                            )
                        )
                        LtrContent {
                            Column {
                                Text(
                                    text = "${hit.ip}:${hit.port}",
                                    color = p.textPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "${hit.colo} • ${hit.subnet}",
                                    color = p.textMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LtrContent {
                            Text(
                                text = "${hit.latencyMs} ms",
                                color = p.emerald,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                        Surface(
                            onClick = {
                                copyPlainTextToClipboard(context, "CleanIP", hit.ip)
                                feedback = "آی‌پی ${hit.ip} کپی شد."
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = p.subtleBg
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Outlined.ContentCopy, null, tint = p.textSecondary, modifier = Modifier.size(15.dp))
                            }
                        }
                        Surface(
                            onClick = {
                                singleQuickIp = hit.ip
                                showApplyModal = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = p.accentSoftBg
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Outlined.Bolt, null, tint = p.accentGold, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApplyModal) {
        val ipsToUse = when {
            singleQuickIp != null -> listOf(singleQuickIp!!)
            checkedIps.isNotEmpty() -> checkedIps.toList()
            else -> cleanIpHits.map { it.ip }
        }
        ApplyCleanIpToRepoDialog(
            ips = ipsToUse,
            profiles = profiles,
            onDismiss = { showApplyModal = false },
            onConfirm = { targetMode, selIndices, cloneMode, clonesCount ->
                showApplyModal = false
                onApplyOrCloneIps(ipsToUse, targetMode, selIndices, cloneMode, clonesCount)
                feedback = if (cloneMode) {
                    "کلون‌های مجهز به IP تمیز به مخزن اضافه شدند."
                } else {
                    "IP تمیز روی کانفیگ‌ها اعمال شد."
                }
            }
        )
    }
}

@Composable
private fun ApplyCleanIpToRepoDialog(
    ips: List<String>,
    profiles: List<ProfileItem>,
    onDismiss: () -> Unit,
    onConfirm: (targetMode: String, selectedIndices: List<Int>, cloneMode: Boolean, clonesPerConfig: Int) -> Unit
) {
    val p = LocalAlanPalette.current
    var targetMode by remember { mutableStateOf("single") }
    var cloneMode by remember { mutableStateOf(true) }
    var singleIdx by remember {
        mutableIntStateOf(profiles.indexOfFirst { it.isSelected }.coerceAtLeast(0))
    }
    var clonesPerCfg by remember { mutableIntStateOf(3) }
    var showMultiPicker by remember { mutableStateOf(false) }
    val multiIndices = remember { mutableStateListOf<Int>() }

    val profileOptions = remember(profiles) {
        profiles.mapIndexed { idx, item ->
            SelectOptionItem(idx.toString(), item.name, "${item.protocol} • ${item.host}")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = p.surfaceBg,
                border = BorderStroke(1.2.dp, p.glowBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "اعمال / کلون ${ips.size} آی‌پی تمیز",
                        color = p.accentGold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "single" to "یک کانفیگ",
                            "selected" to "چند کانفیگ",
                            "cf_only" to "کلادفلرها",
                            "all" to "کل مخزن"
                        ).forEach { (k, label) ->
                            val sel = targetMode == k
                            Surface(
                                onClick = { targetMode = k },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) p.accentGold else p.subtleBg,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                    Text(
                                        text = label,
                                        color = if (sel) p.onAccent else p.textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    if (targetMode == "single" && profileOptions.isNotEmpty()) {
                        AlanCustomSelectBox(
                            label = "انتخاب کانفیگ",
                            selectedValue = singleIdx.toString(),
                            options = profileOptions,
                            onSelect = { singleIdx = it.toIntOrNull() ?: 0 }
                        )
                    } else if (targetMode == "selected") {
                        Surface(
                            onClick = { showMultiPicker = true },
                            shape = RoundedCornerShape(10.dp),
                            color = p.subtleBg,
                            border = BorderStroke(1.dp, p.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    text = if (multiIndices.isEmpty()) "انتخاب کانفیگ‌ها از لیست..." else "${multiIndices.size} کانفیگ انتخاب شد (ویرایش)",
                                    color = p.accentGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(p.subtleBg)
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text("ساخت کلون مجزا (حفظ کانفیگ اصلی)", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        AlanPillSwitch(checked = cloneMode, onCheckedChange = { cloneMode = it })
                    }

                    if (cloneMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تعداد کلون به ازای هر کانفیگ:", color = p.textSecondary, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(2, 3, 5, 8).forEach { cnt ->
                                    val sel = clonesPerCfg == cnt
                                    Surface(
                                        onClick = { clonesPerCfg = cnt },
                                        shape = RoundedCornerShape(7.dp),
                                        color = if (sel) p.accentGold else p.subtleBg
                                    ) {
                                        Text(
                                            text = "$cnt",
                                            color = if (sel) p.onAccent else p.textPrimary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            color = p.subtleBg,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text("انصراف", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Surface(
                            onClick = {
                                val indices = if (targetMode == "single") listOf(singleIdx) else multiIndices.toList()
                                onConfirm(targetMode, indices, cloneMode, clonesPerCfg)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = p.accentGold,
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text("تایید و اجرا", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMultiPicker) {
        MultiProfilePickerDialog(
            profiles = profiles,
            initialSelected = multiIndices.toList(),
            onDismiss = { showMultiPicker = false },
            onConfirm = { picked: List<Int> ->
                multiIndices.clear()
                multiIndices.addAll(picked)
                showMultiPicker = false
            }
        )
    }
}

@Composable
fun MultiProfilePickerDialog(
    profiles: List<ProfileItem>,
    initialSelected: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit
) {
    val p = LocalAlanPalette.current
    var filterText by remember { mutableStateOf("") }
    val selectedSet = remember { mutableStateListOf<Int>().apply { addAll(initialSelected) } }

    val visibleItems = remember(profiles, filterText) {
        val q = filterText.trim().lowercase(Locale.ROOT)
        profiles.mapIndexedNotNull { idx, item ->
            if (q.isEmpty() || "${item.name} ${item.host} ${item.sni}".lowercase(Locale.ROOT).contains(q)) {
                idx to item
            } else null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = p.surfaceBg,
                border = BorderStroke(1.2.dp, p.glowBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(15.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "انتخاب کانفیگ‌ها (${selectedSet.size})",
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                onClick = {
                                    selectedSet.clear()
                                    selectedSet.addAll(visibleItems.map { it.first })
                                },
                                shape = RoundedCornerShape(7.dp),
                                color = p.subtleBg
                            ) {
                                Text("همه", color = p.accentGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Surface(
                                onClick = { selectedSet.clear() },
                                shape = RoundedCornerShape(7.dp),
                                color = p.subtleBg
                            ) {
                                Text("پاک کردن", color = p.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }

                    AlanSheetTextField("جستجو...", filterText, { filterText = it }, ltr = false)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 290.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        visibleItems.forEach { (idx, item) ->
                            val checked = idx in selectedSet
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (checked) p.accentSoftBg else p.subtleBg)
                                    .clickable {
                                        if (checked) selectedSet.remove(idx) else selectedSet.add(idx)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (it) {
                                            if (idx !in selectedSet) selectedSet.add(idx)
                                        } else {
                                            selectedSet.remove(idx)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = p.accentGold, checkmarkColor = p.onAccent)
                                )
                                FlagStripeBadge(item.flagStripeColors, item.countryCode, 20.dp, 13.dp)
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = item.name,
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { onConfirm(selectedSet.toList()) },
                        shape = RoundedCornerShape(10.dp),
                        color = p.accentGold,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text("ثبت انتخاب (${selectedSet.size} کانفیگ)", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
