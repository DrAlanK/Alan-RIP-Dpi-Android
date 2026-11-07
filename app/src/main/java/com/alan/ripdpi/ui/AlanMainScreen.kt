package com.alan.ripdpi.ui

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
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
    val titleFa: String,
    val titleEn: String,
    val icon: ImageVector
)

@Composable
fun AlanMainScreen(
    controller: AlanEngineController = remember { DemoAlanEngineController() },
    @Suppress("UNUSED_PARAMETER") initialDarkTheme: Boolean = true
) {
    val context = LocalContext.current
    val uiPrefs = remember(context) {
        context.getSharedPreferences("alan_ui_prefs", Context.MODE_PRIVATE)
    }
    var appLang by remember {
        val saved = uiPrefs.getString("ui_lang", "FA") ?: "FA"
        mutableStateOf(if (saved == "EN") AlanLang.EN else AlanLang.FA)
    }

    val toggleLanguage: () -> Unit = {
        val next = if (appLang == AlanLang.FA) AlanLang.EN else AlanLang.FA
        appLang = next
        uiPrefs.edit().putString("ui_lang", next.name).apply()
    }

    val settings by controller.settings.collectAsState()
    val darkTheme = settings.darkTheme
    var currentTab by rememberSaveable(settings.lastActiveTab) {
        mutableIntStateOf(settings.lastActiveTab.coerceIn(0, 6))
    }
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
    val cleanIpHits by controller.cleanIpHits.collectAsState()
    val cleanIpState by controller.cleanIpState.collectAsState()

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
        val layoutDir = if (appLang == AlanLang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

        val navItems = remember {
            listOf(
                SimpleNavSpec(0, "خانه", "Home", Icons.Outlined.Home),
                SimpleNavSpec(1, "کانفیگ‌ها", "Profiles", Icons.Outlined.Description),
                SimpleNavSpec(2, "اسکنر متد", "Method Scanner", Icons.Outlined.Speed),
                SimpleNavSpec(3, "اسکنر IP تمیز", "Clean IP Scanner", Icons.Outlined.Radar),
                SimpleNavSpec(4, "تنظیمات عمومی", "General Settings", Icons.Outlined.Settings),
                SimpleNavSpec(5, "تنظیمات Xray", "Xray Settings", Icons.Outlined.Tune),
                SimpleNavSpec(6, "لاگ‌ها", "Logs", Icons.Outlined.Terminal)
            )
        }

        val drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)

        CompositionLocalProvider(
            LocalAlanLang provides appLang,
            LocalLayoutDirection provides layoutDir
        ) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = p.surfaceBg,
                        drawerContentColor = p.textPrimary,
                        drawerShape = drawerShape,
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .border(BorderStroke(1.dp, p.cardBorder), drawerShape)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 16.dp)
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
                                        .size(60.dp)
                                        .alanGlassCard(cornerRadius = 18.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.alan_brand_logo),
                                        contentDescription = APP_NAME,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                LtrContent {
                                    Text(
                                        text = APP_NAME,
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                }
                                Text(
                                    text = tr("ابزار هوشمند عبور از DPI", "Smart Anti-DPI Suite"),
                                    color = p.textMuted,
                                    fontSize = 11.sp
                                )

                                Spacer(Modifier.height(18.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    navItems.forEach { item ->
                                        val selected = currentTab == item.index
                                        val itemTitle = if (appLang == AlanLang.FA) item.titleFa else item.titleEn
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                                .then(
                                                    if (selected) {
                                                        Modifier
                                                            .alanNeuInset(13.dp)
                                                            .border(1.dp, p.glowBorder, RoundedCornerShape(13.dp))
                                                    } else {
                                                        Modifier.clip(RoundedCornerShape(13.dp))
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
                                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = itemTitle,
                                                    tint = if (selected) p.accentGold else p.textSecondary,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                                Text(
                                                    text = itemTitle,
                                                    color = if (selected) p.textPrimary else p.textSecondary,
                                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    fontSize = 13.sp,
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
                                    .alanGlassCard(cornerRadius = 14.dp)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr("حالت خودکار هوشمند", "Smart Auto-Pilot"),
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                    LtrContent {
                                        Text(
                                            text = "v$APP_VERSION",
                                            color = p.textMuted,
                                            fontFamily = FontFamily.Monospace,
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
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            // Menu Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .alanGlassCard(cornerRadius = 11.dp)
                                    .clickable { scope.launch { drawerState.open() } }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Menu,
                                    contentDescription = tr("منو", "Menu"),
                                    tint = p.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Brand Title + Subtle Active Status Dot
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                telemetry.isConnected -> p.emerald
                                                telemetry.isConnecting -> p.accentGold
                                                else -> p.textMuted.copy(alpha = 0.45f)
                                            }
                                        )
                                )
                                LtrContent {
                                    Text(
                                        text = APP_NAME.uppercase(),
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        letterSpacing = 0.9.sp
                                    )
                                }
                            }

                            // Language Switch (FA/EN) + Theme Switch (No Xray badge!)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(38.dp)
                                        .width(42.dp)
                                        .alanGlassCard(cornerRadius = 11.dp)
                                        .clickable { toggleLanguage() }
                                ) {
                                    LtrContent {
                                        Text(
                                            text = if (appLang == AlanLang.FA) "EN" else "FA",
                                            color = p.accentGold,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .alanGlassCard(cornerRadius = 11.dp)
                                        .clickable { controller.updateSettings(mapOf("darkTheme" to !darkTheme)) }
                                ) {
                                    Icon(
                                        imageVector = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                        contentDescription = tr("تغییر تم", "Toggle Theme"),
                                        tint = p.accentGold,
                                        modifier = Modifier.size(19.dp)
                                    )
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
                                onActivateAutoMode = { connectNow ->
                                    controller.activateAutoModeAndConnect(connectNow)
                                },
                                onImportFromClipboard = { controller.importFromClipboard() },
                                onSelectProfile = { idx -> controller.selectConfig(idx) }
                            )
                            1 -> ProfilesTabScreen(
                                profiles = profiles,
                                subscriptions = subscriptions,
                                vectors = vectors,
                                onSelectConfig = { idx -> controller.selectConfig(idx) },
                                onImportFromClipboard = { controller.importFromClipboard() },
                                onHuntBest = {
                                    controller.activateAutoModeAndConnect(true)
                                    selectTab(0)
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
                                }
                            )
                            4 -> GeneralSettingsScreen(
                                settings = settings,
                                vectors = vectors,
                                isFindingBestMethod = isFindingBestMethod,
                                autoMethodStatus = autoMethodStatus,
                                onAutoDetectBestMethod = { controller.autoDetectBestMethod() },
                                onReplaceSettings = { controller.updateSettings(it) },
                                onPatchSettings = { controller.updateSettings(it) }
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
                    }
                }
            }
        }
    }
}