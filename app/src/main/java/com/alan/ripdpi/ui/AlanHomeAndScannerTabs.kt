package com.alan.ripdpi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Route
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    var showServerPicker by remember { mutableStateOf(false) }
    var showLiveRouteModal by remember { mutableStateOf(false) }

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
        telemetry.isConnected -> "متصل شد"
        telemetry.isConnecting -> "در حال اتصال..."
        telemetry.uiPhase.equals("ERROR", ignoreCase = true) -> "خطا در اتصال"
        else -> "وصل نیست"
    }

    val cleanSubtitle = when {
        telemetry.isConnected -> "زمان اتصال: ${telemetry.uptimeLabel}"
        telemetry.isConnecting -> telemetry.statusDetail.ifBlank { "در حال اسکن و قفل بهترین مسیر..." }
        telemetry.uiPhase.equals("ERROR", ignoreCase = true) -> telemetry.statusDetail.ifBlank { "دوباره تلاش کنید" }
        isPureAutoMode -> "حالت خودکار فعال است • دکمه اتصال را بزنید"
        else -> "برای وصل شدن، دکمه اتصال را بزنید"
    }

    val selectorLabel = when {
        activeProfile != null -> {
            val em = activeProfile.flagEmoji.trim()
            if (em.isNotEmpty() && !activeProfile.name.contains(em)) "$em ${activeProfile.name}" else activeProfile.name
        }
        else -> if (telemetry.isConnected) "Alan Smart Auto-Server" else "حالت خودکار • ریپازیتوری"
    }

    // شرط نمایش باکس مسیر (فقط در حین اتصال، اتصال موفق یا خطا)
    val showRouteBox = telemetry.isConnecting ||
        telemetry.isConnected ||
        telemetry.uiPhase.equals("ERROR", ignoreCase = true)

    // چیدمان یک‌تکه بدون اسکرول
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        AnimatedVisibility(
            visible = showRouteBox,
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
        ) {
            LiveStepperHeaderCard(
                telemetry = telemetry,
                stages = connectionStages,
                autoPilot = settings.autoPilot,
                onOpenModal = { showLiveRouteModal = true }
            )
        }

        // فضای متغیر برای دکمه اتصال که صفحه نیازی به اسکرول نداشته باشد
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val dynamicOrbSize = (maxHeight - 98.dp).coerceIn(136.dp, 198.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                CenterPowerOrbButton(
                    isConnected = telemetry.isConnected,
                    isConnecting = telemetry.isConnecting,
                    uiPhase = telemetry.uiPhase,
                    progressPercent = telemetry.progressPercent,
                    accentColorLong = resolvedAccent,
                    onClick = onToggleConnection,
                    modifier = Modifier.size(dynamicOrbSize)
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = cleanHeadline,
                    color = when {
                        telemetry.isConnected -> p.emerald
                        telemetry.uiPhase.equals("ERROR", true) -> p.danger
                        else -> p.accentGold
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = cleanSubtitle,
                    color = p.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(24.dp))

                // کنترل‌های فشرده زیر دکمه
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // دکمه اختصاصی حالت خودکار با استایل نئومورفیک
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .then(if (isPureAutoMode) Modifier.alanNeuInset(14.dp) else Modifier.alanGlassCard(cornerRadius = 14.dp))
                            .clickable {
                                if (!isPureAutoMode) {
                                    onActivateAutoMode(false)
                                } else if (!telemetry.isConnected && !telemetry.isConnecting) {
                                    onActivateAutoMode(true)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoFixHigh,
                                contentDescription = "حالت خودکار",
                                tint = if (isPureAutoMode) p.accentGold else p.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "حالت خودکار",
                                color = if (isPureAutoMode) p.accentGold else p.textSecondary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // کپسول انتخاب سرور
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .weight(1f)
                            .alanGlassCard(cornerRadius = 14.dp)
                            .clickable { showServerPicker = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
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
                                    .background(if (telemetry.isConnected) p.emerald else p.accentGold)
                            )
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
                            imageVector = Icons.Outlined.ChevronLeft,
                            contentDescription = null,
                            tint = p.accentGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // بخش پایینی ثابت (پرچم‌ها + کارت‌های سرعت)
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
        }
    }

    if (showServerPicker) {
        Dialog(onDismissRequest = { showServerPicker = false }) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .alanGlassCard(cornerRadius = 24.dp, highlighted = true)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "انتخاب منبع اتصال",
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .alanGlassCard(cornerRadius = 12.dp)
                                    .clickable {
                                        showServerPicker = false
                                        onImportFromClipboard()
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.ContentPaste, null, tint = p.textPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("افزودن کلیپ‌بورد", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1.15f)
                                    .alanGlassCard(cornerRadius = 12.dp)
                                    .clickable {
                                        showServerPicker = false
                                        onActivateAutoMode(true)
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Bolt, null, tint = p.accentGold, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("اسکن و اتصال", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        }

                        // ردیف اختصاصی برای حالت خودکار
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isPureAutoMode) Modifier.alanNeuInset(14.dp) else Modifier.alanGlassCard(cornerRadius = 14.dp))
                                .clickable {
                                    showServerPicker = false
                                    onActivateAutoMode(false)
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.AutoFixHigh, null, tint = p.accentGold, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "حالت خودکار (فقط ریپازیتوری)",
                                    color = if (isPureAutoMode) p.accentGold else p.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (profiles.isNotEmpty()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                profiles.forEachIndexed { idx, item ->
                                    val sel = idx == activeIdx
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (sel) Modifier.alanNeuInset(14.dp) else Modifier.alanGlassCard(cornerRadius = 14.dp))
                                            .clickable {
                                                showServerPicker = false
                                                onSelectProfile(idx)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            FlagStripeBadge(item.flagStripeColors, item.countryCode, 24.dp, 16.dp)
                                            Text(
                                                text = item.name,
                                                color = if (sel) p.accentGold else p.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
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
                                                fontSize = 11.sp
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
        animationSpec = tween(240),
        label = "liveStepPct"
    )

    val shortLabels = listOf("اپراتور", "پینگ", "هسته", "کش", "متدها", "تست", "اتصال")
    val headerStatus = when {
        telemetry.isConnected -> "مسیر امن فعال"
        telemetry.isConnecting -> "در حال راه‌اندازی"
        else -> "توقف در مسیر"
    }
    val dotTint = when {
        telemetry.isConnected -> p.emerald
        telemetry.isConnecting -> p.accentGold
        else -> p.danger
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(highlighted = telemetry.isConnecting, cornerRadius = 18.dp)
            .clickable(onClick = onOpenModal)
            .padding(horizontal = 16.dp, vertical = 14.dp)
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
                        .alanNeuInset(cornerRadius = 8.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    LtrContent {
                        Text(
                            text = if (telemetry.isConnected) telemetry.activeVectorId else if (autoPilot) "Auto" else telemetry.activeVectorId,
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
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Outlined.Route, null, tint = p.accentGold, modifier = Modifier.size(16.dp))
                Text(
                    text = "جزئیات مسیر",
                    color = p.accentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
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
                    else -> p.textMuted
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        ) {
            // پس‌زمینه نوار پیشرفت (فرورفته نئومورفیک را با کشیدن شبیه‌سازی می‌کنیم یا ساده رندر می‌کنیم)
            drawRect(color = p.subtleBg, size = size)
            if (telemetry.isConnected) {
                drawRect(color = p.emerald, size = size)
            } else if (telemetry.isConnecting) {
                val fillW = size.width * animPct.coerceAtLeast(0.08f)
                drawRect(color = p.accentGold, size = Size(fillW, size.height))
            } else {
                drawRect(color = p.danger.copy(alpha = 0.65f), size = size)
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
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .alanGlassCard(cornerRadius = 24.dp, highlighted = true)
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .alanGlassCard(cornerRadius = 14.dp)
                            ) {
                                Icon(Icons.Outlined.Route, null, tint = p.accentGold, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("مسیر زنده • Xray", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                Text("فرایند اتصال، مرحله به مرحله", color = p.textMuted, fontSize = 11.sp)
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .alanGlassCard(cornerRadius = 19.dp)
                                .clickable { onDismiss() }
                        ) {
                            Icon(Icons.Outlined.Close, null, tint = p.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        stages.forEach { step ->
                            val (statusFa, dotCol) = when (step.state) {
                                "DONE" -> "انجام شد" to p.emerald
                                "ACTIVE" -> "در حال اجرا" to p.accentGold
                                "FAILED" -> "ناموفق" to p.danger
                                "SKIPPED" -> "عبور" to p.textMuted
                                else -> "در انتظار" to p.textMuted
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (step.state == "ACTIVE") Modifier.alanNeuInset(14.dp) else Modifier.alanGlassCard(cornerRadius = 14.dp))
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotCol))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(step.title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                        Text(step.subtitle.ifBlank { "—" }, color = p.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(statusFa, color = dotCol, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    if (step.badgeText.isNotBlank()) {
                                        LtrContent {
                                            Text(step.badgeText, color = p.textSecondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .alanGlassCard(highlighted = isConnected, cornerRadius = 18.dp)
            .padding(horizontal = 14.dp, vertical = 14.dp)
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
                        maxLines = 1
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .alanNeuInset(cornerRadius = 19.dp)
            ) {
                Icon(icon, title, tint = accent, modifier = Modifier.size(20.dp))
            }
        }

        LiveSparklineCanvas(
            samples = samples,
            isConnected = isConnected,
            lineColor = accent,
            modifier = Modifier.height(26.dp)
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

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // کنترل انتخاب حالت اسکن (توکار نئومورفیک)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 16.dp)
                .padding(6.dp)
        ) {
            listOf(
                "single" to "یک کانفیگ",
                "selected" to "چند کانفیگ",
                "all" to "کل کانفیگ‌ها"
            ).forEach { (key, label) ->
                val sel = scopeMode == key
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (sel) Modifier.alanGlassCard(cornerRadius = 12.dp) else Modifier)
                        .clickable { scopeMode = key }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        color = if (sel) p.textPrimary else p.textMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 18.dp)
                .padding(16.dp)
        ) {
            when (scopeMode) {
                "single" -> {
                    if (profiles.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("کانفیگی در لیست نیست:", color = p.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .alanGlassCard(cornerRadius = 12.dp)
                                    .clickable { onImportFromClipboard() }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text("افزودن کلیپ‌بورد", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else {
                        AlanCustomSelectBox(
                            label = "انتخاب کانفیگ هدف",
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
                            .alanGlassCard(cornerRadius = 14.dp)
                            .clickable { showMultiPicker = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = if (selectedMultiIndices.isEmpty()) "انتخاب چند کانفیگ از لیست..." else "${selectedMultiIndices.size} کانفیگ انتخاب شد",
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text("انتخاب", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }

                else -> {
                    Text(
                        text = "اسکن هوشمند متدها روی تمام کانفیگ‌های مخزن",
                        color = p.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { autoConnectFirst = !autoConnectFirst }
            ) {
                AlanPillSwitch(checked = autoConnectFirst, onCheckedChange = { autoConnectFirst = it })
                Spacer(Modifier.width(10.dp))
                Text("اتصال خودکار به سریع‌ترین متد", color = p.textSecondary, fontSize = 12.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { useCleanIpInScan = !useCleanIpInScan }
            ) {
                AlanPillSwitch(checked = useCleanIpInScan, onCheckedChange = { useCleanIpInScan = it })
                Spacer(Modifier.width(10.dp))
                Text("تست همراه با IP تمیز", color = p.textSecondary, fontSize = 12.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .alanGlassCard(
                            cornerRadius = 14.dp,
                            highlighted = !scanState.isRunning,
                            accentBorderColor = if (scanState.isRunning) p.danger else p.accentGold
                        )
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
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = if (scanState.isRunning) "توقف" else "شروع اسکن متد",
                        color = if (scanState.isRunning) p.danger else p.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = scanState.summary,
                    color = p.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // لیست نتایج اسکن
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(
                items = scanRows,
                key = { idx, row -> "$idx-${row.targetIndex}-${row.itemId}" }
            ) { _, row ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alanGlassCard(highlighted = row.rank == 1, cornerRadius = 18.dp)
                        .padding(16.dp)
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
                            fontSize = 14.sp,
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
                                fontSize = 14.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .alanNeuInset(cornerRadius = 12.dp)
                                .clickable { onApplyVectorSingle(row.itemId, row.targetIndex, row.endpoint) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text("ثبت متد", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.2f)
                                .alanGlassCard(cornerRadius = 12.dp)
                                .clickable { onApplyVectorAndConnect(row.itemId, row.targetIndex, row.endpoint) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text("ثبت و اتصال", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
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