package com.alan.ripdpi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.alan.ripdpi.model.CountryPaletteRegistry
import java.util.Locale

@Composable
fun HomeTabScreen(
    telemetry: TelemetrySnapshot,
    connectionStages: List<ConnectionStageStep>,
    liveVectorProbes: List<LiveVectorProbeItem>,
    profiles: List<ProfileItem>,
    settings: EngineSettings,
    availableRepoLocations: List<RepoLocationOption> = emptyList(),
    repoScanReport: RepoScanReportSummary = RepoScanReportSummary(),
    subscriptions: List<SubscriptionItem> = emptyList(),
    dohServers: List<DohServerUiItem> = emptyList(),
    dohScanState: DohScanUiState = DohScanUiState(),
    onToggleConnection: () -> Unit,
    onActivateAutoMode: (connectNow: Boolean) -> Unit,
    onSelectAutoRepoCountry: (countryCode: String, reconnectIfActive: Boolean) -> Unit = { _, _ -> },
    onRefreshAutoRepoCatalog: () -> Unit = {},
    onImportFromClipboard: () -> Unit,
    onSelectProfile: (Int) -> Unit,
    onSetTunnelMode: (String) -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    onOpenDohScanner: () -> Unit = {}
) {
    val p = LocalAlanPalette.current
    var showAutoLocationSheet by remember { mutableStateOf(false) }
    var showManualServerPicker by remember { mutableStateOf(false) }
    var showLiveRouteModal by remember { mutableStateOf(false) }

    val isPureAutoMode = settings.autoPilot || telemetry.activeProfileUid == "__SMART_AUTO__"
    val preferredAutoCode = settings.preferredAutoCountry.uppercase(Locale.US)

    val selectedAutoLocation = remember(availableRepoLocations, preferredAutoCode) {
        availableRepoLocations.firstOrNull {
            it.countryCode.equals(preferredAutoCode, ignoreCase = true)
        } ?: availableRepoLocations.firstOrNull() ?: RepoLocationOption(
            countryCode = "AUTO",
            countryNameFa = "خودکار",
            flagEmoji = "",
            serverCount = profiles.size.coerceAtLeast(12),
            avgPingMs = 140
        )
    }

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
    val isErrorPhase = telemetry.uiPhase.equals("ERROR", ignoreCase = true)

    val statusBannerText = when {
        telemetry.isConnected && isPureAutoMode -> "متصل با سرورهای عمومی هوشمند"
        telemetry.isConnected -> "متصل به ${activeProfile?.name ?: telemetry.activeProfileName}"
        telemetry.isConnecting -> telemetry.statusDetail.ifBlank { "در حال مذاکره و قفل مسیر امن..." }
        isErrorPhase -> telemetry.statusDetail.ifBlank { "ارتباط برقرار نشد؛ مجدداً تلاش کنید" }
        isPureAutoMode -> "آماده اتصال هوشمند"
        else -> "آماده برقراری ارتباط"
    }

    val locationValueText = when {
        !isPureAutoMode && activeProfile != null -> activeProfile.countryName.ifBlank { "انتخابی" }
        selectedAutoLocation.countryCode == "AUTO" -> "خودکار"
        else -> selectedAutoLocation.countryNameFa
    }

    val connectionValueText = when {
        isPureAutoMode -> {
            val vecLabel = if (telemetry.isConnected) telemetry.activeVectorId else "هوشمند"
            "خودکار • $vecLabel"
        }
        activeProfile != null -> activeProfile.name
        else -> "انتخاب کانفیگ"
    }

    val subscriptionValueText = when {
        isPureAutoMode -> "عمومی مخزن ابری"
        subscriptions.isNotEmpty() -> "${subscriptions.first().title} (${profiles.size})"
        profiles.isNotEmpty() -> "مخزن شخصی (${profiles.size} نود)"
        else -> "افزودن اشتراک"
    }

    val activeDohLabel = remember(dohScanState.activeDohName, settings.activeDohUrl) {
        dohScanState.activeDohName.ifBlank {
            try { java.net.URI(settings.activeDohUrl).host.orEmpty() } catch (_: Throwable) { "Cloudflare DoH" }
        }
    }

    val footerTechSummary = remember(telemetry, activeProfile, settings, activeDohLabel) {
        val proto = when {
            settings.tunnelMode == "DOH_VEIL" -> "DoH-Veil"
            telemetry.isConnected && telemetry.activeProtocol.isNotBlank() && telemetry.activeProtocol != "—" -> telemetry.activeProtocol
            activeProfile != null -> activeProfile.protocol.uppercase(Locale.US)
            else -> "VLESS"
        }
        val vec = if (telemetry.isConnected) telemetry.activeVectorId else settings.manualVectorId
        "خروجی $proto • متد $vec • $activeDohLabel"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val compact = maxHeight < 640.dp
        val orbSize = if (compact) 164.dp else 192.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Hero: Morphing Neumorphic Orb + Dignified Status Line
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = if (compact) 2.dp else 6.dp)
            ) {
                CenterPowerOrbButton(
                    isConnected = telemetry.isConnected,
                    isConnecting = telemetry.isConnecting,
                    uiPhase = telemetry.uiPhase,
                    progressPercent = telemetry.progressPercent,
                    accentColorLong = telemetry.accentPrimaryColor,
                    onClick = onToggleConnection,
                    modifier = Modifier.size(orbSize)
                )

                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

                Text(
                    text = statusBannerText,
                    color = when {
                        telemetry.isConnected -> p.accentGold
                        telemetry.isConnecting -> p.accentGold
                        isErrorPhase -> p.danger
                        else -> p.textSecondary
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable {
                        if (telemetry.isConnecting || telemetry.isConnected) {
                            showLiveRouteModal = true
                        }
                    }
                )
            }

            // 2. Session Timer, Reconnect Button & Live Receive/Send Card (Neumorphic)
            Column(
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .alanGlassCard(
                        highlighted = telemetry.isConnected,
                        cornerRadius = 20.dp,
                        accentBorderColor = p.emerald.copy(alpha = 0.50f)
                    )
                    .padding(horizontal = 15.dp, vertical = if (compact) 11.dp else 13.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Raised Tactile Reconnect Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .alanNeuInset(cornerRadius = 11.dp, accented = telemetry.isConnected)
                            .clickable {
                                if (isPureAutoMode) {
                                    onActivateAutoMode(true)
                                } else {
                                    onToggleConnection()
                                }
                            }
                            .padding(horizontal = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "اتصال مجدد",
                                tint = if (telemetry.isConnected) p.emerald else p.accentGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "اتصال مجدد",
                                color = if (telemetry.isConnected) p.emerald else p.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    // Digital Session Uptime
                    LtrContent {
                        Text(
                            text = if (telemetry.isConnected) telemetry.uptimeLabel else "00:00:00",
                            color = p.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp
                        )
                    }
                }

                // Subtle Concave Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(p.cardBorder.copy(alpha = 0.75f))
                )

                // Receive / Send Rates Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDownward,
                            contentDescription = "دریافت",
                            tint = p.emerald,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "دریافت",
                            color = p.textMuted,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        LtrContent {
                            Text(
                                text = if (telemetry.isConnected) telemetry.rxRateLabel else "0 B/s",
                                color = p.textPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = "ارسال",
                            tint = p.accentGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "ارسال",
                            color = p.textMuted,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        LtrContent {
                            Text(
                                text = if (telemetry.isConnected) telemetry.txRateLabel else "0 B/s",
                                color = p.textPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 3. Segmented Neumorphic Mode Switch: وی‌پی‌ان | پروکسی | دی‌ان‌اس DoH
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .alanNeuInset(cornerRadius = 16.dp)
                    .padding(5.dp)
            ) {
                val curMode = settings.tunnelMode.uppercase(Locale.US).ifBlank { "VPN" }
                listOf(
                    "VPN" to "وی‌پی‌ان",
                    "PROXY" to "پروکسی",
                    "DOH_VEIL" to "دی‌ان‌اس DoH"
                ).forEach { (modeKey, modeTitle) ->
                    val selected = curMode == modeKey
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .then(
                                if (selected) {
                                    Modifier.alanGlassCard(
                                        highlighted = true,
                                        cornerRadius = 12.dp,
                                        accentBorderColor = if (modeKey == "VPN") p.emerald else p.accentGold
                                    )
                                } else {
                                    Modifier.clip(RoundedCornerShape(12.dp))
                                }
                            )
                            .clickable { onSetTunnelMode(modeKey) }
                    ) {
                        Text(
                            text = modeTitle,
                            color = when {
                                selected && modeKey == "VPN" -> p.emerald
                                selected -> p.accentGold
                                else -> p.textSecondary
                            },
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 4. 4-Row Interactive Configuration Matrix Card (موقعیت / اتصال / سابسکریپشن / دی‌ان‌اس)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alanGlassCard(cornerRadius = 20.dp)
                    .padding(horizontal = 15.dp, vertical = if (compact) 8.dp else 10.dp)
            ) {
                HomeSelectorRow(
                    title = "موقعیت",
                    valueText = locationValueText,
                    onClick = { showAutoLocationSheet = true }
                )
                HomeMatrixDivider()
                HomeSelectorRow(
                    title = "اتصال",
                    valueText = connectionValueText,
                    onClick = { showManualServerPicker = true }
                )
                HomeMatrixDivider()
                HomeSelectorRow(
                    title = "سابسکریپشن",
                    valueText = subscriptionValueText,
                    onClick = onOpenSubscriptions
                )
                HomeMatrixDivider()
                HomeSelectorRow(
                    title = "دی‌ان‌اس امن",
                    valueText = activeDohLabel,
                    onClick = onOpenDohScanner
                )
                HomeMatrixDivider()

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = footerTechSummary,
                        color = p.textMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showAutoLocationSheet) {
        SmartRepoLocationPickerSheet(
            locations = availableRepoLocations,
            selectedCode = preferredAutoCode,
            report = repoScanReport,
            isConnected = telemetry.isConnected,
            onDismiss = { showAutoLocationSheet = false },
            onRefreshCatalog = onRefreshAutoRepoCatalog,
            onSelectLocation = { code ->
                showAutoLocationSheet = false
                onSelectAutoRepoCountry(code, telemetry.isConnected)
            },
            onSwitchToManualList = {
                showAutoLocationSheet = false
                showManualServerPicker = true
            }
        )
    }

    if (showManualServerPicker) {
        ManualServerPickerDialog(
            profiles = profiles,
            activeIdx = activeIdx,
            isPureAutoMode = isPureAutoMode,
            onDismiss = { showManualServerPicker = false },
            onImportFromClipboard = {
                showManualServerPicker = false
                onImportFromClipboard()
            },
            onSwitchToAutoRepo = {
                showManualServerPicker = false
                onActivateAutoMode(false)
                showAutoLocationSheet = true
            },
            onSelectProfile = { idx ->
                showManualServerPicker = false
                onSelectProfile(idx)
            }
        )
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
private fun HomeSelectorRow(
    title: String,
    valueText: String,
    onClick: () -> Unit
) {
    val p = LocalAlanPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = p.textMuted,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = valueText,
                color = p.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronLeft,
                contentDescription = null,
                tint = p.textMuted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun HomeMatrixDivider() {
    val p = LocalAlanPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(p.cardBorder.copy(alpha = 0.55f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartRepoLocationPickerSheet(
    locations: List<RepoLocationOption>,
    selectedCode: String,
    report: RepoScanReportSummary,
    isConnected: Boolean,
    onDismiss: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onSelectLocation: (String) -> Unit,
    onSwitchToManualList: () -> Unit
) {
    val p = LocalAlanPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.surfaceBg,
        contentColor = p.textPrimary,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .padding(bottom = 16.dp)
            ) {
                // Sheet Header
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
                                .size(38.dp)
                                .alanGlassCard(highlighted = true, cornerRadius = 12.dp)
                        ) {
                            Icon(Icons.Outlined.Public, null, tint = p.accentGold, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "انتخاب لوکیشن ریپازیتوری هوشمند",
                                color = p.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = if (isConnected) {
                                    "با انتخاب هر کشور، تونل فوراً به آن لوکیشن سوییچ می‌شود"
                                } else {
                                    "انتخاب لوکیشن هدف برای اتصال هوشمند گیت‌هاب"
                                },
                                color = p.textMuted,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    AlanNeuIconButton(
                        icon = Icons.Outlined.Close,
                        tint = p.textSecondary,
                        size = 34.dp,
                        onClick = onDismiss
                    )
                }

                // Sunken Concave Scan Report Box (repoScanReport + Refresh Button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alanNeuInset(cornerRadius = 15.dp, accented = true)
                        .padding(horizontal = 13.dp, vertical = 11.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(p.emerald)
                            )
                            Text(
                                text = "گزارش اسکن مخزن: ${report.totalVerified} سرور سالم از ${report.totalScanned}",
                                color = p.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                        }
                        Text(
                            text = "آخرین بروزرسانی: ${report.updatedAtLabel} • ${report.activeLocationsCount} کشور فعال",
                            color = p.textMuted,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .alanGlassCard(cornerRadius = 11.dp)
                            .clickable { onRefreshCatalog() }
                            .padding(horizontal = 11.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                            Text(
                                text = "بروزرسانی",
                                color = p.accentGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Locations List
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 330.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    locations.forEach { loc ->
                        val isSelected = loc.countryCode.equals(selectedCode, ignoreCase = true) ||
                            (selectedCode.isBlank() && loc.countryCode == "AUTO")
                        val pingTint = when {
                            loc.avgPingMs in 1..200 -> p.emerald
                            loc.avgPingMs > 200 -> p.accentGold
                            else -> p.textMuted
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelected) {
                                        Modifier.alanNeuInset(cornerRadius = 14.dp, accented = true)
                                    } else {
                                        Modifier.alanGlassCard(cornerRadius = 14.dp)
                                    }
                                )
                                .clickable { onSelectLocation(loc.countryCode) }
                                .padding(horizontal = 13.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (loc.countryCode == "AUTO") {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .alanNeuInset(cornerRadius = 10.dp)
                                    ) {
                                        Text("⚡", fontSize = 16.sp)
                                    }
                                } else {
                                    FlagStripeBadge(
                                        stripeColors = loc.flagStripeColors,
                                        countryCode = loc.countryCode,
                                        width = 30.dp,
                                        height = 20.dp
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (loc.countryCode == "AUTO") {
                                            "⚡ ${loc.countryNameFa}"
                                        } else {
                                            "${loc.flagEmoji} ${loc.countryNameFa}"
                                        },
                                        color = if (isSelected) p.accentGold else p.textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${loc.serverCount} سرور آماده در ریپازیتوری",
                                        color = p.textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (loc.avgPingMs > 0) {
                                    Box(
                                        modifier = Modifier
                                            .alanNeuInset(cornerRadius = 8.dp)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        LtrContent {
                                            Text(
                                                text = "~${loc.avgPingMs} ms",
                                                color = pingTint,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = p.accentGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Switch to Manual Personal Profiles Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .alanGlassCard(cornerRadius = 13.dp)
                        .clickable { onSwitchToManualList() }
                ) {
                    Text(
                        text = "سوییچ به لیست کانفیگ‌های شخصی (حالت دستی)",
                        color = p.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualServerPickerDialog(
    profiles: List<ProfileItem>,
    activeIdx: Int,
    isPureAutoMode: Boolean,
    onDismiss: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onSwitchToAutoRepo: () -> Unit,
    onSelectProfile: (Int) -> Unit
) {
    val p = LocalAlanPalette.current
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                            text = "انتخاب سریع کانفیگ شخصی",
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        AlanNeuIconButton(
                            icon = Icons.Outlined.Close,
                            tint = p.textSecondary,
                            size = 32.dp,
                            onClick = onDismiss
                        )
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
                                .clickable { onImportFromClipboard() }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ContentPaste, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("افزودن کلیپ‌بورد", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.15f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.accentGold)
                                .clickable { onSwitchToAutoRepo() }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Bolt, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("لوکیشن‌های خودکار", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }
                        }
                    }

                    if (profiles.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alanNeuInset(cornerRadius = 13.dp)
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "کانفیگ شخصی ثبت نشده است؛ از کلیپ‌بورد یا تب کانفیگ‌ها اضافه کنید.",
                                color = p.textMuted,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            profiles.forEachIndexed { idx, item ->
                                val sel = !isPureAutoMode && idx == activeIdx
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (sel) {
                                                Modifier.alanNeuInset(cornerRadius = 12.dp, accented = true)
                                            } else {
                                                Modifier.alanGlassCard(cornerRadius = 12.dp)
                                            }
                                        )
                                        .clickable { onSelectProfile(idx) }
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
        telemetry.isConnecting -> "در حال راه‌اندازی تونل"
        else -> "توقف در مسیر"
    }
    val dotTint = when {
        telemetry.isConnected -> p.emerald
        telemetry.isConnecting -> p.accentGold
        else -> p.danger
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(highlighted = telemetry.isConnecting, cornerRadius = 16.dp)
            .clickable(onClick = onOpenModal)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotTint)
                )
                Text(
                    text = headerStatus,
                    color = p.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .alanNeuInset(cornerRadius = 7.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    LtrContent {
                        Text(
                            text = if (telemetry.isConnected) telemetry.activeVectorId else if (autoPilot) "Auto" else telemetry.activeVectorId,
                            color = p.accentGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Outlined.Route, null, tint = p.accentGold, modifier = Modifier.size(14.dp))
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
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (st == "ACTIVE") 8.5.dp else 6.5.dp)
                            .clip(CircleShape)
                            .background(nodeColor)
                    )
                    Text(
                        text = title,
                        color = if (st == "DONE" || st == "ACTIVE") p.textPrimary else p.textMuted,
                        fontSize = 8.5.sp,
                        fontWeight = if (st == "ACTIVE") FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
        ) {
            drawRect(color = p.subtleBg, size = size)
            when {
                telemetry.isConnected -> drawRect(color = p.emerald, size = size)
                telemetry.isConnecting -> drawRect(
                    color = p.accentGold,
                    size = Size(size.width * animPct.coerceAtLeast(0.08f), size.height)
                )
                else -> drawRect(color = p.danger.copy(alpha = 0.7f), size = size)
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
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                                    .size(36.dp)
                                    .alanNeuInset(cornerRadius = 11.dp)
                            ) {
                                Icon(Icons.Outlined.Route, null, tint = p.accentGold, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = "مسیر زنده اتصال",
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "فرایند اتصال، مرحله‌به‌مرحله",
                                    color = p.textMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        AlanNeuIconButton(
                            icon = Icons.Outlined.Close,
                            tint = p.textSecondary,
                            size = 32.dp,
                            onClick = onDismiss
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
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
                                    .then(
                                        if (step.state == "ACTIVE") {
                                            Modifier.alanNeuInset(cornerRadius = 12.dp, accented = true)
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
                                            maxLines = 1
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
                                    Text(statusFa, color = dotCol, fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
                                text = "متدهای موفق: " + hitProbes.joinToString(" ، ") { "${it.vectorId} (${it.latencyMs}ms)" },
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
            .alanGlassCard(
                highlighted = isConnected,
                cornerRadius = 16.dp,
                accentBorderColor = accent.copy(alpha = 0.55f)
            )
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
                    .size(32.dp)
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
    var useCleanIpInScan by rememberSaveable { mutableStateOf(true) }
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
        // Segmented Inset Control: یک کانفیگ / چند کانفیگ / کل مخزن
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 15.dp)
                .padding(5.dp)
        ) {
            listOf(
                "single" to "یک کانفیگ",
                "selected" to "چند کانفیگ",
                "all" to "کل مخزن"
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
                                    cornerRadius = 11.dp,
                                    accentBorderColor = p.accentGold
                                )
                            } else {
                                Modifier.clip(RoundedCornerShape(11.dp))
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

        // Scanner Config Panel
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alanGlassCard(cornerRadius = 18.dp)
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
                                text = "کانفیگی در لیست نیست؛ از کلیپ‌بورد وارد کنید:",
                                color = p.textSecondary,
                                fontSize = 11.5.sp,
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
                                    text = "افزودن کلیپ‌بورد",
                                    color = p.accentGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
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
                            .alanNeuInset(cornerRadius = 13.dp)
                            .clickable { showMultiPicker = true }
                            .padding(horizontal = 13.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (selectedMultiIndices.isEmpty()) {
                                "انتخاب چند کانفیگ از لیست (${profiles.size} موجود)..."
                            } else {
                                "${selectedMultiIndices.size} کانفیگ برای تست انتخاب شد"
                            },
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "انتخاب",
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                else -> {
                    Text(
                        text = if (profiles.isNotEmpty()) {
                            "اسکن هوشمند متدها روی تمام ${profiles.size} کانفیگ موجود در مخزن"
                        } else {
                            "اسکن مستقیم متدها روی سرورهای ریپازیتوری گیت‌هاب"
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
                    .alanNeuInset(cornerRadius = 12.dp)
                    .clickable { autoConnectFirst = !autoConnectFirst }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "اتصال خودکار به سریع‌ترین متد تاییدشده",
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
                    .alanNeuInset(cornerRadius = 12.dp)
                    .clickable { useCleanIpInScan = !useCleanIpInScan }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "تست همراه با IP تمیز کلادفلر (خاموش = تست خالص سرور)",
                    color = p.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                AlanPillSwitch(checked = useCleanIpInScan, onCheckedChange = { useCleanIpInScan = it })
            }

            // Concave Progress Bar
            if (scanState.isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .alanNeuInset(cornerRadius = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animScanPct)
                            .height(6.dp)
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
                        text = if (scanState.isRunning) "توقف اسکن" else "شروع اسکن متد",
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

        // Scan Results List
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
                        .alanGlassCard(highlighted = row.rank == 1, cornerRadius = 16.dp)
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
                                .alanNeuInset(cornerRadius = 11.dp)
                                .clickable { onApplyVectorSingle(row.itemId, row.targetIndex, row.endpoint) }
                        ) {
                            Text(
                                text = "ثبت متد",
                                color = p.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.25f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.accentGold)
                                .clickable { onApplyVectorAndConnect(row.itemId, row.targetIndex, row.endpoint) }
                        ) {
                            Text(
                                text = "ثبت و اتصال فوری",
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