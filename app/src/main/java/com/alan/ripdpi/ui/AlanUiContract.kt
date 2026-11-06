package com.alan.ripdpi.ui

import com.alan.ripdpi.model.CountryPaletteRegistry
import com.alan.ripdpi.model.VectorCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectionStageStep(
    val stepNumber: Int,
    val key: String,
    val title: String,
    val subtitle: String = "",
    val state: String = "PENDING",
    val badgeText: String = ""
)

data class LiveVectorProbeItem(
    val vectorId: String,
    val codeName: String,
    val family: String,
    val state: String = "QUEUED",
    val latencyMs: Int = -1,
    val rank: Int = 0
)

data class TelemetrySnapshot(
    val uiPhase: String = "IDLE",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val statusHeadline: String = "آماده اتصال",
    val statusDetail: String = "موتور هوشمند آماده برقراری تونل امن است.",
    val buttonText: String = "اتصال هوشمند",
    val uptimeLabel: String = "00:00:00",
    val egressIp: String = "—",
    val activeVectorId: String = "AUTO",
    val pingMs: Int = 0,
    val rxRateKbps: Float = 0f,
    val txRateKbps: Float = 0f,
    val rxRateLabel: String = "0 B/s",
    val txRateLabel: String = "0 B/s",
    val progressPercent: Int = 0,
    val selectedIndex: Int = -1,
    val activeProfileUid: String = "",
    val activeVectorName: String = "Auto-Adaptive Core",
    val activeProfileName: String = "—",
    val activeProtocol: String = "—",
    val layerHost: String = "—",
    val originCountryCode: String = "IR",
    val originCountryName: String = "ایران",
    val originFlagEmoji: String = CountryPaletteRegistry.IRAN_SPEC.flagEmoji,
    val countryCode: String = "UN",
    val countryName: String = "بین‌الملل",
    val flagEmoji: String = CountryPaletteRegistry.DEFAULT_SPEC.flagEmoji,
    val flagStripeColors: List<Long> = CountryPaletteRegistry.DEFAULT_SPEC.stripeColors,
    val accentPrimaryColor: Long = CountryPaletteRegistry.DEFAULT_SPEC.accentPrimary,
    val accentSecondaryColor: Long = CountryPaletteRegistry.DEFAULT_SPEC.accentSecondary,
    val currentStageIndex: Int = 0,
    val totalStages: Int = 7,
    val rxHistory: List<Float> = emptyList(),
    val txHistory: List<Float> = emptyList(),
    val pingHistory: List<Float> = emptyList()
)

data class TaskProgressState(
    val active: Boolean = false,
    val percent: Int = 0,
    val message: String = "",
    val stageIndex: Int = 0,
    val stageCount: Int = 7
)

data class ProfileItem(
    val uid: String,
    val name: String,
    val protocol: String = "VLESS",
    val host: String = "",
    val originalHost: String = "",
    val port: Int = 443,
    val sni: String = "",
    val status: String = "UNTESTED",
    val pingMs: Int = 0,
    val cachedVectorId: String = "",
    val preferredVectorId: String = "AUTO",
    val isSelected: Boolean = false,
    val uri: String = "",
    val countryCode: String = "UN",
    val countryName: String = "بین‌الملل",
    val flagEmoji: String = CountryPaletteRegistry.DEFAULT_SPEC.flagEmoji,
    val flagStripeColors: List<Long> = CountryPaletteRegistry.DEFAULT_SPEC.stripeColors,
    val accentPrimaryColor: Long = CountryPaletteRegistry.DEFAULT_SPEC.accentPrimary,
    val accentSecondaryColor: Long = CountryPaletteRegistry.DEFAULT_SPEC.accentSecondary
)

data class EditableConfigFields(
    val name: String = "",
    val protocol: String = "vless",
    val address: String = "",
    val port: Int = 443,
    val uuidOrPassword: String = "",
    val network: String = "ws",
    val security: String = "tls",
    val sni: String = "",
    val hostHeader: String = "",
    val path: String = "/",
    val alpn: String = "http/1.1",
    val fingerprint: String = "custom_poly",
    val flow: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val preferredVectorId: String = "AUTO",
    val rawUri: String = "",
    val useRawUri: Boolean = false
)

data class EngineSettings(
    val autoPilot: Boolean = true,
    val requireYoutube6s: Boolean = false,
    val verifyUploadStream: Boolean = true,
    val selfHealingWatchdog: Boolean = true,
    val engineMode: String = "auto_hybrid",
    val fragmentEnabled: Boolean = true,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "5-16",
    val fragmentInterval: String = "2-3",
    val fingerprint: String = "custom_poly",
    val alpnOverride: String = "auto",
    val wsEarlyData: Int = 2048,
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val manualVectorId: String = "VEC-01",
    val forwardStrideBytes: Int = 400,
    val strideJitterBytes: Int = 32,
    val tunMtu: Int = 1400,
    val tunDns: String = "1.1.1.1",
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val darkTheme: Boolean = true,
    val lastActiveTab: Int = 0,
    val subnetsRepoUrl: String = "https://raw.githubusercontent.com/DrAlanK/Alan-RIP-Dpi-Android/main/subnets.txt",
    val autoCleanIpForCf: Boolean = true
)

data class VectorSpec(
    val id: String,
    val name: String
)

data class ScanResultRow(
    val scanType: String,
    val itemId: String,
    val rank: Int = 0,
    val targetIndex: Int = -1,
    val boundVector: String = "",
    val title: String = "",
    val category: String = "",
    val endpoint: String = "",
    val tcpMs: Int = 0,
    val tunnelMs: Int = 0,
    val status: String = "UNTESTED",
    val score: Float = 0f,
    val detail: String = "",
    val countryCode: String = "UN",
    val flagEmoji: String = ""
)

data class ScanUiState(
    val isRunning: Boolean = false,
    val mode: String = "active_profile",
    val percent: Int = 0,
    val summary: String = "آماده شروع اسکن شبکه و متد"
)

data class SubscriptionItem(
    val id: String,
    val title: String,
    val url: String,
    val updatedAt: String = ""
)


data class CleanIpHit(
    val ip: String,
    val port: Int = 443,
    val latencyMs: Int = 0,
    val subnet: String = "",
    val colo: String = "CF"
)

data class CleanIpScanState(
    val isRunning: Boolean = false,
    val sourceMode: String = "github",
    val port: Int = 443,
    val percent: Int = 0,
    val summary: String = "آماده اسکن IP تمیز کلادفلر",
    val loadedSubnetsCount: Int = 22
)

interface AlanEngineController {
    val telemetry: StateFlow<TelemetrySnapshot>
    val taskProgress: StateFlow<TaskProgressState>
    val profiles: StateFlow<List<ProfileItem>>
    val settings: StateFlow<EngineSettings>
    val scanRows: StateFlow<List<ScanResultRow>>
    val scanState: StateFlow<ScanUiState>
    val subscriptions: StateFlow<List<SubscriptionItem>>
    val logs: StateFlow<List<String>>

    val connectionStages: StateFlow<List<ConnectionStageStep>>
        get() = MutableStateFlow(emptyList())

    val liveVectorProbes: StateFlow<List<LiveVectorProbeItem>>
        get() = MutableStateFlow(emptyList())

    fun toggleConnection()
    fun huntBestAndConnect()
    fun startScanner(
        mode: String,
        targetIndex: Int = -1,
        autoConnect: Boolean = false,
        pruneDead: Boolean = false
    )
    fun stopScanner()
    fun applyScannedVector(
        vectorId: String,
        targetIndex: Int = -1,
        endpoint: String = "",
        autoConnect: Boolean = false
    )
    fun applyVectorToAllConfigs(vectorId: String, endpoint: String = "")
    fun connectScannedProfile(
        targetIndex: Int,
        boundVector: String = "",
        endpoint: String = ""
    )

    fun selectConfig(index: Int)
    fun importConfigs(rawText: String)
    fun importFromClipboard() {
        importConfigs("")
    }
    fun quickPingConfigs()
    fun sortConfigs(by: String)
    fun deduplicateConfigs()
    fun removeDeadConfigs()
    fun exportConfigs(mode: String = "verified", injectEarlyData: Int = 2048): String
    fun clearAllConfigs()
    fun deleteConfig(index: Int)
    fun getConfigEditable(index: Int): EditableConfigFields
    fun saveConfigEditable(index: Int, fields: EditableConfigFields)

    fun addSubscription(url: String, title: String)
    fun refreshSubscriptions()
    fun deleteSubscription(index: Int)

    fun updateSettings(newSettings: EngineSettings)
    fun updateSettings(patch: Map<String, Any>)
    fun getVectors(): List<VectorSpec>

    val isFindingBestMethod: StateFlow<Boolean>
        get() = MutableStateFlow(false)

    val autoMethodStatus: StateFlow<String>
        get() = MutableStateFlow("")

    fun autoDetectBestMethod() {}

    val cleanIpHits: StateFlow<List<CleanIpHit>>
        get() = MutableStateFlow(emptyList())

    val cleanIpState: StateFlow<CleanIpScanState>
        get() = MutableStateFlow(CleanIpScanState())

    fun activateAutoModeAndConnect(connectImmediately: Boolean = true) {}

    fun startMultiTargetMethodScan(
        scopeMode: String,
        selectedIndices: List<Int>,
        autoConnect: Boolean,
        useCleanIp: Boolean = false
    ) {}

    fun startCleanIpScan(
        sourceMode: String = "github",
        customRanges: String = "",
        port: Int = 443,
        maxResults: Int = 20
    ) {}

    fun stopCleanIpScan() {}

    fun refreshSubnetsFromGithub() {}

    fun applyCleanIpsToTargets(
        ips: List<String>,
        targetMode: String,
        selectedIndices: List<Int>,
        cloneMode: Boolean,
        clonesPerConfig: Int = 3
    ) {}

    fun quickCloneConfigWithCleanIps(profileIndex: Int, cloneCount: Int = 5) {}

    fun clearLogs()
}

class DemoAlanEngineController : AlanEngineController {
    private val deSpec = CountryPaletteRegistry.resolveByCode("DE")
    private val _settings = MutableStateFlow(EngineSettings())
    override val settings: StateFlow<EngineSettings> = _settings.asStateFlow()

    private val _profiles = MutableStateFlow(
        listOf(
            ProfileItem(
                uid = "demo-de-1",
                name = "DE Frankfurt Core",
                protocol = "VLESS",
                host = "89.167.37.58",
                originalHost = "89.167.37.58",
                port = 443,
                sni = "srv-f1.example.com",
                status = "VERIFIED",
                pingMs = 185,
                cachedVectorId = "VEC-01",
                isSelected = true,
                countryCode = deSpec.code,
                countryName = deSpec.nameFa,
                flagEmoji = deSpec.flagEmoji,
                flagStripeColors = deSpec.stripeColors,
                accentPrimaryColor = deSpec.accentPrimary,
                accentSecondaryColor = deSpec.accentSecondary
            )
        )
    )
    override val profiles: StateFlow<List<ProfileItem>> = _profiles.asStateFlow()

    private val _telemetry = MutableStateFlow(
        TelemetrySnapshot(
            selectedIndex = 0,
            activeProfileUid = "demo-de-1",
            activeProfileName = "DE Frankfurt Core",
            activeProtocol = "VLESS",
            layerHost = "srv-f1.example.com",
            countryCode = deSpec.code,
            countryName = deSpec.nameFa,
            flagEmoji = deSpec.flagEmoji,
            flagStripeColors = deSpec.stripeColors,
            accentPrimaryColor = deSpec.accentPrimary,
            accentSecondaryColor = deSpec.accentSecondary,
            rxHistory = listOf(2f, 6f, 14f, 9f, 22f, 18f, 30f),
            txHistory = listOf(1f, 3f, 5f, 4f, 8f, 6f, 11f),
            pingHistory = listOf(190f, 186f, 184f, 188f, 185f)
        )
    )
    override val telemetry: StateFlow<TelemetrySnapshot> = _telemetry.asStateFlow()
    override val taskProgress: StateFlow<TaskProgressState> = MutableStateFlow(TaskProgressState())
    override val scanRows: StateFlow<List<ScanResultRow>> = MutableStateFlow(emptyList())
    override val scanState: StateFlow<ScanUiState> = MutableStateFlow(ScanUiState())
    override val subscriptions: StateFlow<List<SubscriptionItem>> = MutableStateFlow(emptyList())
    override val logs: StateFlow<List<String>> = MutableStateFlow(listOf("[00:00:00] Alan-RIP-Dpi Ready"))
    override val connectionStages: StateFlow<List<ConnectionStageStep>> = MutableStateFlow(emptyList())
    override val liveVectorProbes: StateFlow<List<LiveVectorProbeItem>> = MutableStateFlow(emptyList())

    override fun toggleConnection() {}
    override fun huntBestAndConnect() {}
    override fun startScanner(mode: String, targetIndex: Int, autoConnect: Boolean, pruneDead: Boolean) {}
    override fun stopScanner() {}
    override fun applyScannedVector(vectorId: String, targetIndex: Int, endpoint: String, autoConnect: Boolean) {}
    override fun applyVectorToAllConfigs(vectorId: String, endpoint: String) {}
    override fun connectScannedProfile(targetIndex: Int, boundVector: String, endpoint: String) {}
    override fun selectConfig(index: Int) {}
    override fun importConfigs(rawText: String) {}
    override fun quickPingConfigs() {}
    override fun sortConfigs(by: String) {}
    override fun deduplicateConfigs() {}
    override fun removeDeadConfigs() {}
    override fun exportConfigs(mode: String, injectEarlyData: Int): String = ""
    override fun clearAllConfigs() {}
    override fun deleteConfig(index: Int) {}
    override fun getConfigEditable(index: Int): EditableConfigFields = EditableConfigFields()
    override fun saveConfigEditable(index: Int, fields: EditableConfigFields) {}
    override fun addSubscription(url: String, title: String) {}
    override fun refreshSubscriptions() {}
    override fun deleteSubscription(index: Int) {}
    override fun updateSettings(newSettings: EngineSettings) {
        _settings.value = newSettings
    }
    override fun updateSettings(patch: Map<String, Any>) {}
    override fun getVectors(): List<VectorSpec> =
        VectorCatalog.VECTOR_REGISTRY.map { VectorSpec(it.vectorId, "${it.vectorId} — ${it.codeName}") }
    override fun clearLogs() {}
}
