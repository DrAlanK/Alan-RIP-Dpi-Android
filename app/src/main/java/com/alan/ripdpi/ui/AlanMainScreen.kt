package com.alan.ripdpi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    val icon: ImageVector,
    val badge: String = ""
)

@Composable
fun AlanMainScreen(
    controller: AlanEngineController = remember { DemoAlanEngineController() },
    initialDarkTheme: Boolean = true
) {
    val settings by controller.settings.collectAsState()
    val darkTheme = settings.darkTheme
    var currentTab by rememberSaveable(settings.lastActiveTab) { mutableIntStateOf(settings.lastActiveTab) }
    var forcedScannerTarget by remember { mutableStateOf<Int?>(null) }

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

    val vectors = remember(controller) { controller.getVectors() }

    BackHandler(enabled = drawerState.isOpen || currentTab != 0) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            currentTab = 0
            controller.updateSettings(mapOf("lastActiveTab" to 0))
        }
    }

    AlanCyberTheme(darkTheme = darkTheme) {
        val p = LocalAlanPalette.current

        val navItems = remember(profiles.size) {
            listOf(
                SimpleNavSpec(0, "خانه", Icons.Outlined.Home),
                SimpleNavSpec(1, "کانفیگ‌ها", Icons.Outlined.Description, if (profiles.isNotEmpty()) "${profiles.size}" else ""),
                SimpleNavSpec(2, "اسکنر متد", Icons.Outlined.Speed),
                SimpleNavSpec(3, "تنظیمات عمومی", Icons.Outlined.Settings),
                SimpleNavSpec(4, "تنظیمات Xray", Icons.Outlined.Tune),
                SimpleNavSpec(5, "لاگ‌ها", Icons.Outlined.Terminal)
            )
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = p.surfaceBg,
                        drawerContentColor = p.textPrimary,
                        drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                        modifier = Modifier
                            .width(292.dp)
                            .fillMaxHeight()
                            .border(
                                width = 1.dp,
                                color = p.cardBorder,
                                shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)
                            )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 18.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.alan_brand_logo),
                                    contentDescription = APP_NAME,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                LtrContent {
                                    Text(
                                        text = APP_NAME,
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }
                                Text(
                                    text = "ابزارهای امن شبکه",
                                    color = p.textMuted,
                                    fontSize = 11.sp
                                )

                                Spacer(Modifier.height(22.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    navItems.forEach { item ->
                                        val selected = currentTab == item.index
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                                .clip(RoundedCornerShape(13.dp))
                                                .background(if (selected) p.accentSoftBg else Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (selected) p.glowBorder else Color.Transparent,
                                                    shape = RoundedCornerShape(13.dp)
                                                )
                                                .clickable {
                                                    currentTab = item.index
                                                    controller.updateSettings(mapOf("lastActiveTab" to item.index))
                                                    scope.launch { drawerState.close() }
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.5.dp)
                                                    .fillMaxHeight()
                                                    .background(if (selected) p.accentGold else Color.Transparent)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                if (item.badge.isNotBlank()) {
                                                    LtrContent {
                                                        Text(
                                                            text = item.badge,
                                                            color = p.accentGold,
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(p.cardBg)
                                    .border(1.dp, p.cardBorder, RoundedCornerShape(13.dp))
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Column {
                                    Text("اتصال خودکار هوشمند", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    LtrContent {
                                        Text("v$APP_VERSION", color = p.textMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
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
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Surface(
                                onClick = { scope.launch { drawerState.open() } },
                                shape = RoundedCornerShape(11.dp),
                                color = p.cardBg,
                                border = BorderStroke(1.dp, p.cardBorder)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Outlined.Menu, "منو", tint = p.textPrimary, modifier = Modifier.size(22.dp))
                                }
                            }

                            LtrContent {
                                Text(
                                    text = APP_NAME.uppercase(),
                                    color = p.accentGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    letterSpacing = 1.2.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    onClick = { controller.updateSettings(mapOf("darkTheme" to !darkTheme)) },
                                    shape = RoundedCornerShape(11.dp),
                                    color = p.cardBg,
                                    border = BorderStroke(1.dp, p.cardBorder)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                            contentDescription = "تغییر تم",
                                            tint = p.accentGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        currentTab = 4
                                        controller.updateSettings(mapOf("lastActiveTab" to 4))
                                    },
                                shape = RoundedCornerShape(18.dp),
                                color = p.cardBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (telemetry.isConnected) p.emerald.copy(alpha = 0.7f) else p.glowBorder
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.alan_brand_logo),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    LtrContent {
                                        Text(
                                            text = "Xray",
                                            color = if (telemetry.isConnected) p.emerald else p.accentGold,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            }
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
                                onToggleConnection = { controller.toggleConnection() },
                                onHuntBest = { controller.huntBestAndConnect() },
                                onImportFromClipboard = { controller.importFromClipboard() },
                                onSelectProfile = { idx -> controller.selectConfig(idx) },
                                onUpdateSettingsPatch = { patch -> controller.updateSettings(patch) }
                            )
                            1 -> ProfilesTabScreen(
                                profiles = profiles,
                                subscriptions = subscriptions,
                                vectors = vectors,
                                onSelectConfig = { idx -> controller.selectConfig(idx) },
                                onImportFromClipboard = { controller.importFromClipboard() },
                                onHuntBest = {
                                    controller.huntBestAndConnect()
                                    currentTab = 0
                                },
                                onQuickPingAll = { controller.quickPingConfigs() },
                                onSortConfigs = { by -> controller.sortConfigs(by) },
                                onDeduplicate = { controller.deduplicateConfigs() },
                                onRemoveDead = { controller.removeDeadConfigs() },
                                onExportVerifiedEarlyData = {
                                    controller.exportConfigs("verified", settings.wsEarlyData.coerceAtLeast(2048))
                                },
                                onClearAll = { controller.clearAllConfigs() },
                                onDeleteConfig = { idx -> controller.deleteConfig(idx) },
                                onGetEditable = { idx -> controller.getConfigEditable(idx) },
                                onSaveEditable = { idx, fields -> controller.saveConfigEditable(idx, fields) },
                                onSendToMethodScanner = { idx ->
                                    forcedScannerTarget = idx
                                    currentTab = 2
                                },
                                onAddSubscription = { url, title -> controller.addSubscription(url, title) },
                                onRefreshSubscriptions = { controller.refreshSubscriptions() },
                                onDeleteSubscription = { idx -> controller.deleteSubscription(idx) }
                            )
                            2 -> ScannerTabScreen(
                                profiles = profiles,
                                scanRows = scanRows,
                                scanState = scanState,
                                forcedTargetIndex = forcedScannerTarget,
                                onConsumedForcedTarget = { forcedScannerTarget = null },
                                onStartScan = { mode, targetIdx, autoConn, prune ->
                                    controller.startScanner(mode, targetIdx, autoConn, prune)
                                },
                                onStopScan = { controller.stopScanner() },
                                onApplyVectorSingle = { vecId, tIdx, ep ->
                                    controller.applyScannedVector(vecId, tIdx, ep, false)
                                },
                                onApplyVectorAll = { vecId, ep ->
                                    controller.applyVectorToAllConfigs(vecId, ep)
                                },
                                onApplyVectorAndConnect = { vecId, tIdx, ep ->
                                    controller.applyScannedVector(vecId, tIdx, ep, true)
                                    currentTab = 0
                                },
                                onConnectScannedProfile = { tIdx, boundVec, ep ->
                                    controller.connectScannedProfile(tIdx, boundVec, ep)
                                    currentTab = 0
                                }
                            )
                            3 -> GeneralSettingsScreen(
                                settings = settings,
                                vectors = vectors,
                                isFindingBestMethod = isFindingBestMethod,
                                autoMethodStatus = autoMethodStatus,
                                onAutoDetectBestMethod = { controller.autoDetectBestMethod() },
                                onReplaceSettings = { controller.updateSettings(it) },
                                onPatchSettings = { controller.updateSettings(it) }
                            )
                            4 -> XraySettingsScreen(
                                settings = settings,
                                onPatchSettings = { controller.updateSettings(it) }
                            )
                            5 -> LogsTabScreen(
                                logs = logs,
                                onClearLogs = { controller.clearLogs() }
                            )
                        }
                    }
                }
            }
        }
    }
}
