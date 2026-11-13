package com.alan.ripdpi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
    dohServers: List<DohServerUiItem> = emptyList(),
    dohScanState: DohScanUiState = DohScanUiState(),
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
    ) -> Unit,
    onStartDohScan: (String) -> Unit = {},
    onStopDohScan: () -> Unit = {},
    onSelectActiveDoh: (url: String, connectNow: Boolean) -> Unit = { _, _ -> },
    onAddCustomDohUrls: (String) -> Unit = {}
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current

    var activeSubTab by rememberSaveable { mutableStateOf("CLEAN_IP") }
    var dohTestDomain by rememberSaveable { mutableStateOf("github.com") }
    var customDohInput by rememberSaveable { mutableStateOf("") }
    var showAddDohModal by remember { mutableStateOf(false) }

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
        // Dual Engine Switch: رادار IP تمیز کلادفلر | اسکنر و تنظیم DoH (DNSveil)
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 15.dp)
                .padding(5.dp)
        ) {
            listOf(
                "CLEAN_IP" to "رادار IP تمیز کلادفلر",
                "DOH_VEIL" to "اسکنر و تنظیم DoH (DNSveil)"
            ).forEach { (tabKey, tabTitle) ->
                val sel = activeSubTab == tabKey
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(37.dp)
                        .then(
                            if (sel) {
                                Modifier.alanGlassCard(highlighted = true, cornerRadius = 11.dp)
                            } else {
                                Modifier.clip(RoundedCornerShape(11.dp))
                            }
                        )
                        .clickable { activeSubTab = tabKey }
                ) {
                    Text(
                        text = tabTitle,
                        color = if (sel) (if (tabKey == "DOH_VEIL") p.emerald else p.accentGold) else p.textSecondary,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        if (activeSubTab == "DOH_VEIL") {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .alanGlassCard(cornerRadius = 18.dp)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "دی‌ان‌اس امن فعال: ${dohScanState.activeDohName}",
                            color = p.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp
                        )
                        LtrContent {
                            Text(
                                text = dohScanState.activeDohUrl,
                                color = p.emerald,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(34.dp)
                            .alanGlassCard(cornerRadius = 10.dp)
                            .clickable { showAddDohModal = !showAddDohModal }
                            .padding(horizontal = 11.dp)
                    ) {
                        Text("افزودن مخزن DoH", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                if (showAddDohModal) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AlanSheetTextField(
                            label = "لینک سرور DoH (https://.../dns-query)",
                            value = customDohInput,
                            onValueChange = { customDohInput = it },
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
                                    if (customDohInput.isNotBlank()) {
                                        onAddCustomDohUrls(customDohInput)
                                        customDohInput = ""
                                        showAddDohModal = false
                                    }
                                }
                                .padding(horizontal = 14.dp)
                        ) {
                            Text("ثبت", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AlanSheetTextField(
                        label = "دامنه تست (github.com / youtube.com)",
                        value = dohTestDomain,
                        onValueChange = { dohTestDomain = it },
                        ltr = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (dohScanState.isRunning) p.danger else p.emerald)
                            .clickable {
                                if (dohScanState.isRunning) onStopDohScan() else onStartDohScan(dohTestDomain)
                            }
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = if (dohScanState.isRunning) "توقف" else "اسکن DoH",
                            color = if (dohScanState.isRunning) Color.White else Color(0xFF07130E),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp
                        )
                    }
                }

                Text(
                    text = dohScanState.summary,
                    color = p.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(dohServers, key = { _, item -> item.url }) { _, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (item.isSelected) {
                                    Modifier.alanNeuInset(cornerRadius = 15.dp, accented = true)
                                } else {
                                    Modifier.alanGlassCard(cornerRadius = 15.dp)
                                }
                            )
                            .clickable { onSelectActiveDoh(item.url, false) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.name,
                                    color = if (item.isSelected) p.accentGold else p.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.5.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .alanNeuInset(cornerRadius = 6.dp)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    LtrContent {
                                        Text(
                                            text = item.category,
                                            color = p.textMuted,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                            LtrContent {
                                Text(
                                    text = item.url,
                                    color = p.textMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (item.resolvedIp.isNotBlank()) {
                                LtrContent {
                                    Text(
                                        text = "IP: ${item.resolvedIp} (${item.statusDetail})",
                                        color = p.emerald,
                                        fontSize = 9.5.sp
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            if (item.latencyMs > 0) {
                                LtrContent {
                                    Text(
                                        text = "${item.latencyMs} ms",
                                        color = p.emerald,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (item.isSelected) p.emerald else p.accentGold)
                                    .clickable { onSelectActiveDoh(item.url, true) }
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = if (item.isSelected) "فعال" else "ست روی نت",
                                    color = p.onAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Segmented Inset Control: فایل گیت‌هاب / ساب‌نت پیش‌فرض / رنج دلخواه
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .alanNeuInset(cornerRadius = 15.dp)
                    .padding(5.dp)
            ) {
                listOf(
                    "github" to "فایل گیت‌هاب",
                    "default" to "ساب‌نت پیش‌فرض",
                    "custom" to "رنج دلخواه"
                ).forEach { (key, title) ->
                    val sel = sourceMode == key
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .then(
                                if (sel) {
                                    Modifier.alanGlassCard(highlighted = true, cornerRadius = 11.dp)
                                } else {
                                    Modifier.clip(RoundedCornerShape(11.dp))
                                }
                            )
                            .clickable { sourceMode = key }
                    ) {
                        Text(
                            text = title,
                            color = if (sel) p.accentGold else p.textSecondary,
                            fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
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
                when (sourceMode) {
                    "github" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "فایل ساب‌نت گیت‌هاب (${cleanIpState.loadedSubnetsCount} رنج فعال)",
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                LtrContent {
                                    Text(
                                        text = settings.subnetsRepoUrl,
                                        color = p.textMuted,
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AlanNeuIconButton(
                                    icon = Icons.Outlined.Edit,
                                    tint = p.textSecondary,
                                    size = 34.dp,
                                    isPressed = editingRepoUrl,
                                    onClick = { editingRepoUrl = !editingRepoUrl }
                                )
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
                                        Text("بروزرسانی", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                                    label = "لینک فایل ساب‌نت گیت‌هاب",
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
                                                feedback = "لینک فایل ساب‌نت ذخیره شد."
                                            }
                                        }
                                        .padding(horizontal = 14.dp)
                                ) {
                                    Text("ثبت", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
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
                            text = "استفاده از ۲۲ ساب‌نت منتخب کلادفلر برای اپراتورهای همراه و ثابت",
                            color = p.textSecondary,
                            fontSize = 11.5.sp
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
                            text = if (cleanIpState.isRunning) "توقف" else "شروع اسکن",
                            color = if (cleanIpState.isRunning) Color.White else p.onAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp
                        )
                    }
                }

                if (cleanIpState.isRunning) {
                    Box(modifier = Modifier.fillMaxWidth().height(6.dp).alanNeuInset(cornerRadius = 3.dp)) {
                        Box(modifier = Modifier.fillMaxWidth(animPct).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.accentGold))
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
                            .alanGlassCard(cornerRadius = 12.dp)
                            .clickable {
                                val targetList = if (checkedIps.isNotEmpty()) checkedIps.toList() else cleanIpHits.map { it.ip }
                                copyPlainTextToClipboard(context, "CleanIPs", targetList.joinToString("\n"))
                                feedback = "${targetList.size} آی‌پی تمیز در کلیپ‌بورد کپی شد."
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ContentCopy, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (checkedIps.isNotEmpty()) "کپی انتخابی (${checkedIps.size})" else "کپی همه (${cleanIpHits.size})",
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
                            .clip(RoundedCornerShape(12.dp))
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
                                text = "اعمال و کلون روی کانفیگ‌ها",
                                color = p.onAccent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }

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
                            .alanGlassCard(highlighted = rankIdx == 0, cornerRadius = 15.dp)
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
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .then(
                                        if (isChecked) {
                                            Modifier.clip(RoundedCornerShape(6.dp)).background(p.accentGold)
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
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${hit.colo} • ${hit.subnet}",
                                        color = p.textMuted,
                                        fontSize = 10.sp
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
                                        feedback = "آی‌پی ${hit.ip} کپی شد."
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
                shape = RoundedCornerShape(22.dp),
                color = p.surfaceBg,
                shadowElevation = 12.dp,
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
                            text = "اعمال / کلون ${ips.size} آی‌پی تمیز",
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        AlanNeuIconButton(Icons.Outlined.Close, p.textSecondary, 32.dp, onClick = onDismiss)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanNeuInset(cornerRadius = 12.dp)
                            .padding(4.dp)
                    ) {
                        listOf(
                            "single" to "یک کانفیگ",
                            "selected" to "چند کانفیگ",
                            "cf_only" to "کلادفلرها",
                            "all" to "کل مخزن"
                        ).forEach { (k, label) ->
                            val sel = targetMode == k
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .then(
                                        if (sel) {
                                            Modifier.alanGlassCard(highlighted = true, cornerRadius = 9.dp)
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
                                    fontSize = 10.5.sp
                                )
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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .alanNeuInset(cornerRadius = 11.dp)
                                .clickable { showMultiPicker = true }
                        ) {
                            Text(
                                text = if (multiIndices.isEmpty()) "انتخاب کانفیگ‌ها از لیست..." else "${multiIndices.size} کانفیگ انتخاب شد",
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
                            text = "ساخت کلون مجزا (حفظ کانفیگ اصلی)",
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        AlanPillSwitch(checked = cloneMode, onCheckedChange = { cloneMode = it })
                    }

                    if (cloneMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تعداد کلون به ازای هر کانفیگ:", color = p.textSecondary, fontSize = 11.5.sp)
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
                                                    Modifier.clip(RoundedCornerShape(8.dp)).background(p.accentGold)
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
                            Text("انصراف", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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
                            Text("تایید و اجرا", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
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
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = p.surfaceBg,
                shadowElevation = 12.dp,
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
                            text = "انتخاب کانفیگ‌ها (${selectedSet.size})",
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
                                Text("همه", color = p.accentGold, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(28.dp)
                                    .alanNeuInset(cornerRadius = 8.dp)
                                    .clickable { selectedSet.clear() }
                                    .padding(horizontal = 9.dp)
                            ) {
                                Text("پاک کردن", color = p.textSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    AlanSheetTextField(
                        label = "جستجو...",
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
                                            Modifier.alanNeuInset(cornerRadius = 11.dp, accented = true)
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
                                                Modifier.clip(RoundedCornerShape(5.dp)).background(p.accentGold)
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
                            Text("بستن", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                text = "ثبت انتخاب (${selectedSet.size} کانفیگ)",
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
