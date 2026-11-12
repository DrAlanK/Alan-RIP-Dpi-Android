package com.alan.ripdpi.core

import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.telephony.TelephonyManager
import com.alan.ripdpi.model.BypassVector
import com.alan.ripdpi.model.CountryPaletteRegistry
import com.alan.ripdpi.model.CountryThemeSpec
import com.alan.ripdpi.model.EngineState
import com.alan.ripdpi.model.VectorCatalog
import com.alan.ripdpi.model.formatByteRate
import com.alan.ripdpi.model.formatUptimeHms
import com.alan.ripdpi.ui.AlanEngineController
import com.alan.ripdpi.ui.ConnectionStageStep
import com.alan.ripdpi.ui.EditableConfigFields
import com.alan.ripdpi.ui.EngineSettings
import com.alan.ripdpi.ui.LiveVectorProbeItem
import com.alan.ripdpi.ui.ProfileItem
import com.alan.ripdpi.ui.ScanResultRow
import com.alan.ripdpi.ui.ScanUiState
import com.alan.ripdpi.ui.SubscriptionItem
import com.alan.ripdpi.ui.TaskProgressState
import com.alan.ripdpi.ui.TelemetrySnapshot
import com.alan.ripdpi.ui.VectorSpec
import com.alan.ripdpi.vpn.AlanVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import android.util.Base64
import java.security.MessageDigest
import com.alan.ripdpi.ui.DohServerUiItem
import com.alan.ripdpi.ui.DohScanUiState
import com.alan.ripdpi.ui.RepoLocationOption
import com.alan.ripdpi.ui.RepoScanReportSummary

class AlanAutoPilotEngine private constructor(context: Context) : AlanEngineController {

    companion object {
        const val OFFICIAL_AUTO_SUB_URL =
            "https://raw.githubusercontent.com/DrAlanK/Alan-RIP-Dpi/refs/heads/main/Alan-RIP-Dpi-Sub.txt"
        private const val OFFICIAL_AUTO_SUB_ID = "sub-alan-official"
        private const val OFFICIAL_AUTO_SUB_TITLE = "Alan RIP-DPI Official Sub"
        private const val SPARKLINE_WINDOW = 28

        @Volatile
        private var INSTANCE: AlanAutoPilotEngine? = null

        fun getInstance(context: Context): AlanAutoPilotEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlanAutoPilotEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private data class SandboxNodeEvaluation(
        val qualifiedVector: BypassVector?,
        val softVector: BypassVector?,
        val latencyMs: Long
    )

    private val appContext = context.applicationContext
    private val geoIpResolver = GeoIpQuickResolver(appContext)
    private val cleanIpEngine = CleanIpScannerEngine(appContext)
    private var cleanIpScanJob: Job? = null
    private val _cleanIpHits = MutableStateFlow<List<com.alan.ripdpi.ui.CleanIpHit>>(cleanIpEngine.getCachedHits())
    override val cleanIpHits: StateFlow<List<com.alan.ripdpi.ui.CleanIpHit>> = _cleanIpHits.asStateFlow()
    private val _cleanIpState = MutableStateFlow(
        com.alan.ripdpi.ui.CleanIpScanState(
            loadedSubnetsCount = cleanIpEngine.loadCachedOrDefaultSubnets().size
        )
    )
    override val cleanIpState: StateFlow<com.alan.ripdpi.ui.CleanIpScanState> = _cleanIpState.asStateFlow()
    private val geoProbeSemaphore = Semaphore(5)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pipelineMutex = Mutex()
    private val storageLock = Any()
    private val historyLock = Any()

    private val opGeneration = AtomicLong(0L)
    private val scanGeneration = AtomicLong(0L)

    private var connectionJob: Job? = null
    private var scannerJob: Job? = null
    private var bestMethodJob: Job? = null
    private val _isFindingBestMethod = MutableStateFlow(false)
    override val isFindingBestMethod: StateFlow<Boolean> = _isFindingBestMethod.asStateFlow()
    private val _autoMethodStatus = MutableStateFlow("")
    override val autoMethodStatus: StateFlow<String> = _autoMethodStatus.asStateFlow()

    private val _availableRepoLocations = MutableStateFlow<List<RepoLocationOption>>(emptyList())
    override val availableRepoLocations: StateFlow<List<RepoLocationOption>> = _availableRepoLocations.asStateFlow()

    private val _repoScanReport = MutableStateFlow(RepoScanReportSummary())
    override val repoScanReport: StateFlow<RepoScanReportSummary> = _repoScanReport.asStateFlow()

    private val _dohServers = MutableStateFlow<List<DohServerUiItem>>(emptyList())
    override val dohServers: StateFlow<List<DohServerUiItem>> = _dohServers.asStateFlow()

    private val _dohScanState = MutableStateFlow(DohScanUiState())
    override val dohScanState: StateFlow<DohScanUiState> = _dohScanState.asStateFlow()

    private var dohScanJob: Job? = null
    private val dohStopSignal = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var cachedAutoRepoNodes: List<ProfileItem> = emptyList()
    private var watchdogFailureStreak = 0
    private var configuredBridgePort = 20809
    private var selectedProfileUidPersist = ""

    private val profileSubOwnerMap = ConcurrentHashMap<String, String>()
    private val rxHistoryQueue = ArrayDeque<Float>(SPARKLINE_WINDOW)
    private val txHistoryQueue = ArrayDeque<Float>(SPARKLINE_WINDOW)
    private val pingHistoryQueue = ArrayDeque<Float>(SPARKLINE_WINDOW)

    @Volatile
    private var vpnPermissionStarter: ((() -> Unit) -> Unit)? = null

    private val logLinesBuffer = Collections.synchronizedList(ArrayList<String>(400))
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    override val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val internalAutoNodes = Collections.synchronizedList(ArrayList<ProfileItem>())
    private val xrayCoreManager = XrayCoreManager(appContext) { coreMsg ->
        appendLog(coreMsg)
    }

    @Volatile
    private var liveBridge: FullDuplexMutatorBridge? = null

    @Volatile
    private var liveSession: CompiledXraySession? = null

    @Volatile
    private var connectedTimestampMs: Long = 0L

    private var prevTxBytes: Long = 0L
    private var prevRxBytes: Long = 0L

    private val settingsFile = File(appContext.filesDir, "settings.json")
    private val repositoryFile = File(appContext.filesDir, "repository.json")
    private val subscriptionsFile = File(appContext.filesDir, "subscriptions.json")
    private val scanCacheFile = File(appContext.filesDir, "scan_cache.json")

    private val _engineState = MutableStateFlow(EngineState.DISCONNECTED)

    private val _settings = MutableStateFlow(EngineSettings())
    override val settings: StateFlow<EngineSettings> = _settings.asStateFlow()

    private val _profiles = MutableStateFlow<List<ProfileItem>>(emptyList())
    override val profiles: StateFlow<List<ProfileItem>> = _profiles.asStateFlow()

    private val _selectedIndex = MutableStateFlow(-1)

    private val _telemetry = MutableStateFlow(TelemetrySnapshot())
    override val telemetry: StateFlow<TelemetrySnapshot> = _telemetry.asStateFlow()

    private val _taskProgress = MutableStateFlow(TaskProgressState())
    override val taskProgress: StateFlow<TaskProgressState> = _taskProgress.asStateFlow()

    private val _scanRows = MutableStateFlow<List<ScanResultRow>>(emptyList())
    override val scanRows: StateFlow<List<ScanResultRow>> = _scanRows.asStateFlow()

    private val _scanState = MutableStateFlow(ScanUiState())
    override val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<SubscriptionItem>>(emptyList())
    override val subscriptions: StateFlow<List<SubscriptionItem>> = _subscriptions.asStateFlow()

    private val _connectionStages = MutableStateFlow<List<ConnectionStageStep>>(buildDefaultStages())
    override val connectionStages: StateFlow<List<ConnectionStageStep>> = _connectionStages.asStateFlow()

    private val _liveVectorProbes = MutableStateFlow<List<LiveVectorProbeItem>>(emptyList())
    override val liveVectorProbes: StateFlow<List<LiveVectorProbeItem>> = _liveVectorProbes.asStateFlow()


    init {
        BridgeDeepDiagnostics.globalDeepLogger = { msg -> appendLog(msg) }
        loadPersistentData()
        if (_settings.value.autoPilot) selectConfig(-1)
        loadScanCacheFromDisk()
        ensureOfficialSubscriptionRegistered()
        startTelemetryTicker()
        startSelfHealingWatchdog()
        triggerInstantGeoLocationProbes(_profiles.value.filter { it.countryCode == "UN" }, allowMicroTunnelForCdn = false)
        scope.launch { delay(250L); appendLog("[Alan-RIP-Dpi v1.4.2] Core Ready (${xrayCoreManager.getXrayVersion()})") }
    }

    fun bindVpnPermissionRequester(requester: ((() -> Unit) -> Unit)?) {
        vpnPermissionStarter = requester
    }

    fun onTunFatalFailure(reason: String) {
        appendLog("TUN fatal error: $reason")
        disconnectTunnel()
        _engineState.value = EngineState.ERROR
        _telemetry.update {
            it.copy(
                uiPhase = "ERROR",
                isConnecting = false,
                isConnected = false,
                statusHeadline = "خطا در برقراری تونل",
                statusDetail = reason,
                buttonText = "تلاش مجدد"
            )
        }
    }

    private fun runWithVpnConsent(action: () -> Unit) {
        val prep = try {
            VpnService.prepare(appContext)
        } catch (_: Throwable) {
            null
        }
        if (prep == null) {
            action()
            return
        }
        val requester = vpnPermissionStarter
        if (requester != null) {
            requester(action)
        } else {
            appendLog("VPN system permission required.")
            _telemetry.update {
                it.copy(
                    statusHeadline = "نیاز به مجوز VPN",
                    statusDetail = "لطفاً مجوز ساخت تونل VPN اندروید را تایید کنید."
                )
            }
        }
    }

    override fun toggleConnection() {
        val cur = _engineState.value
        val snap = _telemetry.value
        if (cur == EngineState.CONNECTED || snap.isConnecting || snap.isConnected) {
            disconnectTunnel()
            return
        }
        startSmartConnect()
    }

    private fun startSmartConnect() {
        runWithVpnConsent {
            val gen = opGeneration.incrementAndGet()
            connectionJob?.cancel()
            connectionJob = scope.launch {
                executeSmartConnectPipeline(gen)
            }
        }
    }

    fun disconnectTunnel() {
        opGeneration.incrementAndGet()
        connectionJob?.cancel()
        connectionJob = null
        scope.launch {
            pipelineMutex.withLock {
                teardownActiveRuntime()
                clearSparklines()
                _engineState.value = EngineState.DISCONNECTED
                _connectionStages.value = buildDefaultStages()
                _liveVectorProbes.value = emptyList()
                _taskProgress.value = TaskProgressState(active = false, percent = 0, message = "", stageIndex = 0)
                _telemetry.update {
                    it.copy(
                        uiPhase = "IDLE",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "آماده اتصال",
                        statusDetail = "موتور هوشمند آماده برقراری تونل امن است.",
                        buttonText = "اتصال هوشمند",
                        uptimeLabel = "00:00:00",
                        rxRateKbps = 0f,
                        txRateKbps = 0f,
                        rxRateLabel = "0 B/s",
                        txRateLabel = "0 B/s",
                        progressPercent = 0,
                        currentStageIndex = 0,
                        rxHistory = emptyList(),
                        txHistory = emptyList(),
                        pingHistory = emptyList()
                    )
                }
                appendLog("Tunnel disconnected.")
            }
        }
    }

    override fun huntBestAndConnect() {
        runWithVpnConsent {
            val gen = opGeneration.incrementAndGet()
            connectionJob?.cancel()
            connectionJob = scope.launch {
                executeHuntBestAndConnect(gen)
            }
        }
    }

    override fun selectConfig(index: Int) {
        val current = _profiles.value
        if (index < 0) {
            _selectedIndex.value = -1
            if (current.any { it.isSelected }) {
                _profiles.value = current.map { it.copy(isSelected = false) }
            }
            _settings.update { it.copy(autoPilot = true) }
            _telemetry.update {
                it.copy(
                    selectedIndex = -1,
                    activeProfileUid = "__SMART_AUTO__",
                    activeProfileName = if (it.isConnected) it.activeProfileName else "حالت خودکار • ریپازیتوری",
                    countryCode = if (it.isConnected) it.countryCode else "UN",
                    countryName = if (it.isConnected) it.countryName else "خودکار"
                )
            }
            saveSettingsToDisk()
            return
        }
        if (current.isEmpty()) return
        val safeIdx = index.coerceIn(current.indices)
        _selectedIndex.value = safeIdx
        val chosen = current[safeIdx]
        _profiles.value = current.mapIndexed { i, item -> item.copy(isSelected = i == safeIdx) }
        // Selecting a user config explicitly disables Auto-Repository mode so ONLY this user config is used!
        _settings.update { it.copy(autoPilot = false) }
        _telemetry.update {
            it.copy(
                selectedIndex = safeIdx,
                activeProfileUid = chosen.uid,
                activeProfileName = chosen.name,
                countryCode = chosen.countryCode,
                countryName = chosen.countryName,
                flagEmoji = chosen.flagEmoji,
                flagStripeColors = chosen.flagStripeColors,
                accentPrimaryColor = chosen.accentPrimaryColor,
                accentSecondaryColor = chosen.accentSecondaryColor
            )
        }
        saveSettingsToDisk()
    }

    override fun importFromClipboard() {
        scope.launch {
            val text = kotlinx.coroutines.withContext(Dispatchers.Main) {
                readClipboardText()
            }
            if (text.isBlank()) {
                appendLog("کلیپ‌بورد خالی است.")
            } else {
                importConfigs(text)
            }
        }
    }

    override fun importConfigs(rawText: String) {
        scope.launch {
            val effectiveText = if (rawText.isNotBlank()) {
                rawText.trim()
            } else {
                kotlinx.coroutines.withContext(Dispatchers.Main) { readClipboardText().trim() }
            }

            if (effectiveText.isEmpty()) {
                appendLog("Import skipped: input text is empty.")
                return@launch
            }

            if (
                !effectiveText.contains("\n") &&
                (effectiveText.startsWith("http://", true) || effectiveText.startsWith("https://", true))
            ) {
                addSubscription(effectiveText, "Imported Sub")
                return@launch
            }

            val extracted = UriConfigCodec.extractConfigLinesFromSubscriptionBody(effectiveText)
            if (extracted.isEmpty()) {
                appendLog("Import failed: no valid VLESS/VMess/Trojan/SS links found.")
                return@launch
            }

            val parsedItems = extracted.mapNotNull { UriConfigCodec.parseUriToProfileItem(it)?.takeIf { !CleanIpScannerEngine.isSubscriptionAnnouncementOrFakeNode(it) } }
            if (parsedItems.isEmpty()) return@launch

            val list = _profiles.value.toMutableList()
            val seenUris = list.map { it.uri }.toHashSet()
            var added = 0
            for (item in parsedItems) {
                if (seenUris.add(item.uri)) {
                    list.add(item)
                    added++
                }
            }
            _profiles.value = list
            if (list.isNotEmpty() && _selectedIndex.value !in list.indices) {
                selectConfig(0)
            } else if (_selectedIndex.value in list.indices) {
                selectConfig(_selectedIndex.value)
            }
            saveRepositoryToDisk()
            appendLog("Imported $added server profile(s).")
            triggerInstantGeoLocationProbes(parsedItems, allowMicroTunnelForCdn = parsedItems.size <= 2)
        }
    }

    override fun quickPingConfigs() {
        scope.launch {
            val current = _profiles.value
            if (current.isEmpty()) return@launch
            appendLog("Running parallel TCP ping across ${current.size} profile(s)...")
            _taskProgress.value = TaskProgressState(active = true, percent = 15, message = "TCP Ping (${current.size})")

            val sem = Semaphore(16)
            val pinged = coroutineScope {
                current.map { item ->
                    async {
                        sem.withPermit {
                            val rtt = TunnelStreamVerifier.tcpReachabilityCheck(
                                host = item.host,
                                port = item.port,
                                timeoutMs = 1600,
                                protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
                            )
                            if (rtt > 0) {
                                val nextStatus = if (item.status == "VERIFIED") "VERIFIED" else "ONLINE"
                                item.copy(pingMs = rtt.toInt(), status = nextStatus)
                            } else {
                                item.copy(pingMs = -1, status = "DEAD")
                            }
                        }
                    }
                }.awaitAll()
            }

            val selIdx = _selectedIndex.value
            _profiles.value = pinged.mapIndexed { idx, p -> p.copy(isSelected = idx == selIdx) }
            _taskProgress.value = TaskProgressState(active = false, percent = 0, message = "")
            saveRepositoryToDisk()
            val aliveCount = pinged.count { it.pingMs > 0 }
            appendLog("TCP ping finished: $aliveCount/${pinged.size} reachable.")
        }
    }

    override fun sortConfigs(by: String) {
        val current = _profiles.value
        if (current.size <= 1) return
        val selUid = current.getOrNull(_selectedIndex.value)?.uid
        val sorted = when (by.lowercase(Locale.ROOT)) {
            "name" -> current.sortedBy { it.name.lowercase(Locale.ROOT) }
            "protocol" -> current.sortedWith(
                compareBy<ProfileItem> { it.protocol.lowercase(Locale.ROOT) }
                    .thenBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
            )
            "country" -> current.sortedWith(
                compareBy<ProfileItem> { if (it.countryCode == "UN") "ZZ" else it.countryCode }
                    .thenBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
            )
            else -> current.sortedWith(
                compareBy<ProfileItem> {
                    when (it.status) {
                        "VERIFIED" -> 0
                        "HEALTHY" -> 1
                        "UNTESTED" -> 2
                        else -> 3
                    }
                }.thenBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
            )
        }
        val newSelIdx = if (_selectedIndex.value < 0) -1 else sorted.indexOfFirst { it.uid == selUid }.coerceAtLeast(0)
        _selectedIndex.value = newSelIdx
        _profiles.value = sorted.mapIndexed { idx, item -> item.copy(isSelected = idx == newSelIdx) }
        saveRepositoryToDisk()
    }



    override fun deduplicateConfigs() {
        val current = _profiles.value
        if (current.isEmpty()) return
        val currentSelectedUid = current.getOrNull(_selectedIndex.value)?.uid ?: selectedProfileUidPersist
        val deduped = current.distinctBy { "${it.protocol}|${it.host.lowercase()}|${it.port}|${it.sni.lowercase()}" }
        val removed = current.size - deduped.size
        _profiles.value = deduped
        if (deduped.isNotEmpty()) {
            val newIdx = deduped.indexOfFirst { it.uid == currentSelectedUid }.let { if (it >= 0) it else 0 }
            selectConfig(newIdx)
        } else {
            selectConfig(-1)
        }
        appendLog("Deduplicated repository: removed $removed duplicate(s).")
    }

    override fun removeDeadConfigs() {
        val current = _profiles.value
        if (current.isEmpty()) return
        val currentSelectedUid = current.getOrNull(_selectedIndex.value)?.uid ?: selectedProfileUidPersist
        val kept = current.filterNot { it.status.equals("DEAD", ignoreCase = true) || it.pingMs < 0 }
        val removed = current.size - kept.size
        _profiles.value = kept
        if (kept.isNotEmpty()) {
            val newIdx = kept.indexOfFirst { it.uid == currentSelectedUid }.let { if (it >= 0) it else 0 }
            selectConfig(newIdx)
        } else {
            selectConfig(-1)
        }
        appendLog("Removed $removed dead profile(s).")
    }

    override fun exportConfigs(mode: String, injectEarlyData: Int): String {
        val current = _profiles.value
        val filtered = when (mode.lowercase()) {
            "verified" -> current.filter { it.status == "VERIFIED" || it.cachedVectorId.isNotBlank() }
                .ifEmpty { current }
            "selected" -> listOfNotNull(current.getOrNull(_selectedIndex.value))
            else -> current
        }
        return filtered.joinToString("\n") { item ->
            if (injectEarlyData > 0) {
                UriConfigCodec.injectEarlyDataIntoUri(item.uri, injectEarlyData)
            } else {
                item.uri
            }
        }
    }

    override fun clearAllConfigs() {
        val count = _profiles.value.size
        profileSubOwnerMap.clear()
        _profiles.value = emptyList()
        selectConfig(-1)
        saveRepositoryToDisk()
        appendLog("Cleared all $count profile(s) from repository.")
    }

    override fun deleteConfig(index: Int) {
        val list = _profiles.value.toMutableList()
        if (index !in list.indices) return
        val removed = list.removeAt(index)
        profileSubOwnerMap.remove(removed.uid)
        _profiles.value = list
        if (list.isEmpty()) {
            selectConfig(-1)
        } else {
            selectConfig(_selectedIndex.value.coerceIn(0, list.lastIndex))
        }
        appendLog("Deleted profile: ${removed.name}")
    }

    override fun getConfigEditable(index: Int): EditableConfigFields {
        val item = _profiles.value.getOrNull(index) ?: return EditableConfigFields()
        return UriConfigCodec.toEditableFields(item)
    }

    override fun saveConfigEditable(index: Int, fields: EditableConfigFields) {
        val existingItem = _profiles.value.getOrNull(index)
        val builtUri = UriConfigCodec.buildUriFromEditableFields(
            fields = fields,
            fallbackRawUri = existingItem?.uri.orEmpty()
        )
        if (builtUri.isBlank()) {
            appendLog("Save config failed: invalid URI or host.")
            return
        }
        val parsed = UriConfigCodec.parseUriToProfileItem(builtUri)?.takeIf { !CleanIpScannerEngine.isSubscriptionAnnouncementOrFakeNode(it) }
        if (parsed == null) {
            appendLog("Save config failed: could not parse generated URI.")
            return
        }

        val list = _profiles.value.toMutableList()
        val prefVec = fields.preferredVectorId.ifBlank { "AUTO" }
        val finalName = fields.name.ifBlank { parsed.name }
        val country = CountryPaletteRegistry.detectCountry(
            name = finalName,
            host = parsed.host,
            sni = parsed.sni,
            explicitCode = existingItem?.countryCode.takeIf { it != "UN" }.orEmpty()
        )

        if (existingItem != null && index in list.indices) {
            list[index] = parsed.copy(
                uid = existingItem.uid,
                name = finalName,
                preferredVectorId = prefVec,
                cachedVectorId = if (prefVec != "AUTO") prefVec else existingItem.cachedVectorId,
                status = existingItem.status,
                pingMs = existingItem.pingMs,
                isSelected = existingItem.isSelected,
                countryCode = country.code,
                countryName = country.nameFa,
                flagEmoji = country.flagEmoji,
                flagStripeColors = country.stripeColors,
                accentPrimaryColor = country.accentPrimary,
                accentSecondaryColor = country.accentSecondary
            )
            _profiles.value = list
            selectConfig(index)
            appendLog("Updated profile: ${list[index].name}")
        } else {
            val fresh = parsed.copy(
                name = finalName,
                preferredVectorId = prefVec,
                cachedVectorId = if (prefVec != "AUTO") prefVec else "",
                countryCode = country.code,
                countryName = country.nameFa,
                flagEmoji = country.flagEmoji,
                flagStripeColors = country.stripeColors,
                accentPrimaryColor = country.accentPrimary,
                accentSecondaryColor = country.accentSecondary
            )
            list.add(fresh)
            _profiles.value = list
            selectConfig(list.lastIndex)
            appendLog("Added profile: ${fresh.name}")
        }
        saveRepositoryToDisk()
        val savedItem = _profiles.value.getOrNull(_selectedIndex.value)
        if (savedItem != null) {
            triggerInstantGeoLocationProbes(listOf(savedItem), allowMicroTunnelForCdn = true)
        }
    }

    override fun addSubscription(url: String, title: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val current = _subscriptions.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.url.equals(cleanUrl, ignoreCase = true) }
        val subId: String
        if (existingIdx >= 0) {
            subId = current[existingIdx].id
            current[existingIdx] = current[existingIdx].copy(
                title = title.ifBlank { current[existingIdx].title }
            )
        } else {
            subId = "sub-" + UUID.randomUUID().toString().replace("-", "").take(8)
            current.add(
                SubscriptionItem(
                    id = subId,
                    title = title.ifBlank { "Subscription ${current.size + 1}" },
                    url = cleanUrl,
                    updatedAt = "Pending"
                )
            )
        }
        _subscriptions.value = current
        saveSubscriptionsToDisk()
        appendLog("Registered subscription: $cleanUrl")
        scope.launch {
            syncSingleSubscriptionSuspend(subId)
        }
    }

    override fun refreshSubscriptions() {
        scope.launch {
            syncAllSubscriptionsSuspend()
        }
    }

    override fun deleteSubscription(index: Int) {
        val current = _subscriptions.value.toMutableList()
        if (index !in current.indices) return
        val removed = current.removeAt(index)
        _subscriptions.value = current

        val remainingProfiles = _profiles.value.filterNot { profileSubOwnerMap[it.uid] == removed.id }
        val keysToRemove = profileSubOwnerMap.filterValues { it == removed.id }.keys.toList(); for (k in keysToRemove) { profileSubOwnerMap.remove(k) }
        _profiles.value = remainingProfiles
        if (remainingProfiles.isNotEmpty()) {
            selectConfig(_selectedIndex.value.coerceIn(0, remainingProfiles.lastIndex))
        } else {
            selectConfig(-1)
        }
        saveSubscriptionsToDisk()
        saveRepositoryToDisk()
        appendLog("Removed subscription: ${removed.title}")
    }

    private suspend fun syncAllSubscriptionsSuspend(): Int {
        val subs = _subscriptions.value
        if (subs.isEmpty()) return 0
        var total = 0
        for (sub in subs) {
            total += syncSingleSubscriptionSuspend(sub.id)
        }
        return total
    }

    private suspend fun syncSingleSubscriptionSuspend(subId: String): Int = withContext(Dispatchers.IO) {
        val sub = _subscriptions.value.firstOrNull { it.id == subId } ?: return@withContext 0
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        return@withContext try {
            val req = Request.Builder()
                .url(sub.url)
                .header("User-Agent", "Alan-RIP-DPI-Android/1.0.0 ClashMeta v2rayNG")
                .get()
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    appendLog("Subscription HTTP ${resp.code}: ${sub.title}")
                    return@withContext 0
                }
                resp.body?.string().orEmpty()
            }

            val uris = UriConfigCodec.extractConfigLinesFromSubscriptionBody(body)
            val parsed = uris.mapNotNull { UriConfigCodec.parseUriToProfileItem(it)?.takeIf { !CleanIpScannerEngine.isSubscriptionAnnouncementOrFakeNode(it) } }
            val existingByUri = _profiles.value.associateBy { it.uri }

            val mergedSubItems = parsed.map { fresh ->
                val prev = existingByUri[fresh.uri]
                val finalItem = if (prev != null) {
                    val country = if (prev.countryCode != "UN") {
                        CountryPaletteRegistry.resolveByCode(prev.countryCode)
                    } else {
                        CountryPaletteRegistry.detectCountry(fresh.name, fresh.host, fresh.sni)
                    }
                    fresh.copy(
                        uid = prev.uid,
                        status = prev.status,
                        pingMs = prev.pingMs,
                        cachedVectorId = prev.cachedVectorId,
                        preferredVectorId = prev.preferredVectorId,
                        countryCode = country.code,
                        countryName = country.nameFa,
                        flagEmoji = country.flagEmoji,
                        flagStripeColors = country.stripeColors,
                        accentPrimaryColor = country.accentPrimary,
                        accentSecondaryColor = country.accentSecondary
                    )
                } else {
                    fresh
                }
                profileSubOwnerMap[finalItem.uid] = sub.id
                finalItem
            }

            val nonSubItems = _profiles.value.filterNot { profileSubOwnerMap[it.uid] == sub.id }
            val combined = (nonSubItems + mergedSubItems).distinctBy { it.uri }
            _profiles.value = combined

            if (combined.isNotEmpty()) {
                val idx = combined.indexOfFirst { it.uid == selectedProfileUidPersist }
                selectConfig(if (idx >= 0) idx else _selectedIndex.value.coerceIn(0, combined.lastIndex))
            }

            val stamp = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            _subscriptions.update { list ->
                list.map { item ->
                    if (item.id == sub.id) {
                        item.copy(updatedAt = "$stamp (${mergedSubItems.size})")
                    } else {
                        item
                    }
                }
            }

            saveRepositoryToDisk()
            saveSubscriptionsToDisk()
            appendLog("Synced '${sub.title}': ${mergedSubItems.size} server(s).")
            triggerInstantGeoLocationProbes(mergedSubItems.filter { it.countryCode == "UN" }.take(25), allowMicroTunnelForCdn = false)
            mergedSubItems.size
        } catch (e: Throwable) {
            appendLog("Subscription sync failed (${sub.title}): ${e.javaClass.simpleName}")
            0
        } finally {
            client.connectionPool.evictAll()
        }
    }

    override fun updateSettings(newSettings: EngineSettings) {
        val prev = _settings.value
        _settings.value = newSettings
        saveSettingsToDisk()

        val bridge = liveBridge
        if (bridge != null) {
            val targetVec = if (!newSettings.autoPilot) {
                VectorCatalog.findVector(newSettings.manualVectorId)
            } else {
                bridge.getCurrentVector()
            }
            bridge.updateVector(
                vector = targetVec,
                customStride = newSettings.forwardStrideBytes,
                jitterBytes = newSettings.strideJitterBytes
            )
            if (prev.autoPilot != newSettings.autoPilot || prev.manualVectorId != newSettings.manualVectorId) {
                bridge.dropActiveConnections()
                _telemetry.update {
                    it.copy(
                        activeVectorId = targetVec.vectorId,
                        activeVectorName = targetVec.codeName
                    )
                }
                appendLog("Live bridge vector switched to ${targetVec.vectorId}")
            }
        }
    }

    override fun updateSettings(patch: Map<String, Any>) {
        var cur = _settings.value
        for ((k, v) in patch) {
            cur = when (k) {
                "autoPilot", "autopilotEnabled" -> cur.copy(autoPilot = asBool(v, cur.autoPilot))
                "requireYoutube6s" -> cur.copy(requireYoutube6s = asBool(v, cur.requireYoutube6s))
                "verifyUploadStream" -> cur.copy(verifyUploadStream = asBool(v, cur.verifyUploadStream))
                "selfHealingWatchdog" -> cur.copy(selfHealingWatchdog = asBool(v, cur.selfHealingWatchdog))
                "engineMode" -> cur.copy(engineMode = v.toString())
                "fragmentEnabled" -> cur.copy(fragmentEnabled = asBool(v, cur.fragmentEnabled))
                "fragmentPackets" -> cur.copy(fragmentPackets = v.toString())
                "fragmentLength" -> cur.copy(fragmentLength = v.toString())
                "fragmentInterval" -> cur.copy(fragmentInterval = v.toString())
                "fingerprint" -> cur.copy(fingerprint = v.toString())
                "alpnOverride" -> cur.copy(alpnOverride = v.toString())
                "wsEarlyData" -> cur.copy(wsEarlyData = asInt(v, cur.wsEarlyData))
                "muxEnabled" -> cur.copy(muxEnabled = asBool(v, cur.muxEnabled))
                "muxConcurrency" -> cur.copy(muxConcurrency = asInt(v, cur.muxConcurrency).coerceIn(1, 64))
                "manualVectorId" -> cur.copy(manualVectorId = v.toString())
                "forwardStrideBytes" -> cur.copy(forwardStrideBytes = asInt(v, cur.forwardStrideBytes).coerceAtLeast(0))
                "strideJitterBytes" -> cur.copy(strideJitterBytes = asInt(v, cur.strideJitterBytes).coerceAtLeast(0))
                "tunMtu" -> cur.copy(tunMtu = asInt(v, cur.tunMtu).coerceIn(1280, 9000))
                "tunDns" -> cur.copy(tunDns = v.toString().ifBlank { "1.1.1.1" })
                "socksPort" -> cur.copy(socksPort = asInt(v, cur.socksPort).coerceIn(1025, 65534))
                "httpPort" -> cur.copy(httpPort = asInt(v, cur.httpPort).coerceIn(1025, 65534))
                "darkTheme" -> cur.copy(darkTheme = asBool(v, cur.darkTheme))
                "lastActiveTab" -> cur.copy(lastActiveTab = asInt(v, cur.lastActiveTab).coerceIn(0, 6))
                "subnetsRepoUrl" -> cur.copy(subnetsRepoUrl = v.toString().trim().ifBlank { CleanIpScannerEngine.DEFAULT_GITHUB_SUBNETS_URL })
                "autoCleanIpForCf" -> cur.copy(autoCleanIpForCf = asBool(v, cur.autoCleanIpForCf))
                else -> cur
            }
        }
        updateSettings(cur)
    }

    override fun getVectors(): List<VectorSpec> {
        return VectorCatalog.VECTOR_REGISTRY.map { vec ->
            VectorSpec(id = vec.vectorId, name = "${vec.vectorId} — ${vec.codeName}")
        }
    }

    override fun startScanner(
        mode: String,
        targetIndex: Int,
        autoConnect: Boolean,
        pruneDead: Boolean
    ) {
        val scanGen = scanGeneration.incrementAndGet()
        scannerJob?.cancel()
        scannerJob = scope.launch {
            if (mode.equals("full_repository", ignoreCase = true)) {
                runFullRepositoryScan(scanGen, autoConnect, pruneDead)
            } else {
                runActiveProfileVectorScan(scanGen, targetIndex, autoConnect)
            }
        }
    }

    override fun stopScanner() {
        scanGeneration.incrementAndGet()
        scannerJob?.cancel()
        scannerJob = null
        _scanState.update {
            it.copy(
                isRunning = false,
                summary = "اسکن متوقف شد"
            )
        }
        appendLog("Scanner stopped.")
    }







    override fun applyScannedVector(
        vectorId: String,
        targetIndex: Int,
        endpoint: String,
        autoConnect: Boolean
    ) {
        val current = _profiles.value.toMutableList()
        if (current.isEmpty()) return
        val safeIdx = (if (targetIndex in current.indices) targetIndex else _selectedIndex.value).coerceIn(current.indices)
        val baseProf = current[safeIdx]
        val epHost = endpoint.substringBefore(":").trim()
        val cleanVecId = vectorId.trim().ifBlank { "VEC-21" }

        val updatedProf = if (
            epHost.isNotEmpty() &&
            epHost != baseProf.host &&
            epHost != baseProf.originalHost &&
            !CleanIpScannerEngine.isPoisonedIranIp(epHost) &&
            cleanIpEngine.isCloudflareEdgeProfile(baseProf)
        ) {
            cleanIpEngine.buildProfileWithCleanIp(
                baseProf,
                com.alan.ripdpi.ui.CleanIpHit(ip = epHost, port = baseProf.port, latencyMs = baseProf.pingMs.coerceAtLeast(1), colo = "CF"),
                0
            )?.copy(
                uid = baseProf.uid,
                name = baseProf.name,
                preferredVectorId = cleanVecId,
                cachedVectorId = cleanVecId,
                status = "VERIFIED",
                isSelected = true
            ) ?: baseProf.copy(
                preferredVectorId = cleanVecId,
                cachedVectorId = cleanVecId,
                status = "VERIFIED",
                isSelected = true
            )
        } else {
            baseProf.copy(
                preferredVectorId = cleanVecId,
                cachedVectorId = cleanVecId,
                status = "VERIFIED",
                isSelected = true
            )
        }

        current[safeIdx] = updatedProf
        _profiles.value = current
        selectConfig(safeIdx)
        saveRepositoryToDisk()
        appendLog("[Lab] Pinned $cleanVecId onto '${updatedProf.name}' (old cache cleared).")

        if (autoConnect) {
            if (_engineState.value == EngineState.CONNECTED || _telemetry.value.isConnecting || _telemetry.value.isConnected) {
                disconnectTunnel()
            }
            startSmartConnect()
        }
    }



    override fun applyVectorToAllConfigs(vectorId: String, endpoint: String) {
        val current = _profiles.value
        if (current.isEmpty()) return
        _profiles.value = current.map { item ->
            if (item.protocol.equals("SHADOWSOCKS", ignoreCase = true)) {
                item
            } else {
                item.copy(cachedVectorId = vectorId, preferredVectorId = vectorId)
            }
        }
        saveRepositoryToDisk()
        appendLog("Applied vector $vectorId across ${current.size} profile(s).")
    }

    override fun connectScannedProfile(
        targetIndex: Int,
        boundVector: String,
        endpoint: String
    ) {
        val list = _profiles.value.toMutableList()
        if (targetIndex !in list.indices) return
        if (boundVector.isNotBlank()) {
            val cur = list[targetIndex]
            list[targetIndex] = cur.copy(
                cachedVectorId = boundVector,
                preferredVectorId = boundVector
            )
            _profiles.value = list
            saveRepositoryToDisk()
        }
        selectConfig(targetIndex)
        startSmartConnect()
    }

    override fun autoDetectBestMethod() {
        if (_isFindingBestMethod.value) {
            bestMethodJob?.cancel()
            bestMethodJob = null
            _isFindingBestMethod.value = false
            _autoMethodStatus.value = "جستجوی متد متوقف شد."
            return
        }
        bestMethodJob?.cancel()
        bestMethodJob = scope.launch {
            _isFindingBestMethod.value = true
            _autoMethodStatus.value = "در حال آماده‌سازی تست متدها..."
            try {
                pipelineMutex.withLock {
                    val candidateProfile: ProfileItem? = _profiles.value.getOrNull(
                        _selectedIndex.value.coerceAtLeast(0)
                    ) ?: _profiles.value.firstOrNull() ?: syncInternalOfficialAutoPoolSuspend().firstOrNull()

                    val targetProfile = candidateProfile ?: run {
                        _autoMethodStatus.value = "ابتدا یک کانفیگ در بخش کانفیگ‌ها اضافه کنید."
                        return@withLock
                    }

                    if (_engineState.value == EngineState.CONNECTED) {
                        teardownActiveRuntime()
                        _engineState.value = EngineState.DISCONNECTED
                        _telemetry.update { it.copy(uiPhase = "IDLE", isConnected = false, isConnecting = false) }
                    }

                    val cfg = _settings.value
                    val session = try {
                        UriConfigCodec.compileXraySession(targetProfile, cfg, 0, 0, 0, forceLogSilent = true)
                    } catch (e: Throwable) {
                        _autoMethodStatus.value = "خطا در کانفیگ انتخابی: ${e.message}"
                        return@withLock
                    }

                    val vectors = VectorCatalog.VECTOR_REGISTRY.filter { it.vectorId != "VEC-00" } +
                        listOfNotNull(VectorCatalog.VECTOR_REGISTRY.firstOrNull { it.vectorId == "VEC-00" })

                    val bridge = FullDuplexMutatorBridge(
                        listenPort = session.bridgePort,
                        initialRemoteHost = session.remoteHost,
                        initialRemotePort = session.remotePort,
                        initialVector = vectors.first(),
                        initialCustomStride = cfg.forwardStrideBytes,
                        initialJitterBytes = cfg.strideJitterBytes,
                        protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
                    )

                    var bestId = ""
                    var bestMs = Long.MAX_VALUE

                    try {
                        bridge.start()
                        if (!xrayCoreManager.startCore(session.configJson)) {
                            _autoMethodStatus.value = "خطا در اجرای هسته Xray"
                            return@withLock
                        }
                        delay(150L)

                        for ((idx, vec) in vectors.withIndex()) {
                            if (!isActive) break
                            _autoMethodStatus.value = "تست ${vec.vectorId} (${idx + 1} از ${vectors.size})..."
                            bridge.updateVector(vec, cfg.forwardStrideBytes, cfg.strideJitterBytes)
                            bridge.dropActiveConnections()

                            delay(40L)
                            val probe = TunnelStreamVerifier.fastProbe(session.httpPort, 3800L)
                            // Continue testing remaining vectors even on 502/503
                            if (probe.ok && probe.latencyMs < bestMs) {
                                bestMs = probe.latencyMs
                                bestId = vec.vectorId
                                if (bestMs <= 320L && idx >= 3) break
                            }
                        }
                    } finally {
                        xrayCoreManager.stopCore()
                        bridge.stop()
                    }

                    if (bestId.isNotEmpty()) {
                        updateSettings(_settings.value.copy(manualVectorId = bestId))
                        updateProfileStatus(targetProfile.uid, bestId, bestMs.toInt(), "VERIFIED")
                        _autoMethodStatus.value = "بهترین متد انتخاب شد: $bestId (${bestMs} ms)"
                        saveSettingsToDisk()
                        appendLog("Auto-selected best method in settings: $bestId (${bestMs}ms)")
                    } else {
                        _autoMethodStatus.value = "متد فعالی روی این کانفیگ پاسخ نداد."
                    }
                }
            } finally {
                _isFindingBestMethod.value = false
            }
        }
    }

    private data class EffectiveEndpoint(
        val connectHost: String,
        val connectPort: Int,
        val tcpRttMs: Int,
        val cleanIpApplied: Boolean
    )

    private suspend fun resolveEffectiveEndpointForProfile(
        profile: ProfileItem,
        allowAutoCleanIp: Boolean = _settings.value.autoCleanIpForCf
    ): EffectiveEndpoint? = withContext(Dispatchers.IO) {
        val port = profile.port.coerceIn(1, 65535)
        val rawHost = profile.host.trim()
        val isCfEdge = cleanIpEngine.isCloudflareEdgeProfile(profile)

        // 1. Try direct TCP if not a poisoned IP
        val sysIp = try {
            InetAddress.getAllByName(rawHost)
                .firstOrNull { it is java.net.Inet4Address }?.hostAddress
                ?: InetAddress.getByName(rawHost).hostAddress.orEmpty()
        } catch (_: Throwable) {
            ""
        }

        if (sysIp.isNotEmpty() && !CleanIpScannerEngine.isPoisonedIranIp(sysIp)) {
            val directMs = measureProtectedTcpMs(sysIp, port, 1500)
            if (directMs > 0) {
                return@withContext EffectiveEndpoint(sysIp, port, directMs, false)
            }
        }

        // 2. Try DoH anti-DNS-poisoning resolution
        if (!rawHost.all { it.isDigit() || it == '.' }) {
            val dohIp = cleanIpEngine.resolveDomainViaDoh(rawHost)
            if (!dohIp.isNullOrBlank() && dohIp != sysIp && !CleanIpScannerEngine.isPoisonedIranIp(dohIp)) {
                val dohMs = measureProtectedTcpMs(dohIp, port, 1500)
                if (dohMs > 0) {
                    return@withContext EffectiveEndpoint(dohIp, port, dohMs, false)
                }
            }
        }

        // 3. Automatic Clean Cloudflare IP Rescue for pages.dev / workers.dev / Cloudflare CDN!
        if (allowAutoCleanIp && isCfEdge) {
            val cleanHits = cleanIpEngine.quickFindBestCleanIps(port = port, neededCount = 2)
            val best = cleanHits.firstOrNull()
            if (best != null) {
                appendLog("Clean-IP rescue on '${profile.name}' -> ${best.ip}:${port} (${best.latencyMs}ms)")
                return@withContext EffectiveEndpoint(best.ip, port, best.latencyMs, true)
            }
        }

        null
    }

    private fun measureProtectedTcpMs(host: String, port: Int, timeoutMs: Int): Int {
        return cleanIpEngine.fastTcpPingBlocking(host, port, timeoutMs)
    }

    override fun activateAutoModeAndConnect(connectImmediately: Boolean) {
        selectConfig(-1)
        updateSettings(mapOf("autoPilot" to true))
        appendLog("Auto-Mode activated (GitHub Repository + Smart Clean-IP).")
        if (connectImmediately) {
            huntBestAndConnect()
        }
    }

    override fun refreshSubnetsFromGithub() {
        scope.launch {
            _cleanIpState.update { it.copy(summary = "در حال دریافت ساب‌نت‌ها از گیت‌هاب...") }
            val list = cleanIpEngine.fetchSubnetsFromGithub(_settings.value.subnetsRepoUrl)
            _cleanIpState.update {
                it.copy(
                    loadedSubnetsCount = list.size,
                    summary = "${list.size} ساب‌نت آماده از مخزن گیت‌هاب بارگذاری شد"
                )
            }
            appendLog("Loaded ${list.size} Clean-IP subnet(s) from GitHub.")
        }
    }

    override fun startCleanIpScan(
        sourceMode: String,
        customRanges: String,
        port: Int,
        maxResults: Int
    ) {
        cleanIpScanJob?.cancel()
        cleanIpScanJob = scope.launch {
            _cleanIpState.update {
                it.copy(
                    isRunning = true,
                    sourceMode = sourceMode,
                    port = port,
                    percent = 1,
                    summary = "در حال آماده‌سازی رنج‌های اسکن..."
                )
            }
            try {
                val subnets: List<String> = when (sourceMode) {
                    "custom" -> cleanIpEngine.parseSubnetsText(customRanges).ifEmpty { CleanIpScannerEngine.DEFAULT_CF_SUBNETS }
                    "default" -> CleanIpScannerEngine.DEFAULT_CF_SUBNETS
                    else -> cleanIpEngine.fetchSubnetsFromGithub(_settings.value.subnetsRepoUrl)
                }
                _cleanIpState.update {
                    it.copy(
                        loadedSubnetsCount = subnets.size,
                        summary = "اسکن موازی روی ${subnets.size} رنج ساب‌نت (پورت $port)..."
                    )
                }
                appendLog("Clean-IP scan started (${subnets.size} subnet ranges, port $port)...")

                val results = cleanIpEngine.scanCleanIps(
                    subnets = subnets,
                    port = port,
                    maxResults = maxResults.coerceIn(5, 50),
                    totalCandidates = 160,
                    onProgress = { pct, tested, total, hits ->
                        _cleanIpState.update { st ->
                            st.copy(
                                percent = pct,
                                summary = "تست‌شده: $tested/$total  •  آی‌پی سالم: $hits"
                            )
                        }
                    },
                    onHitUpdate = { liveHits ->
                        _cleanIpHits.value = liveHits
                    }
                )
                _cleanIpHits.value = results
                _cleanIpState.update {
                    it.copy(
                        isRunning = false,
                        percent = 100,
                        summary = if (results.isNotEmpty()) {
                            "اسکن کامل شد: ${results.size} آی‌پی تمیز کلادفلر پیدا شد (بهترین: ${results.first().latencyMs}ms)"
                        } else {
                            "در این دور آی‌پی تمیزی پاسخ نداد؛ دوباره اسکن کنید یا پورت را تغییر دهید."
                        }
                    )
                }
                appendLog("Clean-IP scan finished: ${results.size} clean IP(s) verified.")
            } catch (_: CancellationException) {
                _cleanIpState.update { it.copy(isRunning = false, summary = "اسکن IP متوقف شد.") }
            } finally {
                _cleanIpState.update { it.copy(isRunning = false) }
            }
        }
    }

    override fun stopCleanIpScan() {
        cleanIpScanJob?.cancel()
        cleanIpScanJob = null
        _cleanIpState.update { it.copy(isRunning = false, summary = "اسکن IP متوقف شد.") }
    }

    override fun applyCleanIpsToTargets(
        ips: List<String>,
        targetMode: String,
        selectedIndices: List<Int>,
        cloneMode: Boolean,
        clonesPerConfig: Int
    ) {
        scope.launch {
            val currentList = _profiles.value.toMutableList()
            if (currentList.isEmpty()) return@launch

            val hitLookup = _cleanIpHits.value.associateBy { it.ip }
            val cleanHits = ips.map { ip ->
                hitLookup[ip] ?: com.alan.ripdpi.ui.CleanIpHit(ip = ip, port = 443, latencyMs = 95, colo = "CF")
            }.ifEmpty {
                cleanIpEngine.quickFindBestCleanIps(443, clonesPerConfig.coerceAtLeast(3))
            }
            if (cleanHits.isEmpty()) {
                appendLog("No verified Clean IP available yet.")
                return@launch
            }

            val targetIdxList: List<Int> = when (targetMode) {
                "single" -> listOf((selectedIndices.firstOrNull() ?: _selectedIndex.value).coerceIn(currentList.indices))
                "selected" -> selectedIndices.filter { it in currentList.indices }.distinct()
                "all" -> currentList.indices.toList()
                else -> currentList.indices.filter { cleanIpEngine.isCloudflareEdgeProfile(currentList[it]) }
                    .ifEmpty { currentList.indices.toList() }
            }

            if (cloneMode) {
                val generated = ArrayList<ProfileItem>()
                val countPerCfg = clonesPerConfig.coerceIn(1, 15)
                for (idx in targetIdxList) {
                    val baseItem = currentList[idx]
                    val slice = cleanHits.take(countPerCfg)
                    slice.forEachIndexed { cIdx, hit ->
                        cleanIpEngine.buildProfileWithCleanIp(baseItem, hit, cIdx + 1)?.let { cloned ->
                            generated.add(cloned)
                        }
                    }
                }
                if (generated.isNotEmpty()) {
                    val existingUris = currentList.map { it.uri }.toHashSet()
                    val fresh = generated.filter { existingUris.add(it.uri) }
                    currentList.addAll(0, fresh)
                    _profiles.value = currentList
                    selectConfig(0)
                    saveRepositoryToDisk()
                    appendLog("Created ${fresh.size} Clean-IP clone(s) across ${targetIdxList.size} config(s).")
                }
            } else {
                var replacedCount = 0
                for ((pos, idx) in targetIdxList.withIndex()) {
                    val hit = cleanHits[pos % cleanHits.size]
                    val updated = cleanIpEngine.buildProfileWithCleanIp(currentList[idx], hit, 0)
                    if (updated != null) {
                        currentList[idx] = updated.copy(isSelected = currentList[idx].isSelected)
                        replacedCount++
                    }
                }
                if (replacedCount > 0) {
                    _profiles.value = currentList
                    selectConfig(_selectedIndex.value.coerceAtLeast(0))
                    saveRepositoryToDisk()
                    appendLog("Injected Clean IP onto $replacedCount config(s).")
                }
            }
        }
    }

    override fun quickCloneConfigWithCleanIps(profileIndex: Int, cloneCount: Int) {
        scope.launch {
            val baseList = _profiles.value
            if (profileIndex !in baseList.indices) return@launch
            val target = baseList[profileIndex]
            val port = if (target.port in CleanIpScannerEngine.CF_COMPATIBLE_PORTS) target.port else 443
            _taskProgress.value = TaskProgressState(
                active = true,
                percent = 25,
                message = "در حال شکار $cloneCount آی‌پی تمیز برای «${target.name}»..."
            )
            val hits = cleanIpEngine.quickFindBestCleanIps(port = port, neededCount = cloneCount.coerceIn(1, 12))
            if (hits.isEmpty()) {
                _taskProgress.value = TaskProgressState(active = false)
                appendLog("Quick Clean-IP clone could not find an open IP on port $port.")
                return@launch
            }
            val clones = hits.mapIndexedNotNull { idx, hit ->
                cleanIpEngine.buildProfileWithCleanIp(target, hit, idx + 1)
            }
            if (clones.isNotEmpty()) {
                val updated = _profiles.value.toMutableList()
                val insertAt = (profileIndex + 1).coerceAtMost(updated.size)
                updated.addAll(insertAt, clones)
                _profiles.value = updated
                selectConfig(insertAt)
                saveRepositoryToDisk()
                appendLog("Cloned '${target.name}' with ${clones.size} Clean Cloudflare IP(s).")
            }
            _taskProgress.value = TaskProgressState(active = false)
        }
    }

    override fun startMultiTargetMethodScan(
        scopeMode: String,
        selectedIndices: List<Int>,
        autoConnect: Boolean,
        useCleanIp: Boolean
    ) {
        scannerJob?.cancel()
        scannerJob = scope.launch {
            val repo = _profiles.value
            val targets: List<Pair<Int, ProfileItem>> = when (scopeMode) {
                "single" -> {
                    val idx = (selectedIndices.firstOrNull() ?: _selectedIndex.value.coerceAtLeast(0))
                    if (idx in repo.indices) listOf(idx to repo[idx]) else emptyList()
                }
                "selected" -> selectedIndices.distinct().filter { it in repo.indices }.map { it to repo[it] }
                else -> repo.mapIndexed { idx, item -> idx to item }
            }

            if (targets.isEmpty()) {
                _scanState.value = ScanUiState(
                    isRunning = false,
                    mode = "active_profile",
                    percent = 0,
                    summary = "ابتدا در بخش کانفیگ‌ها حداقل یک کانفیگ وارد کنید"
                )
                return@launch
            }

            _scanRows.value = emptyList()
            _scanState.value = ScanUiState(
                isRunning = true,
                mode = "active_profile",
                percent = 4,
                summary = if (targets.size == 1) {
                    "اسکن موازی ۶ مسیره روی «${targets.first().second.name}»..."
                } else {
                    "اسکن موازی آزمایشگاه روی ${targets.size} کانفیگ..."
                }
            )

            val collected = java.util.Collections.synchronizedList(ArrayList<ScanResultRow>())
            var winnerForAutoConnect: Triple<Int, String, String>? = null
            val portEdgeCache = HashMap<Int, List<String>>()
            val myGen = opGeneration.get()

            try {
                pipelineMutex.withLock {
                    if (_engineState.value == EngineState.CONNECTED) {
                        teardownActiveRuntime()
                        _engineState.value = EngineState.DISCONNECTED
                    }

                    val cfg = _settings.value
                    xrayCoreManager.probeSilentMode = true
                    try {
                        for ((tPos, pair) in targets.withIndex()) {
                            if (!isActive) break
                            val (origIdx, prof) = pair

                            // Never inject Cloudflare IP unless user explicitly checked `useCleanIp = true`!
                            val reachableEps: List<String> = resolveCandidateEndpointsForNode(
                                node = prof,
                                gen = myGen,
                                portEdgeCache = portEdgeCache,
                                sampleCount = 36,
                                forceCleanIp = useCleanIp
                            )

                            if (reachableEps.isEmpty()) {
                                appendLog("[Lab] Server unreachable on TCP: '${prof.name}' (${prof.host}:${prof.port})")
                                continue
                            }

                            val activeEp = reachableEps.first()
                            val tcpRtt = withContext(Dispatchers.IO) {
                                cleanIpEngine.fastTcpPingBlocking(activeEp, prof.port, 1200).coerceAtLeast(1)
                            }

                            val plan: List<BypassVector> = if (targets.size == 1) {
                                UriConfigCodec.buildBalancedRacePlan(prof.copy(cachedVectorId = "", preferredVectorId = "AUTO"), cfg)
                            } else if (cleanIpEngine.isDomesticOrDirectTunnel(prof)) {
                                listOf(VectorCatalog.findVector("VEC-00"))
                            } else {
                                UriConfigCodec.buildBalancedRacePlan(prof.copy(cachedVectorId = "", preferredVectorId = "AUTO"), cfg).take(12)
                            }

                            val waves = plan.chunked(6)
                            var detectedCountryOnNode = prof.countryCode

                            for ((wIdx, waveVecs) in waves.withIndex()) {
                                if (!isActive) break
                                val pct = if (targets.size == 1) {
                                    (12 + (((wIdx + 1) * 86) / waves.size.coerceAtLeast(1))).coerceIn(12, 99)
                                } else {
                                    (8 + (((tPos * waves.size + wIdx + 1) * 90) / (targets.size * waves.size).coerceAtLeast(1))).coerceIn(8, 99)
                                }
                                val waveLabel = waveVecs.joinToString("/") { it.vectorId }
                                _scanState.value = _scanState.value.copy(
                                    percent = pct,
                                    summary = "موج ${wIdx + 1}/${waves.size}: تست همزمان [$waveLabel]..."
                                )

                                val laneSpecs = waveVecs.map { prof to activeEp }
                                val multiSession = try {
                                    UriConfigCodec.compileMultiLaneProbeSession(laneSpecs, cfg)
                                } catch (_: Throwable) {
                                    null
                                } ?: continue

                                val bridges = ArrayList<FullDuplexMutatorBridge>(multiSession.lanes.size)
                                try {
                                    for ((lIdx, lane) in multiSession.lanes.withIndex()) {
                                        val vec = waveVecs[lIdx]
                                        val br = FullDuplexMutatorBridge(
                                            listenPort = lane.bridgePort,
                                            initialRemoteHost = activeEp,
                                            initialRemotePort = prof.port,
                                            initialVector = vec,
                                            initialCustomStride = cfg.forwardStrideBytes,
                                            initialJitterBytes = cfg.strideJitterBytes,
                                            initialRawPassthrough = vec.family == "DIRECT"
                                        )
                                        br.start()
                                        bridges.add(br)
                                    }

                                    if (!xrayCoreManager.startCore(multiSession.configJson)) continue
                                    waitLocalHttpReady(multiSession.lanes.first().httpPort, 480L)

                                    val waveHits = coroutineScope {
                                        multiSession.lanes.mapIndexed { lIdx, lane ->
                                            async(Dispatchers.IO) {
                                                if (!isActive) return@async null
                                                val vec = waveVecs[lIdx]
                                                val geoRes = TunnelStreamVerifier.probeTunnelAndGeo(
                                                    httpPort = lane.httpPort,
                                                    timeoutMs = 2600L,
                                                    fetchGeo = (detectedCountryOnNode == "UN" || detectedCountryOnNode.isBlank())
                                                )
                                                if (!geoRes.ok) return@async null
                                                val (upOk, upKbps) = TunnelStreamVerifier.quickUploadCheck(lane.httpPort, 1600L)
                                                Triple(vec, geoRes, upOk to upKbps)
                                            }
                                        }.awaitAll()
                                    }.filterNotNull()

                                    for ((vec, geoRes, upPair) in waveHits) {
                                        val (upOk, upKbps) = upPair
                                        if (geoRes.countryCode != "UN" && geoRes.countryCode.length == 2) {
                                            detectedCountryOnNode = geoRes.countryCode
                                        }
                                        val cSpec = CountryPaletteRegistry.resolveByCode(detectedCountryOnNode)
                                        val finalRtt = geoRes.latencyMs.coerceAtLeast(1)
                                        val detailStr = if (upOk && upKbps > 0.0) {
                                            "${cSpec.flagEmoji} ${cSpec.nameFa} | Up=${upKbps.toInt()} KB/s | ${vec.codeName}"
                                        } else {
                                            "${cSpec.flagEmoji} ${cSpec.nameFa} | ${finalRtt}ms | ${vec.codeName}"
                                        }
                                        val rowTitle = if (targets.size == 1) {
                                            "${vec.vectorId} • ${vec.codeName}"
                                        } else {
                                            "${cSpec.flagEmoji} ${prof.name} [${vec.vectorId}]"
                                        }
                                        val shownEndpoint = if (useCleanIp) "$activeEp:${prof.port}" else "${prof.host}:${prof.port}"
                                        val scoreVal = (100f - (finalRtt / 24f)).coerceIn(15f, 100f)

                                        collected.add(
                                            ScanResultRow(
                                                scanType = "vector",
                                                itemId = vec.vectorId,
                                                rank = collected.size + 1,
                                                targetIndex = origIdx,
                                                boundVector = vec.vectorId,
                                                title = rowTitle,
                                                category = vec.family,
                                                endpoint = shownEndpoint,
                                                tcpMs = tcpRtt,
                                                tunnelMs = finalRtt,
                                                status = "VERIFIED",
                                                score = scoreVal,
                                                detail = detailStr,
                                                countryCode = cSpec.code,
                                                flagEmoji = cSpec.flagEmoji
                                            )
                                        )
                                    }

                                    if (waveHits.isNotEmpty()) {
                                        val ranked = collected.sortedBy { it.tunnelMs }
                                            .mapIndexed { i, r -> r.copy(rank = i + 1) }
                                        _scanRows.value = ranked

                                        val topForThisNode = ranked.firstOrNull { it.targetIndex == origIdx } ?: ranked.first()
                                        val mut = _profiles.value.toMutableList()
                                        if (origIdx in mut.indices) {
                                            val curItem = mut[origIdx]
                                            val renamedItem = buildFormattedProfileWithCountry(
                                                item = curItem.copy(
                                                    cachedVectorId = topForThisNode.boundVector,
                                                    status = "VERIFIED"
                                                ),
                                                countryCode = detectedCountryOnNode,
                                                seqNum = origIdx + 1,
                                                pingMs = topForThisNode.tunnelMs,
                                                renameRemark = cfg.autoRenameOnScan
                                            )
                                            mut[origIdx] = renamedItem
                                            _profiles.value = mut
                                        }

                                        val bestOverall = ranked.first()
                                        winnerForAutoConnect = Triple(bestOverall.targetIndex, bestOverall.boundVector, bestOverall.endpoint)
                                        if (autoConnect && targets.size == 1) break
                                        if (targets.size > 1) break
                                    }
                                } finally {
                                    xrayCoreManager.stopCore()
                                    bridges.forEach { it.stop() }
                                }
                            }
                        }
                    } finally {
                        xrayCoreManager.probeSilentMode = false
                    }
                    saveRepositoryToDisk()
                }

                val finalRanked = _scanRows.value
                saveScanCacheToDisk()
                _scanState.value = ScanUiState(
                    isRunning = false,
                    mode = "active_profile",
                    percent = 100,
                    summary = if (finalRanked.isNotEmpty()) {
                        "برنده آزمایشگاه: ${finalRanked.first().boundVector} (${finalRanked.first().tunnelMs}ms) — ${finalRanked.size} متد فعال"
                    } else {
                        "متد فعالی پاسخ نداد (اگر کانفیگ کلادفلر فیلتر است، گزینه IP تمیز را فعال کنید)."
                    }
                )

                if (autoConnect && winnerForAutoConnect != null) {
                    val (wIdx, wVec, wEp) = winnerForAutoConnect!!
                    applyScannedVector(wVec, wIdx, wEp, true)
                }
            } catch (_: CancellationException) {
                _scanState.value = _scanState.value.copy(isRunning = false, summary = "اسکن توسط کاربر متوقف شد.")
            } finally {
                xrayCoreManager.probeSilentMode = false
                _scanState.value = _scanState.value.copy(isRunning = false)
            }
        }
    }


















    private suspend fun waitLocalHttpReady(port: Int, maxWaitMs: Long = 550L): Boolean = withContext(Dispatchers.IO) {
        val deadline = android.os.SystemClock.elapsedRealtime() + maxWaitMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress("127.0.0.1", port), 90)
                return@withContext true
            } catch (_: Throwable) {
            } finally {
                try { s.close() } catch (_: Throwable) {}
            }
            delay(18L)
        }
        false
    }

    private suspend fun resolveCandidateEndpointsForNode(
        node: ProfileItem,
        gen: Long,
        portEdgeCache: MutableMap<Int, List<String>>? = null,
        sampleCount: Int = 36,
        forceCleanIp: Boolean = false
    ): List<String> = withContext(Dispatchers.IO) {
        val candidates = ArrayList<String>(5)
        val origHost = node.originalHost.ifBlank { node.host }.trim()
        val curHost = node.host.trim()
        val isCdn = forceCleanIp || cleanIpEngine.isCloudflareEdgeProfile(node)

        appendLog("[DNS/EDGE] Resolving endpoints for '${node.name}' (host=$curHost, orig=$origHost, port=${node.port}, isCdn=$isCdn)")

        // 1. If it's a Cloudflare/CDN config (e.g. .pages.dev, .workers.dev) or forceCleanIp is requested,
        // ALWAYS fetch verified Clean Cloudflare Edge IPs FIRST because default .pages.dev DNS IPs are blocked!
        if (isCdn) {
            val cleanEdges: List<String> = if (portEdgeCache != null && !portEdgeCache[node.port].isNullOrEmpty()) {
                portEdgeCache[node.port].orEmpty()
            } else {
                val hits = cleanIpEngine.quickFindBestCleanIps(
                    port = node.port,
                    neededCount = 4,
                    sampleCount = sampleCount
                )
                val ips = hits.map { it.ip }
                if (portEdgeCache != null && ips.isNotEmpty()) {
                    portEdgeCache[node.port] = ips
                }
                ips
            }
            for (ipVal in cleanEdges) {
                if (ipVal.isNotEmpty() && ipVal !in candidates) {
                    candidates.add(ipVal)
                }
            }
            if (cleanEdges.isNotEmpty()) {
                appendLog("[CLEAN-IP] Selected ${cleanEdges.size} live Cloudflare Edge IPs for '${node.name}': ${cleanEdges.joinToString(", ")}")
            }
        }

        // 2. If user already pinned a custom IP in node.host, check if it responds on TCP
        if (curHost.isNotEmpty() && curHost != origHost && !CleanIpScannerEngine.isPoisonedIranIp(curHost)) {
            val rtt = cleanIpEngine.fastTcpPingBlocking(curHost, node.port, 1100)
            appendLog("[TCP-DIAL] Custom host $curHost:${node.port} -> ${if (rtt >= 0) "${rtt}ms" else "TIMEOUT"}")
            if (rtt >= 0 && curHost !in candidates) {
                candidates.add(0, curHost)
            }
        }

        // 3. Also test direct DNS resolution of origHost
        val resolvedOrig = cleanIpEngine.resolveUnpoisonedIpv4Blocking(origHost, 1400)
        if (resolvedOrig.isNotEmpty()) {
            val rtt = cleanIpEngine.fastTcpPingBlocking(resolvedOrig, node.port, 1150)
            appendLog("[TCP-DIAL] Direct DNS $origHost -> $resolvedOrig:${node.port} -> ${if (rtt >= 0) "${rtt}ms" else "TIMEOUT"}")
            if (rtt >= 0 && resolvedOrig !in candidates) {
                // For non-CDN servers, direct IP goes first; for CDN (.pages.dev), Clean IPs stay first!
                if (!isCdn) candidates.add(0, resolvedOrig) else candidates.add(resolvedOrig)
            }
        } else {
            appendLog("[DNS] Could not resolve unpoisoned IPv4 for '$origHost'")
        }

        // 4. Emergency fallback: if still empty and host looks like TLS/WS on 443, try instant seed Clean IPs
        if (candidates.isEmpty() && node.port in CleanIpScannerEngine.CF_COMPATIBLE_PORTS) {
            val fallbackHits = cleanIpEngine.quickFindBestCleanIps(port = node.port, neededCount = 3, sampleCount = 28)
            for (h in fallbackHits) {
                if (h.ip !in candidates) candidates.add(h.ip)
            }
            if (candidates.isNotEmpty()) {
                appendLog("[CLEAN-IP] Emergency seed fallback provided: ${candidates.joinToString(", ")}")
            }
        }

        candidates.take(4)
    }


    private suspend fun executeSingleNodePipelineUnlocked(
        gen: Long,
        rawNode: ProfileItem,
        maxVectors: Int = 12,
        isHiddenAutoNode: Boolean = false,
        rawUriForMeta: String = rawNode.uri
    ): Boolean {
        val cfg = _settings.value
        val dispName = if (isHiddenAutoNode) "Alan Smart Auto-Server" else rawNode.name
        val pinnedVecId = rawNode.preferredVectorId.trim().takeIf { it.isNotEmpty() && !it.equals("AUTO", true) }

        // FAST PATH 1: Explicit pinned vector from Lab or Editor -> Instant Direct Lock (<0.6s)!
        if (pinnedVecId != null && !isHiddenAutoNode) {
            val pinnedVec = VectorCatalog.findVector(pinnedVecId)
            val targetHost = cleanIpEngine.resolveUnpoisonedIpv4Blocking(rawNode.host.ifBlank { rawNode.originalHost }, 1200)
                .ifBlank { rawNode.host.ifBlank { rawNode.originalHost } }
            val tcpMs = cleanIpEngine.fastTcpPingBlocking(targetHost, rawNode.port, 1200)
            if (tcpMs >= 0 && isGenValid(gen)) {
                updateStageState(2, "DONE", "پینگ سرور: ${tcpMs}ms", "${tcpMs}ms")
                updateStageState(3, "DONE", "هسته Xray v26 آماده", "XRAY")
                updateStageState(4, "DONE", "قفل مستقیم متد انتخابی: ${pinnedVec.vectorId}", pinnedVec.vectorId)
                finalizeAndLockWinnerUnlocked(
                    gen = gen,
                    baseNode = rawNode,
                    endpointIp = targetHost,
                    vec = pinnedVec,
                    pingMs = tcpMs.coerceAtLeast(1).toLong(),
                    isHiddenAutoNode = false,
                    rawUriForMeta = rawUriForMeta
                )
                return true
            }
        }

        val candidateEndpoints = resolveCandidateEndpointsForNode(rawNode, gen, sampleCount = 32)
        if (candidateEndpoints.isEmpty()) return false

        val balancedPlan = UriConfigCodec.buildBalancedRacePlan(rawNode, cfg).take(maxVectors.coerceAtLeast(1))
        var winningOutcome: Triple<String, BypassVector, Long>? = null

        outerEndpointLoop@ for (ep in candidateEndpoints) {
            if (!isGenValid(gen)) return false
            val tcpMs = cleanIpEngine.fastTcpPingBlocking(ep, rawNode.port, 1150)
            if (tcpMs < 0) continue

            updateStageState(2, "DONE", "پینگ TCP: ${tcpMs}ms", "${tcpMs}ms")
            updateStageState(3, "DONE", "هسته ۶ مسیره موازی Xray v26 آماده", "6-LANE")

            // Split vectors into parallel waves of up to 6 simultaneous lanes!
            val waves = balancedPlan.chunked(6)
            for ((wIdx, waveVecs) in waves.withIndex()) {
                if (!isGenValid(gen)) return false
                val pct = (32 + (((wIdx + 1) * 58) / waves.size.coerceAtLeast(1))).coerceIn(32, 92)
                val vecLabels = waveVecs.joinToString("/") { it.vectorId }
                _telemetry.update {
                    it.copy(
                        progressPercent = pct,
                        statusDetail = "مسابقه موازی [$vecLabels] روی $dispName..."
                    )
                }
                updateStageState(5, "ACTIVE", "تست همزمان ${waveVecs.size} متد روی $dispName", waveVecs.first().vectorId)

                val laneSpecs = waveVecs.map { rawNode to ep }
                val multiSession = try {
                    UriConfigCodec.compileMultiLaneProbeSession(laneSpecs, cfg)
                } catch (_: Throwable) {
                    null
                } ?: continue

                val bridges = ArrayList<FullDuplexMutatorBridge>(multiSession.lanes.size)
                xrayCoreManager.probeSilentMode = true
                try {
                    for ((lIdx, lane) in multiSession.lanes.withIndex()) {
                        val vec = waveVecs[lIdx]
                        val br = FullDuplexMutatorBridge(
                            listenPort = lane.bridgePort,
                            initialRemoteHost = ep,
                            initialRemotePort = rawNode.port,
                            initialVector = vec,
                            initialCustomStride = cfg.forwardStrideBytes,
                            initialJitterBytes = cfg.strideJitterBytes,
                            initialRawPassthrough = vec.family == "DIRECT"
                        )
                        br.start()
                        bridges.add(br)
                    }

                    if (!xrayCoreManager.startCore(multiSession.configJson)) continue
                    waitLocalHttpReady(multiSession.lanes.first().httpPort, 480L)

                    val waveResults: List<Pair<BypassVector, Long>?> = coroutineScope {
                        multiSession.lanes.mapIndexed { lIdx, lane ->
                            async(Dispatchers.IO) {
                                if (!isGenValid(gen)) return@async null
                                val vec = waveVecs[lIdx]
                                val fp = TunnelStreamVerifier.fastProbe(lane.httpPort, 2300L)
                                if (!fp.ok) return@async null
                                val rtt = fp.latencyMs.coerceAtLeast(1L)
                                if (cfg.verifyUploadStream) {
                                    val (upOk, _) = TunnelStreamVerifier.quickUploadCheck(lane.httpPort, 1600L)
                                    if (!upOk) return@async (vec to (rtt + 250L))
                                }
                                vec to rtt
                            }
                        }.awaitAll()
                    }

                    val bestInWave = waveResults.filterNotNull().minByOrNull { it.second }
                    if (bestInWave != null) {
                        winningOutcome = Triple(ep, bestInWave.first, bestInWave.second)
                        break@outerEndpointLoop
                    }
                } finally {
                    xrayCoreManager.stopCore()
                    bridges.forEach { it.stop() }
                    xrayCoreManager.probeSilentMode = false
                }
            }
        }

        val winner = winningOutcome ?: return false
        if (!isGenValid(gen)) return false
        finalizeAndLockWinnerUnlocked(
            gen = gen,
            baseNode = rawNode,
            endpointIp = winner.first,
            vec = winner.second,
            pingMs = winner.third,
            isHiddenAutoNode = isHiddenAutoNode,
            rawUriForMeta = rawUriForMeta
        )
        return true
    }



    private suspend fun finalizeAndLockWinnerUnlocked(
        gen: Long,
        baseNode: ProfileItem,
        endpointIp: String,
        vec: BypassVector,
        pingMs: Long,
        isHiddenAutoNode: Boolean,
        rawUriForMeta: String
    ) {
        if (!isGenValid(gen)) return
        val effectiveNode: ProfileItem = if (cleanIpEngine.isCloudflareEdgeProfile(baseNode) && endpointIp != baseNode.originalHost) {
            cleanIpEngine.buildProfileWithCleanIp(
                baseNode,
                com.alan.ripdpi.ui.CleanIpHit(ip = endpointIp, port = baseNode.port, latencyMs = pingMs.toInt(), colo = "CF"),
                0
            ) ?: baseNode.copy(host = endpointIp)
        } else {
            baseNode.copy(host = endpointIp)
        }

        val runtimeProfile = if (isHiddenAutoNode) {
            effectiveNode.copy(
                uid = "__SMART_AUTO__",
                name = "Alan Smart Auto-Server",
                cachedVectorId = vec.vectorId,
                pingMs = pingMs.toInt(),
                status = "VERIFIED"
            )
        } else {
            updateProfileStatus(baseNode.uid, vec.vectorId, pingMs.toInt(), "VERIFIED")
            if (endpointIp != baseNode.host && cleanIpEngine.isCloudflareEdgeProfile(baseNode)) {
                val listMut = _profiles.value.toMutableList()
                val idx = listMut.indexOfFirst { it.uid == baseNode.uid }
                if (idx >= 0) {
                    listMut[idx] = effectiveNode.copy(
                        uid = baseNode.uid,
                        name = baseNode.name,
                        preferredVectorId = baseNode.preferredVectorId,
                        isSelected = listMut[idx].isSelected,
                        cachedVectorId = vec.vectorId,
                        pingMs = pingMs.toInt(),
                        status = "VERIFIED"
                    )
                    _profiles.value = listMut
                    saveRepositoryToDisk()
                }
            }
            effectiveNode
        }

        if (isHiddenAutoNode) {
            savePoolWinnerMeta(rawUriForMeta, vec.vectorId, endpointIp, pingMs.toInt())
            _selectedIndex.value = -1
        }

        updateStageState(5, "DONE", "برنده: ${runtimeProfile.name}", vec.vectorId)
        updateStageState(6, "DONE", "سلامت تونل تایید شد (${pingMs}ms)", "${pingMs}ms")
        updateStageState(7, "ACTIVE", "راه‌اندازی تونل سراسری AlanTUN...", "TUN")

        launchLockedProfileUnlocked(gen, runtimeProfile, vec, pingMs)

        if (isHiddenAutoNode && _engineState.value == EngineState.CONNECTED) {
            _selectedIndex.value = -1
            _telemetry.update {
                it.copy(
                    selectedIndex = -1,
                    activeProfileUid = "__SMART_AUTO__",
                    activeProfileName = "Alan Smart Auto-Server"
                )
            }
        }
    }

    private suspend fun executeSmartHuntUnlocked(gen: Long) {
        _selectedIndex.value = -1
        if (_profiles.value.any { it.isSelected }) {
            _profiles.value = _profiles.value.map { it.copy(isSelected = false) }
        }
        _settings.update { it.copy(autoPilot = true) }

        val targetCountry = _settings.value.preferredAutoCountry.trim().uppercase(Locale.US).ifBlank { "AUTO" }
        val targetSpec = if (targetCountry == "AUTO") null else CountryPaletteRegistry.resolveByCode(targetCountry)
        val targetBanner = if (targetSpec != null && targetCountry != "AUTO") {
            "${targetSpec.flagEmoji} ${targetSpec.nameFa}"
        } else {
            "سریع‌ترین لوکیشن"
        }

        _engineState.value = EngineState.HUNTING_BEST
        _telemetry.update {
            it.copy(
                selectedIndex = -1,
                activeProfileUid = "__SMART_AUTO__",
                activeProfileName = "Alan Smart Auto-Server ($targetBanner)",
                uiPhase = "HUNTING",
                isConnecting = true,
                isConnected = false,
                progressPercent = 8,
                statusHeadline = "اتصال خودکار ($targetBanner)...",
                statusDetail = "دریافت سرورهای ریپازیتوری هوشمند گیت‌هاب..."
            )
        }
        updateStageState(1, "ACTIVE", "دریافت ریپازیتوری گیت‌هاب ($targetBanner)...", targetCountry)
        _taskProgress.value = TaskProgressState(active = true, percent = 8, message = "دریافت ریپازیتوری ($targetBanner)...")

        val repoNodes = syncInternalOfficialAutoPoolSuspend()
        if (repoNodes.isEmpty()) {
            updateStageState(1, "FAILED", "ریپازیتوری خودکار در دسترس نیست", "ERR")
            _engineState.value = EngineState.ERROR
            _taskProgress.value = TaskProgressState(active = false)
            _telemetry.update {
                it.copy(
                    uiPhase = "ERROR",
                    isConnecting = false,
                    isConnected = false,
                    statusHeadline = "ریپازیتوری خودکار در دسترس نیست",
                    statusDetail = "اینترنت خود را بررسی کنید و دوباره تلاش کنید."
                )
            }
            return
        }

        val filteredByCountry = if (targetCountry != "AUTO") {
            repoNodes.filter { it.countryCode.equals(targetCountry, ignoreCase = true) }
        } else {
            repoNodes
        }
        val activePool = if (filteredByCountry.isNotEmpty()) {
            filteredByCountry
        } else {
            appendLog("[AutoPool] No servers for $targetCountry in repo; using all locations.")
            repoNodes
        }

        updateStageState(1, "DONE", "${detectNetworkOperatorLabel()} • ${activePool.size} سرور ($targetBanner)", "${activePool.size}")

        // Step 1: Try prior verified winner for this location first
        val priorWinners = activePool
            .filter { it.status == "VERIFIED" && it.cachedVectorId.isNotBlank() }
            .sortedBy { if (it.pingMs > 0) it.pingMs else 99999 }
        for (fastNode in priorWinners.take(1)) {
            if (!isGenValid(gen)) return
            if (executeSingleNodePipelineUnlocked(gen, fastNode, maxVectors = 6, isHiddenAutoNode = true, rawUriForMeta = fastNode.uri)) {
                return
            }
        }

        val verifiedH = activePool.filter { it.status == "VERIFIED" }
        val othersH = activePool.filter { it.status != "VERIFIED" }.shuffled()
        val allNodes = verifiedH + othersH.take(70)

        val portEdgeCache = HashMap<Int, List<String>>()
        val cdnPorts = allNodes.filter { cleanIpEngine.isCloudflareEdgeProfile(it) }.map { it.port }.distinct().sorted()
        if (_settings.value.autoCleanIpForCf && cdnPorts.isNotEmpty()) {
            updateStageState(2, "ACTIVE", "کشف لبه‌های تمیز CDN...", "EDGE")
            for (pNum in cdnPorts.take(2)) {
                if (!isGenValid(gen)) return
                val edges = cleanIpEngine.quickFindBestCleanIps(port = pNum, neededCount = 2, sampleCount = 28)
                if (edges.isNotEmpty()) {
                    portEdgeCache[pNum] = edges.map { it.ip }
                }
            }
        }

        if (!isGenValid(gen)) return
        updateStageState(2, "ACTIVE", "غربالگری موازی TCP روی ${allNodes.size} سرور ($targetBanner)...", "SCAN")
        _telemetry.update { it.copy(progressPercent = 28, statusDetail = "غربالگری موازی ${allNodes.size} سرور ($targetBanner)...") }

        val tcpSem = Semaphore(24)
        val reachablePool = java.util.Collections.synchronizedList(ArrayList<Triple<Int, ProfileItem, String>>())

        coroutineScope {
            allNodes.map { nd ->
                async(Dispatchers.IO) {
                    if (!isGenValid(gen)) return@async
                    tcpSem.withPermit {
                        if (!isGenValid(gen)) return@withPermit
                        val orig = nd.originalHost.ifBlank { nd.host }
                        val directIp = if (nd.host != orig && !CleanIpScannerEngine.isPoisonedIranIp(nd.host)) {
                            nd.host
                        } else {
                            cleanIpEngine.resolveUnpoisonedIpv4Blocking(orig, 1300)
                        }
                        var bestEp = ""
                        var bestMs = -1
                        if (directIp.isNotEmpty()) {
                            val ms = cleanIpEngine.fastTcpPingBlocking(directIp, nd.port, 1200)
                            if (ms >= 0) {
                                bestEp = directIp
                                bestMs = ms
                            }
                        }
                        if (bestMs < 0 && cleanIpEngine.isCloudflareEdgeProfile(nd) && portEdgeCache.containsKey(nd.port)) {
                            val cfEp = portEdgeCache[nd.port]!!.first()
                            val ms = cleanIpEngine.fastTcpPingBlocking(cfEp, nd.port, 1200)
                            if (ms >= 0) {
                                bestEp = cfEp
                                bestMs = ms
                            }
                        }
                        if (bestMs >= 0 && bestEp.isNotEmpty()) {
                            reachablePool.add(Triple(bestMs, nd, bestEp))
                        }
                    }
                }
            }.awaitAll()
        }

        if (!isGenValid(gen)) return
        val shortlist = reachablePool.sortedBy { it.first }.take(12)

        if (shortlist.isEmpty()) {
            updateStageState(2, "FAILED", "پاسخی از سرورها دریافت نشد", "TIMEOUT")
            _engineState.value = EngineState.ERROR
            _taskProgress.value = TaskProgressState(active = false)
            _telemetry.update {
                it.copy(
                    uiPhase = "ERROR",
                    isConnecting = false,
                    isConnected = false,
                    statusHeadline = "سرور فعالی پاسخ نداد",
                    statusDetail = "لوکیشن دیگری انتخاب کنید یا وضعیت اینترنت را بررسی کنید."
                )
            }
            return
        }

        updateStageState(2, "DONE", "${shortlist.size} سرور برتر آماده مسابقه موازی", "${shortlist.first().first}ms")
        updateStageState(3, "DONE", "موتور ۶ مسیره موازی Xray v26 آماده", "6-LANE")

        val cfg = _settings.value
        val candidateBatches = shortlist.chunked(6)
        val roundVecIds = listOf("VEC-11", "VEC-17", "VEC-21", "VEC-23", "VEC-01")
        var huntWinner: Triple<Long, Triple<Int, ProfileItem, String>, BypassVector>? = null

        outerBatchLoop@ for ((bIdx, batch) in candidateBatches.withIndex()) {
            if (!isGenValid(gen)) return
            val laneSpecs = batch.map { it.second to it.third }
            val multiSession = try {
                UriConfigCodec.compileMultiLaneProbeSession(laneSpecs, cfg)
            } catch (_: Throwable) {
                null
            } ?: continue

            val bridges = ArrayList<FullDuplexMutatorBridge>(multiSession.lanes.size)
            xrayCoreManager.probeSilentMode = true
            try {
                for ((lIdx, lane) in multiSession.lanes.withIndex()) {
                    val initVec = if (cleanIpEngine.isDomesticOrDirectTunnel(lane.profile)) {
                        VectorCatalog.findVector("VEC-00")
                    } else {
                        VectorCatalog.findVector(lane.profile.cachedVectorId.ifBlank { roundVecIds.first() })
                    }
                    val br = FullDuplexMutatorBridge(
                        listenPort = lane.bridgePort,
                        initialRemoteHost = batch[lIdx].third,
                        initialRemotePort = lane.remotePort,
                        initialVector = initVec,
                        initialCustomStride = cfg.forwardStrideBytes,
                        initialJitterBytes = cfg.strideJitterBytes,
                        initialRawPassthrough = initVec.family == "DIRECT"
                    )
                    br.start()
                    bridges.add(br)
                }

                if (!xrayCoreManager.startCore(multiSession.configJson)) continue
                waitLocalHttpReady(multiSession.lanes.first().httpPort, 500L)

                for ((rIdx, roundVid) in roundVecIds.withIndex()) {
                    if (!isGenValid(gen)) return
                    val pct = (42 + (((bIdx * roundVecIds.size + rIdx + 1) * 48) / (candidateBatches.size * roundVecIds.size).coerceAtLeast(1))).coerceIn(42, 92)
                    val msg = "مسابقه همزمان ${batch.size} سرور ($targetBanner) • موج ${rIdx + 1}..."
                    _telemetry.update { it.copy(progressPercent = pct, statusDetail = msg) }
                    _taskProgress.value = TaskProgressState(active = true, percent = pct, message = msg)
                    updateStageState(5, "ACTIVE", msg, roundVid)

                    val activeVecs = ArrayList<BypassVector>(multiSession.lanes.size)
                    for ((lIdx, lane) in multiSession.lanes.withIndex()) {
                        val vec = if (cleanIpEngine.isDomesticOrDirectTunnel(lane.profile)) {
                            VectorCatalog.findVector("VEC-00")
                        } else if (rIdx == 0 && lane.profile.cachedVectorId.isNotBlank()) {
                            VectorCatalog.findVector(lane.profile.cachedVectorId)
                        } else {
                            VectorCatalog.findVector(roundVid)
                        }
                        activeVecs.add(vec)
                        if (rIdx > 0) {
                            bridges[lIdx].updateRoute(
                                remoteHost = batch[lIdx].third,
                                remotePort = lane.remotePort,
                                vector = vec,
                                customStride = cfg.forwardStrideBytes,
                                jitterBytes = cfg.strideJitterBytes,
                                rawPassthrough = vec.family == "DIRECT",
                                flushExistingPool = true
                            )
                        }
                    }

                    val roundWinners = coroutineScope {
                        multiSession.lanes.mapIndexed { lIdx, lane ->
                            async(Dispatchers.IO) {
                                if (!isGenValid(gen)) return@async null
                                val fp = TunnelStreamVerifier.fastProbe(lane.httpPort, 3400L)
                                if (!fp.ok) return@async null
                                val rtt = fp.latencyMs.coerceAtLeast(1L)
                                Triple(rtt, batch[lIdx], activeVecs[lIdx])
                            }
                        }.awaitAll()
                    }.filterNotNull().sortedBy { it.first }

                    if (roundWinners.isNotEmpty()) {
                        huntWinner = roundWinners.first()
                        break@outerBatchLoop
                    }
                }
            } finally {
                // CRITICAL: Always stop temporary probe core & bridges BEFORE exiting try/finally!
                xrayCoreManager.stopCore()
                bridges.forEach { it.stop() }
                xrayCoreManager.probeSilentMode = false
            }
        }

        // CRITICAL: Call finalizeAndLockWinnerUnlocked STRICTLY OUTSIDE the try/finally block so production Xray is NEVER stopped!
        if (huntWinner != null && isGenValid(gen)) {
            val (winMs, winTriple, winVec) = huntWinner!!
            finalizeAndLockWinnerUnlocked(
                gen = gen,
                baseNode = winTriple.second,
                endpointIp = winTriple.third,
                vec = winVec,
                pingMs = winMs,
                isHiddenAutoNode = true,
                rawUriForMeta = winTriple.second.uri
            )
            return
        }

        // Fallback: Deep 12-vector single-node pipeline on top 2 servers of the chosen location
        for (topCand in shortlist.take(2)) {
            if (!isGenValid(gen)) return
            val candNode = topCand.second.copy(host = topCand.third)
            if (executeSingleNodePipelineUnlocked(gen, candNode, maxVectors = 12, isHiddenAutoNode = true, rawUriForMeta = topCand.second.uri)) {
                return
            }
        }

        updateStageState(5, "FAILED", "هیچ سروری تونل پایدار باز نکرد", "FAIL")
        _engineState.value = EngineState.ERROR
        _taskProgress.value = TaskProgressState(active = false)
        _telemetry.update {
            it.copy(
                uiPhase = "ERROR",
                isConnecting = false,
                isConnected = false,
                statusHeadline = "اتصال خودکار ناموفق بود",
                statusDetail = "لوکیشن دیگری انتخاب کنید یا مجدداً تلاش کنید."
            )
        }
    }




    private fun rebuildAvailableRepoLocations(nodes: List<ProfileItem>, reportJsonRaw: String = "") {
        val reportPingMap = HashMap<String, Int>()
        var updatedLabel = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        var totalScanned = nodes.size
        var totalVerified = nodes.size

        if (reportJsonRaw.isNotBlank()) {
            try {
                val j = org.json.JSONObject(reportJsonRaw)
                totalScanned = j.optInt("total_candidates_scanned", nodes.size).coerceAtLeast(nodes.size)
                totalVerified = j.optInt("total_verified_exported", nodes.size)
                val isoStr = j.optString("generated_at_iso", "")
                if (isoStr.length >= 16) {
                    updatedLabel = isoStr.substring(11, 16)
                }
                val locArr = j.optJSONArray("available_locations")
                if (locArr != null) {
                    for (i in 0 until locArr.length()) {
                        val o = locArr.getJSONObject(i)
                        val c = o.optString("code", "").uppercase(Locale.US)
                        val p = o.optInt("avg_ping_ms", -1)
                        if (c.length == 2 && p > 0) reportPingMap[c] = p
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val grouped = nodes.groupBy { it.countryCode.uppercase(Locale.US).ifBlank { "UN" } }
        val countryOptions = ArrayList<RepoLocationOption>()

        val globalPings = nodes.mapNotNull { it.pingMs.takeIf { p -> p > 0 } }
        val globalAvg = if (globalPings.isNotEmpty()) globalPings.average().toInt() else -1

        countryOptions.add(
            RepoLocationOption(
                countryCode = "AUTO",
                countryNameFa = "سریع‌ترین (همه لوکیشن‌ها)",
                countryNameEn = "Smart Fastest",
                flagEmoji = "⚡",
                flagStripeColors = listOf(0xFFF5C451, 0xFF10B981, 0xFFF5C451),
                serverCount = nodes.size,
                avgPingMs = globalAvg
            )
        )

        grouped.entries
            .sortedByDescending { (if (it.key != "UN") 100000 else 0) + it.value.size }
            .forEach { (cc, list) ->
                val spec = CountryPaletteRegistry.resolveByCode(cc)
                val pings = list.mapNotNull { it.pingMs.takeIf { p -> p > 0 } }
                val avgP = when {
                    pings.isNotEmpty() -> pings.average().toInt()
                    reportPingMap.containsKey(cc) -> reportPingMap[cc] ?: -1
                    else -> -1
                }
                countryOptions.add(
                    RepoLocationOption(
                        countryCode = spec.code,
                        countryNameFa = if (spec.code == "UN") "سایر لوکیشن‌ها" else spec.nameFa,
                        countryNameEn = spec.nameEn,
                        flagEmoji = spec.flagEmoji,
                        flagStripeColors = spec.stripeColors,
                        serverCount = list.size,
                        avgPingMs = avgP
                    )
                )
            }

        _availableRepoLocations.value = countryOptions
        _repoScanReport.value = RepoScanReportSummary(
            updatedAtLabel = updatedLabel,
            totalScanned = totalScanned,
            totalVerified = totalVerified,
            activeLocationsCount = (countryOptions.size - 1).coerceAtLeast(1),
            sourceLabel = "GitHub Auto-Repo"
        )
    }

    override fun selectAutoRepoCountry(countryCode: String, reconnectIfActive: Boolean) {
        val cleanCode = countryCode.trim().uppercase(Locale.US).ifBlank { "AUTO" }
        _selectedIndex.value = -1
        if (_profiles.value.any { it.isSelected }) {
            _profiles.value = _profiles.value.map { it.copy(isSelected = false) }
        }
        _settings.update { it.copy(autoPilot = true, preferredAutoCountry = cleanCode) }
        saveSettingsToDisk()

        val locSpec = if (cleanCode == "AUTO") null else CountryPaletteRegistry.resolveByCode(cleanCode)
        val labelFa = if (locSpec == null || cleanCode == "AUTO") {
            "حالت خودکار • سریع‌ترین لوکیشن"
        } else {
            "حالت خودکار • ${locSpec.flagEmoji} ${locSpec.nameFa}"
        }

        if (!_telemetry.value.isConnected || reconnectIfActive) {
            _telemetry.update {
                it.copy(
                    selectedIndex = -1,
                    activeProfileUid = "__SMART_AUTO__",
                    activeProfileName = labelFa,
                    countryCode = locSpec?.code ?: "UN",
                    countryName = locSpec?.nameFa ?: "خودکار",
                    flagEmoji = locSpec?.flagEmoji ?: "⚡",
                    flagStripeColors = locSpec?.stripeColors ?: it.flagStripeColors
                )
            }
        }
        appendLog("[AutoPool] Target location filter set to: $cleanCode")

        if (reconnectIfActive) {
            if (_engineState.value == EngineState.CONNECTED || _telemetry.value.isConnecting || _telemetry.value.isConnected) {
                disconnectTunnel()
            }
            startSmartConnect()
        }
    }

    override fun refreshAutoRepoCatalog() {
        scope.launch(Dispatchers.IO) {
            _taskProgress.value = TaskProgressState(active = true, percent = 25, message = "بروزرسانی لیست لوکیشن‌های ریپازیتوری...")
            val nodes = syncInternalOfficialAutoPoolSuspend()
            _taskProgress.value = TaskProgressState(
                active = false,
                percent = 100,
                message = if (nodes.isNotEmpty()) "ریپازیتوری بروز شد (${nodes.size} سرور فعال)" else "خطا در دریافت ریپازیتوری"
            )
        }
    }

    private fun buildFormattedProfileWithCountry(
        item: ProfileItem,
        countryCode: String,
        seqNum: Int,
        pingMs: Int = item.pingMs,
        renameRemark: Boolean = false
    ): ProfileItem {
        val cleanCc = countryCode.trim().uppercase(Locale.US).let { if (it == "UK") "GB" else it }
        val spec = CountryPaletteRegistry.resolveByCode(if (cleanCc.length == 2) cleanCc else item.countryCode)
        return item.copy(
            pingMs = pingMs,
            countryCode = spec.code,
            countryName = spec.nameFa,
            flagEmoji = spec.flagEmoji,
            flagStripeColors = spec.stripeColors,
            accentPrimaryColor = spec.accentPrimary,
            accentSecondaryColor = spec.accentSecondary
        )
    }


    override fun autoRenameConfigsWithLocation(onlySelectedIndices: List<Int>) {
        val current = _profiles.value
        if (current.isEmpty()) return
        val selSet = onlySelectedIndices.toSet()
        val perCountryCounter = HashMap<String, Int>()

        val updated = current.mapIndexed { idx, item ->
            if (selSet.isNotEmpty() && idx !in selSet) {
                item
            } else {
                val explicitIso = UriConfigCodec.extractExplicitIsoFromTitle(item.name)
                val detected = when {
                    explicitIso.isNotEmpty() -> explicitIso
                    item.countryCode != "UN" && item.countryCode.isNotBlank() -> item.countryCode
                    else -> CountryPaletteRegistry.detectCountry(item.name, item.originalHost.ifBlank { item.host }, item.sni).code
                }
                val nextSeq = (perCountryCounter[detected] ?: 0) + 1
                perCountryCounter[detected] = nextSeq
                buildFormattedProfileWithCountry(item, detected, nextSeq, item.pingMs, renameRemark = true)
            }
        }
        _profiles.value = updated
        saveRepositoryToDisk()
        appendLog("[Repository] Auto-renamed configs with location flags & ISO tags.")
    }

    override fun deleteSelectedConfigs(indices: List<Int>) {
        val toRemove = indices.toSet()
        if (toRemove.isEmpty()) return
        val remaining = _profiles.value.filterIndexed { idx, _ -> idx !in toRemove }
        _profiles.value = remaining
        if (_selectedIndex.value >= remaining.size) {
            _selectedIndex.value = (remaining.size - 1).coerceAtLeast(-1)
        }
        saveRepositoryToDisk()
        appendLog("[Repository] Deleted ${toRemove.size} selected profile(s).")
    }

    override fun importQrPayload(rawQrText: String): Int {
        val clean = rawQrText.trim()
        if (clean.isEmpty()) return 0
        if (clean.startsWith("http://", true) || clean.startsWith("https://", true)) {
            addSubscription(clean, "QR-Subscription")
            return 1
        }
        val beforeCount = _profiles.value.size
        importConfigs(clean)
        return (_profiles.value.size - beforeCount).coerceAtLeast(0)
    }

    override fun shareOrExportSelectedConfigs(indices: List<Int>, asBase64Sub: Boolean): String {
        val current = _profiles.value
        val picked = if (indices.isEmpty()) current else indices.mapNotNull { current.getOrNull(it) }
        val rawText = picked.joinToString("\n") { it.uri }
        if (!asBase64Sub || rawText.isEmpty()) return rawText
        return android.util.Base64.encodeToString(rawText.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    }

    override fun pingSingleConfig(index: Int, realTunnelPing: Boolean) {
        if (realTunnelPing) {
            realTunnelPingConfigs(selectedIndices = listOf(index), autoRenameWithLocation = _settings.value.autoRenameOnScan)
        } else {
            scope.launch(Dispatchers.IO) {
                val list = _profiles.value
                val item = list.getOrNull(index) ?: return@launch
                val rtt = cleanIpEngine.fastTcpPingBlocking(item.host.ifBlank { item.originalHost }, item.port, 1600)
                val mut = _profiles.value.toMutableList()
                if (index in mut.indices) {
                    mut[index] = mut[index].copy(
                        pingMs = rtt,
                        status = if (rtt > 0) "HEALTHY" else "DEAD"
                    )
                    _profiles.value = mut
                    saveRepositoryToDisk()
                }
            }
        }
    }

    override fun realTunnelPingConfigs(selectedIndices: List<Int>, autoRenameWithLocation: Boolean) {
        scope.launch {
            val all = _profiles.value
            if (all.isEmpty()) return@launch
            val targetPairs = if (selectedIndices.isNotEmpty()) {
                selectedIndices.distinct().filter { it in all.indices }.map { it to all[it] }
            } else {
                all.mapIndexed { idx, item -> idx to item }
            }
            if (targetPairs.isEmpty()) return@launch

            _taskProgress.value = TaskProgressState(
                active = true,
                percent = 6,
                message = "پینگ واقعی تونل و تشخیص لوکیشن روی ${targetPairs.size} کانفیگ..."
            )

            val cfg = _settings.value
            val resultsMap = java.util.concurrent.ConcurrentHashMap<String, LaneGeoPingResult>()
            var sharedCfIp = ""
            if (cfg.autoCleanIpForCf && targetPairs.any { cleanIpEngine.isCloudflareEdgeProfile(it.second) }) {
                val bestCf = cleanIpEngine.quickFindBestCleanIps(port = 443, neededCount = 1, sampleCount = 24)
                if (bestCf.isNotEmpty()) sharedCfIp = bestCf.first().ip
            }

            pipelineMutex.withLock {
                if (_engineState.value == EngineState.CONNECTED) {
                    teardownActiveRuntime()
                    _engineState.value = EngineState.DISCONNECTED
                }
                val chunkSize = 6
                val chunks = targetPairs.chunked(chunkSize)
                for ((cIdx, chunk) in chunks.withIndex()) {
                    if (!isActive) break
                    val laneSpecs = ArrayList<Pair<ProfileItem, String>>()
                    for ((_, prof) in chunk) {
                        val isCfNode = cleanIpEngine.isCloudflareEdgeProfile(prof)
                        val origHost = prof.host.ifBlank { prof.originalHost }
                        var ep = if (isCfNode && sharedCfIp.isNotEmpty()) {
                            sharedCfIp
                        } else {
                            withContext(Dispatchers.IO) {
                                cleanIpEngine.resolveUnpoisonedIpv4Blocking(origHost, 1300)
                            }
                        }
                        if (ep.isEmpty() && sharedCfIp.isNotEmpty()) {
                            ep = sharedCfIp
                        }
                        appendLog("[REAL-PING] Target '${prof.name}' -> Endpoint=$ep:${prof.port} (CDN=$isCfNode)")
                        if (ep.isNotEmpty()) {
                            laneSpecs.add(prof to ep)
                        } else {
                            resultsMap[prof.uid] = LaneGeoPingResult(ok = false, latencyMs = -1, detail = "DNS Fail")
                        }
                    }

                    if (laneSpecs.isNotEmpty()) {
                        val multiSession = try {
                            UriConfigCodec.compileMultiLaneProbeSession(laneSpecs, cfg)
                        } catch (_: Throwable) {
                            null
                        }
                        if (multiSession != null && multiSession.lanes.isNotEmpty()) {
                            val bridges = ArrayList<FullDuplexMutatorBridge>(multiSession.lanes.size)
                            xrayCoreManager.probeSilentMode = true
                            try {
                                for (lane in multiSession.lanes) {
                                    val plan = UriConfigCodec.buildBalancedRacePlan(lane.profile, cfg)
                                    val vec = plan.first()
                                    val br = FullDuplexMutatorBridge(
                                        listenPort = lane.bridgePort,
                                        initialRemoteHost = lane.remoteHost,
                                        initialRemotePort = lane.remotePort,
                                        initialVector = vec,
                                        initialCustomStride = cfg.forwardStrideBytes,
                                        initialJitterBytes = cfg.strideJitterBytes,
                                        initialRawPassthrough = vec.family == "DIRECT"
                                    )
                                    br.start()
                                    bridges.add(br)
                                }
                                if (xrayCoreManager.startCore(multiSession.configJson)) {
                                    waitLocalHttpReady(multiSession.lanes.first().httpPort, 500L)
                                    coroutineScope {
                                        multiSession.lanes.mapIndexed { lIdx, lane ->
                                            async(Dispatchers.IO) {
                                                var res = TunnelStreamVerifier.probeTunnelAndGeo(
                                                    httpPort = lane.httpPort,
                                                    timeoutMs = 2600L,
                                                    fetchGeo = true
                                                )
                                                if (!res.ok) {
                                                    // Second chance with Zero-OOB fallback vector (VEC-17) for Railway/Envoy servers!
                                                    val plan = UriConfigCodec.buildBalancedRacePlan(lane.profile, cfg)
                                                    val altVec = plan.firstOrNull { it.vectorId == "VEC-17" } ?: plan.getOrNull(1)
                                                    if (altVec != null) {
                                                        bridges[lIdx].updateRoute(
                                                            remoteHost = lane.remoteHost,
                                                            remotePort = lane.remotePort,
                                                            vector = altVec,
                                                            customStride = cfg.forwardStrideBytes,
                                                            jitterBytes = cfg.strideJitterBytes,
                                                            rawPassthrough = altVec.family == "DIRECT",
                                                            flushExistingPool = true
                                                        )
                                                        res = TunnelStreamVerifier.probeTunnelAndGeo(
                                                            httpPort = lane.httpPort,
                                                            timeoutMs = 2600L,
                                                            fetchGeo = true
                                                        )
                                                    }
                                                }
                                                resultsMap[lane.profile.uid] = res
                                            }
                                        }.awaitAll()
                                    }
                                }
                            } finally {
                                xrayCoreManager.stopCore()
                                bridges.forEach { it.stop() }
                                xrayCoreManager.probeSilentMode = false
                            }
                        }
                    }

                    val doneCount = ((cIdx + 1) * chunkSize).coerceAtMost(targetPairs.size)
                    val pct = (10 + ((doneCount * 88) / targetPairs.size.coerceAtLeast(1))).coerceIn(10, 98)
                    _taskProgress.value = TaskProgressState(
                        active = true,
                        percent = pct,
                        message = "پینگ واقعی و تشخیص لوکیشن ($doneCount/${targetPairs.size})..."
                    )
                }
            }

            val countrySeq = HashMap<String, Int>()
            var aliveCount = 0
            val updatedList = _profiles.value.map { item ->
                val res = resultsMap[item.uid] ?: return@map item
                if (res.ok && res.latencyMs > 0) {
                    aliveCount++
                    val cc = if (res.countryCode != "UN" && res.countryCode.length == 2) {
                        res.countryCode
                    } else {
                        UriConfigCodec.extractExplicitIsoFromTitle(item.name).ifBlank { item.countryCode }
                    }
                    val seq = (countrySeq[cc] ?: 0) + 1
                    countrySeq[cc] = seq
                    buildFormattedProfileWithCountry(
                        item = item.copy(status = "VERIFIED"),
                        countryCode = cc,
                        seqNum = seq,
                        pingMs = res.latencyMs,
                        renameRemark = autoRenameWithLocation
                    )
                } else {
                    item.copy(pingMs = -1, status = "DEAD")
                }
            }

            _profiles.value = updatedList
            if (selectedIndices.isEmpty()) {
                sortConfigs("ping")
            } else {
                saveRepositoryToDisk()
            }
            _taskProgress.value = TaskProgressState(
                active = false,
                percent = 100,
                message = "تست کامل شد: $aliveCount از ${targetPairs.size} کانفیگ فعال و لوکیشن‌گذاری شدند"
            )
            appendLog("[RealPing] Verified $aliveCount/${targetPairs.size} configs with live GeoIP.")
        }
    }

    private fun refreshDohUiList() {
        val activeUrl = _settings.value.activeDohUrl.ifBlank { AlanDohEngine.activeDohUrl }
        val verifiedMap = AlanDohEngine.getVerifiedResults().associateBy { it.url }
        val list = AlanDohEngine.getAllConfiguredEndpoints().map { ep ->
            val hit = verifiedMap[ep.url]
            DohServerUiItem(
                id = ep.id,
                name = ep.name,
                url = ep.url,
                category = ep.category,
                latencyMs = hit?.latencyMs ?: -1,
                resolvedIp = hit?.resolvedSampleIp.orEmpty(),
                isHealthy = hit?.isHealthy == true,
                isSelected = ep.url.equals(activeUrl, ignoreCase = true),
                statusDetail = hit?.statusDetail ?: "آماده تست"
            )
        }.sortedWith(
            compareByDescending<DohServerUiItem> { it.isSelected }
                .thenByDescending { it.isHealthy }
                .thenBy { if (it.latencyMs > 0) it.latencyMs else Int.MAX_VALUE }
        )
        _dohServers.value = list
    }

    override fun startDohScan(testDomain: String) {
        dohScanJob?.cancel()
        dohStopSignal.set(false)
        dohScanJob = scope.launch {
            _dohScanState.update {
                it.copy(isRunning = true, percent = 2, summary = "در حال اسکن سرورهای DoH روی دامنه $testDomain...")
            }
            val cleanEdges = cleanIpEngine.quickFindBestCleanIps(port = 443, neededCount = 1, sampleCount = 24)
            if (cleanEdges.isNotEmpty()) {
                AlanDohEngine.activeBootstrapIp = cleanEdges.first().ip
                appendLog("[DOH-BOOTSTRAP] Using verified Clean CF IP ${cleanEdges.first().ip} for DoH TLS bootstrap.")
            }
            val winners = AlanDohEngine.runFullDohScan(
                testDomain = testDomain.ifBlank { "github.com" },
                stopSignal = dohStopSignal,
                logger = { msg -> appendLog(msg) },
                onProgress = { pct, tested, total, _, summary ->
                    _dohScanState.update {
                        it.copy(isRunning = true, percent = pct, testedCount = tested, totalCount = total, summary = summary)
                    }
                    refreshDohUiList()
                }
            )
            if (winners.isNotEmpty()) {
                val best = winners.first()
                _settings.update { it.copy(activeDohUrl = best.url) }
                saveSettingsToDisk()
                _dohScanState.update {
                    it.copy(
                        isRunning = false,
                        percent = 100,
                        activeDohUrl = best.url,
                        activeDohName = best.name,
                        summary = "بهترین DoH فعال شد: ${best.name} (${best.latencyMs}ms) • ${winners.size} سرور سالم"
                    )
                }
            } else {
                _dohScanState.update {
                    it.copy(isRunning = false, percent = 100, summary = "پاسخی از سرورهای DoH دریافت نشد.")
                }
            }
            refreshDohUiList()
        }
    }

    override fun stopDohScan() {
        dohStopSignal.set(true)
        dohScanJob?.cancel()
        _dohScanState.update { it.copy(isRunning = false, summary = "اسکن DoH متوقف شد.") }
    }

    override fun selectActiveDoh(url: String, connectDohVeilNow: Boolean) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        AlanDohEngine.activeDohUrl = cleanUrl
        val epName = AlanDohEngine.getAllConfiguredEndpoints().firstOrNull { it.url.equals(cleanUrl, true) }?.name ?: "Custom DoH"
        _settings.update { it.copy(activeDohUrl = cleanUrl) }
        saveSettingsToDisk()
        _dohScanState.update {
            it.copy(activeDohUrl = cleanUrl, activeDohName = epName, summary = "سرور DoH روی $epName تنظیم شد.")
        }
        refreshDohUiList()
        appendLog("[DOH] Active DoH resolver set to: $epName ($cleanUrl)")
        if (connectDohVeilNow) {
            setTunnelOperationMode("DOH_VEIL")
            if (_engineState.value == EngineState.CONNECTED) disconnectTunnel()
            startSmartConnect()
        }
    }

    override fun addCustomDohList(rawUrls: String) {
        val count = AlanDohEngine.addCustomDohUrls(rawUrls)
        refreshDohUiList()
        appendLog("[DOH] Added $count custom DoH endpoint(s).")
    }

    override fun setTunnelOperationMode(mode: String) {
        val cleanMode = mode.trim().uppercase(Locale.US).let {
            if (it in setOf("VPN", "PROXY", "DOH_VEIL")) it else "VPN"
        }
        _settings.update { it.copy(tunnelMode = cleanMode) }
        saveSettingsToDisk()
        appendLog("[ENGINE-MODE] Operation mode switched to: $cleanMode")
    }

    override fun sendAllConfigsToDpiLab(autoConnectBest: Boolean, useCleanIp: Boolean) {
        val allIndices = _profiles.value.indices.toList()
        appendLog("[DPI-LAB] Sending entire repository (${allIndices.size} configs) to 6-Lane DPI Lab (useCleanIp=$useCleanIp)...")
        startMultiTargetMethodScan(
            scopeMode = "all",
            selectedIndices = allIndices,
            autoConnect = autoConnectBest,
            useCleanIp = useCleanIp
        )
    }

    override fun clearLogs() {
        synchronized(logLinesBuffer) {
            logLinesBuffer.clear()
            _logs.value = emptyList()
        }
    }

    private suspend fun executeSmartConnectPipeline(gen: Long) {
        pipelineMutex.withLock {
            if (!isGenValid(gen)) return

            resetConnectionStagesForNewRun()
            _liveVectorProbes.value = emptyList()
            clearSparklines()

            updateStageState(1, "ACTIVE", "بررسی مخزن و اشتراک‌ها...", "")
            setConnectingTelemetry(
                phase = "STAGE_1_SYNC",
                headline = "مرحله ۱ از ۷ • آماده‌سازی مخزن",
                detail = "بررسی کانفیگ فعال و اشتراک‌ها...",
                percent = 6,
                stageIndex = 1
            )

            if (_profiles.value.isEmpty() && _subscriptions.value.isNotEmpty()) {
                updateStageState(1, "ACTIVE", "به‌روزرسانی سابسکریپشن‌های کاربر...", "SYNC")
                syncAllSubscriptionsSuspend()
            }
            if (isAutoRepoMode()) {
                executeSmartHuntUnlocked(gen)
                return
            }
            val userProfiles = _profiles.value
            val selIdx = _selectedIndex.value
            if (selIdx !in userProfiles.indices) {
                _engineState.value = EngineState.ERROR
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "کانفیگی انتخاب نشده است",
                        statusDetail = "یک کانفیگ انتخاب کنید یا دکمه «حالت خودکار» را روشن کنید."
                    )
                }
                return
            }
            val chosenUserProfile = userProfiles[selIdx]
            val lockedOk = executeSingleNodePipelineUnlocked(
                gen = gen,
                rawNode = chosenUserProfile,
                maxVectors = 14,
                isHiddenAutoNode = false,
                rawUriForMeta = chosenUserProfile.uri
            )
            if (!lockedOk && isGenValid(gen)) {
                updateStageState(5, "FAILED", "پاسخی از این کانفیگ دریافت نشد", "FAIL")
                _engineState.value = EngineState.ERROR
                _taskProgress.value = TaskProgressState(active = false)
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "اتصال برقرار نشد",
                        statusDetail = "سرور «${chosenUserProfile.name}» پاسخ نداد؛ کانفیگ دیگر یا حالت خودکار را امتحان کنید."
                    )
                }
            }
            return
            val hiddenFallbackList: List<ProfileItem> = if (_profiles.value.isEmpty()) {
                syncInternalOfficialAutoPoolSuspend()
            } else {
                emptyList()
            }

            val profileList = if (_profiles.value.isNotEmpty()) _profiles.value else hiddenFallbackList
            if (profileList.isEmpty()) {
                updateStageState(1, "FAILED", "سروری در مخزن یا ساب یافت نشد", "EMPTY")
                _engineState.value = EngineState.ERROR
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "مخزن خالی است",
                        statusDetail = "سروری در مخزن یا لینک اشتراک یافت نشد.",
                        buttonText = "تلاش مجدد"
                    )
                }
                appendLog("Stage 1 aborted: 0 profiles.")
                return
            }

            val usingVisibleRepo = _profiles.value.isNotEmpty()
            val idx = if (usingVisibleRepo && _selectedIndex.value in profileList.indices) _selectedIndex.value else 0
            if (usingVisibleRepo && _selectedIndex.value != idx) {
                selectConfig(idx)
            }
            val profile = profileList[idx]
            val cfgSettings = _settings.value
            val initialCountry = CountryPaletteRegistry.detectCountry(
                name = profile.name,
                host = profile.host,
                sni = profile.sni,
                explicitCode = profile.countryCode
            )
            applyCountryPaletteToTelemetry(profile, initialCountry)

            updateStageState(
                stepNumber = 1,
                state = "DONE",
                subtitle = "${detectNetworkOperatorLabel()} • ${profile.name}",
                badge = "${initialCountry.flagEmoji} ${initialCountry.code}"
            )

            teardownActiveRuntime()
            if (!isGenValid(gen)) return

            updateStageState(2, "ACTIVE", "سنجش مستقیم پورت ${profile.host}:${profile.port}", "SYN")
            setConnectingTelemetry(
                phase = "STAGE_2_TCP",
                headline = "مرحله ۲ از ۷ • پیش‌پالایش لایه ۴ (TCP)",
                detail = "تست دسترس‌پذیری پایه ${profile.host}:${profile.port}...",
                percent = 16,
                stageIndex = 2
            )

            val baseTcpRtt = TunnelStreamVerifier.multiSampleTcpPreflight(
                host = profile.host,
                port = profile.port,
                attempts = 2,
                timeoutMs = 1650,
                protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
            )
            if (baseTcpRtt < 0) {
                updateStageState(2, "FAILED", "سرور مقصد به هندشیک TCP پاسخ نداد", "TIMEOUT")
                updateProfileStatus(profile.uid, profile.cachedVectorId, -1, "DEAD")
                _taskProgress.value = TaskProgressState(active = false)
                _engineState.value = EngineState.ERROR
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "سرور در دسترس نیست",
                        statusDetail = "پورت ${profile.port} روی ${profile.host} پاسخ نداد (TCP Timeout).",
                        buttonText = "تلاش مجدد"
                    )
                }
                appendLog("Stage 2 failed: TCP timeout on ${profile.host}:${profile.port}")
                return
            }
            updateStageState(2, "DONE", "پاسخ لایه ۴ تایید شد", "${baseTcpRtt}ms")
            appendLog("Stage 2 TCP preflight OK: ${baseTcpRtt}ms")

            if (!isGenValid(gen)) return

            updateStageState(3, "ACTIVE", "راه‌اندازی Xray و پل تزریق کرنلی MSG_OOB", "BOOT")
            setConnectingTelemetry(
                phase = "STAGE_3_CORE",
                headline = "مرحله ۳ از ۷ • راه‌اندازی هسته و پل",
                detail = "اجرای هسته Xray و FullDuplexMutatorBridge...",
                percent = 28,
                stageIndex = 3
            )

            val session = try {
                UriConfigCodec.compileXraySession(
                    profile = profile,
                    settings = cfgSettings,
                    preferredBridgePort = configuredBridgePort
                )
            } catch (e: Throwable) {
                updateStageState(3, "FAILED", e.message ?: "خطای کامپایل کانفیگ", "ERR")
                _engineState.value = EngineState.ERROR
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "خطا در کانفیگ",
                        statusDetail = e.message ?: "ساختار کانفیگ نامعتبر است",
                        buttonText = "تلاش مجدد"
                    )
                }
                return
            }

            val startVector = VectorCatalog.findVector(cfgSettings.manualVectorId)
            val bridge = FullDuplexMutatorBridge(
                listenPort = session.bridgePort,
                initialRemoteHost = session.remoteHost,
                initialRemotePort = session.remotePort,
                initialVector = startVector,
                initialCustomStride = cfgSettings.forwardStrideBytes,
                initialJitterBytes = cfgSettings.strideJitterBytes,
                protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
            )

            try {
                bridge.start()
            } catch (e: Throwable) {
                updateStageState(3, "FAILED", "عدم امکان باز کردن پورت پل محلی", "PORT")
                _engineState.value = EngineState.ERROR
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "خطای پورت محلی",
                        statusDetail = e.message ?: "پورت پل اشغال است",
                        buttonText = "تلاش مجدد"
                    )
                }
                return
            }

            if (!xrayCoreManager.startCore(session.configJson)) {
                bridge.stop()
                updateStageState(3, "FAILED", "هسته libv2ray اجرا نشد", "CORE")
                _engineState.value = EngineState.ERROR
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "خطای هسته Xray",
                        statusDetail = xrayCoreManager.lastErrorMessage.ifBlank { "راه‌اندازی هسته بومی libv2ray ناموفق بود." },
                        buttonText = "تلاش مجدد"
                    )
                }
                return
            }

            delay(170L)
            if (!isGenValid(gen)) {
                xrayCoreManager.stopCore()
                bridge.stop()
                return
            }
            updateStageState(3, "DONE", "هسته Xray و پل دوتایی آماده به کار", ":${session.bridgePort}")

            var winningVector: BypassVector? = null
            var winningLatency = -1L

            if (!cfgSettings.autoPilot) {
                val chosenId = if (
                    profile.preferredVectorId.isNotBlank() &&
                    !profile.preferredVectorId.equals("AUTO", true)
                ) {
                    profile.preferredVectorId
                } else {
                    cfgSettings.manualVectorId
                }
                val manualVec = VectorCatalog.findVector(chosenId)
                updateStageState(4, "SKIPPED", "AutoPilot غیرفعال است (حالت دستی)", "MANUAL")
                updateStageState(5, "SKIPPED", "انتخاب مستقیم متد ${manualVec.vectorId}", manualVec.vectorId)

                updateStageState(6, "ACTIVE", "سنجش تونل با متد دستی ${manualVec.vectorId}...", "TEST")
                setConnectingTelemetry(
                    phase = "STAGE_6_QUALIFY",
                    headline = "مرحله ۶ از ۷ • تست متد دستی",
                    detail = "آزمایش ${manualVec.vectorId} (${manualVec.codeName})...",
                    percent = 76,
                    stageIndex = 6
                )
                bridge.updateVector(manualVec, cfgSettings.forwardStrideBytes, cfgSettings.strideJitterBytes)
                bridge.dropActiveConnections()
                val probe = TunnelStreamVerifier.fastProbe(session.httpPort, 3000L)
                winningVector = manualVec
                winningLatency = if (probe.ok) probe.latencyMs else baseTcpRtt
                updateStageState(6, "DONE", "متد دستی تنظیم شد", "${winningLatency}ms")
            } else {
                val cachedId = profile.cachedVectorId.ifBlank {
                    profile.preferredVectorId.takeIf { !it.equals("AUTO", true) }.orEmpty()
                }
                val cachedVec = cachedId.takeIf { it.isNotBlank() }?.let { cid ->
                    VectorCatalog.VECTOR_REGISTRY.firstOrNull { it.vectorId == cid }
                }

                if (cachedVec != null && isGenValid(gen)) {
                    _engineState.value = EngineState.FAST_RESUME
                    updateStageState(4, "ACTIVE", "آزمایش متد کش‌شده ${cachedVec.vectorId}...", cachedVec.vectorId)
                    setConnectingTelemetry(
                        phase = "STAGE_4_FAST_RESUME",
                        headline = "مرحله ۴ از ۷ • بازیابی سریع متد کش‌شده",
                        detail = "تست سریع ${cachedVec.vectorId} (${cachedVec.codeName})...",
                        percent = 38,
                        stageIndex = 4
                    )

                    bridge.updateVector(cachedVec, cfgSettings.forwardStrideBytes, cfgSettings.strideJitterBytes)
                    bridge.dropActiveConnections()
                    delay(40L)
                    val fast = TunnelStreamVerifier.fastProbe(session.httpPort, 3600L)
                    if (fast.ok) {
                        val qual = TunnelStreamVerifier.runFullQualification(
                            httpPort = session.httpPort,
                            verifyUploadStream = cfgSettings.verifyUploadStream,
                            requireYoutube6s = cfgSettings.requireYoutube6s,
                            isCancelled = { !isGenValid(gen) }
                        )
                        if (qual.fullyQualified) {
                            winningVector = cachedVec
                            winningLatency = qual.latencyMs
                            updateStageState(4, "DONE", "قفل سریع روی ${cachedVec.vectorId}", "${qual.latencyMs}ms")
                            updateStageState(5, "SKIPPED", "نیازی به اسکن مجدد متدها نبود", "CACHED")
                            updateStageState(6, "DONE", "آپلود و استریم ۶ ثانیه تایید شد", "A+")
                            appendLog("Stage 4 Fast Resume locked: ${cachedVec.vectorId} (${qual.latencyMs}ms)")
                        } else {
                            updateStageState(4, "SKIPPED", "متد قبلی افت کرده؛ ورود به اسکن متدها", "ROTATE")
                        }
                    } else {
                        updateStageState(4, "SKIPPED", "پاسخی از متد قبلی دریافت نشد؛ شروع اسکن", "SCAN")
                    }
                } else {
                    updateStageState(4, "SKIPPED", "فاقد متد ذخیره‌شده؛ ورود به اسکن هوشمند", "NEW")
                }

                if (winningVector == null && isGenValid(gen)) {
                    _engineState.value = EngineState.PROBING_VECTORS
                    val vectorPlan = UriConfigCodec.buildVectorPlan(profile, cfgSettings)
                    _liveVectorProbes.value = vectorPlan.map { v ->
                        LiveVectorProbeItem(
                            vectorId = v.vectorId,
                            codeName = v.codeName,
                            family = v.family,
                            state = "QUEUED",
                            latencyMs = -1,
                            rank = 0
                        )
                    }

                    updateStageState(5, "ACTIVE", "اسکن زنده ${vectorPlan.size} متد ضد DPI...", "0/${vectorPlan.size}")
                    val passedCandidates = mutableListOf<Pair<BypassVector, Long>>()

                    for ((stepIdx, vec) in vectorPlan.withIndex()) {
                        if (!isGenValid(gen)) break
                        if (cachedVec != null && vec.vectorId == cachedVec.vectorId) {
                            markVectorProbe(vec.vectorId, "BLOCKED", -1)
                            continue
                        }

                        val pct = 42 + (((stepIdx + 1) * 32) / vectorPlan.size.coerceAtLeast(1))
                        markVectorProbe(vec.vectorId, "TESTING", -1)
                        updateStageState(
                            stepNumber = 5,
                            state = "ACTIVE",
                            subtitle = "اسکن متد ${vec.vectorId}: ${vec.codeName}",
                            badge = "${stepIdx + 1}/${vectorPlan.size}"
                        )
                        setConnectingTelemetry(
                            phase = "STAGE_5_SCAN_METHODS",
                            headline = "مرحله ۵ از ۷ • اسکن هوشمند متدهای ضد DPI",
                            detail = "آزمایش ${vec.vectorId} (${stepIdx + 1}/${vectorPlan.size}) — ${vec.codeName}",
                            percent = pct,
                            stageIndex = 5
                        )

                        bridge.updateVector(vec, cfgSettings.forwardStrideBytes, cfgSettings.strideJitterBytes)
                        bridge.dropActiveConnections()

                        val fast = TunnelStreamVerifier.fastProbe(session.httpPort, 2500L)
                        if (fast.fatalUpstreamError) {
                            markVectorProbe(vec.vectorId, "BLOCKED", -1)
                            appendLog("Upstream HTTP error (${fast.reason}); stopping vector scan.")
                            break
                        }

                        if (fast.ok) {
                            passedCandidates.add(vec to fast.latencyMs)
                            passedCandidates.sortBy { it.second }
                            refreshVectorProbeRanks(passedCandidates, vec.vectorId, fast.latencyMs.toInt())
                            appendLog("Method scan hit: ${vec.vectorId} -> ${fast.latencyMs}ms")

                            val ultraFastHit = fast.latencyMs <= 420L && stepIdx >= 1
                            val enoughShortlist = passedCandidates.size >= 3
                            if (vectorPlan.size == 1 || ultraFastHit || enoughShortlist) {
                                break
                            }
                        } else {
                            markVectorProbe(vec.vectorId, "BLOCKED", -1)
                        }
                    }

                    if (!isGenValid(gen)) {
                        xrayCoreManager.stopCore()
                        bridge.stop()
                        return
                    }

                    if (passedCandidates.isEmpty()) {
                        updateStageState(5, "FAILED", "هیچ متدی از سد فیلترینگ عبور نکرد", "0/${vectorPlan.size}")
                    } else {
                        val bestCandidate = passedCandidates.first()
                        updateStageState(
                            stepNumber = 5,
                            state = "DONE",
                            subtitle = "${passedCandidates.size} متد عبور کرد • صدرنشین: ${bestCandidate.first.vectorId}",
                            badge = "${bestCandidate.second}ms"
                        )

                        _engineState.value = EngineState.VERIFYING_STABILITY
                        updateStageState(6, "ACTIVE", "تست بار آپلود ۱۲KB و پایداری استریم یوتیوب...", "QUALIFY")

                        var softWinner: Pair<BypassVector, Long>? = null
                        for ((rankIdx, pair) in passedCandidates.withIndex()) {
                            if (!isGenValid(gen)) break
                            val (candVec, candFastMs) = pair
                            val qPct = 78 + (((rankIdx + 1) * 14) / passedCandidates.size.coerceAtLeast(1))
                            setConnectingTelemetry(
                                phase = "STAGE_6_QUALIFY",
                                headline = "مرحله ۶ از ۷ • راستی‌آزمایی پایداری استریم",
                                detail = "تست عمیق کاندیدای #${rankIdx + 1} (${candVec.vectorId} • ${candFastMs}ms)...",
                                percent = qPct,
                                stageIndex = 6
                            )

                            bridge.updateVector(candVec, cfgSettings.forwardStrideBytes, cfgSettings.strideJitterBytes)
                            bridge.dropActiveConnections()

                            val qual = TunnelStreamVerifier.runFullQualification(
                                httpPort = session.httpPort,
                                verifyUploadStream = cfgSettings.verifyUploadStream,
                                requireYoutube6s = cfgSettings.requireYoutube6s,
                                isCancelled = { !isGenValid(gen) }
                            )
                            if (qual.fullyQualified) {
                                winningVector = candVec
                                winningLatency = qual.latencyMs
                                markVectorProbe(candVec.vectorId, "QUALIFIED", qual.latencyMs.toInt())
                                updateStageState(
                                    stepNumber = 6,
                                    state = "DONE",
                                    subtitle = "پایداری کامل استریم روی ${candVec.vectorId} تایید شد",
                                    badge = "${qual.latencyMs}ms"
                                )
                                appendLog("Stage 6 qualified #${rankIdx + 1}: ${candVec.vectorId} (${qual.latencyMs}ms)")
                                break
                            } else if (qual.softReachable) {
                                val lat = if (qual.latencyMs > 0) qual.latencyMs else candFastMs
                                if (softWinner == null || lat < softWinner.second) {
                                    softWinner = candVec to lat
                                }
                            } else if (softWinner == null) {
                                softWinner = candVec to candFastMs
                            }
                        }

                        if (winningVector == null && softWinner != null && isGenValid(gen)) {
                            winningVector = softWinner.first
                            winningLatency = softWinner.second
                            markVectorProbe(softWinner.first.vectorId, "QUALIFIED", softWinner.second.toInt())
                            updateStageState(
                                stepNumber = 6,
                                state = "DONE",
                                subtitle = "قفل روی سریع‌ترین متد پاسخ‌گو (${softWinner.first.vectorId})",
                                badge = "${softWinner.second}ms"
                            )
                        }
                    }
                }
            }

            if (!isGenValid(gen)) {
                xrayCoreManager.stopCore()
                bridge.stop()
                return
            }

            val finalVec = winningVector
            if (finalVec == null) {
                xrayCoreManager.stopCore()
                bridge.stop()
                updateProfileStatus(profile.uid, cachedVectorId = "", pingMs = -1, status = "DEAD")
                _taskProgress.value = TaskProgressState(active = false)
                _engineState.value = EngineState.ERROR
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "عدم برقراری تونل",
                        statusDetail = "تمام متدهای عبور از DPI روی ${profile.name} مسدود شدند.",
                        buttonText = "تلاش مجدد",
                        progressPercent = 0
                    )
                }
                appendLog("All bypass vectors failed on '${profile.name}'.")
                return
            }

            updateStageState(7, "ACTIVE", "راه‌اندازی رابط کرنلی AlanTUN و استخراج Geo-IP...", "TUN")
            setConnectingTelemetry(
                phase = "STAGE_7_TUN_LOCK",
                headline = "مرحله ۷ از ۷ • قفل تونل کرنلی AlanTUN",
                detail = "انتقال ترافیک سیستم روی ${finalVec!!.vectorId}...",
                percent = 96,
                stageIndex = 7
            )

            activateVerifiedSessionUnlocked(
                gen = gen,
                profile = profile,
                session = session,
                bridge = bridge,
                vector = finalVec!!,
                verifiedLatencyMs = winningLatency
            )
        }
    }

    private suspend fun executeHuntBestAndConnect(gen: Long) {
        pipelineMutex.withLock {
            if (!isGenValid(gen)) return
            teardownActiveRuntime()
            resetConnectionStagesForNewRun()
            _liveVectorProbes.value = emptyList()
            clearSparklines()

            _engineState.value = EngineState.HUNTING_BEST
            updateStageState(1, "ACTIVE", "به‌روزرسانی اشتراک‌ها برای شکار هوشمند...", "SYNC")
            setConnectingTelemetry(
                phase = "HUNTING",
                headline = "شکار هوشمند بهترین سرور",
                detail = "به‌روزرسانی اشتراک‌ها و آماده‌سازی مخزن...",
                percent = 8,
                stageIndex = 1
            )
            if (gen >= 0L) {
                executeSmartHuntUnlocked(gen)
                return
            }
            syncAllSubscriptionsSuspend()
            val hiddenNodes = syncInternalOfficialAutoPoolSuspend()
            val visibleCount = _profiles.value.size
            val allNodes = (_profiles.value + hiddenNodes).distinctBy { it.uri }
            if (allNodes.isEmpty()) {
                updateStageState(1, "FAILED", "مخزن خالی است", "EMPTY")
                _taskProgress.value = TaskProgressState(active = false)
                _engineState.value = EngineState.ERROR
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "سروری یافت نشد",
                        statusDetail = "مخزن خالی است؛ کانفیگ اضافه کنید یا ساب را پر کنید.",
                        buttonText = "اتصال هوشمند"
                    )
                }
                return
            }
            updateStageState(1, "DONE", "${allNodes.size} کانفیگ آماده ارزیابی", "${allNodes.size}")

            updateStageState(2, "ACTIVE", "پیش‌پالایش موازی TCP روی ${allNodes.size} سرور...", "TCP")
            setConnectingTelemetry(
                phase = "HUNTING",
                headline = "پیش‌پالایش موازی TCP",
                detail = "تست هم‌زمان پاسخ‌دهی ${allNodes.size} سرور...",
                percent = 20,
                stageIndex = 2
            )

            val sem = Semaphore(16)
            val reachable = coroutineScope {
                allNodes.mapIndexed { idx, node ->
                    async<Triple<Int, ProfileItem, Long>?> {
                        if (!isGenValid(gen)) return@async null
                        sem.withPermit {
                            val rtt = TunnelStreamVerifier.tcpReachabilityCheck(
                                host = node.host,
                                port = node.port,
                                timeoutMs = 1650,
                                protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
                            )
                            if (rtt > 0) Triple(idx, node, rtt) else null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (!isGenValid(gen)) return
            if (reachable.isEmpty()) {
                updateStageState(2, "FAILED", "هیچ سروری به TCP پاسخ نداد", "0/${allNodes.size}")
                _taskProgress.value = TaskProgressState(active = false)
                _engineState.value = EngineState.ERROR
                _telemetry.update {
                    it.copy(
                        uiPhase = "ERROR",
                        isConnecting = false,
                        isConnected = false,
                        statusHeadline = "خطای شبکه",
                        statusDetail = "هیچ‌یک از سرورها به هندشیک TCP پاسخ ندادند.",
                        buttonText = "تلاش مجدد"
                    )
                }
                return
            }
            updateStageState(2, "DONE", "${reachable.size} سرور فعال شناسایی شد", "${reachable.size} UP")

            val reachableMap = reachable.associate { it.second.uid to it.third.toInt() }
            val selIdx = _selectedIndex.value
            _profiles.update { list ->
                list.mapIndexed { idx, p ->
                    val rtt = reachableMap[p.uid]
                    if (rtt != null) {
                        p.copy(pingMs = rtt, status = if (p.status == "VERIFIED") "VERIFIED" else "ONLINE", isSelected = idx == selIdx)
                    } else {
                        p.copy(pingMs = -1, status = "DEAD", isSelected = idx == selIdx)
                    }
                }
            }

            val previouslyVerified = reachable
                .filter { it.second.cachedVectorId.isNotBlank() || it.second.status == "VERIFIED" }
                .sortedBy { it.third }
                .take(4)

            val domesticOrSs = reachable
                .filter {
                    it.second.protocol.equals("SHADOWSOCKS", true) ||
                        it.second.host.lowercase().endsWith(".ir")
                }
                .sortedBy { it.third }
                .take(5)

            val foreignStandard = reachable
                .sortedBy { it.third }
                .take(8)

            val balancedBasket = (previouslyVerified + foreignStandard + domesticOrSs)
                .distinctBy { it.second.uid }
                .take(14)

            updateStageState(3, "DONE", "سبد متوازن ${balancedBasket.size} سرور برتر تشکیل شد", "BASKET")
            updateStageState(4, "SKIPPED", "حالت شکار مخزن فعال است", "HUNT")
            updateStageState(5, "ACTIVE", "اسکن متدها روی نامزدهای برتر...", "0/${balancedBasket.size}")

            val cfgSettings = _settings.value
            var softFallback: Triple<Int, BypassVector, Long>? = null

            for ((pos, entry) in balancedBasket.withIndex()) {
                if (!isGenValid(gen)) break
                val (nodeIdx, node, _) = entry
                val pct = 30 + (((pos + 1) * 60) / balancedBasket.size.coerceAtLeast(1))
                val nodeCountry = CountryPaletteRegistry.detectCountry(node.name, node.host, node.sni, node.countryCode)
                applyCountryPaletteToTelemetry(node, nodeCountry)

                updateStageState(
                    stepNumber = 5,
                    state = "ACTIVE",
                    subtitle = "تست ${nodeCountry.flagEmoji} ${node.name}",
                    badge = "${pos + 1}/${balancedBasket.size}"
                )
                setConnectingTelemetry(
                    phase = "HUNTING",
                    headline = "شکار هوشمند (${pos + 1}/${balancedBasket.size})",
                    detail = "${nodeCountry.flagEmoji} ارزیابی استریم و متد روی ${node.name}...",
                    percent = pct,
                    stageIndex = 5
                )

                val eval = evaluateNodeInSandbox(
                    profile = node,
                    settings = cfgSettings,
                    maxVectors = 6,
                    isCancelled = { !isGenValid(gen) }
                )

                if (eval.qualifiedVector != null) {
                    updateStageState(5, "DONE", "برنده: ${node.name}", eval.qualifiedVector.vectorId)
                    updateStageState(6, "DONE", "استریم و آپلود تایید شد", "${eval.latencyMs}ms")
                    updateStageState(7, "ACTIVE", "قفل نهایی AlanTUN...", "TUN")
                    val targetProfile = if (nodeIdx in _profiles.value.indices) {
                        selectConfig(nodeIdx)
                        _profiles.value[nodeIdx]
                    } else {
                        node.copy(name = "Alan Auto Cloud")
                    }
                    launchLockedProfileUnlocked(gen, targetProfile, eval.qualifiedVector, eval.latencyMs)
                    return
                } else if (eval.softVector != null) {
                    val prevSoft = softFallback
                    if (prevSoft == null || eval.latencyMs < prevSoft.third) {
                        softFallback = Triple(nodeIdx, eval.softVector, eval.latencyMs)
                    }
                }
            }

            if (softFallback != null && isGenValid(gen)) {
                val (winIdx, winVec, winMs) = softFallback
                updateStageState(5, "DONE", "انتخاب بهترین کاندیدای پاسخ‌گو", winVec.vectorId)
                updateStageState(6, "DONE", "پاسخ شبکه تایید شد", "${winMs}ms")
                updateStageState(7, "ACTIVE", "قفل نهایی AlanTUN...", "TUN")
                val targetProfile = if (winIdx in _profiles.value.indices) {
                    selectConfig(winIdx)
                    _profiles.value[winIdx]
                } else {
                    allNodes[winIdx].copy(name = "Alan Auto Cloud")
                }
                launchLockedProfileUnlocked(gen, targetProfile, winVec, winMs)
                return
            }

            updateStageState(5, "FAILED", "سرور فعالی یافت نشد", "FAIL")
            _taskProgress.value = TaskProgressState(active = false)
            _engineState.value = EngineState.ERROR
            _telemetry.update {
                it.copy(
                    uiPhase = "ERROR",
                    isConnecting = false,
                    isConnected = false,
                    statusHeadline = "شکار ناموفق",
                    statusDetail = "سرور فعالی با قابلیت عبور استریم یافت نشد.",
                    buttonText = "تلاش مجدد"
                )
            }
        }
    }

    private suspend fun launchLockedProfileUnlocked(
        gen: Long,
        profile: ProfileItem,
        vector: BypassVector,
        verifiedLatencyMs: Long
    ) {
        if (!isGenValid(gen)) return
        teardownActiveRuntime()
        val cfgSettings = _settings.value
        val session = UriConfigCodec.compileXraySession(
            profile = profile,
            settings = cfgSettings,
            preferredBridgePort = configuredBridgePort
        )
        val bridge = FullDuplexMutatorBridge(
            listenPort = session.bridgePort,
            initialRemoteHost = session.remoteHost,
            initialRemotePort = session.remotePort,
            initialVector = vector,
            initialCustomStride = cfgSettings.forwardStrideBytes,
            initialJitterBytes = cfgSettings.strideJitterBytes,
            protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
        )
        bridge.start()
        if (!xrayCoreManager.startCore(session.configJson)) {
            bridge.stop()
            updateStageState(7, "FAILED", "خطا در اجرای مجدد هسته Xray", "ERR")
            _engineState.value = EngineState.ERROR
            _telemetry.update {
                it.copy(
                    uiPhase = "ERROR",
                    isConnecting = false,
                    isConnected = false,
                    statusHeadline = "خطای هسته Xray",
                    statusDetail = "راه‌اندازی مجدد هسته ناموفق بود.",
                    buttonText = "تلاش مجدد"
                )
            }
            return
        }

        activateVerifiedSessionUnlocked(
            gen = gen,
            profile = profile,
            session = session,
            bridge = bridge,
            vector = vector,
            verifiedLatencyMs = verifiedLatencyMs
        )
    }

    private suspend fun activateVerifiedSessionUnlocked(
        gen: Long,
        profile: ProfileItem,
        session: CompiledXraySession,
        bridge: FullDuplexMutatorBridge,
        vector: BypassVector,
        verifiedLatencyMs: Long
    ) {
        val cfgSettings = _settings.value
        bridge.updateVector(vector, cfgSettings.forwardStrideBytes, cfgSettings.strideJitterBytes)
        bridge.dropActiveConnections()
        bridge.resetCounters()
        prevTxBytes = 0L
        prevRxBytes = 0L

        liveBridge = bridge
        liveSession = session
        connectedTimestampMs = System.currentTimeMillis()
        watchdogFailureStreak = 0

        val country = CountryPaletteRegistry.detectCountry(
            name = profile.name,
            host = profile.host,
            sni = profile.sni,
            explicitCode = profile.countryCode
        )

        AlanVpnService.resetTunHandshakeState()
        AlanVpnService.startTunService(
            context = appContext,
            socksPort = session.socksPort,
            tunMtu = cfgSettings.tunMtu,
            tunDns = cfgSettings.tunDns,
            profileTitle = "${country.flagEmoji} ${profile.name}",
            vectorLabel = vector.vectorId
        )

        var waitedLoops = 0
        while (waitedLoops < 70 && !AlanVpnService.tunEstablishedFlag && AlanVpnService.tunEstablishError == null && isGenValid(gen)) {
            delay(50L)
            waitedLoops++
        }
        if (!isGenValid(gen)) {
            teardownActiveRuntime()
            return
        }
        val tunFatal = AlanVpnService.tunEstablishError
        if (tunFatal != null) {
            updateStageState(7, "FAILED", tunFatal, "TUN")
            teardownActiveRuntime()
            _engineState.value = EngineState.ERROR
            _taskProgress.value = TaskProgressState(active = false)
            _telemetry.update {
                it.copy(
                    uiPhase = "ERROR",
                    isConnecting = false,
                    isConnected = false,
                    statusHeadline = "خطا در راه‌اندازی TUN",
                    statusDetail = tunFatal,
                    buttonText = "تلاش مجدد"
                )
            }
            return
        }

        val finalPing = verifiedLatencyMs.toInt().coerceAtLeast(1)
        pushPingSample(finalPing.toFloat())
        updateProfileStatus(profile.uid, cachedVectorId = vector.vectorId, pingMs = finalPing, status = "VERIFIED")
        updateStageState(7, "DONE", "تونل AlanTUN فعال شد", "${country.flagEmoji} ${vector.vectorId}")

        val layerHostLabel = profile.sni.ifBlank { profile.host }
        _taskProgress.value = TaskProgressState(active = false, percent = 100, message = "", stageIndex = 7)
        _engineState.value = EngineState.CONNECTED
        _telemetry.update {
            it.copy(
                uiPhase = "CONNECTED",
                isConnecting = false,
                isConnected = true,
                statusHeadline = "متصل است VPN",
                statusDetail = "${vector.codeName}  •  لایه: $layerHostLabel",
                buttonText = "قطع اتصال امن",
                uptimeLabel = "00:00:00",
                egressIp = "...",
                activeVectorId = vector.vectorId,
                activeVectorName = vector.codeName,
                activeProfileName = profile.name,
                activeProtocol = profile.protocol,
                layerHost = layerHostLabel,
                countryCode = country.code,
                countryName = country.nameFa,
                flagEmoji = country.flagEmoji,
                flagStripeColors = country.stripeColors,
                accentPrimaryColor = country.accentPrimary,
                accentSecondaryColor = country.accentSecondary,
                pingMs = finalPing,
                rxRateKbps = 0f,
                txRateKbps = 0f,
                rxRateLabel = "0 B/s",
                txRateLabel = "0 B/s",
                progressPercent = 100,
                currentStageIndex = 7,
                selectedIndex = _selectedIndex.value,
                activeProfileUid = profile.uid,
                pingHistory = snapshotQueue(pingHistoryQueue)
            )
        }
        appendLog("AlanTUN UP: '${profile.name}' [${vector.vectorId}] (${finalPing}ms)")

        scope.launch {
            delay(350L)
            val geo = TunnelStreamVerifier.resolveEgressEndpoint(session.httpPort)
            if (_engineState.value == EngineState.CONNECTED && isGenValid(gen)) {
                val resolvedSpec = if (geo.countryCode != "UN") {
                    CountryPaletteRegistry.resolveByCode(geo.countryCode)
                } else {
                    country
                }
                updateProfileCountry(profile.uid, resolvedSpec)
                _telemetry.update {
                    it.copy(
                        egressIp = geo.ip,
                        countryCode = resolvedSpec.code,
                        countryName = resolvedSpec.nameFa,
                        flagEmoji = resolvedSpec.flagEmoji,
                        flagStripeColors = resolvedSpec.stripeColors,
                        accentPrimaryColor = resolvedSpec.accentPrimary,
                        accentSecondaryColor = resolvedSpec.accentSecondary
                    )
                }
                AlanVpnService.activeInstance?.updateNotificationSummary(
                    "${resolvedSpec.flagEmoji} ${profile.name} (${resolvedSpec.code})",
                    vector.vectorId
                )
                appendLog("Verified Egress: ${geo.ip} (${resolvedSpec.flagEmoji} ${resolvedSpec.code})")
            }
        }
    }

    private suspend fun evaluateNodeInSandbox(
        profile: ProfileItem,
        settings: EngineSettings,
        maxVectors: Int,
        isCancelled: () -> Boolean
    ): SandboxNodeEvaluation {
        val session = try {
            UriConfigCodec.compileXraySession(
                profile = profile,
                settings = settings,
                preferredSocksPort = 0,
                preferredHttpPort = 0,
                preferredBridgePort = 0,
                forceLogSilent = true
            )
        } catch (_: Throwable) {
            return SandboxNodeEvaluation(null, null, -1L)
        }

        val plan = buildList {
            if (profile.cachedVectorId.isNotBlank()) {
                VectorCatalog.VECTOR_REGISTRY.firstOrNull { it.vectorId == profile.cachedVectorId }?.let { add(it) }
            }
            addAll(UriConfigCodec.buildVectorPlan(profile, settings))
        }.distinctBy { it.vectorId }.take(maxVectors)

        if (plan.isEmpty()) return SandboxNodeEvaluation(null, null, -1L)

        val bridge = FullDuplexMutatorBridge(
            listenPort = session.bridgePort,
            initialRemoteHost = session.remoteHost,
            initialRemotePort = session.remotePort,
            initialVector = plan.first(),
            initialCustomStride = settings.forwardStrideBytes,
            initialJitterBytes = settings.strideJitterBytes,
            protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
        )

        try {
            bridge.start()
            if (!xrayCoreManager.startCore(session.configJson)) {
                return SandboxNodeEvaluation(null, null, -1L)
            }
            delay(140L)

            val passed = mutableListOf<Pair<BypassVector, Long>>()
            for ((idx, vec) in plan.withIndex()) {
                if (isCancelled()) break
                bridge.updateVector(vec, settings.forwardStrideBytes, settings.strideJitterBytes)
                bridge.dropActiveConnections()
                delay(35L)
                val fast = TunnelStreamVerifier.fastProbe(session.httpPort, 3600L)
                // Continue testing remaining vectors even on 502/503
                if (!fast.ok) continue

                passed.add(vec to fast.latencyMs)
                if ((fast.latencyMs <= 420L && idx >= 1) || passed.size >= 2 || plan.size == 1) {
                    break
                }
            }

            passed.sortBy { it.second }
            var bestSoft: Pair<BypassVector, Long>? = passed.firstOrNull()

            for ((candVec, _) in passed) {
                if (isCancelled()) break
                bridge.updateVector(candVec, settings.forwardStrideBytes, settings.strideJitterBytes)
                bridge.dropActiveConnections()

                val qual = TunnelStreamVerifier.runFullQualification(
                    httpPort = session.httpPort,
                    verifyUploadStream = settings.verifyUploadStream,
                    requireYoutube6s = settings.requireYoutube6s,
                    isCancelled = isCancelled
                )
                if (qual.fullyQualified) {
                    return SandboxNodeEvaluation(candVec, candVec, qual.latencyMs)
                }
                if (qual.softReachable && qual.latencyMs > 0) {
                    if (bestSoft == null || qual.latencyMs < bestSoft.second) {
                        bestSoft = candVec to qual.latencyMs
                    }
                }
            }

            return if (bestSoft != null) {
                SandboxNodeEvaluation(null, bestSoft.first, bestSoft.second)
            } else {
                SandboxNodeEvaluation(null, null, -1L)
            }
        } finally {
            xrayCoreManager.stopCore()
            bridge.stop()
        }
    }

    private suspend fun runActiveProfileVectorScan(
        scanGen: Long,
        targetIndex: Int,
        autoConnect: Boolean
    ) {
        pipelineMutex.withLock {
            val list = _profiles.value
            if (list.isEmpty()) {
                _scanState.value = ScanUiState(
                    isRunning = false,
                    mode = "active_profile",
                    percent = 0,
                    summary = "ابتدا یک کانفیگ وارد مخزن کنید"
                )
                return
            }
            val resolvedIdx = if (targetIndex in list.indices) {
                targetIndex
            } else {
                _selectedIndex.value.coerceIn(0, list.lastIndex)
            }
            val profile = list[resolvedIdx]

            if (_engineState.value == EngineState.CONNECTED) {
                teardownActiveRuntime()
                _engineState.value = EngineState.DISCONNECTED
                _telemetry.update { it.copy(uiPhase = "IDLE", isConnected = false, isConnecting = false) }
            }

            val vectors = VectorCatalog.VECTOR_REGISTRY
            _scanRows.value = emptyList()
            _scanState.value = ScanUiState(
                isRunning = true,
                mode = "active_profile",
                percent = 2,
                summary = "اسکن ۱۷ بردار روی ${profile.flagEmoji} ${profile.name}..."
            )
            appendLog("Vector benchmark started on '${profile.name}'...")

            val tcpBase = TunnelStreamVerifier.tcpReachabilityCheck(
                host = profile.host,
                port = profile.port,
                timeoutMs = 1800,
                protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
            ).let { if (it > 0) it.toInt() else 0 }

            val cfgSettings = _settings.value
            val session = try {
                UriConfigCodec.compileXraySession(profile, cfgSettings, 0, 0, 0, forceLogSilent = true)
            } catch (e: Throwable) {
                _scanState.value = ScanUiState(false, "active_profile", 0, "خطای کانفیگ: ${e.message}")
                return
            }

            val bridge = FullDuplexMutatorBridge(
                listenPort = session.bridgePort,
                initialRemoteHost = session.remoteHost,
                initialRemotePort = session.remotePort,
                initialVector = vectors.first(),
                initialCustomStride = cfgSettings.forwardStrideBytes,
                initialJitterBytes = cfgSettings.strideJitterBytes,
                protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
            )

            val workingRows = mutableListOf<ScanResultRow>()

            try {
                bridge.start()
                if (!xrayCoreManager.startCore(session.configJson)) {
                    _scanState.value = ScanUiState(false, "active_profile", 0, "خطا در اجرای هسته Xray")
                    return
                }
                delay(160L)

                for ((i, vec) in vectors.withIndex()) {
                    if (scanGeneration.get() != scanGen) break
                    val pct = ((i + 1) * 100) / vectors.size
                    _scanState.value = ScanUiState(
                        isRunning = true,
                        mode = "active_profile",
                        percent = pct,
                        summary = "تست ${vec.vectorId} (${i + 1}/${vectors.size}): ${vec.codeName}"
                    )

                    bridge.updateVector(vec, cfgSettings.forwardStrideBytes, cfgSettings.strideJitterBytes)
                    bridge.dropActiveConnections()
                    delay(40L)
                    val fast = TunnelStreamVerifier.fastProbe(session.httpPort, 3800L)
                    if (fast.ok) {
                        val qual = TunnelStreamVerifier.runFullQualification(
                            httpPort = session.httpPort,
                            verifyUploadStream = cfgSettings.verifyUploadStream,
                            requireYoutube6s = false,
                            isCancelled = { scanGeneration.get() != scanGen }
                        )
                        val tunMs = (if (qual.latencyMs > 0) qual.latencyMs else fast.latencyMs).toInt()
                        val st = if (qual.fullyQualified) "VERIFIED" else "ONLINE"
                        val scoreVal = ((if (qual.fullyQualified) 100f else 72f) - (tunMs / 25f)).coerceIn(10f, 99.9f)

                        workingRows.add(
                            ScanResultRow(
                                scanType = "vector",
                                itemId = vec.vectorId,
                                rank = 0,
                                targetIndex = resolvedIdx,
                                boundVector = vec.vectorId,
                                title = "${vec.vectorId} • ${vec.codeName}",
                                category = vec.family,
                                endpoint = "${profile.host}:${profile.port}",
                                tcpMs = tcpBase,
                                tunnelMs = tunMs,
                                status = st,
                                score = scoreVal,
                                detail = vec.summary,
                                countryCode = profile.countryCode,
                                flagEmoji = profile.flagEmoji
                            )
                        )

                        val ranked = workingRows
                            .sortedWith(compareBy<ScanResultRow> { it.status != "VERIFIED" }.thenBy { it.tunnelMs })
                            .mapIndexed { rIdx, r -> r.copy(rank = rIdx + 1) }
                        _scanRows.value = ranked
                    }
                }
            } finally {
                xrayCoreManager.stopCore()
                bridge.stop()
            }

            val finalRows = _scanRows.value
            _scanState.value = ScanUiState(
                isRunning = false,
                mode = "active_profile",
                percent = 100,
                summary = "تکمیل شد: ${finalRows.size} متد فعال از ${vectors.size} بردار"
            )
            saveScanCacheToDisk()
            appendLog("Vector scan finished: ${finalRows.size}/${vectors.size} working.")

            val topWinner = finalRows.firstOrNull()
            if (autoConnect && topWinner != null && scanGeneration.get() == scanGen) {
                applyScannedVector(
                    vectorId = topWinner.itemId,
                    targetIndex = resolvedIdx,
                    endpoint = "",
                    autoConnect = true
                )
            }
        }
    }

    private suspend fun runFullRepositoryScan(
        scanGen: Long,
        autoConnect: Boolean,
        pruneDead: Boolean
    ) {
        pipelineMutex.withLock {
            val repo = _profiles.value
            if (repo.isEmpty()) {
                _scanState.value = ScanUiState(false, "full_repository", 0, "مخزن کانفیگ خالی است")
                return
            }

            if (_engineState.value == EngineState.CONNECTED) {
                teardownActiveRuntime()
                _engineState.value = EngineState.DISCONNECTED
                _telemetry.update { it.copy(uiPhase = "IDLE", isConnected = false, isConnecting = false) }
            }

            _scanRows.value = emptyList()
            _scanState.value = ScanUiState(
                isRunning = true,
                mode = "full_repository",
                percent = 1,
                summary = "پویش سلامت ${repo.size} سرور مخزن..."
            )
            appendLog("Full repository scan started (${repo.size} profiles)...")

            val cfgSettings = _settings.value
            val collected = mutableListOf<ScanResultRow>()

            for ((idx, node) in repo.withIndex()) {
                if (scanGeneration.get() != scanGen) break
                val pct = ((idx + 1) * 100) / repo.size
                _scanState.value = ScanUiState(
                    isRunning = true,
                    mode = "full_repository",
                    percent = pct,
                    summary = "تست [${idx + 1}/${repo.size}]: ${node.flagEmoji} ${node.name}"
                )

                val tcpRtt = TunnelStreamVerifier.tcpReachabilityCheck(
                    host = node.host,
                    port = node.port,
                    timeoutMs = 1600,
                    protectSocket = { sock -> AlanVpnService.protectRawSocket(sock) }
                )
                if (tcpRtt < 0) {
                    updateProfileStatus(node.uid, node.cachedVectorId, -1, "DEAD")
                    continue
                }

                val eval = evaluateNodeInSandbox(
                    profile = node,
                    settings = cfgSettings,
                    maxVectors = 4,
                    isCancelled = { scanGeneration.get() != scanGen }
                )
                val bestVec = eval.qualifiedVector ?: eval.softVector
                if (bestVec != null && eval.latencyMs > 0) {
                    val tunMs = eval.latencyMs.toInt()
                    val st = if (eval.qualifiedVector != null) "VERIFIED" else "ONLINE"
                    updateProfileStatus(node.uid, bestVec.vectorId, tunMs, st)

                    val scoreVal = ((if (st == "VERIFIED") 100f else 75f) - (tunMs / 30f)).coerceIn(10f, 99.9f)
                    collected.add(
                        ScanResultRow(
                            scanType = "profile",
                            itemId = node.uid,
                            rank = 0,
                            targetIndex = idx,
                            boundVector = bestVec.vectorId,
                            title = "${node.flagEmoji} ${node.name}",
                            category = node.protocol,
                            endpoint = "${node.host}:${node.port}",
                            tcpMs = tcpRtt.toInt(),
                            tunnelMs = tunMs,
                            status = st,
                            score = scoreVal,
                            detail = "${bestVec.vectorId} • ${bestVec.codeName}",
                            countryCode = node.countryCode,
                            flagEmoji = node.flagEmoji
                        )
                    )
                    val ranked = collected
                        .sortedWith(compareBy<ScanResultRow> { it.status != "VERIFIED" }.thenBy { it.tunnelMs })
                        .mapIndexed { rIdx, r -> r.copy(rank = rIdx + 1) }
                    _scanRows.value = ranked
                } else {
                    updateProfileStatus(node.uid, "", -1, "DEAD")
                }
            }

            if (pruneDead && scanGeneration.get() == scanGen) {
                removeDeadConfigs()
            }

            val healthyCount = _scanRows.value.size
            _scanState.value = ScanUiState(
                isRunning = false,
                mode = "full_repository",
                percent = 100,
                summary = "اسکن مخزن کامل شد: $healthyCount سرور سالم از ${repo.size}"
            )
            saveScanCacheToDisk()
            appendLog("Repository scan finished: $healthyCount/${repo.size} healthy.")

            val winner = _scanRows.value.firstOrNull()
            if (autoConnect && winner != null && scanGeneration.get() == scanGen) {
                val winIdx = _profiles.value.indexOfFirst { it.uid == winner.itemId }
                if (winIdx >= 0) {
                    connectScannedProfile(winIdx, winner.boundVector, "")
                }
            }
        }
    }

    private fun startTelemetryTicker() {
        scope.launch {
            while (isActive) {
                delay(1000L)
                if (_engineState.value != EngineState.CONNECTED) continue
                val bridge = liveBridge ?: continue

                val curTx = bridge.txBytesTotal.get()
                val curRx = bridge.rxBytesTotal.get()
                val deltaTx = (curTx - prevTxBytes).coerceAtLeast(0L)
                val deltaRx = (curRx - prevRxBytes).coerceAtLeast(0L)
                prevTxBytes = curTx
                prevRxBytes = curRx

                val txKbps = deltaTx.toDouble() / 1024.0
                val rxKbps = deltaRx.toDouble() / 1024.0
                val uptimeSec = ((System.currentTimeMillis() - connectedTimestampMs) / 1000L).coerceAtLeast(0L)

                val (rxHist, txHist, pingHist) = synchronized(historyLock) {
                    pushBounded(rxHistoryQueue, rxKbps.toFloat())
                    pushBounded(txHistoryQueue, txKbps.toFloat())
                    if (pingHistoryQueue.isEmpty() && _telemetry.value.pingMs > 0) {
                        pushBounded(pingHistoryQueue, _telemetry.value.pingMs.toFloat())
                    }
                    Triple(
                        rxHistoryQueue.toList(),
                        txHistoryQueue.toList(),
                        pingHistoryQueue.toList()
                    )
                }

                _telemetry.update {
                    it.copy(
                        uptimeLabel = formatUptimeHms(uptimeSec),
                        txRateKbps = txKbps.toFloat(),
                        rxRateKbps = rxKbps.toFloat(),
                        txRateLabel = formatByteRate(txKbps),
                        rxRateLabel = formatByteRate(rxKbps),
                        rxHistory = rxHist,
                        txHistory = txHist,
                        pingHistory = pingHist
                    )
                }
            }
        }
    }

    private fun startSelfHealingWatchdog() {
        scope.launch {
            while (isActive) {
                delay(18_000L)
                val cfg = _settings.value
                if (_engineState.value != EngineState.CONNECTED || !cfg.autoPilot || !cfg.selfHealingWatchdog) {
                    watchdogFailureStreak = 0
                    continue
                }
                if (pipelineMutex.isLocked) continue

                val session = liveSession ?: continue
                val probe = TunnelStreamVerifier.fastProbe(session.httpPort, 3000L)
                if (probe.ok) {
                    watchdogFailureStreak = 0
                    val pMs = probe.latencyMs.toInt()
                    pushPingSample(pMs.toFloat())
                    _telemetry.update {
                        it.copy(
                            pingMs = pMs,
                            pingHistory = snapshotQueue(pingHistoryQueue)
                        )
                    }
                    continue
                }

                watchdogFailureStreak++
                appendLog("Watchdog check failed ($watchdogFailureStreak/2): ${probe.reason}")
                if (watchdogFailureStreak >= 2) {
                    watchdogFailureStreak = 0
                    val bridge = liveBridge ?: continue
                    val activeProfile = _profiles.value.getOrNull(_selectedIndex.value) ?: continue
                    val plan = UriConfigCodec.buildVectorPlan(activeProfile, cfg)
                    val curId = bridge.getCurrentVector().vectorId

                    appendLog("Watchdog healing: rotating vector from $curId...")
                    var healed = false
                    for (candidate in plan) {
                        if (_engineState.value != EngineState.CONNECTED) break
                        if (candidate.vectorId == curId) continue
                        bridge.updateVector(candidate, cfg.forwardStrideBytes, cfg.strideJitterBytes)
                        bridge.dropActiveConnections()
                        val verify = TunnelStreamVerifier.fastProbe(session.httpPort, 2600L)
                        if (verify.ok) {
                            healed = true
                            val newPing = verify.latencyMs.toInt()
                            pushPingSample(newPing.toFloat())
                            updateProfileStatus(activeProfile.uid, candidate.vectorId, newPing, "VERIFIED")
                            val layer = activeProfile.sni.ifBlank { activeProfile.host }
                            _telemetry.update {
                                it.copy(
                                    activeVectorId = candidate.vectorId,
                                    activeVectorName = candidate.codeName,
                                    pingMs = newPing,
                                    statusDetail = "${candidate.codeName}  •  لایه: $layer",
                                    pingHistory = snapshotQueue(pingHistoryQueue)
                                )
                            }
                            AlanVpnService.activeInstance?.updateNotificationSummary(
                                "${activeProfile.flagEmoji} ${activeProfile.name}",
                                candidate.vectorId
                            )
                            appendLog("Watchdog healed tunnel via ${candidate.vectorId} (${newPing}ms)")
                            break
                        }
                    }

                    if (!healed) {
                        bridge.updateVector(
                            VectorCatalog.findVector(curId),
                            cfg.forwardStrideBytes,
                            cfg.strideJitterBytes
                        )
                    }
                }
            }
        }
    }

    private fun detectNetworkOperatorLabel(): String {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val caps = if (net != null) cm.getNetworkCapabilities(net) else null
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                "Wi-Fi"
            } else {
                val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val op = tm?.networkOperatorName?.trim().orEmpty()
                if (op.isNotEmpty()) op else "Mobile Data"
            }
        } catch (_: Throwable) {
            "Network"
        }
    }

    private fun saveScanCacheToDisk() {
        synchronized(storageLock) {
            try {
                val arr = JSONArray()
                for (r in _scanRows.value) {
                    arr.put(
                        JSONObject().apply {
                            put("scanType", r.scanType)
                            put("itemId", r.itemId)
                            put("rank", r.rank)
                            put("targetIndex", r.targetIndex)
                            put("boundVector", r.boundVector)
                            put("title", r.title)
                            put("category", r.category)
                            put("endpoint", r.endpoint)
                            put("tcpMs", r.tcpMs)
                            put("tunnelMs", r.tunnelMs)
                            put("status", r.status)
                            put("score", r.score.toDouble())
                            put("detail", r.detail)
                            put("countryCode", r.countryCode)
                            put("flagEmoji", r.flagEmoji)
                        }
                    )
                }
                val root = JSONObject().apply {
                    put("mode", _scanState.value.mode)
                    put("summary", _scanState.value.summary)
                    put("rows", arr)
                }
                scanCacheFile.writeText(root.toString(), Charsets.UTF_8)
            } catch (_: Throwable) {
            }
        }
    }

    private fun loadScanCacheFromDisk() {
        if (!scanCacheFile.exists()) return
        try {
            val root = JSONObject(scanCacheFile.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("rows") ?: return
            val list = ArrayList<ScanResultRow>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ScanResultRow(
                        scanType = o.optString("scanType", "vector"),
                        itemId = o.optString("itemId", ""),
                        rank = o.optInt("rank", i + 1),
                        targetIndex = o.optInt("targetIndex", 0),
                        boundVector = o.optString("boundVector", ""),
                        title = o.optString("title", ""),
                        category = o.optString("category", ""),
                        endpoint = o.optString("endpoint", ""),
                        tcpMs = o.optInt("tcpMs", 0),
                        tunnelMs = o.optInt("tunnelMs", 0),
                        status = o.optString("status", "VERIFIED"),
                        score = o.optDouble("score", 90.0).toFloat(),
                        detail = o.optString("detail", ""),
                        countryCode = o.optString("countryCode", "UN"),
                        flagEmoji = o.optString("flagEmoji", "")
                    )
                )
            }
            if (list.isNotEmpty()) {
                _scanRows.value = list
                _scanState.value = ScanUiState(
                    isRunning = false,
                    mode = root.optString("mode", "active_profile"),
                    percent = 100,
                    summary = root.optString("summary", "نتایج آخرین اسکن بازیابی شد")
                )
            }
        } catch (_: Throwable) {
        }
    }

    private fun buildDefaultStages(): List<ConnectionStageStep> = listOf(
        ConnectionStageStep(1, "SYNC_PARSE", "آماده‌سازی مخزن و تحلیل کانفیگ"),
        ConnectionStageStep(2, "TCP_PREFLIGHT", "پیش‌پالایش لایه ۴ و پینگ TCP"),
        ConnectionStageStep(3, "CORE_BRIDGE", "راه‌اندازی هسته Xray و پل MSG_OOB"),
        ConnectionStageStep(4, "FAST_RESUME", "بررسی متد کش‌شده قبلی"),
        ConnectionStageStep(5, "VECTOR_SCAN", "اسکن رقابتی ماتریس متدهای ضد DPI"),
        ConnectionStageStep(6, "STREAM_QUALIFY", "راستی‌آزمایی آپلود و پایداری ۶ ثانیه"),
        ConnectionStageStep(7, "TUN_GEO_LOCK", "قفل AlanTUN و شناسایی کشور خروجی")
    )

    private fun resetConnectionStagesForNewRun() {
        _connectionStages.value = buildDefaultStages()
    }

    private fun updateStageState(
        stepNumber: Int,
        state: String,
        subtitle: String,
        badge: String
    ) {
        _connectionStages.update { list ->
            list.map { step ->
                if (step.stepNumber == stepNumber) {
                    step.copy(state = state, subtitle = subtitle, badgeText = badge)
                } else {
                    step
                }
            }
        }
    }

    private fun markVectorProbe(vectorId: String, state: String, latencyMs: Int) {
        _liveVectorProbes.update { list ->
            list.map { item ->
                if (item.vectorId == vectorId) {
                    item.copy(state = state, latencyMs = latencyMs)
                } else {
                    item
                }
            }
        }
    }

    private fun refreshVectorProbeRanks(
        sortedPassed: List<Pair<BypassVector, Long>>,
        justPassedId: String,
        justPassedMs: Int
    ) {
        val rankMap = sortedPassed.mapIndexed { idx, p -> p.first.vectorId to (idx + 1) }.toMap()
        _liveVectorProbes.update { list ->
            list.map { item ->
                val rk = rankMap[item.vectorId] ?: 0
                if (item.vectorId == justPassedId) {
                    item.copy(state = "PASSED", latencyMs = justPassedMs, rank = rk)
                } else if (rk > 0) {
                    item.copy(rank = rk)
                } else {
                    item
                }
            }
        }
    }

    private fun applyCountryPaletteToTelemetry(profile: ProfileItem, spec: CountryThemeSpec) {
        _telemetry.update {
            it.copy(
                activeProfileUid = profile.uid,
                activeProfileName = profile.name,
                activeProtocol = profile.protocol,
                layerHost = profile.sni.ifBlank { profile.host },
                countryCode = spec.code,
                countryName = spec.nameFa,
                flagEmoji = spec.flagEmoji,
                flagStripeColors = spec.stripeColors,
                accentPrimaryColor = spec.accentPrimary,
                accentSecondaryColor = spec.accentSecondary
            )
        }
    }

    private fun setConnectingTelemetry(
        phase: String,
        headline: String,
        detail: String,
        percent: Int,
        stageIndex: Int
    ) {
        val pct = percent.coerceIn(0, 100)
        _taskProgress.value = TaskProgressState(
            active = true,
            percent = pct,
            message = detail,
            stageIndex = stageIndex,
            stageCount = 7
        )
        _telemetry.update {
            it.copy(
                uiPhase = phase,
                isConnecting = true,
                isConnected = false,
                statusHeadline = headline,
                statusDetail = detail,
                buttonText = "لغو عملیات",
                progressPercent = pct,
                currentStageIndex = stageIndex
            )
        }
    }

    private fun pushPingSample(valueMs: Float) {
        synchronized(historyLock) {
            pushBounded(pingHistoryQueue, valueMs)
        }
    }

    private fun clearSparklines() {
        synchronized(historyLock) {
            rxHistoryQueue.clear()
            txHistoryQueue.clear()
            pingHistoryQueue.clear()
        }
    }

    private fun snapshotQueue(q: ArrayDeque<Float>): List<Float> {
        return synchronized(historyLock) { q.toList() }
    }

    private fun pushBounded(q: ArrayDeque<Float>, v: Float) {
        if (q.size >= SPARKLINE_WINDOW) {
            q.removeFirst()
        }
        q.addLast(v)
    }

    private fun teardownActiveRuntime() {
        try {
            AlanVpnService.stopTunService(appContext)
        } catch (_: Throwable) {
        }
        xrayCoreManager.stopCore()
        liveBridge?.stop()
        liveBridge = null
        liveSession = null
    }

    private fun isGenValid(gen: Long): Boolean = opGeneration.get() == gen

    private fun updateProfileStatus(uid: String, cachedVectorId: String, pingMs: Int, status: String) {
        val selIdx = _selectedIndex.value
        _profiles.update { list ->
            list.mapIndexed { idx, p ->
                if (p.uid == uid) {
                    p.copy(
                        cachedVectorId = cachedVectorId,
                        pingMs = pingMs,
                        status = status,
                        isSelected = idx == selIdx
                    )
                } else {
                    p.copy(isSelected = idx == selIdx)
                }
            }
        }
        saveRepositoryToDisk()
    }

    private fun triggerInstantGeoLocationProbes(candidates: List<ProfileItem>, allowMicroTunnelForCdn: Boolean = false) {
        val unknownOnly = candidates.filter { it.countryCode == "UN" }
        if (unknownOnly.isEmpty()) return
        scope.launch {
            val byHost = unknownOnly.groupBy { it.host.trim().lowercase(Locale.US) }
            var resolvedHosts = 0
            for ((hostKey, itemsForHost) in byHost) {
                if (hostKey.isEmpty()) continue
                geoProbeSemaphore.withPermit {
                    val fastRes = geoIpResolver.resolveHostCountryFast(hostKey)
                    val code = fastRes.countryCode
                    if (code != "UN") {
                        val spec = CountryPaletteRegistry.resolveByCode(code)
                        resolvedHosts++
                        for (item in itemsForHost) {
                            if (item.countryCode == "UN") {
                                updateProfileCountry(item.uid, spec)
                            }
                        }
                        val activeUid = _profiles.value.getOrNull(_selectedIndex.value)?.uid ?: _telemetry.value.activeProfileUid
                        if (!_telemetry.value.isConnected && itemsForHost.any { it.uid == activeUid }) {
                            _telemetry.update {
                                it.copy(
                                    countryCode = spec.code,
                                    countryName = spec.nameFa,
                                    flagEmoji = spec.flagEmoji,
                                    flagStripeColors = spec.stripeColors,
                                    accentPrimaryColor = spec.accentPrimary,
                                    accentSecondaryColor = spec.accentSecondary
                                )
                            }
                        }
                    }
                }
            }
            if (resolvedHosts > 0) {
                appendLog("Geo-IP resolved $resolvedHosts server host(s).")
            }
        }
    }

    private suspend fun probeCloudflareExitCountrySilently(profile: ProfileItem): String {
        if (_engineState.value != EngineState.DISCONNECTED || pipelineMutex.isLocked) return "UN"
        return pipelineMutex.withLock {
            if (_engineState.value != EngineState.DISCONNECTED) return@withLock "UN"
            val cfg = _settings.value
            val session = try {
                UriConfigCodec.compileXraySession(profile, cfg, 0, 0, 0, forceLogSilent = true)
            } catch (_: Throwable) {
                return@withLock "UN"
            }
            val plan = UriConfigCodec.buildVectorPlan(profile, cfg).take(2)
            val bridge = FullDuplexMutatorBridge(
                listenPort = session.bridgePort,
                initialRemoteHost = session.remoteHost,
                initialRemotePort = session.remotePort,
                initialVector = plan.first(),
                initialCustomStride = cfg.forwardStrideBytes,
                initialJitterBytes = cfg.strideJitterBytes
            )
            try {
                bridge.start()
                if (!xrayCoreManager.startCore(session.configJson)) return@withLock "UN"
                delay(120L)
                for (vec in plan) {
                    if (_engineState.value != EngineState.DISCONNECTED) break
                    bridge.updateVector(vec, cfg.forwardStrideBytes, cfg.strideJitterBytes)
                    bridge.dropActiveConnections()
                    delay(30L)
                    val geo = TunnelStreamVerifier.resolveEgressEndpoint(session.httpPort)
                    if (geo.countryCode.length == 2 && geo.countryCode != "UN" && geo.countryCode != "XX") {
                        geoIpResolver.rememberResolvedCountry(profile.host, geo.countryCode)
                        return@withLock geo.countryCode
                    }
                }
                "UN"
            } catch (_: Throwable) {
                "UN"
            } finally {
                xrayCoreManager.stopCore()
                bridge.stop()
            }
        }
    }

    private fun updateProfileCountry(uid: String, spec: CountryThemeSpec) {
        val selIdx = _selectedIndex.value
        _profiles.update { list ->
            list.mapIndexed { idx, p ->
                if (p.uid == uid) {
                    p.copy(
                        countryCode = spec.code,
                        countryName = spec.nameFa,
                        flagEmoji = spec.flagEmoji,
                        flagStripeColors = spec.stripeColors,
                        accentPrimaryColor = spec.accentPrimary,
                        accentSecondaryColor = spec.accentSecondary,
                        isSelected = idx == selIdx
                    )
                } else {
                    p.copy(isSelected = idx == selIdx)
                }
            }
        }
        saveRepositoryToDisk()
    }

    private fun ensureOfficialSubscriptionRegistered() {
        scope.launch(Dispatchers.IO) {
            try {
                syncInternalOfficialAutoPoolSuspend()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                appendLog("[AutoPool] Init sync skipped: ${t.javaClass.simpleName}")
            }
        }
    }

    private fun poolUriSha1Key(uri: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(uri.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }.take(16)
        } catch (_: Throwable) {
            uri.hashCode().toUInt().toString(16)
        }

        val httpClient = clientBuilder.build()
        val collectedUris = LinkedHashSet<String>()

        for (url in autoRepoUrls) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "v2rayNG/1.9.5")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        UriConfigCodec.extractConfigLinesFromSubscriptionBody(body).forEach { u ->
                            if (u.isNotBlank()) collectedUris.add(u.trim())
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        if (collectedUris.isNotEmpty()) {
            try {
                val rawBytes = collectedUris.joinToString("\n").toByteArray(Charsets.UTF_8)
                val b64 = android.util.Base64.encodeToString(rawBytes, android.util.Base64.NO_WRAP)
                cacheFile.writeText(b64, Charsets.UTF_8)
            } catch (_: Throwable) {
            }
            val parsed = collectedUris.mapNotNull { UriConfigCodec.parseUriToProfileItem(it) }
            return@withContext hydrateAutoPoolNodesWithMeta(parsed)
        }

        if (cacheFile.exists()) {
            try {
                val rawB64 = cacheFile.readText(Charsets.UTF_8).trim()
                val decoded = String(android.util.Base64.decode(rawB64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val cachedUris = UriConfigCodec.extractConfigLinesFromSubscriptionBody(decoded)
                val parsed = cachedUris.mapNotNull { UriConfigCodec.parseUriToProfileItem(it) }
                if (parsed.isNotEmpty()) {
                    return@withContext hydrateAutoPoolNodesWithMeta(parsed)
                }
            } catch (_: Throwable) {
            }
        }

        emptyList()
    }

    private fun loadPoolWinnerMetaMap(): org.json.JSONObject {
        val f = java.io.File(appContext.filesDir, ".pool_meta.json")
        if (!f.exists()) return org.json.JSONObject()
        return try {
            org.json.JSONObject(f.readText(Charsets.UTF_8))
        } catch (_: Throwable) {
            org.json.JSONObject()
        }
    }

    private fun savePoolWinnerMeta(rawUri: String, vecId: String, cleanIp: String, pingMs: Int) {
        try {
            val key = poolUriSha1Key(rawUri)
            val meta = loadPoolWinnerMetaMap()
            meta.put(
                key,
                org.json.JSONObject()
                    .put("vec", vecId)
                    .put("ip", cleanIp)
                    .put("ping", pingMs)
                    .put("ts", System.currentTimeMillis() / 1000L)
            )
            java.io.File(appContext.filesDir, ".pool_meta.json").writeText(meta.toString(), Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    private fun hydrateAutoPoolNodesWithMeta(nodes: List<ProfileItem>): List<ProfileItem> {
        val meta = loadPoolWinnerMetaMap()
        if (meta.length() == 0) return nodes
        return nodes.map { item ->
            val entry = meta.optJSONObject(poolUriSha1Key(item.uri))
            if (entry != null) {
                val vec = entry.optString("vec", "")
                val ip = entry.optString("ip", "")
                val ping = entry.optInt("ping", -1)
                item.copy(
                    host = if (ip.isNotBlank() && !CleanIpScannerEngine.isPoisonedIranIp(ip)) ip else item.host,
                    cachedVectorId = vec.ifBlank { item.cachedVectorId },
                    pingMs = if (ping > 0) ping else item.pingMs,
                    status = if (ping > 0) "VERIFIED" else item.status
                )
            } else {
                item
            }
        }
    }

    fun isAutoRepoMode(): Boolean {
        return _settings.value.autoPilot || _selectedIndex.value < 0
    }

    private suspend fun syncInternalOfficialAutoPoolSuspend(): List<ProfileItem> = withContext(Dispatchers.IO) {
        val autoRepoUrls = listOf(
            "https://raw.githubusercontent.com/DrAlanK/Alan-RIP-Dpi/refs/heads/main/Alan-RIP-Dpi-Sub.txt"
        )
        val reportUrl = "https://raw.githubusercontent.com/DrAlanK/Alan-RIP-Dpi/refs/heads/main/Alan-RIP-Dpi-Report.json"
        val ctx = appContext ?: return@withContext emptyList()
        val cacheFile = java.io.File(ctx.filesDir, ".pool_cache.dat")
        val reportCacheFile = java.io.File(ctx.filesDir, ".pool_report.json")

        val clientBuilder = okhttp3.OkHttpClient.Builder()
            .connectTimeout(6500L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(7500L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (!_telemetry.value.isConnected) {
            clientBuilder.socketFactory(object : javax.net.SocketFactory() {
                override fun createSocket(): Socket = CleanIpScannerEngine.createProtectedRawSocket()
                override fun createSocket(h: String?, p: Int): Socket =
                    CleanIpScannerEngine.createProtectedRawSocket().apply { connect(InetSocketAddress(h, p), 4200) }
                override fun createSocket(h: String?, p: Int, lh: InetAddress?, lp: Int): Socket =
                    CleanIpScannerEngine.createProtectedRawSocket().apply { connect(InetSocketAddress(h, p), 4200) }
                override fun createSocket(h: InetAddress?, p: Int): Socket =
                    CleanIpScannerEngine.createProtectedRawSocket().apply { connect(InetSocketAddress(h, p), 4200) }
                override fun createSocket(a: InetAddress?, p: Int, la: InetAddress?, lp: Int): Socket =
                    CleanIpScannerEngine.createProtectedRawSocket().apply { connect(InetSocketAddress(a, p), 4200) }
            })
        }

        val httpClient = clientBuilder.build()
        val collectedUris = LinkedHashSet<String>()

        for (url in autoRepoUrls) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "v2rayNG/1.9.5")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        UriConfigCodec.extractConfigLinesFromSubscriptionBody(body).forEach { u ->
                            if (u.isNotBlank()) collectedUris.add(u.trim())
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        var reportJsonStr = ""
        try {
            val repReq = okhttp3.Request.Builder()
                .url(reportUrl)
                .header("User-Agent", "Alan-RIP-DPI-Android/1.4.0")
                .get()
                .build()
            httpClient.newCall(repReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty().trim()
                    if (body.startsWith("{")) {
                        reportJsonStr = body
                        reportCacheFile.writeText(body, Charsets.UTF_8)
                    }
                }
            }
        } catch (_: Throwable) {
        }
        if (reportJsonStr.isEmpty() && reportCacheFile.exists()) {
            try { reportJsonStr = reportCacheFile.readText(Charsets.UTF_8) } catch (_: Throwable) {}
        }

        val rawNodes: List<ProfileItem> = if (collectedUris.isNotEmpty()) {
            try {
                val rawBytes = collectedUris.joinToString("\n").toByteArray(Charsets.UTF_8)
                val b64 = android.util.Base64.encodeToString(rawBytes, android.util.Base64.NO_WRAP)
                cacheFile.writeText(b64, Charsets.UTF_8)
            } catch (_: Throwable) {
            }
            collectedUris.mapNotNull { UriConfigCodec.parseUriToProfileItem(it)?.takeIf { !CleanIpScannerEngine.isSubscriptionAnnouncementOrFakeNode(it) } }
        } else if (cacheFile.exists()) {
            try {
                val rawB64 = cacheFile.readText(Charsets.UTF_8).trim()
                val decoded = String(android.util.Base64.decode(rawB64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                UriConfigCodec.extractConfigLinesFromSubscriptionBody(decoded).mapNotNull { UriConfigCodec.parseUriToProfileItem(it)?.takeIf { !CleanIpScannerEngine.isSubscriptionAnnouncementOrFakeNode(it) } }
            } catch (_: Throwable) {
                emptyList()
            }
        } else {
            emptyList()
        }

        if (rawNodes.isEmpty()) return@withContext emptyList()

        // Enrich country metadata from explicit [ISO-01] tags or CountryPaletteRegistry
        val enriched = hydrateAutoPoolNodesWithMeta(rawNodes).map { item ->
            val explicitCode = UriConfigCodec.extractExplicitIsoFromTitle(item.name)
            if (explicitCode.isNotEmpty() && explicitCode != item.countryCode) {
                val cSpec = CountryPaletteRegistry.resolveByCode(explicitCode)
                item.copy(
                    countryCode = cSpec.code,
                    countryName = cSpec.nameFa,
                    flagEmoji = cSpec.flagEmoji,
                    flagStripeColors = cSpec.stripeColors,
                    accentPrimaryColor = cSpec.accentPrimary,
                    accentSecondaryColor = cSpec.accentSecondary
                )
            } else {
                item
            }
        }

        cachedAutoRepoNodes = enriched
        rebuildAvailableRepoLocations(enriched, reportJsonStr)
        enriched
    }



    private fun loadPersistentData() {
        synchronized(storageLock) {
            var savedSelectedIdx = 0
            if (settingsFile.exists()) {
                try {
                    val j = JSONObject(settingsFile.readText(Charsets.UTF_8))
                    configuredBridgePort = j.optInt("bridgePort", 20809)
                    selectedProfileUidPersist = j.optString("selectedProfileUid", "")
                    savedSelectedIdx = j.optInt("selectedIndex", 0)
                    _autoMethodStatus.value = j.optString("autoMethodStatus", "")
                    _settings.value = EngineSettings(
                        autoPilot = j.optBoolean("autoPilot", true),
                        requireYoutube6s = j.optBoolean("requireYoutube6s", true),
                        verifyUploadStream = j.optBoolean("verifyUploadStream", true),
                        selfHealingWatchdog = j.optBoolean("selfHealingWatchdog", true),
                        engineMode = j.optString("engineMode", "auto_hybrid"),
                        fragmentEnabled = j.optBoolean("fragmentEnabled", true),
                        fragmentPackets = j.optString("fragmentPackets", "tlshello"),
                        fragmentLength = j.optString("fragmentLength", "5-16"),
                        fragmentInterval = j.optString("fragmentInterval", "2-3"),
                        fingerprint = j.optString("fingerprint", "chrome"),
                        alpnOverride = j.optString("alpnOverride", "auto"),
                        wsEarlyData = j.optInt("wsEarlyData", 2048),
                        muxEnabled = j.optBoolean("muxEnabled", false),
                        muxConcurrency = j.optInt("muxConcurrency", 8),
                        manualVectorId = j.optString("manualVectorId", "VEC-01"),
                        forwardStrideBytes = j.optInt("forwardStrideBytes", 400),
                        strideJitterBytes = j.optInt("strideJitterBytes", 32),
                        tunMtu = j.optInt("tunMtu", 1500),
                        tunDns = j.optString("tunDns", "1.1.1.1"),
                        socksPort = j.optInt("socksPort", 10808),
                        httpPort = j.optInt("httpPort", 10809),
                        darkTheme = j.optBoolean("darkTheme", true),
                        lastActiveTab = j.optInt("lastActiveTab", 0),
                        subnetsRepoUrl = j.optString("subnetsRepoUrl", CleanIpScannerEngine.DEFAULT_GITHUB_SUBNETS_URL),
                        autoCleanIpForCf = j.optBoolean("autoCleanIpForCf", true)
                    )
                } catch (_: Throwable) {
                }
            }

            if (subscriptionsFile.exists()) {
                try {
                    val arr = JSONArray(subscriptionsFile.readText(Charsets.UTF_8))
                    val subs = ArrayList<SubscriptionItem>(arr.length())
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val url = o.optString("url", "").trim()
                        if (url.isNotEmpty()) {
                            subs.add(
                                SubscriptionItem(
                                    id = o.optString("id", "sub-$i"),
                                    title = o.optString("title", "Subscription"),
                                    url = url,
                                    updatedAt = o.optString("updatedAt", "")
                                )
                            )
                        }
                    }
                    _subscriptions.value = subs
                } catch (_: Throwable) {
                }
            }

            if (repositoryFile.exists()) {
                try {
                    val arr = JSONArray(repositoryFile.readText(Charsets.UTF_8))
                    val loaded = ArrayList<ProfileItem>(arr.length())
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val uri = o.optString("uri", "").trim()
                        val host = o.optString("host", "").trim()
                        if (uri.isEmpty() || host.isEmpty()) continue
                        val uid = o.optString("uid", UUID.randomUUID().toString().replace("-", "").take(12))
                        val ownerSub = o.optString("sourceSubId", "")
                        if (ownerSub.isNotEmpty()) {
                            profileSubOwnerMap[uid] = ownerSub
                        }
                        val nameStr = o.optString("name", "Server")
                        val sniStr = o.optString("sni", "")
                        val savedCc = o.optString("countryCode", "")
                        val country = CountryPaletteRegistry.detectCountry(
                            name = nameStr,
                            host = host,
                            sni = sniStr,
                            explicitCode = savedCc
                        )

                        loaded.add(
                            ProfileItem(
                                uid = uid,
                                name = nameStr,
                                protocol = o.optString("protocol", "VLESS"),
                                host = host,
                                originalHost = o.optString("originalHost", host),
                                port = o.optInt("port", 443),
                                sni = sniStr,
                                status = o.optString("status", "UNTESTED"),
                                pingMs = o.optInt("pingMs", 0),
                                cachedVectorId = o.optString("cachedVectorId", ""),
                                preferredVectorId = o.optString("preferredVectorId", "AUTO"),
                                isSelected = false,
                                uri = uri,
                            groupName = o.optString("groupName", "دستی").ifBlank { "دستی" },
                                countryCode = country.code,
                                countryName = country.nameFa,
                                flagEmoji = country.flagEmoji,
                                flagStripeColors = country.stripeColors,
                                accentPrimaryColor = country.accentPrimary,
                                accentSecondaryColor = country.accentSecondary
                            )
                        )
                    }

                    if (loaded.isNotEmpty()) {
                        val byUid = loaded.indexOfFirst { it.uid == selectedProfileUidPersist }
                        val resolved = when {
                            byUid >= 0 -> byUid
                            savedSelectedIdx in loaded.indices -> savedSelectedIdx
                            else -> 0
                        }
                        _selectedIndex.value = resolved
                        val chosen = loaded[resolved]
                        selectedProfileUidPersist = chosen.uid
                        _profiles.value = loaded.mapIndexed { idx, p -> p.copy(isSelected = idx == resolved) }
                        _telemetry.update {
                            it.copy(
                                selectedIndex = resolved,
                                activeProfileUid = chosen.uid,
                                activeProfileName = chosen.name,
                                activeProtocol = chosen.protocol,
                                layerHost = chosen.sni.ifBlank { chosen.host },
                                countryCode = chosen.countryCode,
                                countryName = chosen.countryName,
                                flagEmoji = chosen.flagEmoji,
                                flagStripeColors = chosen.flagStripeColors,
                                accentPrimaryColor = chosen.accentPrimaryColor,
                                accentSecondaryColor = chosen.accentSecondaryColor
                            )
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun saveSettingsToDisk() {
        synchronized(storageLock) {
            try {
                val s = _settings.value
                val j = JSONObject().apply {
                    put("autoPilot", s.autoPilot)
                    put("requireYoutube6s", s.requireYoutube6s)
                    put("verifyUploadStream", s.verifyUploadStream)
                    put("selfHealingWatchdog", s.selfHealingWatchdog)
                    put("engineMode", s.engineMode)
                    put("fragmentEnabled", s.fragmentEnabled)
                    put("fragmentPackets", s.fragmentPackets)
                    put("fragmentLength", s.fragmentLength)
                    put("fragmentInterval", s.fragmentInterval)
                    put("fingerprint", s.fingerprint)
                    put("alpnOverride", s.alpnOverride)
                    put("wsEarlyData", s.wsEarlyData)
                    put("muxEnabled", s.muxEnabled)
                    put("muxConcurrency", s.muxConcurrency)
                    put("manualVectorId", s.manualVectorId)
                    put("forwardStrideBytes", s.forwardStrideBytes)
                    put("strideJitterBytes", s.strideJitterBytes)
                    put("tunMtu", s.tunMtu)
                    put("tunDns", s.tunDns)
                    put("socksPort", s.socksPort)
                    put("httpPort", s.httpPort)
                    put("darkTheme", s.darkTheme)
                    put("lastActiveTab", s.lastActiveTab)
                    put("subnetsRepoUrl", s.subnetsRepoUrl)
                    put("autoCleanIpForCf", s.autoCleanIpForCf)
                    put("autoMethodStatus", _autoMethodStatus.value)
                    put("bridgePort", configuredBridgePort)
                    put("selectedProfileUid", selectedProfileUidPersist)
                    put("selectedIndex", _selectedIndex.value)
                }
                settingsFile.writeText(j.toString(2), Charsets.UTF_8)
            } catch (_: Throwable) {
            }
        }
    }

    private fun saveRepositoryToDisk() {
        synchronized(storageLock) {
            try {
                val arr = JSONArray()
                for (p in _profiles.value) {
                    arr.put(
                        JSONObject().apply {
                            put("uid", p.uid)
                            put("name", p.name)
                            put("protocol", p.protocol)
                            put("host", p.host)
                            put("originalHost", p.originalHost)
                            put("port", p.port)
                            put("sni", p.sni)
                            put("status", p.status)
                            put("pingMs", p.pingMs)
                            put("cachedVectorId", p.cachedVectorId)
                            put("preferredVectorId", p.preferredVectorId)
                            put("uri", p.uri)
                            put("countryCode", p.countryCode)
                            put("sourceSubId", profileSubOwnerMap[p.uid].orEmpty())
                        }
                    )
                }
                repositoryFile.writeText(arr.toString(), Charsets.UTF_8)
            } catch (_: Throwable) {
            }
        }
    }

    private fun saveSubscriptionsToDisk() {
        synchronized(storageLock) {
            try {
                val arr = JSONArray()
                for (sub in _subscriptions.value) {
                    arr.put(
                        JSONObject().apply {
                            put("id", sub.id)
                            put("title", sub.title)
                            put("url", sub.url)
                            put("updatedAt", sub.updatedAt)
                        }
                    )
                }
                subscriptionsFile.writeText(arr.toString(2), Charsets.UTF_8)
            } catch (_: Throwable) {
            }
        }
    }

    private fun readClipboardText(): String {
        return try {
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(appContext)?.toString().orEmpty()
            } else {
                ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun asBool(v: Any, fallback: Boolean): Boolean {
        return when (v) {
            is Boolean -> v
            is String -> v.toBooleanStrictOrNull() ?: fallback
            is Number -> v.toInt() != 0
            else -> fallback
        }
    }

    private fun asInt(v: Any, fallback: Int): Int {
        return when (v) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun appendLog(msg: String) {
        // Deep logging enabled: all Xray lifecycle events are recorded
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "[$stamp] $msg"
        synchronized(logLinesBuffer) {
            logLinesBuffer.add(line)
            while (logLinesBuffer.size > 300) {
                logLinesBuffer.removeAt(0)
            }
            _logs.value = logLinesBuffer.toList()
        }
    }
}
