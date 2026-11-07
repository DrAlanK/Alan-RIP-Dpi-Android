package com.alan.ripdpi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

@Composable
fun HomeTabScreen(
    telemetry: TelemetrySnapshot,
    connectionStages: List<ConnectionStageStep>,
    liveVectorProbes: List<LiveVectorProbeItem>,
    profiles: List<ProfileItem>,
    settings: EngineSettings,
    onToggleConnection: () -> Unit,
    onActivateAutoMode: (connectNow: Boolean) -> Unit,
    onImportFromClipboard: () -> Unit,
    onSelectProfile: (Int) -> Unit
) {
    val p = LocalAlanPalette.current
    val isFa = LocalAlanLang.current == AlanLang.FA
    val layoutDir = if (isFa) LayoutDirection.Rtl else LayoutDirection.Ltr

    var showServerPicker by remember { mutableStateOf(false) }
    var showLiveRouteModal by remember { mutableStateOf(false) }

    // Floating route notification toast: appears on connect, stays briefly after connected/error, then auto-dismisses
    val isErrorPhase = telemetry.uiPhase.equals("ERROR", ignoreCase = true)
    var routeToastVisible by remember { mutableStateOf(false) }

    LaunchedEffect(telemetry.isConnecting, telemetry.isConnected, isErrorPhase) {
        when {
            telemetry.isConnecting -> {
                routeToastVisible = true
            }
            telemetry.isConnected -> {
                routeToastVisible = true
                delay(4200)
                routeToastVisible = false
            }
            isErrorPhase -> {
                routeToastVisible = true
                delay(5200)
                routeToastVisible = false
            }
            else -> {
                routeToastVisible = false
            }
        }
    }

    val isPureAutoMode = settings.autoPilot || telemetry.activeProfileUid == "__SMART_AUTO__"
    val activeIdx = remember(profiles, telemetry.selectedIndex, isPureAutoMode) {
        if (isPureAutoMode) {
            -1
        } else {
            val byFlag = profiles.indexOfFirst { it.isSelected }
            when {
                byFlag >= 0 -> byFlag
                telemetry.selectedIndex in profiles.indices -> telemetry.selectedIndex
                else -> -1
            }
        }
    }
    val activeProfile = if (isPureAutoMode) null else profiles.getOrNull(activeIdx)

    val resolvedCountryCode = when {
        telemetry.isConnected && telemetry.countryCode.isNotBlank() -> telemetry.countryCode
        activeProfile != null && activeProfile.countryCode.isNotBlank() -> activeProfile.countryCode
        telemetry.countryCode.isNotBlank() -> telemetry.countryCode
        else -> "UN"
    }
    val resolvedCountryName = when {
        telemetry.isConnected && telemetry.countryName.isNotBlank() -> telemetry.countryName
        activeProfile != null && activeProfile.countryName.isNotBlank() -> activeProfile.countryName
        else -> "خودکار"
    }
    val resolvedFlagEmoji = activeProfile?.flagEmoji ?: telemetry.flagEmoji
    val resolvedStripes = when {
        telemetry.isConnected && telemetry.flagStripeColors.size >= 3 -> telemetry.flagStripeColors
        activeProfile != null && activeProfile.flagStripeColors.size >= 3 -> activeProfile.flagStripeColors
        else -> telemetry.flagStripeColors
    }
    val resolvedAccent = when {
        telemetry.isConnected && telemetry.accentPrimaryColor != 0L -> telemetry.accentPrimaryColor
        activeProfile != null && activeProfile.accentPrimaryColor != 0L -> activeProfile.accentPrimaryColor
        telemetry.accentPrimaryColor != 0L -> telemetry.accentPrimaryColor
        else -> 0xFFF5C451L
    }

    val cleanHeadline = when {
        telemetry.isConnected -> tr("متصل شد", "Connected")
        telemetry.isConnecting -> tr("در حال اتصال...", "Connecting...")
        isErrorPhase -> tr("خطا در اتصال", "Connection Failed")
        else -> tr("آماده اتصال", "Disconnected")
    }

    val cleanSubtitle = when {
        telemetry.isConnected -> tr("مدت اتصال: ${telemetry.uptimeLabel}", "Uptime: ${telemetry.uptimeLabel}")
        telemetry.isConnecting -> if (isFa) {
            telemetry.statusDetail.ifBlank { "در حال اسکن و قفل بهترین مسیر..." }
        } else {
            "Scanning & locking optimal anti-DPI route..."
        }
        isErrorPhase -> if (isFa) {
            telemetry.statusDetail.ifBlank { "مسیر برقرار نشد؛ دوباره تلاش کنید" }
        } else {
            "Tunnel failed; tap to retry"
        }
        isPureAutoMode -> tr("حالت خودکار فعال است • دکمه اتصال را بزنید", "Smart Auto active • Tap Connect")
        else -> tr("برای برقراری تونل امن دکمه اتصال را بزنید", "Tap Connect to start encrypted tunnel")
    }

    val selectorLabel = when {
        activeProfile != null -> activeProfile.name
        telemetry.isConnected -> "Alan Smart Auto"
        else -> tr("مخزن هوشمند خودکار", "Smart Auto Repository")
    }

    // Root Box: Base layout is 100% locked with ZERO scroll; Floating notification sits on top without shifting layout!
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val orbSize = (maxHeight * 0.27f).coerceIn(124.dp, 182.dp)
            val compactVertical = maxHeight < 610.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize()
            ) {
                // Top & Center Hero Section (Takes remaining height cleanly, never scrolls)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CenterPowerOrbButton(
                        isConnected = telemetry.isConnected,
                        isConnecting = telemetry.isConnecting,
                        uiPhase = telemetry.uiPhase,
                        progressPercent = telemetry.progressPercent,
                        accentColorLong = resolvedAccent,
                        onClick = onToggleConnection,
                        modifier = Modifier.size(orbSize)
                    )

                    Spacer(Modifier.height(if (compactVertical) 6.dp else 10.dp))

                    Text(
                        text = cleanHeadline,
                        color = when {
                            telemetry.isConnected -> p.emerald
                            isErrorPhase -> p.danger
                            telemetry.isConnecting -> p.accentGold
                            else -> p.textPrimary
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compactVertical) 17.sp else 19.sp,
                        maxLines = 1
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = telemetry.isConnecting || telemetry.isConnected || isErrorPhase) {
                                showLiveRouteModal = true
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = cleanSubtitle,
                            color = p.textSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (telemetry.isConnected || telemetry.isConnecting) {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Outlined.Route,
                                contentDescription = null,
                                tint = p.accentGold,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(if (compactVertical) 10.dp else 16.dp))

                    // Control Bar: Exact 44.dp matched heights for Auto Mode Button & Server Selector Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(44.dp)
                                .then(
                                    if (isPureAutoMode) {
                                        Modifier
                                            .alanNeuInset(13.dp)
                                            .border(1.2.dp, p.accentGold, RoundedCornerShape(13.dp))
                                    } else {
                                        Modifier.alanGlassCard(cornerRadius = 13.dp)
                                    }
                                )
                                .clickable {
                                    if (!isPureAutoMode) {
                                        onActivateAutoMode(false)
                                    } else if (!telemetry.isConnected && !telemetry.isConnecting) {
                                        onActivateAutoMode(true)
                                    }
                                }
                                .padding(horizontal = 13.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoFixHigh,
                                    contentDescription = null,
                                    tint = if (isPureAutoMode) p.accentGold else p.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = tr("حالت خودکار", "Auto Mode"),
                                    color = if (isPureAutoMode) p.accentGold else p.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .alanGlassCard(cornerRadius = 13.dp)
                                .clickable { showServerPicker = true }
                                .padding(horizontal = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (activeProfile != null) {
                                    FlagStripeBadge(
                                        stripeColors = activeProfile.flagStripeColors,
                                        countryCode = activeProfile.countryCode,
                                        width = 20.dp,
                                        height = 13.dp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (telemetry.isConnected) p.emerald else p.accentGold)
                                    )
                                }
                                Text(
                                    text = selectorLabel,
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = p.accentGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Bottom Fixed Telemetry Section: Country Flags Bar + Speed Cards
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    JustifiedCountryFlagsBar(
                        originCountryName = telemetry.originCountryName,
                        destCountryCode = resolvedCountryCode,
                        destCountryName = resolvedCountryName,
                        destFlagEmoji = resolvedFlagEmoji,
                        destStripeColors = resolvedStripes,
                        destAccentLong = resolvedAccent,
                        egressIp = telemetry.egressIp,
                        pingMs = telemetry.pingMs,
                        isConnected = telemetry.isConnected,
                        isConnecting = telemetry.isConnecting
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SpeedMetricCard(
                            title = tr("دانلود", "Download"),
                            rateLabel = if (telemetry.isConnected) telemetry.rxRateLabel else "0 B/s",
                            icon = Icons.Outlined.ArrowDownward,
                            accent = p.accentGold,
                            samples = telemetry.rxHistory,
                            isConnected = telemetry.isConnected,
                            modifier = Modifier.weight(1f)
                        )
                        SpeedMetricCard(
                            title = tr("آپلود", "Upload"),
                            rateLabel = if (telemetry.isConnected) telemetry.txRateLabel else "0 B/s",
                            icon = Icons.Outlined.ArrowUpward,
                            accent = p.emerald,
                            samples = telemetry.txHistory,
                            isConnected = telemetry.isConnected,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Layer 2: Compact Floating Notification Pill (Does NOT shift or occupy layout space!)
        AnimatedVisibility(
            visible = routeToastVisible,
            enter = slideInVertically(initialOffsetY = { -it - 24 }, animationSpec = tween(260)) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { -it - 24 }, animationSpec = tween(240)) + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
        ) {
            FloatingRouteToastPill(
                telemetry = telemetry,
                stages = connectionStages,
                autoPilot = settings.autoPilot,
                onClick = { showLiveRouteModal = true },
                onDismiss = { routeToastVisible = false }
            )
        }
    }

    if (showServerPicker) {
        Dialog(onDismissRequest = { showServerPicker = false }) {
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
                                text = tr("انتخاب منبع اتصال", "Select Connection Source"),
                                color = p.accentGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .alanNeuInset(cornerRadius = 15.dp)
                                    .clickable { showServerPicker = false }
                            ) {
                                Icon(Icons.Outlined.Close, null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
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
                                    .height(40.dp)
                                    .alanGlassCard(cornerRadius = 11.dp)
                                    .clickable {
                                        showServerPicker = false
                                        onImportFromClipboard()
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.ContentPaste, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = tr("افزودن کلیپ‌بورد", "Paste Clipboard"),
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(p.accentGold)
                                    .clickable {
                                        showServerPicker = false
                                        onActivateAutoMode(true)
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Bolt, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = tr("اسکن خودکار و اتصال", "Auto Hunt & Connect"),
                                        color = p.onAccent,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Pure Auto Mode Option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isPureAutoMode) {
                                        Modifier
                                            .alanNeuInset(12.dp)
                                            .border(1.1.dp, p.accentGold, RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier.alanGlassCard(cornerRadius = 12.dp)
                                    }
                                )
                                .clickable {
                                    showServerPicker = false
                                    onActivateAutoMode(false)
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.AutoFixHigh, null, tint = p.accentGold, modifier = Modifier.size(17.dp))
                                Text(
                                    text = tr("حالت خودکار (مخزن هوشمند)", "Smart Auto Mode (Repository)"),
                                    color = if (isPureAutoMode) p.accentGold else p.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (profiles.isNotEmpty()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                profiles.forEachIndexed { idx, item ->
                                    val sel = idx == activeIdx
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (sel) {
                                                    Modifier
                                                        .alanNeuInset(11.dp)
                                                        .border(1.1.dp, p.accentGold, RoundedCornerShape(11.dp))
                                                } else {
                                                    Modifier.alanGlassCard(cornerRadius = 11.dp)
                                                }
                                            )
                                            .clickable {
                                                showServerPicker = false
                                                onSelectProfile(idx)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            FlagStripeBadge(item.flagStripeColors, item.countryCode, 22.dp, 14.dp)
                                            Text(
                                                text = item.name,
                                                color = if (sel) p.accentGold else p.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        LtrContent {
                                            Text(
                                                text = if (item.pingMs > 0) "${item.pingMs} ms" else item.protocol,
                                                color = if (item.pingMs in 1..280) p.emerald else p.textMuted,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLiveRouteModal) {
        LiveRouteTimelineDialog(
            stages = connectionStages,
            probes = liveVectorProbes,
            onDismiss = { showLiveRouteModal = false }
        )
    }
}

@Composable
private fun FloatingRouteToastPill(
    telemetry: TelemetrySnapshot,
    stages: List<ConnectionStageStep>,
    autoPilot: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalAlanPalette.current
    val isFa = LocalAlanLang.current == AlanLang.FA
    val isError = telemetry.uiPhase.equals("ERROR", ignoreCase = true)

    val animPct by animateFloatAsState(
        targetValue = when {
            telemetry.isConnected -> 1f
            telemetry.isConnecting -> (telemetry.progressPercent.coerceIn(8, 98)) / 100f
            else -> 1f
        },
        animationSpec = tween(220),
        label = "toastPct"
    )

    val activeStage = stages.firstOrNull { it.state == "ACTIVE" }
        ?: stages.lastOrNull { it.state == "DONE" }

    val statusTint = when {
        telemetry.isConnected -> p.emerald
        telemetry.isConnecting -> p.accentGold
        else -> p.danger
    }

    val titleText = when {
        telemetry.isConnected -> tr("تونل امن متصل شد", "Secure Tunnel Active")
        telemetry.isConnecting -> if (isFa) {
            activeStage?.title ?: "در حال برقراری مسیر..."
        } else {
            "Step ${telemetry.currentStageIndex.coerceAtLeast(1)}/${telemetry.totalStages} • Connecting..."
        }
        else -> tr("توقف در مسیر اتصال", "Connection Interrupted")
    }

    val badgeLabel = when {
        telemetry.isConnected -> telemetry.activeVectorId.ifBlank { "TUN" }
        telemetry.isConnecting && telemetry.progressPercent > 0 -> "${telemetry.progressPercent}%"
        autoPilot -> "Auto"
        else -> telemetry.activeVectorId.ifBlank { "DPI" }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = p.surfaceBg.copy(alpha = 0.97f),
        border = BorderStroke(1.2.dp, statusTint.copy(alpha = 0.55f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .widthIn(min = 210.dp, max = 305.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusTint)
                )

                Text(
                    text = titleText,
                    color = p.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Box(
                    modifier = Modifier
                        .alanNeuInset(cornerRadius = 8.dp)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    LtrContent {
                        Text(
                            text = badgeLabel,
                            color = statusTint,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.5.sp
                        )
                    }
                }

                if (!telemetry.isConnecting) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = p.textMuted,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onDismiss() }
                    )
                }
            }

            // Sleek thin progress line along bottom edge of notification pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(p.subtleBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isError) 1f else animPct)
                        .height(2.5.dp)
                        .background(statusTint)
                )
            }
        }
    }
}

@Composable
private fun LiveRouteTimelineDialog(
    stages: List<ConnectionStageStep>,
    probes: List<LiveVectorProbeItem>,
    onDismiss: () -> Unit
) {
    val p = LocalAlanPalette.current
    val layoutDir = if (LocalAlanLang.current == AlanLang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .alanNeuInset(cornerRadius = 11.dp)
                            ) {
                                Icon(Icons.Outlined.Route, null, tint = p.accentGold, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = tr("جزئیات مسیر اتصال", "Live Connection Timeline"),
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = tr("وضعیت مرحله‌به‌مرحله تونل", "Step-by-step tunnel diagnostics"),
                                    color = p.textMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

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

                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        stages.forEach { step ->
                            val (statusLabel, dotCol) = when (step.state) {
                                "DONE" -> tr("انجام شد", "Done") to p.emerald
                                "ACTIVE" -> tr("در حال اجرا", "Active") to p.accentGold
                                "FAILED" -> tr("ناموفق", "Failed") to p.danger
                                "SKIPPED" -> tr("رد شد", "Skipped") to p.textMuted
                                else -> tr("در انتظار", "Pending") to p.textMuted
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (step.state == "ACTIVE") {
                                            Modifier
                                                .alanNeuInset(12.dp)
                                                .border(1.dp, p.accentGold, RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier.alanGlassCard(cornerRadius = 12.dp)
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotCol)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = step.title,
                                            color = p.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (step.subtitle.isNotBlank()) {
                                            Text(
                                                text = step.subtitle,
                                                color = p.textMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(statusLabel, color = dotCol, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    if (step.badgeText.isNotBlank()) {
                                        LtrContent {
                                            Text(
                                                text = step.badgeText,
                                                color = p.textSecondary,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val hitProbes = probes.filter { it.state == "PASSED" || it.state == "QUALIFIED" }
                        if (hitProbes.isNotEmpty()) {
                            Text(
                                text = tr("متدهای تاییدشده: ", "Verified Vectors: ") +
                                    hitProbes.joinToString(" , ") { "${it.vectorId} (${it.latencyMs}ms)" },
                                color = p.emerald,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedMetricCard(
    title: String,
    rateLabel: String,
    icon: ImageVector,
    accent: Color,
    samples: List<Float>,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val p = LocalAlanPalette.current
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .alanGlassCard(highlighted = isConnected, cornerRadius = 15.dp, accentBorderColor = accent.copy(alpha = 0.55f))
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = p.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                LtrContent {
                    Text(
                        text = rateLabel,
                        color = p.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        maxLines = 1
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(31.dp)
                    .alanNeuInset(cornerRadius = 16.dp)
            ) {
                Icon(icon, title, tint = accent, modifier = Modifier.size(16.dp))
            }
        }

        LiveSparklineCanvas(
            samples = samples,
            isConnected = isConnected,
            lineColor = accent,
            modifier = Modifier.height(19.dp)
        )
    }
}

@Composable
fun ScannerTabScreen(
    profiles: List<ProfileItem>,
    scanRows: List<ScanResultRow>,
    scanState: ScanUiState,
    forcedTargetIndex: Int?,
    onConsumedForcedTarget: () -> Unit,
    onStartMultiMethodScan: (scopeMode: String, selectedIndices: List<Int>, autoConnect: Boolean, useCleanIp: Boolean) -> Unit,
    onStopScan: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onApplyVectorSingle: (vectorId: String, targetIndex: Int, endpoint: String) -> Unit,
    onApplyVectorAndConnect: (vectorId: String, targetIndex: Int, endpoint: String) -> Unit
) {
    val p = LocalAlanPalette.current
    var scopeMode by rememberSaveable { mutableStateOf("single") }
    var autoConnectFirst by rememberSaveable { mutableStateOf(false) }
    var useCleanIpInScan by rememberSaveable { mutableStateOf(false) }
    var showMultiPicker by remember { mutableStateOf(false) }
    val selectedMultiIndices = remember { mutableStateListOf<Int>() }

    val defaultSelectedIdx = remember(profiles) {
        profiles.indexOfFirst { it.isSelected }.let { if (it >= 0) it else 0 }
    }
    var singleTargetIdx by remember(defaultSelectedIdx) { mutableIntStateOf(defaultSelectedIdx) }

    LaunchedEffect(forcedTargetIndex) {
        if (forcedTargetIndex != null && forcedTargetIndex >= 0) {
            scopeMode = "single"
            singleTargetIdx = forcedTargetIndex
            onConsumedForcedTarget()
        }
    }

    val profileOptions = remember(profiles) {
        profiles.mapIndexed { idx, prof ->
            SelectOptionItem(
                value = idx.toString(),
                label = prof.name,
                subtitle = "${prof.protocol} • ${prof.host}:${prof.port}"
            )
        }
    }

    val animScanPct by animateFloatAsState(
        targetValue = (scanState.percent.coerceIn(5, 100)) / 100f,
        animationSpec = tween(220),
        label = "scanPct"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Segmented Inset Control
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 14.dp)
                .padding(5.dp)
        ) {
            listOf(
                "single" to tr("یک کانفیگ", "Single"),
                "selected" to tr("چند کانفیگ", "Selected"),
                "all" to tr("کل کانفیگ‌ها", "All Configs")
            ).forEach { (key, label) ->
                val sel = scopeMode == key
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
                        .clickable { scopeMode = key }
                ) {
                    Text(
                        text = label,
                        color = if (sel) p.accentGold else p.textSecondary,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // Scanner Configuration Card
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 16.dp)
                .padding(14.dp)
        ) {
            when (scopeMode) {
                "single" -> {
                    if (profiles.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tr("کانفیگی در لیست نیست:", "No configs in list:"),
                                color = p.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(36.dp)
                                    .alanGlassCard(cornerRadius = 10.dp)
                                    .clickable { onImportFromClipboard() }
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = tr("افزودن کلیپ‌بورد", "Paste Clipboard"),
                                    color = p.accentGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    } else {
                        AlanCustomSelectBox(
                            label = tr("کانفیگ هدف", "Target Config"),
                            selectedValue = singleTargetIdx.coerceIn(profiles.indices).toString(),
                            options = profileOptions,
                            onSelect = { singleTargetIdx = it.toIntOrNull() ?: 0 }
                        )
                    }
                }

                "selected" -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanNeuInset(cornerRadius = 12.dp)
                            .clickable { showMultiPicker = true }
                            .padding(horizontal = 13.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (selectedMultiIndices.isEmpty()) {
                                tr("انتخاب کانفیگ‌ها از لیست (${profiles.size})", "Pick configs from list (${profiles.size})")
                            } else {
                                tr("${selectedMultiIndices.size} کانفیگ انتخاب شد", "${selectedMultiIndices.size} configs selected")
                            },
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = tr("انتخاب", "Select"),
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                else -> {
                    Text(
                        text = if (profiles.isNotEmpty()) {
                            tr("اسکن متد روی تمام ${profiles.size} کانفیگ موجود در مخزن", "Scan methods across all ${profiles.size} saved configs")
                        } else {
                            tr("اسکن مستقیم متدها روی سرورهای مخزن هوشمند", "Scan methods directly on Smart Auto repository")
                        },
                        color = p.textSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .alanNeuInset(cornerRadius = 11.dp)
                    .clickable { autoConnectFirst = !autoConnectFirst }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = tr("اتصال خودکار به سریع‌ترین متد", "Auto-connect to fastest verified method"),
                    color = p.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                AlanPillSwitch(checked = autoConnectFirst, onCheckedChange = { autoConnectFirst = it })
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .alanNeuInset(cornerRadius = 11.dp)
                    .clickable { useCleanIpInScan = !useCleanIpInScan }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = tr("تست همراه با IP تمیز کلادفلر", "Inject Cloudflare Clean IP during test"),
                    color = p.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                AlanPillSwitch(checked = useCleanIpInScan, onCheckedChange = { useCleanIpInScan = it })
            }

            if (scanState.isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .alanNeuInset(cornerRadius = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animScanPct)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(p.accentGold)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (scanState.isRunning) p.danger else p.accentGold)
                        .clickable {
                            if (scanState.isRunning) {
                                onStopScan()
                            } else {
                                val indices = when (scopeMode) {
                                    "single" -> listOf(singleTargetIdx)
                                    "selected" -> selectedMultiIndices.toList()
                                    else -> profiles.indices.toList()
                                }
                                onStartMultiMethodScan(scopeMode, indices, autoConnectFirst, useCleanIpInScan)
                            }
                        }
                        .padding(horizontal = 18.dp)
                ) {
                    Text(
                        text = if (scanState.isRunning) tr("توقف", "Stop") else tr("شروع اسکن متد", "Start Scan"),
                        color = if (scanState.isRunning) Color.White else p.onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp
                    )
                }

                Text(
                    text = scanState.summary,
                    color = p.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Results List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(
                items = scanRows,
                key = { idx, row -> "$idx-${row.targetIndex}-${row.itemId}" }
            ) { _, row ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alanGlassCard(highlighted = row.rank == 1, cornerRadius = 15.dp)
                        .padding(13.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "#${row.rank}  ${row.title.ifBlank { row.itemId }}",
                            color = if (row.rank == 1) p.accentGold else p.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        LtrContent {
                            Text(
                                text = "${row.tunnelMs} ms",
                                color = p.emerald,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
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
                                .height(38.dp)
                                .alanNeuInset(cornerRadius = 10.dp)
                                .clickable { onApplyVectorSingle(row.itemId, row.targetIndex, row.endpoint) }
                        ) {
                            Text(
                                text = tr("ثبت متد", "Save Method"),
                                color = p.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.accentGold)
                                .clickable { onApplyVectorAndConnect(row.itemId, row.targetIndex, row.endpoint) }
                        ) {
                            Text(
                                text = tr("ثبت و اتصال", "Save & Connect"),
                                color = p.onAccent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
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
            initialSelected = selectedMultiIndices.toList(),
            onDismiss = { showMultiPicker = false },
            onConfirm = { picked ->
                selectedMultiIndices.clear()
                selectedMultiIndices.addAll(picked)
                showMultiPicker = false
            }
        )
    }
}