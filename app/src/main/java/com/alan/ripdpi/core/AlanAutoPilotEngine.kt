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
        loadPersistentData()
        loadScanCacheFromDisk()
        ensureOfficialSubscriptionRegistered()
        startTelemetryTicker()
        startSelfHealingWatchdog()
        triggerInstantGeoLocationProbes(_profiles.value.filter { it.countryCode == "UN" }, allowMicroTunnelForCdn = false)
        scope.launch { delay(250L); appendLog("[Alan-RIP-Dpi v1.0.0] Core Ready (${xrayCoreManager.getXrayVersion()})") }
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
        val currentList = _profiles.value
        if (currentList.isEmpty()) {
            _selectedIndex.value = -1
            selectedProfileUidPersist = ""
            val def = CountryPaletteRegistry.DEFAULT_SPEC
            _telemetry.update {
                it.copy(
                    selectedIndex = -1,
                    activeProfileUid = "",
                    activeProfileName = "—",
                    activeProtocol = "—",
                    layerHost = "—",
                    countryCode = def.code,
                    countryName = def.nameFa,
                    flagEmoji = def.flagEmoji,
                    flagStripeColors = def.stripeColors,
                    accentPrimaryColor = def.accentPrimary,
                    accentSecondaryColor = def.accentSecondary
                )
            }
            saveSettingsToDisk()
            return
        }
        val clamped = index.coerceIn(0, currentList.lastIndex)
        val chosen = currentList[clamped]
        _selectedIndex.value = clamped
        selectedProfileUidPersist = chosen.uid
        _profiles.value = currentList.mapIndexed { idx, item ->
            item.copy(isSelected = idx == clamped)
        }
        if (!_telemetry.value.isConnected) {
            _telemetry.update {
                it.copy(
                    selectedIndex = clamped,
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
        } else {
            _telemetry.update { it.copy(selectedIndex = clamped) }
        }
        saveSettingsToDisk()
        saveRepositoryToDisk()
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

            val parsedItems = extracted.mapNotNull { UriConfigCodec.parseUriToProfileItem(it) }
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
        if (current.isEmpty()) return
        val currentSelectedUid = current.getOrNull(_selectedIndex.value)?.uid ?: selectedProfileUidPersist

        val sorted = when (by.lowercase()) {
            "name" -> current.sortedBy { it.name.lowercase() }
            "protocol" -> current.sortedBy { it.protocol }
            "country" -> current.sortedBy { it.countryCode }
            else -> current.sortedWith(
                compareBy<ProfileItem> {
                    when (it.status) {
                        "VERIFIED" -> 0
                        "ONLINE" -> 1
                        "UNTESTED" -> 2
                        else -> 3
                    }
                }.thenBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
            )
        }

        val newIdx = sorted.indexOfFirst { it.uid == currentSelectedUid }.let { if (it >= 0) it else 0 }
        _profiles.value = sorted
        selectConfig(newIdx)
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
        val parsed = UriConfigCodec.parseUriToProfileItem(builtUri)
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
            val parsed = uris.mapNotNull { UriConfigCodec.parseUriToProfileItem(it) }
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
                "lastActiveTab" -> cur.copy(lastActiveTab = asInt(v, cur.lastActiveTab).coerceIn(0, 5))
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
        val list = _profiles.value.toMutableList()
        if (list.isEmpty()) return
        val resolvedIdx = if (targetIndex in list.indices) {
            targetIndex
        } else {
            _selectedIndex.value.coerceIn(0, list.lastIndex)
        }
        val targetProfile = list[resolvedIdx]
        list[resolvedIdx] = targetProfile.copy(
            cachedVectorId = vectorId,
            preferredVectorId = vectorId,
            status = "VERIFIED"
        )
        _profiles.value = list
        selectConfig(resolvedIdx)
        updateSettings(_settings.value.copy(manualVectorId = vectorId))
        saveRepositoryToDisk()
        appendLog("Bound vector $vectorId to '${targetProfile.name}'.")

        if (_engineState.value == EngineState.CONNECTED && resolvedIdx == _selectedIndex.value && !autoConnect) {
            val bridge = liveBridge
            val vecObj = VectorCatalog.findVector(vectorId)
            if (bridge != null) {
                bridge.updateVector(vecObj, _settings.value.forwardStrideBytes, _settings.value.strideJitterBytes)
                bridge.dropActiveConnections()
                _telemetry.update {
                    it.copy(
                        activeVectorId = vecObj.vectorId,
                        activeVectorName = vecObj.codeName
                    )
                }
                AlanVpnService.activeInstance?.updateNotificationSummary(
                    "${targetProfile.flagEmoji} ${targetProfile.name}",
                    vecObj.vectorId
                )
                return
            }
        }

        if (autoConnect) {
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
                            if (probe.fatalUpstreamError) break
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
                detail = "انتقال ترافیک سیستم روی ${finalVec.vectorId}...",
                percent = 96,
                stageIndex = 7
            )

            activateVerifiedSessionUnlocked(
                gen = gen,
                profile = profile,
                session = session,
                bridge = bridge,
                vector = finalVec,
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
                if (fast.fatalUpstreamError) break
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
        if (candidates.isEmpty()) return
        scope.launch {
            for (item in candidates) {
                launch {
                    geoProbeSemaphore.withPermit {
                        val fastRes = geoIpResolver.resolveHostCountryFast(item.host)
                        var resolvedCode = fastRes.countryCode

                        if (
                            resolvedCode == "UN" &&
                            fastRes.isCloudflareAnycast &&
                            item.countryCode == "UN" &&
                            allowMicroTunnelForCdn &&
                            _engineState.value == EngineState.DISCONNECTED &&
                            !pipelineMutex.isLocked
                        ) {
                            resolvedCode = probeCloudflareExitCountrySilently(item)
                        }

                        if (resolvedCode != "UN" && resolvedCode != item.countryCode) {
                            val spec = CountryPaletteRegistry.resolveByCode(resolvedCode)
                            updateProfileCountry(item.uid, spec)
                            val curSnap = _telemetry.value
                            val activeUid = _profiles.value.getOrNull(_selectedIndex.value)?.uid ?: curSnap.activeProfileUid
                            if (!curSnap.isConnected && activeUid == item.uid) {
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
                            appendLog("Geo-IP verified '${item.name}' -> ${spec.nameFa} (${spec.code})")
                        }
                    }
                }
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
        val cleanedSubs = _subscriptions.value.filterNot {
            it.id == OFFICIAL_AUTO_SUB_ID || it.url.equals(OFFICIAL_AUTO_SUB_URL, ignoreCase = true)
        }
        if (cleanedSubs.size != _subscriptions.value.size) {
            _subscriptions.value = cleanedSubs
            saveSubscriptionsToDisk()
        }
        val cleanedProfiles = _profiles.value.filterNot { profileSubOwnerMap[it.uid] == OFFICIAL_AUTO_SUB_ID }
        if (cleanedProfiles.size != _profiles.value.size) {
            val keysToRemove = profileSubOwnerMap.filterValues { it == OFFICIAL_AUTO_SUB_ID }.keys.toList(); for (k in keysToRemove) { profileSubOwnerMap.remove(k) }
            _profiles.value = cleanedProfiles
            selectConfig(if (cleanedProfiles.isEmpty()) -1 else 0)
            saveRepositoryToDisk()
        }
        scope.launch {
            syncInternalOfficialAutoPoolSuspend()
        }
    }

    private suspend fun syncInternalOfficialAutoPoolSuspend(): List<ProfileItem> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(9, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        return@withContext try {
            val req = Request.Builder()
                .url(OFFICIAL_AUTO_SUB_URL)
                .header("User-Agent", "Alan-RIP-DPI-Android/1.0.0")
                .get()
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext internalAutoNodes.toList()
                resp.body?.string().orEmpty()
            }
            val uris = UriConfigCodec.extractConfigLinesFromSubscriptionBody(body)
            val parsed = uris.mapNotNull { UriConfigCodec.parseUriToProfileItem(it) }
            synchronized(internalAutoNodes) {
                internalAutoNodes.clear()
                internalAutoNodes.addAll(parsed)
            }
            parsed
        } catch (_: Throwable) {
            internalAutoNodes.toList()
        } finally {
            client.connectionPool.evictAll()
        }
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
                        lastActiveTab = j.optInt("lastActiveTab", 0)
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
