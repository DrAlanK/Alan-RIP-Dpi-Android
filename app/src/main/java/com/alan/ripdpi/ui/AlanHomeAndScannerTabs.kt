package com.alan.ripdpi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale


private fun sanitizePersianStatusLine(raw: String, progress: Int): String {
    val clean = raw.trim()
    if (clean.isEmpty() || clean.contains("[") || clean.contains("]") || clean.contains("VEC-", true) || clean.contains("://") || clean.contains(".pages.") || clean.contains(".workers.")) {
        return when {
            progress < 25 -> "در حال آماده‌سازی هسته و بررسی شبکه..."
            progress < 55 -> "در حال تست موازی ۶ مسیره و یافتن بهترین متد..."
            progress < 85 -> "در حال مذاکره TLS و تایید سلامت تونل..."
            else -> "در حال برقراری و قفل نهایی تونل امن..."
        }
    }
    return clean
}

private fun sanitizeCleanServerTitle(rawName: String, fallbackProto: String): String {
    var s = rawName.trim()
    // Remove bracketed prefixes like [NL-01] or [⚡120ms] and mixed Persian country prefixes when followed by Latin hostnames
    s = Regex("""\[[^\]]*\]""").replace(s, "").trim()
    s = s.replace("VLESS · ", "").replace("VMESS · ", "").replace("TROJAN · ", "")
    // Strip leading flags and Persian words if the rest of the name is Latin/ASCII
    val asciiPart = Regex("""[A-Za-z0-9._-]{4,}.*$""").find(s)?.value?.trim()
    if (!asciiPart.isNullOrBlank()) {
        return asciiPart.trimStart('•', '-', '|', '·', ' ')
    }
    return s.trimStart('•', '-', '|', '·', ' ').ifBlank { fallbackProto }
}

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
    onConnectBestUserConfig: (applyDpiMethods: Boolean) -> Unit = {},
    onToggleApplyDpiOnUserAuto: (Boolean) -> Unit = {},
    onSelectAutoRepoCountry: (countryCode: String, reconnectIfActive: Boolean) -> Unit = { _, _ -> },
    onRefreshAutoRepoCatalog: () -> Unit = {},
    onImportFromClipboard: () -> Unit,
    onSelectProfile: (Int) -> Unit,
    onSetTunnelMode: (String) -> Unit = {},
    onOpenSplitTunnelDialog: () -> Unit = {},
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
            countryNameFa = "خودکار (سریع‌ترین)",
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
    val isDisconnectingPhase = telemetry.uiPhase.equals("DISCONNECTING", ignoreCase = true)

    val cleanStatusHeadline = when {
        isDisconnectingPhase -> "در حال قطع اتصال..."
        telemetry.isConnected && isPureAutoMode -> "متصل از طریق شبکه ابری هوشمند"
        telemetry.isConnected -> "تونل امن روی پروفایل شخصی برقرار است"
        telemetry.isConnecting -> sanitizePersianStatusLine(telemetry.statusDetail, telemetry.progressPercent)
        isErrorPhase -> "ارتباط برقرار نشد؛ متد یا سرور دیگری را امتحان کنید"
        isPureAutoMode -> "آماده اتصال هوشمند"
        else -> "آماده برقراری ارتباط"
    }

    val locationTitleText = when {
        !isPureAutoMode && activeProfile != null -> activeProfile.countryName.ifBlank { "سرور انتخابی" }
        selectedAutoLocation.countryCode == "AUTO" -> "خودکار (سریع‌ترین)"
        else -> selectedAutoLocation.countryNameFa
    }
    val locationSubText = when {
        telemetry.isConnected && telemetry.egressIp.isNotBlank() && telemetry.egressIp != "—" -> telemetry.egressIp
        !isPureAutoMode && activeProfile != null -> "${activeProfile.host}:${activeProfile.port}"
        else -> "${selectedAutoLocation.serverCount} سرور مخزن"
    }

    val activeVecLabel = if (telemetry.isConnected) {
        telemetry.activeVectorId
    } else if (!settings.applyDpiOnUserAuto && !isPureAutoMode) {
        "VEC-00"
    } else {
        settings.manualVectorId
    }

    val connectionTitleText = when {
        isPureAutoMode -> "Smart Cloud Repo"
        activeProfile != null -> sanitizeCleanServerTitle(activeProfile.name, activeProfile.protocol)
        else -> "Personal Repo (${profiles.size})"
    }

    val activeDohLabel = remember(dohScanState.activeDohName, settings.activeDohUrl) {
        dohScanState.activeDohName.ifBlank { "Cloudflare DoH" }
    }

    val splitTunnelBadge = remember(settings.splitTunnelMode, settings.splitTunnelPackagesCsv) {
        val cnt = settings.splitTunnelPackagesCsv.split(",").count { it.isNotBlank() }
        when (settings.splitTunnelMode.uppercase(Locale.US)) {
            "INCLUDE" -> if (cnt > 0) "فقط $cnt برنامه" else "تفکیک برنامه‌ها"
            "EXCLUDE" -> if (cnt > 0) "استثنا: $cnt برنامه" else "تفکیک برنامه‌ها"
            else -> "همه برنامه‌ها"
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        val compact = maxHeight < 650.dp
        val orbAreaHeight = if (compact) 202.dp else 244.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Hero: Wide Bubble Field + Seamless One-Piece Morphing Orb
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                CenterPowerOrbButton(
                    isConnected = telemetry.isConnected,
                    isConnecting = telemetry.isConnecting,
                    uiPhase = telemetry.uiPhase,
                    progressPercent = telemetry.progressPercent,
                    accentColorLong = telemetry.accentPrimaryColor,
                    onClick = onToggleConnection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(orbAreaHeight)
                )

                Text(
                    text = cleanStatusHeadline,
                    color = when {
                        telemetry.isConnected -> p.emerald
                        telemetry.isConnecting -> p.accentGold
                        isErrorPhase -> p.danger
                        else -> p.textSecondary
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
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

            // 2. Telemetry & Quick User Auto-Connect Pod
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .alanGlassCard(cornerRadius = 22.dp)
                    .padding(horizontal = 13.dp, vertical = if (compact) 9.dp else 11.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(34.dp)
                                .alanGlassCard(cornerRadius = 11.dp)
                                .clickable {
                                    if (isPureAutoMode) onActivateAutoMode(true) else onToggleConnection()
                                }
                                .padding(horizontal = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "اتصال مجدد",
                                    tint = if (telemetry.isConnected) p.emerald else p.accentGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("اتصال مجدد", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(34.dp)
                                .alanGlassCard(cornerRadius = 11.dp)
                                .clickable {
                                    onConnectBestUserConfig(settings.applyDpiOnUserAuto)
                                }
                                .padding(horizontal = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.Bolt, null, tint = p.accentGold, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "بهترینِ کانفیگ من",
                                    color = p.accentGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(34.dp)
                            .alanNeuInset(cornerRadius = 11.dp)
                            .padding(horizontal = 12.dp)
                    ) {
                        LtrContent {
                            Text(
                                text = if (telemetry.isConnected) telemetry.uptimeLabel else "00:00:00",
                                color = if (telemetry.isConnected) p.emerald else p.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .weight(1f)
                            .alanNeuInset(cornerRadius = 12.dp)
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.ArrowDownward, null, tint = p.emerald, modifier = Modifier.size(13.dp))
                            Text("دریافت", color = p.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        LtrContent {
                            Text(
                                text = if (telemetry.isConnected) telemetry.rxRateLabel else "0 B/s",
                                color = p.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .weight(1f)
                            .alanNeuInset(cornerRadius = 12.dp)
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.ArrowUpward, null, tint = p.accentGold, modifier = Modifier.size(13.dp))
                            Text("ارسال", color = p.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        LtrContent {
                            Text(
                                text = if (telemetry.isConnected) telemetry.txRateLabel else "0 B/s",
                                color = p.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }

            // 3. Borderless Mode Switcher
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
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
                            .height(36.dp)
                            .then(
                                if (selected) {
                                    Modifier.alanGlassCard(highlighted = true, cornerRadius = 12.dp)
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
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // 4. Bento Deck (Location Tile + Profile/DPI Mode Tile + DoH & Split-Tunnel Bar)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .alanGlassCard(cornerRadius = 20.dp)
                            .clickable { showAutoLocationSheet = true }
                            .padding(horizontal = 13.dp, vertical = if (compact) 9.dp else 11.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("موقعیت سرور", color = p.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .alanNeuInset(cornerRadius = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Public, null, tint = p.emerald, modifier = Modifier.size(15.dp))
                            }
                        }
                        Text(
                            text = locationTitleText,
                            color = p.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LtrContent {
                            Text(
                                text = locationSubText,
                                color = p.textSecondary,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .alanGlassCard(cornerRadius = 20.dp)
                            .clickable { showManualServerPicker = true }
                            .padding(horizontal = 13.dp, vertical = if (compact) 9.dp else 11.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("پروفایل و متد", color = p.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(24.dp)
                                    .alanNeuInset(cornerRadius = 7.dp, accented = settings.applyDpiOnUserAuto)
                                    .clickable { onToggleApplyDpiOnUserAuto(!settings.applyDpiOnUserAuto) }
                                    .padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = if (settings.applyDpiOnUserAuto) "ضد DPI فعال" else "خالص v2ray",
                                    color = if (settings.applyDpiOnUserAuto) p.accentGold else p.textSecondary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                        LtrContent {
                            Text(
                                text = connectionTitleText,
                                color = p.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LtrContent {
                            Text(
                                text = "${activeProfile?.protocol?.uppercase(Locale.US) ?: "VLESS"} • $activeVecLabel",
                                color = p.emerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .weight(1.1f)
                            .alanGlassCard(cornerRadius = 16.dp)
                            .clickable { onOpenDohScanner() }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(p.emerald))
                            LtrContent {
                                Text(
                                    text = activeDohLabel,
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text("DoH", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 9.5.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .weight(1f)
                            .alanGlassCard(cornerRadius = 16.dp)
                            .clickable { onOpenSplitTunnelDialog() }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Apps, null, tint = p.accentGold, modifier = Modifier.size(15.dp))
                            Text(
                                text = splitTunnelBadge,
                                color = p.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
            applyDpiOnUserAuto = settings.applyDpiOnUserAuto,
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
            onConnectBestUserConfig = { applyDpi ->
                showManualServerPicker = false
                onConnectBestUserConfig(applyDpi)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "انتخاب لوکیشن مخزن هوشمند",
                        color = p.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.5.sp
                    )
                    AlanNeuIconButton(
                        icon = Icons.Outlined.Close,
                        tint = p.textSecondary,
                        size = 34.dp,
                        onClick = onDismiss
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alanNeuInset(cornerRadius = 15.dp, accented = true)
                        .padding(horizontal = 13.dp, vertical = 11.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "سرورهای تاییدشده: ${report.totalVerified} از ${report.totalScanned}",
                            color = p.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "بروزرسانی: ${report.updatedAtLabel} • ${report.activeLocationsCount} کشور فعال",
                            color = p.textMuted,
                            fontSize = 10.sp
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(34.dp)
                            .alanGlassCard(cornerRadius = 10.dp)
                            .clickable { onRefreshCatalog() }
                            .padding(horizontal = 11.dp)
                    ) {
                        Text("بروزرسانی", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 310.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    locations.forEach { loc ->
                        val isSelected = loc.countryCode.equals(selectedCode, ignoreCase = true) ||
                            (selectedCode.isBlank() && loc.countryCode == "AUTO")
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = loc.countryNameFa,
                                    color = if (isSelected) p.accentGold else p.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.5.sp
                                )
                                Text(
                                    text = "${loc.serverCount} سرور آماده",
                                    color = p.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                            if (loc.avgPingMs > 0) {
                                LtrContent {
                                    Text(
                                        text = "~${loc.avgPingMs} ms",
                                        color = p.emerald,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .alanGlassCard(cornerRadius = 13.dp)
                        .clickable { onSwitchToManualList() }
                ) {
                    Text(
                        text = "انتخاب از کانفیگ‌های شخصی من",
                        color = p.textPrimary,
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
    applyDpiOnUserAuto: Boolean = true,
    onDismiss: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onSwitchToAutoRepo: () -> Unit,
    onConnectBestUserConfig: (Boolean) -> Unit = {},
    onSelectProfile: (Int) -> Unit
) {
    val p = LocalAlanPalette.current
    var applyDpiLocal by remember(applyDpiOnUserAuto) { mutableStateOf(applyDpiOnUserAuto) }

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
                            text = "کانفیگ‌های من و اتصال هوشمند",
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

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alanNeuInset(cornerRadius = 15.dp, accented = true)
                            .padding(11.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyDpiLocal = !applyDpiLocal }
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "اعمال متدهای ضد DPI و IP تمیز",
                                    color = p.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = if (applyDpiLocal) "فعال: تست خودکار متدهای ضد فیلتر" else "غیرفعال: اتصال خالص استاندارد v2rayNG",
                                    color = p.textMuted,
                                    fontSize = 9.5.sp
                                )
                            }
                            AlanPillSwitch(checked = applyDpiLocal, onCheckedChange = { applyDpiLocal = it })
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.emerald)
                                .clickable { onConnectBestUserConfig(applyDpiLocal) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Bolt, null, tint = Color(0xFF07130E), modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (applyDpiLocal) {
                                        "اتصال خودکار به بهترین کانفیگ من (با متد DPI)"
                                    } else {
                                        "اتصال خودکار به بهترین کانفیگ من (خالص v2ray)"
                                    },
                                    color = Color(0xFF07130E),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
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
                                .height(38.dp)
                                .alanGlassCard(cornerRadius = 11.dp)
                                .clickable { onImportFromClipboard() }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ContentPaste, null, tint = p.accentGold, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("افزودن کلیپ‌بورد", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(38.dp)
                                .alanGlassCard(cornerRadius = 11.dp)
                                .clickable { onSwitchToAutoRepo() }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Public, null, tint = p.emerald, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("مخزن ابری گیت‌هاب", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    if (profiles.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alanNeuInset(cornerRadius = 13.dp)
                                .padding(18.dp)
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
                                .heightIn(max = 240.dp)
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
                                    LtrContent {
                                        Text(
                                            text = item.name,
                                            color = if (sel) p.accentGold else p.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    LtrContent {
                                        Text(
                                            text = if (item.pingMs > 0) "${item.pingMs} ms" else item.protocol,
                                            color = if (item.pingMs in 1..280) p.emerald else p.textMuted,
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Route, null, tint = p.accentGold, modifier = Modifier.size(18.dp))
                            Text("مسیر زنده اتصال", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        AlanNeuIconButton(Icons.Outlined.Close, p.textSecondary, 32.dp, onClick = onDismiss)
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        stages.forEach { step ->
                            val (statusFa, dotCol) = when (step.state) {
                                "DONE" -> "انجام شد" to p.emerald
                                "ACTIVE" -> "در حال اجرا" to p.accentGold
                                "FAILED" -> "ناموفق" to p.danger
                                else -> "در انتظار" to p.textMuted
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alanGlassCard(cornerRadius = 12.dp)
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(step.title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                    if (step.subtitle.isNotBlank()) {
                                        Text(step.subtitle, color = p.textMuted, fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                                Text(statusFa, color = dotCol, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }
        }
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
    var autoConnectFirst by rememberSaveable { mutableStateOf(true) }
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
                                Modifier.alanGlassCard(highlighted = true, cornerRadius = 11.dp)
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
            when (scopeMode) {
                "single" -> {
                    if (profiles.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("کانفیگی در لیست نیست؛ از کلیپ‌بورد وارد کنید:", color = p.textSecondary, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(36.dp)
                                    .alanGlassCard(cornerRadius = 10.dp)
                                    .clickable { onImportFromClipboard() }
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text("افزودن کلیپ‌بورد", color = p.accentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                            text = if (selectedMultiIndices.isEmpty()) "انتخاب چند کانفیگ از لیست (${profiles.size} موجود)..." else "${selectedMultiIndices.size} کانفیگ برای تست انتخاب شد",
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text("انتخاب", color = p.accentGold, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                }
                else -> {
                    Text(
                        text = "اسکن هوشمند متدها روی تمام ${profiles.size} کانفیگ موجود در مخزن",
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
                Text("اتصال خودکار به سریع‌ترین متد تاییدشده", color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
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
                Text("تزریق خودکار IP تمیز کلادفلر حین تست", color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                AlanPillSwitch(checked = useCleanIpInScan, onCheckedChange = { useCleanIpInScan = it })
            }

            if (scanState.isRunning) {
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).alanNeuInset(cornerRadius = 3.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(animScanPct).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.accentGold))
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
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .alanNeuInset(cornerRadius = 11.dp)
                                .clickable { onApplyVectorSingle(row.itemId, row.targetIndex, row.endpoint) }
                        ) {
                            Text("ثبت متد", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
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
                            Text("ثبت و اتصال فوری", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
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
