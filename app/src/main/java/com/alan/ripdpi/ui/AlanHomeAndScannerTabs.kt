package com.alan.ripdpi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Route
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.util.Locale
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing

@Composable
fun HomeTabScreen(
    telemetry: TelemetrySnapshot,
    connectionStages: List<ConnectionStageStep>,
    liveVectorProbes: List<LiveVectorProbeItem>,
    profiles: List<ProfileItem>,
    settings: EngineSettings,
    onToggleConnection: () -> Unit,
    onHuntBest: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onSelectProfile: (Int) -> Unit,
    onUpdateSettingsPatch: (Map<String, Any>) -> Unit
) {
    val p = LocalAlanPalette.current
    var showServerPicker by remember { mutableStateOf(false) }
    var showLiveRouteModal by remember { mutableStateOf(false) }

    val activeIdx = remember(profiles, telemetry.selectedIndex, telemetry.activeProfileUid) {
        val byFlag = profiles.indexOfFirst { it.isSelected }
        when {
            byFlag >= 0 -> byFlag
            telemetry.activeProfileUid.isNotBlank() ->
                profiles.indexOfFirst { it.uid == telemetry.activeProfileUid }
            telemetry.selectedIndex in profiles.indices -> telemetry.selectedIndex
            else -> -1
        }
    }
    val activeProfile = profiles.getOrNull(activeIdx)

    val resolvedCountryCode = telemetry.countryCode.ifBlank {
        activeProfile?.countryCode.orEmpty().ifBlank { "UN" }
    }
    val resolvedCountryName = telemetry.countryName.ifBlank {
        activeProfile?.countryName.orEmpty().ifBlank { "خودکار" }
    }
    val resolvedFlagEmoji = telemetry.flagEmoji.ifBlank {
        activeProfile?.flagEmoji.orEmpty()
    }
    val resolvedStripes = if (telemetry.flagStripeColors.size >= 3) {
        telemetry.flagStripeColors
    } else {
        activeProfile?.flagStripeColors ?: emptyList()
    }
    val resolvedAccent = if (telemetry.accentPrimaryColor != 0L) {
        telemetry.accentPrimaryColor
    } else {
        activeProfile?.accentPrimaryColor ?: 0xFFF5C451L
    }

    val cleanHeadline = when {
        telemetry.isConnected -> "متصل شد"
        telemetry.isConnecting -> "در حال اتصال..."
        telemetry.uiPhase.equals("ERROR", ignoreCase = true) -> "خطا در اتصال"
        else -> "وصل نیست"
    }

    val cleanSubtitle = when {
        telemetry.isConnected -> "زمان اتصال: ${telemetry.uptimeLabel}"
        telemetry.isConnecting -> telemetry.statusDetail.ifBlank { "در حال تست بهترین مسیر..." }
        telemetry.uiPhase.equals("ERROR", ignoreCase = true) -> telemetry.statusDetail.ifBlank { "دوباره تلاش کنید" }
        else -> "برای وصل شدن، دکمه رو بزن"
    }

    val selectorLabel = when {
        activeProfile != null -> {
            val em = activeProfile.flagEmoji.trim()
            if (em.isNotEmpty() && !activeProfile.name.contains(em)) "$em ${activeProfile.name}" else activeProfile.name
        }
        settings.autoPilot -> "Auto • خودکار"
        else -> settings.manualVectorId
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        LiveStepperHeaderCard(
            telemetry = telemetry,
            stages = connectionStages,
            autoPilot = settings.autoPilot,
            onOpenModal = { showLiveRouteModal = true }
        )

        // Center Power Ring + Clean Status + Server Selector Pill
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CenterPowerOrbButton(
                isConnected = telemetry.isConnected,
                isConnecting = telemetry.isConnecting,
                uiPhase = telemetry.uiPhase,
                progressPercent = telemetry.progressPercent,
                accentColorLong = resolvedAccent,
                onClick = onToggleConnection
            )

            Text(
                text = cleanHeadline,
                color = when {
                    telemetry.isConnected -> p.emerald
                    telemetry.uiPhase.equals("ERROR", true) -> p.danger
                    else -> p.accentGold
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )

            Text(
                text = cleanSubtitle,
                color = p.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // Clean Selector Pill (like "• انتخاب شده | Auto • خودکار <")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.cardBg)
                    .border(1.dp, p.cardBorder, RoundedCornerShape(22.dp))
                    .clickable { showServerPicker = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (telemetry.isConnected) p.emerald else p.accentGold)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "انتخاب شده",
                    color = p.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "  |  ",
                    color = p.cardBorder,
                    fontSize = 12.sp
                )
                Text(
                    text = selectorLabel,
                    color = p.accentGold,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = null,
                    tint = p.accentGold,
                    modifier = Modifier.size(16.dp)
                )
            }

        }

        Spacer(Modifier.height(14.dp))

        // Bottom Telemetry Stack: Justified Flags Bar + 2 Speed Cards + 3-Col Summary Bar
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SpeedMetricCard(
                    title = "دانلود",
                    rateLabel = if (telemetry.isConnected) telemetry.rxRateLabel else "0 B/s",
                    icon = Icons.Outlined.ArrowDownward,
                    accent = p.accentGold,
                    samples = telemetry.rxHistory,
                    isConnected = telemetry.isConnected,
                    modifier = Modifier.weight(1f)
                )
                SpeedMetricCard(
                    title = "آپلود",
                    rateLabel = if (telemetry.isConnected) telemetry.txRateLabel else "0 B/s",
                    icon = Icons.Outlined.ArrowUpward,
                    accent = p.emerald,
                    samples = telemetry.txHistory,
                    isConnected = telemetry.isConnected,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3-Column Justified Feature/Status Card (امن | سریع | پایدار)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .alanGlassCard(highlighted = false, cornerRadius = 16.dp)
                    .padding(vertical = 12.dp)
            ) {
                TripleSummaryCol(
                    icon = Icons.Outlined.Shield,
                    title = "امن",
                    subtitle = if (telemetry.isConnected) telemetry.activeVectorId else "رمزگذاری شده",
                    tint = if (telemetry.isConnected) p.emerald else p.accentGold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(p.cardBorder)
                )
                TripleSummaryCol(
                    icon = Icons.Outlined.Bolt,
                    title = "سریع",
                    subtitle = if (telemetry.isConnected && telemetry.pingMs > 0) "${telemetry.pingMs} ms" else "بهینه",
                    tint = p.accentGold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(p.cardBorder)
                )
                TripleSummaryCol(
                    icon = Icons.Outlined.Wifi,
                    title = "پایدار",
                    subtitle = if (settings.autoPilot) "خودکار فعال" else "حالت دستی",
                    tint = p.accentGold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Custom Dark-Gold Server & Quick Action Modal (No White Dropdown!)
    if (showServerPicker) {
        Dialog(onDismissRequest = { showServerPicker = false }) {
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
                            text = "انتخاب سرور و حالت اتصال",
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                onClick = {
                                    showServerPicker = false
                                    onImportFromClipboard()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = p.subtleBg,
                                border = BorderStroke(1.dp, p.cardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentPaste, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("افزودن کلیپ‌بورد", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            Surface(
                                onClick = {
                                    showServerPicker = false
                                    onHuntBest()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = p.accentGold,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Outlined.Bolt, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("شکار بهترین سرور", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        }

                        if (profiles.isEmpty()) {
                            Text(
                                text = "کانفیگ شخصی ثبت نشده؛ در حالت خودکار اتصال برقرار می‌شود.",
                                color = p.textMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                profiles.forEachIndexed { idx, item ->
                                    val sel = idx == activeIdx
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(if (sel) p.accentSoftBg else p.subtleBg)
                                            .border(
                                                width = 1.dp,
                                                color = if (sel) p.accentGold else p.cardBorder,
                                                shape = RoundedCornerShape(11.dp)
                                            )
                                            .clickable {
                                                showServerPicker = false
                                                onSelectProfile(idx)
                                            }
                                            .padding(horizontal = 11.dp, vertical = 10.dp)
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
                                                color = if (item.pingMs in 1..250) p.emerald else p.textMuted,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
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
            telemetry = telemetry,
            stages = connectionStages,
            probes = liveVectorProbes,
            onDismiss = { showLiveRouteModal = false }
        )
    }
}

@Composable
private fun LiveStepperHeaderCard(
    telemetry: TelemetrySnapshot,
    stages: List<ConnectionStageStep>,
    autoPilot: Boolean,
    onOpenModal: () -> Unit
) {
    val p = LocalAlanPalette.current
    val animPct by animateFloatAsState(
        targetValue = (telemetry.progressPercent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(260),
        label = "liveStepPct"
    )
    val shortLabels = listOf("اپراتور", "پینگ", "هسته", "کش", "متدها", "تست", "اتصال")
    val headerStatus = when {
        telemetry.isConnected -> "مسیر امن فعال"
        telemetry.isConnecting -> "در حال راه‌اندازی"
        telemetry.uiPhase.equals("ERROR", true) -> "توقف در مسیر"
        else -> "مسیر زنده • آماده"
    }
    val dotTint = when {
        telemetry.isConnected -> p.emerald
        telemetry.isConnecting -> p.accentGold
        telemetry.uiPhase.equals("ERROR", true) -> p.danger
        else -> p.textMuted
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(highlighted = telemetry.isConnecting, cornerRadius = 16.dp)
            .clickable(onClick = onOpenModal)
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotTint)
                )
                Text(
                    text = headerStatus,
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(p.accentSoftBg)
                        .border(1.dp, p.glowBorder, RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    LtrContent {
                        Text(
                            text = if (autoPilot) "Auto" else telemetry.activeVectorId,
                            color = p.accentGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Outlined.Route, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                Text(
                    text = "جزئیات مسیر",
                    color = p.accentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            shortLabels.forEachIndexed { i, title ->
                val st = stages.getOrNull(i)?.state ?: "PENDING"
                val nodeColor = when (st) {
                    "DONE" -> p.emerald
                    "ACTIVE" -> p.accentGold
                    "FAILED" -> p.danger
                    else -> p.cardBorder
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (st == "ACTIVE") 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(nodeColor)
                    )
                    Text(
                        text = title,
                        color = if (st == "DONE" || st == "ACTIVE") p.textPrimary else p.textMuted,
                        fontSize = 9.sp,
                        fontWeight = if (st == "ACTIVE") FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        val shimmerInf = androidx.compose.animation.core.rememberInfiniteTransition(label = "stepperScan")
        val scanOffset by shimmerInf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.LinearEasing)
            ),
            label = "scanPos"
        )
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        ) {
            drawRect(color = p.subtleBg, size = size)
            if (telemetry.isConnected) {
                drawRect(color = p.emerald, size = size)
                val sx = size.width * scanOffset
                drawRect(
                    color = Color.White.copy(alpha = 0.38f),
                    topLeft = androidx.compose.ui.geometry.Offset((sx - size.width * 0.12f).coerceAtLeast(0f), 0f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height)
                )
            } else if (telemetry.isConnecting) {
                val fillW = size.width * animPct.coerceAtLeast(0.08f)
                drawRect(color = p.accentGold, size = androidx.compose.ui.geometry.Size(fillW, size.height))
            } else {
                val segW = size.width * 0.24f
                val startX = (size.width + segW) * scanOffset - segW
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, p.accentGold.copy(alpha = 0.65f), Color.Transparent),
                        startX = startX,
                        endX = startX + segW
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(startX.coerceAtLeast(0f), 0f),
                    size = androidx.compose.ui.geometry.Size(segW, size.height)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = telemetry.activeProfileName.takeIf { it.isNotBlank() && it != "—" } ?: "آماده انتخاب خودکار مسیر",
                color = p.textMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            LtrContent {
                Text(
                    text = if (telemetry.isConnected) "${telemetry.activeVectorId} • ${telemetry.pingMs}ms" else "${telemetry.progressPercent}%",
                    color = if (telemetry.isConnected) p.emerald else p.accentGold,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LiveRouteTimelineDialog(
    telemetry: TelemetrySnapshot,
    stages: List<ConnectionStageStep>,
    probes: List<LiveVectorProbeItem>,
    onDismiss: () -> Unit
) {
    val p = LocalAlanPalette.current
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = p.surfaceBg,
                border = BorderStroke(1.2.dp, p.glowBorder),
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(p.accentSoftBg)
                            ) {
                                Icon(Icons.Outlined.Route, null, tint = p.accentGold, modifier = Modifier.size(19.dp))
                            }
                            Column {
                                Text(
                                    text = "مسیر زنده • Xray",
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "فرایند اتصال، مرحله به مرحله",
                                    color = p.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Surface(
                            onClick = onDismiss,
                            shape = CircleShape,
                            color = p.subtleBg
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Outlined.Close, null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(p.subtleBg)
                            .border(1.dp, p.cardBorder, RoundedCornerShape(13.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("کانفیگ", color = p.textMuted, fontSize = 10.sp)
                                Text(
                                    text = telemetry.activeProfileName.ifBlank { "خودکار" },
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(135.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("پیشرفت", color = p.textMuted, fontSize = 10.sp)
                                LtrContent {
                                    Text(
                                        text = "${if (telemetry.isConnected) 100 else telemetry.progressPercent}%",
                                        color = p.accentGold,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("متد فعال", color = p.textMuted, fontSize = 10.sp)
                                LtrContent {
                                    Text(
                                        text = telemetry.activeVectorId,
                                        color = p.emerald,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 330.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        stages.forEach { step ->
                            val (statusFa, dotCol) = when (step.state) {
                                "DONE" -> "انجام شد" to p.emerald
                                "ACTIVE" -> "در حال اجرا" to p.accentGold
                                "FAILED" -> "ناموفق" to p.danger
                                "SKIPPED" -> "رد شد" to p.textMuted
                                else -> "در انتظار" to p.cardBorder
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(p.cardBg)
                                    .border(
                                        width = 1.dp,
                                        color = if (step.state == "ACTIVE") p.accentGold else dotCol.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(dotCol)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = step.title,
                                            color = p.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = step.subtitle.ifBlank { "—" },
                                            color = p.textMuted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = statusFa,
                                        color = dotCol,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    if (step.badgeText.isNotBlank()) {
                                        LtrContent {
                                            Text(
                                                text = step.badgeText,
                                                color = p.textSecondary,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val hitProbes = probes.filter { it.state == "PASSED" || it.state == "QUALIFIED" }
                        if (hitProbes.isNotEmpty()) {
                            Text(
                                text = "متدهای تاییدشده در این اسکن: " + hitProbes.joinToString(" ، ") { "${it.vectorId} (${it.latencyMs}ms)" },
                                color = p.emerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LegendDotItem("انجام شد", p.emerald)
                        LegendDotItem("در حال اجرا", p.accentGold)
                        LegendDotItem("در انتظار", p.textMuted)
                        LegendDotItem("ناموفق", p.danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDotItem(label: String, color: Color) {
    val p = LocalAlanPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = p.textMuted, fontSize = 9.sp)
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
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "speedArrow_$title")
    val iconScale by inf.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "arrowPulse"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .alanGlassCard(highlighted = isConnected, cornerRadius = 16.dp)
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = p.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                LtrContent {
                    Text(
                        text = rateLabel,
                        color = p.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.width(6.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = if (isConnected) 0.65f else 0.35f), CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size((18.0 * (if (isConnected) iconScale.toDouble() else 1.0)).dp)
                )
            }
        }

        LiveSparklineCanvas(
            samples = samples,
            isConnected = isConnected,
            lineColor = accent
        )
    }
}

@Composable
private fun TripleSummaryCol(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val p = LocalAlanPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(horizontal = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = p.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            maxLines = 1
        )
        Text(
            text = subtitle,
            color = p.textMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    onStartScan: (mode: String, targetIndex: Int, autoConnect: Boolean, pruneDead: Boolean) -> Unit,
    onStopScan: () -> Unit,
    onApplyVectorSingle: (vectorId: String, targetIndex: Int, endpoint: String) -> Unit,
    onApplyVectorAll: (vectorId: String, endpoint: String) -> Unit,
    onApplyVectorAndConnect: (vectorId: String, targetIndex: Int, endpoint: String) -> Unit,
    onConnectScannedProfile: (targetIndex: Int, boundVector: String, endpoint: String) -> Unit
) {
    val p = LocalAlanPalette.current
    var selectedMode by rememberSaveable { mutableStateOf("active_profile") }
    var autoConnectFirst by rememberSaveable { mutableStateOf(false) }
    var pruneDead by rememberSaveable { mutableStateOf(false) }

    val defaultSelectedIdx = remember(profiles) {
        profiles.indexOfFirst { it.isSelected }.let { if (it >= 0) it else 0 }
    }
    var targetProfileIndex by remember(defaultSelectedIdx) { mutableIntStateOf(defaultSelectedIdx) }

    LaunchedEffect(forcedTargetIndex) {
        if (forcedTargetIndex != null && forcedTargetIndex >= 0) {
            selectedMode = "active_profile"
            targetProfileIndex = forcedTargetIndex
            onConsumedForcedTarget()
        }
    }

    val profileOptions = remember(profiles) {
        profiles.mapIndexed { idx, prof ->
            SelectOptionItem(
                value = idx.toString(),
                label = "${prof.flagEmoji} ${prof.name}".trim(),
                subtitle = "${prof.protocol} • ${prof.host}:${prof.port}"
            )
        }
    }

    val filteredRows = remember(scanRows, selectedMode) {
        scanRows.filter { row ->
            if (selectedMode == "active_profile") {
                row.scanType == "active_profile" || row.scanType == "vector"
            } else {
                row.scanType == "full_repository" || row.scanType == "repository" || row.scanType == "profile"
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 13.dp)
                .padding(5.dp)
        ) {
            listOf(
                "active_profile" to "اسکن متد روی سرور",
                "full_repository" to "تست سلامت کانفیگ‌ها"
            ).forEach { (modeKey, modeTitle) ->
                val sel = selectedMode == modeKey
                Surface(
                    onClick = { selectedMode = modeKey },
                    shape = RoundedCornerShape(9.dp),
                    color = if (sel) p.accentGold else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 9.dp)) {
                        Text(
                            text = modeTitle,
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
            if (selectedMode == "active_profile") {
                AlanCustomSelectBox(
                    label = "انتخاب کانفیگ هدف",
                    selectedValue = targetProfileIndex.toString(),
                    options = profileOptions.ifEmpty {
                        listOf(SelectOptionItem("0", "ابتدا یک کانفیگ اضافه کنید"))
                    },
                    onSelect = { targetProfileIndex = it.toIntOrNull() ?: 0 }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoConnectFirst = !autoConnectFirst }
                ) {
                    Checkbox(
                        checked = autoConnectFirst,
                        onCheckedChange = { autoConnectFirst = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = p.accentGold,
                            checkmarkColor = p.onAccent,
                            uncheckedColor = p.textMuted
                        )
                    )
                    Text("اتصال خودکار به بهترین متد", color = p.textSecondary, fontSize = 12.sp)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pruneDead = !pruneDead }
                ) {
                    Checkbox(
                        checked = pruneDead,
                        onCheckedChange = { pruneDead = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = p.accentGold,
                            checkmarkColor = p.onAccent,
                            uncheckedColor = p.textMuted
                        )
                    )
                    Text("حذف خودکار کانفیگ‌های خراب پس از اسکن", color = p.textSecondary, fontSize = 12.sp)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    onClick = {
                        if (scanState.isRunning) {
                            onStopScan()
                        } else {
                            onStartScan(
                                selectedMode,
                                if (selectedMode == "active_profile") targetProfileIndex else -1,
                                autoConnectFirst,
                                pruneDead
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (scanState.isRunning) p.danger else p.accentGold
                ) {
                    Text(
                        text = if (scanState.isRunning) "توقف" else "شروع اسکن",
                        color = if (scanState.isRunning) Color.White else p.onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
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

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(
                items = filteredRows,
                key = { idx, row -> "$idx-${row.scanType}-${row.itemId}" }
            ) { _, row ->
                val isVector = row.scanType == "active_profile" || row.scanType == "vector"
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alanGlassCard(highlighted = row.rank == 1, cornerRadius = 14.dp)
                        .padding(12.dp)
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
                            fontSize = 12.sp,
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
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isVector) {
                            Surface(
                                onClick = { onApplyVectorSingle(row.itemId, row.targetIndex, row.endpoint) },
                                shape = RoundedCornerShape(9.dp),
                                color = p.subtleBg,
                                border = BorderStroke(1.dp, p.cardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                    Text("انتخاب متد", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            Surface(
                                onClick = { onApplyVectorAndConnect(row.itemId, row.targetIndex, row.endpoint) },
                                shape = RoundedCornerShape(9.dp),
                                color = p.accentGold,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                    Text("انتخاب و اتصال", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        } else {
                            Surface(
                                onClick = { onConnectScannedProfile(row.targetIndex, row.boundVector, row.endpoint) },
                                shape = RoundedCornerShape(9.dp),
                                color = p.emerald,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                    Text("اتصال به این سرور", color = Color(0xFF06110C), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
