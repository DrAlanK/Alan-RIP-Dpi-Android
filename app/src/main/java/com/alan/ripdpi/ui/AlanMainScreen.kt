package com.alan.ripdpi.ui

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.alan.ripdpi.model.APP_NAME
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
    var glassDockExpanded by rememberSaveable { mutableStateOf(false) }
    var glassDockCompact by rememberSaveable { mutableStateOf(false) }

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

    val vectors = remember(controller) { controller.getVectors() }

    val selectTab: (Int) -> Unit = { idx ->
        currentTab = idx
        controller.updateSettings(mapOf("lastActiveTab" to idx))
    }

    BackHandler(enabled = glassDockExpanded || currentTab != 0) {
        if (glassDockExpanded) {
            glassDockExpanded = false
        } else {
            selectTab(0)
        }
    }

    AlanCyberTheme(darkTheme = darkTheme) {
        val p = LocalAlanPalette.current

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
            LocalLayoutDirection provides if (AlanI18n.lang == "en") LayoutDirection.Ltr else LayoutDirection.Rtl
        ) {
            Scaffold(
                containerColor = p.rootBg,
                topBar = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(p.rootBg)
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        // Clean Brand Pill (No Hamburger Menu!)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .alanNeuInset(cornerRadius = 16.dp)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
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
                                    fontSize = 14.5.sp,
                                    letterSpacing = 0.9.sp
                                )
                            }
                        }

                        // EN/FA & Dark/Light Theme Toggles
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(40.dp)
                                    .width(44.dp)
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
                }
            ) { innerPadding ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(p.rootBg)
                        .padding(innerPadding)
                        .navigationBarsPadding()
                ) {
                    val density = LocalDensity.current
                    val containerWidthPx = with(density) { maxWidth.toPx() }
                    val containerHeightPx = with(density) { maxHeight.toPx() }
                    val marginPx = with(density) { 10.dp.toPx() }
                    val orbSizePx = with(density) { 54.dp.toPx() }

                    // Persistent draggable coordinates (in LTR pixel space) for the Assistive Glass Button & Floating Sidebar
                    var posX by rememberSaveable { mutableFloatStateOf(-1f) }
                    var posY by rememberSaveable { mutableFloatStateOf(-1f) }

                    if (posX < 0f || posY < 0f) {
                        posX = (containerWidthPx - orbSizePx - marginPx).coerceAtLeast(marginPx)
                        posY = (containerHeightPx * 0.28f).coerceIn(marginPx, (containerHeightPx - orbSizePx - marginPx).coerceAtLeast(marginPx))
                    }

                    // 1. Full-Width Unobstructed Screen Content (NEVER displaced by the floating sidebar!)
                    Box(modifier = Modifier.fillMaxSize()) {
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
                                onPatchSettings = { controller.updateSettings(it) }
                            )
                            6 -> LogsTabScreen(
                                logs = logs,
                                onClearLogs = { controller.clearLogs() }
                            )
                            else -> AboutTabScreen()
                        }
                    }

                    // 2. Tap-outside scrim when Floating Glass Sidebar is open
                    if (glassDockExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = if (darkTheme) 0.20f else 0.08f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { glassDockExpanded = false }
                        )
                    }

                    // 3. Draggable & Resizable iPhone-Style Frosted Glass Assistive Orb + Detached Cute Floating Sidebar
                    // Positioned in LTR coordinates so dragging works identically in FA & EN!
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val dockWidthDp by animateDpAsState(
                            targetValue = if (glassDockCompact) 58.dp else 76.dp,
                            animationSpec = tween(180),
                            label = "glassDockW"
                        )
                        val dockWidthPx = with(density) { dockWidthDp.toPx() }
                        val maxDockHeightDp = (maxHeight * 0.72f).coerceIn(280.dp, 500.dp)
                        val maxDockHeightPx = with(density) { maxDockHeightDp.toPx() }

                        val clampedOrbX = posX.coerceIn(marginPx, (containerWidthPx - orbSizePx - marginPx).coerceAtLeast(marginPx))
                        val clampedOrbY = posY.coerceIn(marginPx, (containerHeightPx - orbSizePx - marginPx).coerceAtLeast(marginPx))

                        val clampedDockX = posX.coerceIn(marginPx, (containerWidthPx - dockWidthPx - marginPx).coerceAtLeast(marginPx))
                        val clampedDockY = posY.coerceIn(marginPx, (containerHeightPx - maxDockHeightPx - marginPx).coerceAtLeast(marginPx))

                        val glassBg = if (darkTheme) {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xD62A3346),
                                    Color(0xCC1A202D)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xE8FFFFFF),
                                    Color(0xDCE2EBF7)
                                )
                            )
                        }
                        val glassBorderCol = if (darkTheme) {
                            Color.White.copy(alpha = 0.24f)
                        } else {
                            Color.White.copy(alpha = 0.90f)
                        }

                        // Collapsed State: Cute Translucent iPhone AssistiveTouch Glass Orb
                        AnimatedVisibility(
                            visible = !glassDockExpanded,
                            enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.78f),
                            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.78f),
                            modifier = Modifier.offset { IntOffset(clampedOrbX.roundToInt(), clampedOrbY.roundToInt()) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(54.dp)
                                    .shadow(elevation = 14.dp, shape = CircleShape, clip = false)
                                    .clip(CircleShape)
                                    .background(glassBg)
                                    .border(1.4.dp, glassBorderCol, CircleShape)
                                    .pointerInput(containerWidthPx, containerHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            posX = (posX + dragAmount.x).coerceIn(marginPx, (containerWidthPx - orbSizePx - marginPx).coerceAtLeast(marginPx))
                                            posY = (posY + dragAmount.y).coerceIn(marginPx, (containerHeightPx - orbSizePx - marginPx).coerceAtLeast(marginPx))
                                        }
                                    }
                                    .clickable { glassDockExpanded = true }
                            ) {
                                // Inner concentric glass ring (iPhone AssistiveTouch look)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (darkTheme) Color.White.copy(alpha = 0.12f)
                                            else p.accentGold.copy(alpha = 0.14f)
                                        )
                                        .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.30f else 0.75f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = activeNav.icon,
                                        contentDescription = activeNav.title,
                                        tint = if (telemetry.isConnected) p.emerald else p.accentGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Expanded State: Detached Translucent Floating Glass Sidebar (Draggable + Resizable)
                        AnimatedVisibility(
                            visible = glassDockExpanded,
                            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.85f),
                            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.85f),
                            modifier = Modifier.offset { IntOffset(clampedDockX.roundToInt(), clampedDockY.roundToInt()) }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier
                                    .width(dockWidthDp)
                                    .heightIn(max = maxDockHeightDp)
                                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(28.dp), clip = false)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(glassBg)
                                    .border(1.3.dp, glassBorderCol, RoundedCornerShape(28.dp))
                                    .pointerInput(containerWidthPx, containerHeightPx, dockWidthPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            posX = (posX + dragAmount.x).coerceIn(marginPx, (containerWidthPx - dockWidthPx - marginPx).coerceAtLeast(marginPx))
                                            posY = (posY + dragAmount.y).coerceIn(marginPx, (containerHeightPx - maxDockHeightPx - marginPx).coerceAtLeast(marginPx))
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp)
                            ) {
                                // Top Mini Pill Handle + Resize + Collapse Controls
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(3.5.dp)
                                        .clip(CircleShape)
                                        .background(p.textMuted.copy(alpha = 0.55f))
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(23.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = if (darkTheme) 0.12f else 0.65f))
                                            .clickable { glassDockCompact = !glassDockCompact }
                                    ) {
                                        Icon(
                                            imageVector = if (glassDockCompact) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                                            contentDescription = "تغییر اندازه",
                                            tint = p.accentGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(23.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = if (darkTheme) 0.12f else 0.65f))
                                            .clickable { glassDockExpanded = false }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "بستن",
                                            tint = p.textSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                // Scrollable Circular Nav Options
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(if (glassDockCompact) 7.dp else 9.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 2.dp)
                                ) {
                                    navItems.forEach { nav ->
                                        val selected = currentTab == nav.index
                                        val circleSize = if (glassDockCompact) 36.dp else 40.dp
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    selectTab(nav.index)
                                                    glassDockExpanded = false
                                                }
                                                .padding(vertical = 1.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(circleSize)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (selected) p.accentGold
                                                        else Color.White.copy(alpha = if (darkTheme) 0.10f else 0.62f)
                                                    )
                                                    .border(
                                                        width = if (selected) 0.dp else 0.9.dp,
                                                        color = if (selected) Color.Transparent else glassBorderCol.copy(alpha = 0.6f),
                                                        shape = CircleShape
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = nav.icon,
                                                    contentDescription = nav.title,
                                                    tint = if (selected) p.onAccent else p.textPrimary,
                                                    modifier = Modifier.size(if (glassDockCompact) 18.dp else 19.dp)
                                                )
                                            }
                                            if (!glassDockCompact) {
                                                Text(
                                                    text = nav.title,
                                                    color = if (selected) p.accentGold else p.textPrimary,
                                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    fontSize = 9.5.sp,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
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
                }
            }
        }
    }
}
