package com.alan.ripdpi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
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
    onUpdateSubnetsUrl: (String) -> Unit,
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
    val isFa = LocalAlanLang.current == AlanLang.FA

    var sourceMode by rememberSaveable { mutableStateOf("github") }
    var customRangesText by rememberSaveable {
        mutableStateOf("104.16.0.0/20\n172.64.0.0/20\n188.114.96.0/22")
    }
    var portText by rememberSaveable { mutableStateOf("443") }
    var maxHitsText by rememberSaveable { mutableStateOf("20") }
    var editingRepoUrl by remember { mutableStateOf(false) }
    var repoUrlDraft by remember(settings.subnetsRepoUrl) { mutableStateOf(settings.subnetsRepoUrl) }
    var feedback by remember { mutableStateOf("") }
    var showApplyModal by remember { mutableStateOf(false) }
    var singleQuickIp by remember { mutableStateOf<String?>(null) }

    val checkedIps = remember { mutableStateListOf<String>() }
    val animPct by animateFloatAsState(
        targetValue = (cleanIpState.percent.coerceIn(5, 100)) / 100f,
        animationSpec = tween(220),
        label = "cleanIpPct"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Source Mode Segmented Control
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 14.dp)
                .padding(5.dp)
        ) {
            listOf(
                "github" to tr("فایل گیت‌هاب", "GitHub"),
                "default" to tr("ساب‌نت پیش‌فرض", "Default"),
                "custom" to tr("رنج دلخواه", "Custom")
            ).forEach { (key, title) ->
                val sel = sourceMode == key
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .then(
                            if (sel) {
                                Modifier.alanGlassCard(
                                    highlighted = true,
                                    cornerRadius = 10.dp,
                                    accentBorderColor = p.accentGold
                                )
                            } else {
                                Modifier.clip(RoundedCornerShape(10.dp))
                            }
                        )
                        .clickable { sourceMode = key }
                ) {
                    Text(
                        text = title,
                        color = if (sel) p.accentGold else p.textSecondary,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // Scanner Control Card
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 16.dp)
                .padding(14.dp)
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
                                text = tr(
                                    "فایل ساب‌نت گیت‌هاب (${cleanIpState.loadedSubnetsCount} رنج)",
                                    "GitHub Subnets (${cleanIpState.loadedSubnetsCount} ranges)"
                                ),
                                color = p.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            LtrContent {
                                Text(
                                    text = settings.subnetsRepoUrl,
                                    color = p.textMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .alanNeuInset(cornerRadius = 10.dp)
                                    .clickable { editingRepoUrl = !editingRepoUrl }
                            ) {
                                Icon(Icons.Outlined.Edit, null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(34.dp)
                                    .alanGlassCard(cornerRadius = 10.dp)
                                    .clickable { onRefreshGithubSubnets() }
                                    .padding(horizontal = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Refresh, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = tr("بروزرسانی", "Sync"),
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (editingRepoUrl) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AlanSheetTextField(
                                label = "Subnets URL",
                                value = repoUrlDraft,
                                onValueChange = { repoUrlDraft = it },
                                ltr = true,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(p.accentGold)
                                    .clickable {
                                        if (repoUrlDraft.isNotBlank()) {
                                            onUpdateSubnetsUrl(repoUrlDraft.trim())
                                            editingRepoUrl = false
                                            feedback = if (isFa) "لینک ساب‌نت ذخیره شد." else "Subnets URL updated."
                                        }
                                    }
                                    .padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = tr("ثبت", "Save"),
                                    color = p.onAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                "custom" -> {
                    AlanSheetTextField(
                        label = tr("رنج‌های CIDR یا IP (هر خط یک رنج)", "CIDR or IP ranges (one per line)"),
                        value = customRangesText,
                        onValueChange = { customRangesText = it },
                        ltr = true,
                        singleLine = false
                    )
                }

                else -> {
                    Text(
                        text = tr(
                            "استفاده از ۲۲ ساب‌نت منتخب کلادفلر برای اپراتورهای همراه و ثابت",
                            "Using 22 curated Cloudflare subnets optimized for mobile & fiber ISPs"
                        ),
                        color = p.textSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }

            // Aligned 48.dp Input + Action Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AlanSheetTextField(
                    label = tr("پورت", "Port"),
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                AlanSheetTextField(
                    label = tr("تعداد هدف", "Max IPs"),
                    value = maxHitsText,
                    onValueChange = { maxHitsText = it.filter(Char::isDigit) },
                    ltr = true,
                    numeric = true,
                    modifier = Modifier.weight(0.95f)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (cleanIpState.isRunning) p.danger else p.accentGold)
                        .clickable {
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
                        }
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = if (cleanIpState.isRunning) tr("توقف", "Stop") else tr("شروع اسکن", "Scan"),
                        color = if (cleanIpState.isRunning) Color.White else p.onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp
                    )
                }
            }

            if (cleanIpState.isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .alanNeuInset(cornerRadius = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animPct)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(p.accentGold)
                    )
                }
            }

            Text(
                text = feedback.ifBlank { cleanIpState.summary },
                color = if (feedback.isNotBlank()) p.emerald else p.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Batch Actions Strip
        if (cleanIpHits.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .alanGlassCard(cornerRadius = 11.dp)
                        .clickable {
                            val targetList = if (checkedIps.isNotEmpty()) checkedIps.toList() else cleanIpHits.map { it.ip }
                            copyPlainTextToClipboard(context, "CleanIPs", targetList.joinToString("\n"))
                            feedback = if (isFa) {
                                "${targetList.size} آی‌پی تمیز کپی شد."
                            } else {
                                "Copied ${targetList.size} clean IPs."
                            }
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (checkedIps.isNotEmpty()) {
                                tr("کپی انتخابی (${checkedIps.size})", "Copy (${checkedIps.size})")
                            } else {
                                tr("کپی همه (${cleanIpHits.size})", "Copy All (${cleanIpHits.size})")
                            },
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(p.accentGold)
                        .clickable {
                            singleQuickIp = null
                            showApplyModal = true
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bolt, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = tr("اعمال و کلون روی کانفیگ‌ها", "Apply / Clone to Configs"),
                            color = p.onAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        // Clean IP Results List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
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
                        .alanGlassCard(highlighted = rankIdx == 0, cornerRadius = 14.dp)
                        .clickable {
                            if (isChecked) checkedIps.remove(hit.ip) else checkedIps.add(hit.ip)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Compact 22.dp Tactile Checkbox
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(22.dp)
                                .then(
                                    if (isChecked) {
                                        Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(p.accentGold)
                                    } else {
                                        Modifier.alanNeuInset(cornerRadius = 6.dp)
                                    }
                                )
                        ) {
                            if (isChecked) {
                                Icon(Icons.Outlined.Check, null, tint = p.onAccent, modifier = Modifier.size(14.dp))
                            }
                        }

                        LtrContent {
                            Column {
                                Text(
                                    text = "${hit.ip}:${hit.port}",
                                    color = p.textPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${hit.colo} • ${hit.subnet}",
                                    color = p.textMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .alanNeuInset(cornerRadius = 9.dp)
                                .clickable {
                                    copyPlainTextToClipboard(context, "CleanIP", hit.ip)
                                    feedback = if (isFa) "آی‌پی ${hit.ip} کپی شد." else "Copied ${hit.ip}."
                                }
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, tint = p.textSecondary, modifier = Modifier.size(15.dp))
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .alanGlassCard(cornerRadius = 9.dp)
                                .clickable {
                                    singleQuickIp = hit.ip
                                    showApplyModal = true
                                }
                        ) {
                            Icon(Icons.Outlined.Bolt, null, tint = p.accentGold, modifier = Modifier.size(16.dp))
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
                    if (isFa) "کلون‌های مجهز به IP تمیز اضافه شدند." else "Clean IP clones added to profiles."
                } else {
                    if (isFa) "IP تمیز روی کانفیگ‌ها اعمال شد." else "Clean IP applied to target configs."
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
    val layoutDir = if (LocalAlanLang.current == AlanLang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

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
        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = p.surfaceBg,
                border = BorderStroke(1.2.dp, p.cardBorder),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr("اعمال / کلون (${ips.size} آی‌پی)", "Apply / Clone (${ips.size} IPs)"),
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .alanNeuInset(cornerRadius = 15.dp)
                                .clickable { onDismiss() }
                        ) {
                            Icon(Icons.Outlined.Close, null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanNeuInset(cornerRadius = 12.dp)
                            .padding(4.dp)
                    ) {
                        listOf(
                            "single" to tr("تکی", "Single"),
                            "selected" to tr("چندتایی", "Multi"),
                            "cf_only" to tr("کلادفلر", "CF Only"),
                            "all" to tr("همه", "All")
                        ).forEach { (k, label) ->
                            val sel = targetMode == k
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .then(
                                        if (sel) {
                                            Modifier.alanGlassCard(
                                                highlighted = true,
                                                cornerRadius = 9.dp,
                                                accentBorderColor = p.accentGold
                                            )
                                        } else {
                                            Modifier.clip(RoundedCornerShape(9.dp))
                                        }
                                    )
                                    .clickable { targetMode = k }
                            ) {
                                Text(
                                    text = label,
                                    color = if (sel) p.accentGold else p.textSecondary,
                                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 10.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (targetMode == "single" && profileOptions.isNotEmpty()) {
                        AlanCustomSelectBox(
                            label = tr("انتخاب کانفیگ", "Target Config"),
                            selectedValue = singleIdx.toString(),
                            options = profileOptions,
                            onSelect = { singleIdx = it.toIntOrNull() ?: 0 }
                        )
                    } else if (targetMode == "selected") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .alanNeuInset(cornerRadius = 11.dp)
                                .clickable { showMultiPicker = true }
                        ) {
                            Text(
                                text = if (multiIndices.isEmpty()) {
                                    tr("انتخاب کانفیگ‌ها از لیست...", "Select configs from list...")
                                } else {
                                    tr("${multiIndices.size} کانفیگ انتخاب شد", "${multiIndices.size} configs selected")
                                },
                                color = p.accentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanNeuInset(cornerRadius = 12.dp)
                            .clickable { cloneMode = !cloneMode }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = tr("ساخت کلون مجزا (حفظ اصلی)", "Clone as new configs (keep original)"),
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        AlanPillSwitch(checked = cloneMode, onCheckedChange = { cloneMode = it })
                    }

                    if (cloneMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tr("تعداد کلون هر کانفیگ:", "Clones per config:"),
                                color = p.textSecondary,
                                fontSize = 11.5.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(2, 3, 5, 8).forEach { cnt ->
                                    val sel = clonesPerCfg == cnt
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .height(32.dp)
                                            .width(36.dp)
                                            .then(
                                                if (sel) {
                                                    Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(p.accentGold)
                                                } else {
                                                    Modifier.alanNeuInset(cornerRadius = 8.dp)
                                                }
                                            )
                                            .clickable { clonesPerCfg = cnt }
                                    ) {
                                        LtrContent {
                                            Text(
                                                text = "$cnt",
                                                color = if (sel) p.onAccent else p.textPrimary,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp
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
                            Text(
                                text = tr("انصراف", "Cancel"),
                                color = p.textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.35f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.accentGold)
                                .clickable {
                                    val indices = if (targetMode == "single") listOf(singleIdx) else multiIndices.toList()
                                    onConfirm(targetMode, indices, cloneMode, clonesPerCfg)
                                }
                        ) {
                            Text(
                                text = tr("تایید و اجرا", "Confirm & Apply"),
                                color = p.onAccent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp
                            )
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
            onConfirm = { picked ->
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
    val layoutDir = if (LocalAlanLang.current == AlanLang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

    var filterText by remember { mutableStateOf("") }
    val selectedSet = remember { mutableStateListOf<Int>().apply { addAll(initialSelected) } }

    val visibleItems = remember(profiles, filterText) {
        val q = filterText.trim().lowercase(Locale.ROOT)
        profiles.mapIndexedNotNull { idx, item ->
            if (q.isEmpty() || "${item.name} ${item.host} ${item.sni}".lowercase(Locale.ROOT).contains(q)) {
                idx to item
            } else {
                null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = p.surfaceBg,
                border = BorderStroke(1.2.dp, p.cardBorder),
                shadowElevation = 10.dp,
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
                        Text(
                            text = tr("انتخاب کانفیگ (${selectedSet.size})", "Select Configs (${selectedSet.size})"),
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(28.dp)
                                    .alanGlassCard(cornerRadius = 8.dp)
                                    .clickable {
                                        selectedSet.clear()
                                        selectedSet.addAll(visibleItems.map { it.first })
                                    }
                                    .padding(horizontal = 9.dp)
                            ) {
                                Text(tr("همه", "All"), color = p.accentGold, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(28.dp)
                                    .alanNeuInset(cornerRadius = 8.dp)
                                    .clickable { selectedSet.clear() }
                                    .padding(horizontal = 9.dp)
                            ) {
                                Text(tr("هیچکدام", "None"), color = p.textSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    AlanSheetTextField(
                        label = tr("جستجو...", "Search..."),
                        value = filterText,
                        onValueChange = { filterText = it },
                        ltr = false
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        visibleItems.forEach { (idx, item) ->
                            val checked = idx in selectedSet
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (checked) {
                                            Modifier
                                                .alanNeuInset(11.dp)
                                                .border(1.dp, p.accentGold, RoundedCornerShape(11.dp))
                                        } else {
                                            Modifier.alanGlassCard(cornerRadius = 11.dp)
                                        }
                                    )
                                    .clickable {
                                        if (checked) selectedSet.remove(idx) else selectedSet.add(idx)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .then(
                                            if (checked) {
                                                Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(p.accentGold)
                                            } else {
                                                Modifier.alanNeuInset(cornerRadius = 5.dp)
                                            }
                                        )
                                ) {
                                    if (checked) {
                                        Icon(Icons.Outlined.Check, null, tint = p.onAccent, modifier = Modifier.size(13.dp))
                                    }
                                }
                                Spacer(Modifier.width(9.dp))
                                FlagStripeBadge(item.flagStripeColors, item.countryCode, 22.dp, 14.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item.name,
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            Text(tr("بستن", "Close"), color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.4f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.accentGold)
                                .clickable { onConfirm(selectedSet.toList()) }
                        ) {
                            Text(
                                text = tr("ثبت (${selectedSet.size})", "Confirm (${selectedSet.size})"),
                                color = p.onAccent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}