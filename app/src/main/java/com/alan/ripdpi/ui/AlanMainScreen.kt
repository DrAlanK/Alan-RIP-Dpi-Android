package com.alan.ripdpi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alan.ripdpi.R
import com.alan.ripdpi.model.APP_NAME
import com.alan.ripdpi.model.APP_VERSION
import kotlinx.coroutines.launch

private data class SimpleNavSpec(
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
        mutableIntStateOf(settings.lastActiveTab.coerceIn(0, 6))
    }
    var forcedScannerTarget by remember { mutableStateOf<Int?>(null) }
    var showSplitTunnelDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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

    BackHandler(enabled = drawerState.isOpen || currentTab != 0) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            selectTab(0)
        }
    }

    AlanCyberTheme(darkTheme = darkTheme) {
        val p = LocalAlanPalette.current

        val navItems = remember {
            listOf(
                SimpleNavSpec(0, "خانه", Icons.Outlined.Home),
                SimpleNavSpec(1, "کانفیگ‌ها", Icons.Outlined.Description),
                SimpleNavSpec(2, "اسکنر متد", Icons.Outlined.Speed),
                SimpleNavSpec(3, "IP تمیز و DoH", Icons.Outlined.Radar),
                SimpleNavSpec(4, "تنظیمات عمومی", Icons.Outlined.Settings),
                SimpleNavSpec(5, "تنظیمات Xray", Icons.Outlined.Tune),
                SimpleNavSpec(6, "لاگ‌ها", Icons.Outlined.Terminal)
            )
        }

        val drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = p.surfaceBg,
                        drawerContentColor = p.textPrimary,
                        drawerShape = drawerShape,
                        modifier = Modifier
                            .width(284.dp)
                            .fillMaxHeight()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(horizontal = 15.dp, vertical = 18.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .alanGlassCard(cornerRadius = 20.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.alan_brand_logo),
                                        contentDescription = APP_NAME,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                LtrContent {
                                    Text(
                                        text = APP_NAME,
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 17.sp
                                    )
                                }
                                Text(
                                    text = "موتور هوشمند ضد فیلترینگ و DPI",
                                    color = p.textMuted,
                                    fontSize = 11.sp
                                )

                                Spacer(Modifier.height(20.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    navItems.forEach { item ->
                                        val selected = currentTab == item.index
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                                .then(
                                                    if (selected) {
                                                        Modifier.alanNeuInset(cornerRadius = 14.dp, accented = true)
                                                    } else {
                                                        Modifier.clip(RoundedCornerShape(14.dp))
                                                    }
                                                )
                                                .clickable {
                                                    selectTab(item.index)
                                                    scope.launch { drawerState.close() }
                                                }
                                        ) {
                                            if (selected) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .fillMaxHeight()
                                                        .background(p.accentGold)
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.title,
                                                    tint = if (selected) p.accentGold else p.textSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = item.title,
                                                    color = if (selected) p.textPrimary else p.textSecondary,
                                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    fontSize = 13.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alanGlassCard(cornerRadius = 15.dp)
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "حالت خودکار ابری",
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    LtrContent {
                                        Text(
                                            text = "v$APP_VERSION",
                                            color = p.textMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                AlanPillSwitch(
                                    checked = settings.autoPilot,
                                    onCheckedChange = { controller.updateSettings(mapOf("autoPilot" to it)) }
                                )
                            }
                        }
                    }
                }
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
                            AlanNeuIconButton(
                                icon = Icons.Outlined.Menu,
                                tint = p.textPrimary,
                                size = 42.dp,
                                onClick = { scope.launch { drawerState.open() } }
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .alanNeuInset(cornerRadius = 16.dp)
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
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
                                        fontSize = 15.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            AlanNeuIconButton(
                                icon = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                tint = p.accentGold,
                                size = 42.dp,
                                onClick = { controller.updateSettings(mapOf("darkTheme" to !darkTheme)) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(p.rootBg)
                            .padding(innerPadding)
                            .navigationBarsPadding()
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
                                onOpenDohScanner = { selectTab(3) }
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
                                    controller.sendAllConfigsToDpiLab(true, true)
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
                                onDeleteSubscription = { idx -> controller.deleteSubscription(idx) },
                                onQuickCloneCleanIp = { idx, count ->
                                    controller.quickCloneConfigWithCleanIps(idx, count)
                                }
                            )
                            2 -> ScannerTabScreen(
                                profiles = profiles,
                                scanRows = scanRows,
                                scanState = scanState,
                                forcedTargetIndex = forcedScannerTarget,
                                onConsumedForcedTarget = { forcedScannerTarget = null },
                                onStartMultiMethodScan = { scopeMode, indices, autoConn, useCleanIp ->
                                    controller.startMultiTargetMethodScan(scopeMode, indices, autoConn, useCleanIp)
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
}
