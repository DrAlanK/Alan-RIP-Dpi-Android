package com.alan.ripdpi.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.alan.ripdpi.model.APP_NAME
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class GlassNavSpec(
    val index: Int,
    val title: String,
    val icon: ImageVector
)

@Composable
fun AlanMainScreen(
    controller: AlanEngineController = remember { DemoAlanEngineController() },
    @Suppress("UNUSED_PARAMETER") initialDarkTheme: Boolean = true
) {
    val settings by controller.settings.collectAsState()
    val darkTheme = settings.darkTheme
    var currentTab by rememberSaveable(settings.lastActiveTab) {
        mutableIntStateOf(settings.lastActiveTab.coerceIn(0, 7))
    }
    var forcedScannerTarget by remember { mutableStateOf<Int?>(null) }
    var showSplitTunnelDialog by remember { mutableStateOf(false) }

    // Secret 5-tap trigger on ALAN-RIP-DPI pill for Hardware / Memory-Leak / CA Diagnostics
    var secretTapCount by remember { mutableIntStateOf(0) }
    var lastSecretTapMs by remember { mutableLongStateOf(0L) }
    var showHardwarePanel by remember { mutableStateOf(false) }

    // Glass menu state: starts neatly docked in the top-bar corner next to ALAN-RIP-DPI!
    // Once opened, stays open until user explicitly clicks X; freely draggable & height-resizable!
    var glassDockExpanded by rememberSaveable { mutableStateOf(false) }
    var glassDockCompact by rememberSaveable { mutableStateOf(false) }
    var dockHeightFraction by rememberSaveable { mutableFloatStateOf(0.56f) }
    var isOrbDockedInHeader by rememberSaveable { mutableStateOf(true) }
    var freePosX by rememberSaveable { mutableFloatStateOf(-1f) }
    var freePosY by rememberSaveable { mutableFloatStateOf(-1f) }
    var isDraggingOrb by remember { mutableStateOf(false) }

    val telemetry by controller.telemetry.collectAsState()
    val connectionStages by controller.connectionStages.collectAsState()
    val liveVectorProbes by controller.liveVectorProbes.collectAsState()
    val profiles by controller.profiles.collectAsState()
    val scanRows by controller.scanRows.collectAsState()
    val scanState by controller.scanState.collectAsState()
    val subscriptions by controller.subscriptions.collectAsState()
    val logs by controller.logs.collectAsState()
    val isFindingBestMethod by controller.isFindingBestMethod.collectAsState()
    val autoMethodStatus by controller.autoMethodStatus.collectAsState()
    val cleanIpHits by controller.cleanIpHits.collectAsState()
    val cleanIpState by controller.cleanIpState.collectAsState()
    val availableRepoLocations by controller.availableRepoLocations.collectAsState()
    val repoScanReport by controller.repoScanReport.collectAsState()
    val dohServers by controller.dohServers.collectAsState()
    val dohScanState by controller.dohScanState.collectAsState()
    val installedApps by controller.installedApps.collectAsState()
    val hardwareHealth by controller.hardwareHealth.collectAsState()

    val vectors = remember(controller) { controller.getVectors() }

    val selectTab: (Int) -> Unit = { idx ->
        currentTab = idx
        controller.updateSettings(mapOf("lastActiveTab" to idx))
    }

    BackHandler(enabled = currentTab != 0) {
        selectTab(0)
    }

    AlanCyberTheme(darkTheme = darkTheme) {
        val p = LocalAlanPalette.current
        val isRtl = AlanI18n.lang != "en"

        val navItems = remember {
            listOf(
                GlassNavSpec(0, "خانه", Icons.Outlined.Home),
                GlassNavSpec(1, "کانفیگ", Icons.Outlined.Description),
                GlassNavSpec(2, "متدها", Icons.Outlined.Speed),
                GlassNavSpec(3, "IP/DoH", Icons.Outlined.Radar),
                GlassNavSpec(4, "تنظیمات", Icons.Outlined.Settings),
                GlassNavSpec(5, "Xray", Icons.Outlined.Tune),
                GlassNavSpec(6, "لاگ", Icons.Outlined.Terminal),
                GlassNavSpec(7, "درباره", Icons.Outlined.Info)
            )
        }
        val activeNav = navItems.firstOrNull { it.index == currentTab } ?: navItems.first()

        CompositionLocalProvider(
            LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(p.rootBg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val density = LocalDensity.current
                val rootWidthDp = maxWidth
                val rootHeightDp = maxHeight
                val containerWidthPx = with(density) { rootWidthDp.toPx() }
                val containerHeightPx = with(density) { rootHeightDp.toPx() }
                val topBarHPadPx = with(density) { 14.dp.toPx() }
                val topBarVPadPx = with(density) { 9.dp.toPx() }
                val marginPx = with(density) { 8.dp.toPx() }
                val orbSizeDp = 42.dp
                val orbSizePx = with(density) { orbSizeDp.toPx() }
                val snapRadiusPx = with(density) { 58.dp.toPx() }

                // Exact physical coordinates of the Header Corner Magnetic Slot (where the old Hamburger button sat!)
                val headerSlotX = if (isRtl) {
                    (containerWidthPx - topBarHPadPx - orbSizePx).coerceAtLeast(marginPx)
                } else {
                    topBarHPadPx
                }
                val headerSlotY = topBarVPadPx

                val maxOrbX = (containerWidthPx - orbSizePx - marginPx).coerceAtLeast(marginPx)
                val maxOrbY = (containerHeightPx - orbSizePx - marginPx).coerceAtLeast(marginPx)

                val curOrbX = if (isOrbDockedInHeader || freePosX < 0f) headerSlotX else freePosX.coerceIn(marginPx, maxOrbX)
                val curOrbY = if (isOrbDockedInHeader || freePosY < 0f) headerSlotY else freePosY.coerceIn(marginPx, maxOrbY)
                val distToSlot = hypot((curOrbX - headerSlotX).toDouble(), (curOrbY - headerSlotY).toDouble()).toFloat()
                val nearHeaderSlot = distToSlot <= snapRadiusPx

                // 1. Main Layout (Top Bar with Magnetic Slot next to ALAN-RIP-DPI + Full Content)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        // Start Group: Magnetic Corner Cradle (Old Hamburger Spot) + ALAN-RIP-DPI Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            // Magnetic Resting Socket in the Top Bar Corner
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(orbSizeDp)
                                    .alanNeuInset(cornerRadius = 21.dp, accented = isDraggingOrb && nearHeaderSlot)
                                    .border(
                                        width = if (isDraggingOrb && nearHeaderSlot) 1.8.dp else 1.dp,
                                        color = if (isDraggingOrb && nearHeaderSlot) p.accentGold else p.accentGold.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        isOrbDockedInHeader = true
                                        glassDockExpanded = !glassDockExpanded
                                    }
                            ) {
                                if (!isOrbDockedInHeader || glassDockExpanded) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isDraggingOrb && nearHeaderSlot) p.accentGold
                                                else p.textMuted.copy(alpha = 0.35f)
                                            )
                                    )
                                }
                            }

                            // ALAN-RIP-DPI Pill (5 quick taps opens Hidden Hardware / Leak / CA Diagnostics!)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .height(40.dp)
                                    .alanNeuInset(cornerRadius = 15.dp)
                                    .clickable {
                                        val now = SystemClock.elapsedRealtime()
                                        if (now - lastSecretTapMs > 1600L) {
                                            secretTapCount = 1
                                        } else {
                                            secretTapCount += 1
                                        }
                                        lastSecretTapMs = now
                                        if (secretTapCount >= 5) {
                                            secretTapCount = 0
                                            controller.refreshHardwareHealth()
                                            showHardwarePanel = true
                                        }
                                    }
                                    .padding(horizontal = 13.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                telemetry.isConnected -> p.emerald
                                                telemetry.isConnecting -> p.accentGold
                                                telemetry.uiPhase.equals("ERROR", true) -> p.danger
                                                else -> p.textMuted.copy(alpha = 0.5f)
                                            }
                                        )
                                )
                                LtrContent {
                                    Text(
                                        text = APP_NAME.uppercase(),
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }

                        // End Group: EN/FA + Theme Switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(40.dp)
                                    .width(42.dp)
                                    .alanGlassCard(cornerRadius = 13.dp)
                                    .clickable {
                                        AlanI18n.lang = if (AlanI18n.lang == "fa") "en" else "fa"
                                    }
                            ) {
                                LtrContent {
                                    Text(
                                        text = if (AlanI18n.lang == "fa") "EN" else "FA",
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            AlanNeuIconButton(
                                icon = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                tint = p.accentGold,
                                size = 40.dp,
                                onClick = { controller.updateSettings(mapOf("darkTheme" to !darkTheme)) }
                            )
                        }
                    }

                    // Active Screen Body
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (currentTab) {
                            0 -> HomeTabScreen(
                                telemetry = telemetry,
                                connectionStages = connectionStages,
                                liveVectorProbes = liveVectorProbes,
                                profiles = profiles,
                                settings = settings,
                                availableRepoLocations = availableRepoLocations,
                                repoScanReport = repoScanReport,
                                subscriptions = subscriptions,
                                dohServers = dohServers,
                                dohScanState = dohScanState,
                                onToggleConnection = { controller.toggleConnection() },
                                onActivateAutoMode = { connectNow ->
                                    controller.activateAutoModeAndConnect(connectNow)
                                },
                                onConnectBestUserConfig = { applyDpi ->
                                    controller.connectBestUserConfig(applyDpi, "ALL")
                                },
                                onToggleApplyDpiOnUserAuto = { enabled ->
                                    controller.updateSettings(mapOf("applyDpiOnUserAuto" to enabled))
                                },
                                onSelectAutoRepoCountry = { code, reconnect ->
                                    controller.selectAutoRepoCountry(code, reconnect)
                                },
                                onRefreshAutoRepoCatalog = {
                                    controller.refreshAutoRepoCatalog()
                                },
                                onImportFromClipboard = { controller.importFromClipboard() },
                                onSelectProfile = { idx -> controller.selectConfig(idx) },
                                onSetTunnelMode = { mode -> controller.setTunnelOperationMode(mode) },
                                onOpenSplitTunnelDialog = { showSplitTunnelDialog = true },
                                onOpenDohScanner = { selectTab(3) },
                                onReconnect = { controller.reconnectActiveTunnel() }
                            )
                            1 -> ProfilesTabScreen(
                                profiles = profiles,
                                subscriptions = subscriptions,
                                vectors = vectors,
                                applyDpiOnUserAuto = settings.applyDpiOnUserAuto,
                                onToggleApplyDpiOnUserAuto = { enabled ->
                                    controller.updateSettings(mapOf("applyDpiOnUserAuto" to enabled))
                                },
                                onConnectBestUserConfig = { applyDpi, grp ->
                                    controller.connectBestUserConfig(applyDpi, grp)
                                    selectTab(0)
                                },
                                onSendAllToDpiLab = {
                                    controller.sendAllConfigsToDpiLab(true, false)
                                    selectTab(2)
                                },
                                onSelectConfig = { idx -> controller.selectConfig(idx) },
                                onImportFromClipboard = { controller.importFromClipboard() },
                                onImportConfigsRaw = { rawText -> controller.importConfigs(rawText) },
                                onImportQrPayload = { qrPayload -> controller.importQrPayload(qrPayload) },
                                onHuntBest = {
                                    controller.activateAutoModeAndConnect(true)
                                    selectTab(0)
                                },
                                onQuickPingAll = { controller.quickPingConfigs() },
                                onRealTunnelPingConfigs = { indices, autoRename ->
                                    controller.realTunnelPingConfigs(indices, autoRename)
                                },
                                onPingSingleConfig = { idx, realPing ->
                                    controller.pingSingleConfig(idx, realPing)
                                },
                                onAutoRenameConfigsWithLocation = { indices ->
                                    controller.autoRenameConfigsWithLocation(indices)
                                },
                                onSortConfigs = { by -> controller.sortConfigs(by) },
                                onDeduplicate = { controller.deduplicateConfigs() },
                                onRemoveDead = { controller.removeDeadConfigs() },
                                onExportVerifiedEarlyData = {
                                    controller.exportConfigs("verified", settings.wsEarlyData.coerceAtLeast(2048))
                                },
                                onShareOrExportSelected = { indices, asBase64 ->
                                    controller.shareOrExportSelectedConfigs(indices, asBase64)
                                },
                                onClearAll = { controller.clearAllConfigs() },
                                onDeleteConfig = { idx -> controller.deleteConfig(idx) },
                                onDeleteSelectedConfigs = { indices ->
                                    controller.deleteSelectedConfigs(indices)
                                },
                                onGetEditable = { idx -> controller.getConfigEditable(idx) },
                                onSaveEditable = { idx, fields -> controller.saveConfigEditable(idx, fields) },
                                onSendToMethodScanner = { idx ->
                                    forcedScannerTarget = idx
                                    selectTab(2)
                                },
                                onAddSubscription = { url, title -> controller.addSubscription(url, title) },
                                onRefreshSubscriptions = { controller.refreshSubscriptions() },
                                onRefreshSingleSub = { grp -> controller.refreshSingleSubscription(grp) },
                                onDeleteSubscription = { idx -> controller.deleteSubscription(idx) },
                                onQuickCloneCleanIp = { idx, count ->
                                    controller.quickCloneConfigWithCleanIps(idx, count)
                                }
                            )
                            2 -> ScannerTabScreen(
                                profiles = profiles,
                                vectors = vectors,
                                scanRows = scanRows,
                                scanState = scanState,
                                isTunnelConnected = telemetry.isConnected,
                                isTunnelConnecting = telemetry.isConnecting,
                                activeVectorId = telemetry.activeVectorId,
                                activePingMs = telemetry.pingMs,
                                forcedTargetIndex = forcedScannerTarget,
                                onConsumedForcedTarget = { forcedScannerTarget = null },
                                onStartMultiMethodScan = { scopeMode, indices, autoConn, useCleanIp, selVecs ->
                                    controller.startMultiTargetMethodScan(scopeMode, indices, autoConn, useCleanIp, selVecs)
                                },
                                onStopScan = { controller.stopScanner() },
                                onImportFromClipboard = { controller.importFromClipboard() },
                                onApplyVectorSingle = { vecId, tIdx, ep ->
                                    controller.applyScannedVector(vecId, tIdx, ep, false)
                                },
                                onApplyVectorAndConnect = { vecId, tIdx, ep ->
                                    controller.applyScannedVector(vecId, tIdx, ep, true)
                                    selectTab(0)
                                }
                            )
                            3 -> CleanIpScannerScreen(
                                profiles = profiles,
                                settings = settings,
                                cleanIpHits = cleanIpHits,
                                cleanIpState = cleanIpState,
                                dohServers = dohServers,
                                dohScanState = dohScanState,
                                onStartScan = { srcMode, customRanges, port, maxHits ->
                                    controller.startCleanIpScan(srcMode, customRanges, port, maxHits)
                                },
                                onStopScan = { controller.stopCleanIpScan() },
                                onRefreshGithubSubnets = { controller.refreshSubnetsFromGithub() },
                                onUpdateSubnetsUrl = { url ->
                                    controller.updateSettings(mapOf("subnetsRepoUrl" to url))
                                },
                                onApplyOrCloneIps = { ips, targetMode, selIndices, cloneMode, clonesCount ->
                                    controller.applyCleanIpsToTargets(ips, targetMode, selIndices, cloneMode, clonesCount)
                                },
                                onStartDohScan = { domain -> controller.startDohScan(domain) },
                                onStopDohScan = { controller.stopDohScan() },
                                onSelectActiveDoh = { url, connectNow -> controller.selectActiveDoh(url, connectNow) },
                                onAddCustomDohUrls = { raw -> controller.addCustomDohList(raw) }
                            )
                            4 -> GeneralSettingsScreen(
                                settings = settings,
                                vectors = vectors,
                                isFindingBestMethod = isFindingBestMethod,
                                autoMethodStatus = autoMethodStatus,
                                onAutoDetectBestMethod = { controller.autoDetectBestMethod() },
                                onReplaceSettings = { controller.updateSettings(it) },
                                onPatchSettings = { controller.updateSettings(it) },
                                onOpenSplitTunnelDialog = { showSplitTunnelDialog = true }
                            )
                            5 -> XraySettingsScreen(
                                settings = settings,
                                onPatchSettings = { controller.updateSettings(it) },
                                onImportBypassFile = { targetType, rawText, replaceExisting ->
                                    controller.importBypassListFromText(targetType, rawText, replaceExisting)
                                }
                            )
                            6 -> LogsTabScreen(
                                logs = logs,
                                onClearLogs = { controller.clearLogs() },
                                onExportFullLogs = { controller.exportAllLogsText() }
                            )
                            else -> AboutTabScreen()
                        }
                    }
                }

                // 2. Floating Glass Assistive Orb + Draggable & Height-Resizable Glass Sidebar (Physical LTR overlay over full screen)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        contentAlignment = Alignment.TopStart,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(50f)
                    ) {
                        val dockWidthDp by animateDpAsState(
                            targetValue = if (glassDockCompact) 54.dp else 68.dp,
                            animationSpec = tween(180),
                            label = "glassDockW"
                        )
                        val dockWidthPx = with(density) { dockWidthDp.toPx() }
                        val minDockHeightDp = 210.dp
                        val maxDockHeightDp = (rootHeightDp * 0.80f).coerceAtLeast(300.dp)
                        val currentDockHeightDp = (rootHeightDp * dockHeightFraction).coerceIn(minDockHeightDp, maxDockHeightDp)
                        val currentDockHeightPx = with(density) { currentDockHeightDp.toPx() }

                        val maxDockX = (containerWidthPx - dockWidthPx - marginPx).coerceAtLeast(marginPx)
                        val maxDockY = (containerHeightPx - currentDockHeightPx - marginPx).coerceAtLeast(marginPx)

                        val dockStartY = if (isOrbDockedInHeader) {
                            (headerSlotY + orbSizePx + marginPx).coerceIn(marginPx, maxDockY)
                        } else {
                            curOrbY.coerceIn(marginPx, maxDockY)
                        }
                        val clampedDockX = curOrbX.coerceIn(marginPx, maxDockX)
                        val clampedDockY = dockStartY

                        val glassBg = if (darkTheme) {
                            Brush.verticalGradient(listOf(Color(0xE02A3346), Color(0xD61A202D)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0xF2FFFFFF), Color(0xE4DCE6F5)))
                        }
                        val glassBorderCol = if (darkTheme) p.accentGold.copy(alpha = 0.44f) else p.accentGold.copy(alpha = 0.56f)

                        // Collapsed State: iPhone-style Glass Orb (Rests magnetically in Top Bar Corner next to ALAN-RIP-DPI OR floats anywhere!)
                        AnimatedVisibility(
                            visible = !glassDockExpanded,
                            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f),
                            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.8f),
                            modifier = Modifier.absoluteOffset {
                                IntOffset(curOrbX.roundToInt(), curOrbY.roundToInt())
                            }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(orbSizeDp)
                                    .shadow(elevation = if (isOrbDockedInHeader) 4.dp else 12.dp, shape = CircleShape, clip = false)
                                    .clip(CircleShape)
                                    .background(glassBg)
                                    .border(1.4.dp, glassBorderCol, CircleShape)
                                    .pointerInput(containerWidthPx, containerHeightPx, headerSlotX, headerSlotY) {
                                        detectDragGestures(
                                            onDragStart = {
                                                isDraggingOrb = true
                                                if (isOrbDockedInHeader) {
                                                    freePosX = headerSlotX
                                                    freePosY = headerSlotY
                                                    isOrbDockedInHeader = false
                                                }
                                            },
                                            onDragEnd = {
                                                isDraggingOrb = false
                                                val d = hypot((freePosX - headerSlotX).toDouble(), (freePosY - headerSlotY).toDouble()).toFloat()
                                                if (d <= snapRadiusPx) {
                                                    isOrbDockedInHeader = true
                                                    freePosX = headerSlotX
                                                    freePosY = headerSlotY
                                                }
                                            },
                                            onDragCancel = { isDraggingOrb = false }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            freePosX = (freePosX + dragAmount.x).coerceIn(marginPx, maxOrbX)
                                            freePosY = (freePosY + dragAmount.y).coerceIn(marginPx, maxOrbY)
                                        }
                                    }
                                    .clickable { glassDockExpanded = true }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (darkTheme) Color.White.copy(alpha = 0.13f)
                                            else p.accentGold.copy(alpha = 0.16f)
                                        )
                                        .border(
                                            0.9.dp,
                                            if (darkTheme) Color.White.copy(alpha = 0.34f) else p.accentGold.copy(alpha = 0.45f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = activeNav.icon,
                                        contentDescription = activeNav.title,
                                        tint = if (telemetry.isConnected) p.emerald else p.accentGold,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }

                        // Expanded State: Detached Draggable & Height-Resizable Frosted Glass Sidebar
                        // Stays open across tab switches; collapses to small orb ONLY when tapping X!
                        AnimatedVisibility(
                            visible = glassDockExpanded,
                            enter = fadeIn(tween(170)) + scaleIn(tween(170), initialScale = 0.86f),
                            exit = fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.86f),
                            modifier = Modifier.absoluteOffset {
                                IntOffset(clampedDockX.roundToInt(), clampedDockY.roundToInt())
                            }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .width(dockWidthDp)
                                    .height(currentDockHeightDp)
                                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(26.dp), clip = false)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(glassBg)
                                    .border(1.3.dp, glassBorderCol, RoundedCornerShape(26.dp))
                                    .pointerInput(containerWidthPx, containerHeightPx, dockWidthPx, currentDockHeightPx) {
                                        detectDragGestures(
                                            onDragStart = {
                                                if (isOrbDockedInHeader) {
                                                    freePosX = clampedDockX
                                                    freePosY = clampedDockY
                                                    isOrbDockedInHeader = false
                                                }
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            freePosX = (freePosX + dragAmount.x).coerceIn(marginPx, maxDockX)
                                            freePosY = (freePosY + dragAmount.y).coerceIn(marginPx, maxDockY)
                                        }
                                    }
                                    .padding(top = 8.dp, bottom = 6.dp, start = 5.dp, end = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height(3.5.dp)
                                        .clip(CircleShape)
                                        .background(p.textMuted.copy(alpha = 0.55f))
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(if (darkTheme) Color.White.copy(alpha = 0.14f) else p.accentGold.copy(alpha = 0.16f))
                                            .clickable { glassDockCompact = !glassDockCompact }
                                    ) {
                                        Icon(
                                            imageVector = if (glassDockCompact) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                                            contentDescription = "عرض",
                                            tint = p.accentGold,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(if (darkTheme) Color.White.copy(alpha = 0.14f) else p.textMuted.copy(alpha = 0.16f))
                                            .clickable { glassDockExpanded = false }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "کوچک کردن",
                                            tint = p.textPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(if (glassDockCompact) 6.dp else 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 2.dp)
                                ) {
                                    navItems.forEach { nav ->
                                        val selected = currentTab == nav.index
                                        val circleSize = if (glassDockCompact) 34.dp else 38.dp
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(13.dp))
                                                .clickable { selectTab(nav.index) }
                                                .padding(vertical = 1.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(circleSize)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (selected) p.accentGold
                                                        else if (darkTheme) Color.White.copy(alpha = 0.12f)
                                                        else p.surfaceBg.copy(alpha = 0.92f)
                                                    )
                                                    .border(
                                                        width = if (selected) 0.dp else 0.9.dp,
                                                        color = if (selected) Color.Transparent else glassBorderCol.copy(alpha = 0.65f),
                                                        shape = CircleShape
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = nav.icon,
                                                    contentDescription = nav.title,
                                                    tint = if (selected) p.onAccent else p.textPrimary,
                                                    modifier = Modifier.size(if (glassDockCompact) 17.dp else 18.dp)
                                                )
                                            }
                                            if (!glassDockCompact) {
                                                Text(
                                                    text = nav.title,
                                                    color = if (selected) p.accentGold else p.textPrimary,
                                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                // Bottom Vertical Height Resize Grip
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                        .pointerInput(containerHeightPx) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val deltaFrac = dragAmount.y / containerHeightPx.coerceAtLeast(1f)
                                                dockHeightFraction = (dockHeightFraction + deltaFrac).coerceIn(0.30f, 0.80f)
                                            }
                                        }
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(24.dp)
                                                .height(2.5.dp)
                                                .clip(CircleShape)
                                                .background(p.accentGold.copy(alpha = 0.72f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(2.dp)
                                                .clip(CircleShape)
                                                .background(p.accentGold.copy(alpha = 0.45f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showSplitTunnelDialog) {
                    PerAppProxySplitTunnelDialog(
                        currentMode = settings.splitTunnelMode,
                        selectedPackagesCsv = settings.splitTunnelPackagesCsv,
                        installedApps = installedApps,
                        onRequestLoadApps = { incSys -> controller.loadInstalledAppsList(incSys) },
                        onDismiss = { showSplitTunnelDialog = false },
                        onSave = { mode, pkgs ->
                            controller.updateSplitTunnelConfig(mode, pkgs)
                            showSplitTunnelDialog = false
                        }
                    )
                }

                if (showHardwarePanel) {
                    AlanHardwareDiagnosticsDialog(
                        health = hardwareHealth,
                        isConnected = telemetry.isConnected,
                        tunnelMode = settings.tunnelMode,
                        onRefresh = { controller.refreshHardwareHealth() },
                        onForceGc = { controller.forceGcAndRefreshHardware() },
                        onResetPeaks = { controller.resetHardwarePeaks() },
                        onDismiss = { showHardwarePanel = false }
                    )
                }
            }
        }
    }
}
