#!/usr/bin/env bash
set -e

cat << 'GITIGNORE' > .gitignore
.gradle/
build/
app/build/
local.properties
*.apk
*.aab
*.jks
*.keystore
.idea/
*.iml
GITIGNORE

cat << 'PROPS' > gradle.properties
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
PROPS

cat << 'SETTINGS' > settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Alan-RIP-Dpi-Android"
include(":app")
SETTINGS

cat << 'ROOT_GRADLE' > build.gradle.kts
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
ROOT_GRADLE

cat << 'APP_GRADLE' > app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alan.ripdpi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alan.ripdpi"
        minSdk = 24
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
APP_GRADLE

cat << 'STRINGS' > app/src/main/res/values/strings.xml
<resources>
    <string name="app_name">Alan-RIP-Dpi</string>
</resources>
STRINGS

cat << 'MANIFEST' > app/src/main/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".vpn.AlanVpnService"
            android:exported="false"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:foregroundServiceType="specialUse">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="vpn" />
        </service>
    </application>
</manifest>
MANIFEST

cat << 'TPROXY' > app/src/main/java/hev/htproxy/TProxyService.kt
package hev.htproxy

object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    external fun TProxyStopService()

    @JvmStatic
    external fun TProxyGetStats(): LongArray?
}
TPROXY

cat << 'MODELS' > app/src/main/java/com/alan/ripdpi/model/Models.kt
package com.alan.ripdpi.model

import java.util.Locale
import java.util.UUID

const val APP_NAME = "Alan-RIP-Dpi"
const val APP_VERSION = "1.0.0"

enum class EngineState {
    DISCONNECTED,
    FAST_RESUME,
    PROBING_VECTORS,
    SCANNING_REPOSITORY,
    HUNTING_BEST,
    VERIFYING_STABILITY,
    CONNECTED,
    RECOVERING,
    ERROR
}

fun formatByteRate(kbps: Double): String {
    if (kbps <= 0.01) return "0 B/s"
    if (kbps < 1.0) return String.format(Locale.US, "%d B/s", (kbps * 1024.0).toInt())
    if (kbps < 1024.0) return String.format(Locale.US, "%.1f KB/s", kbps)
    return String.format(Locale.US, "%.2f MB/s", kbps / 1024.0)
}

fun formatUptimeHms(totalSeconds: Long): String {
    val sec = totalSeconds.coerceAtLeast(0L)
    val hrs = sec / 3600L
    val mins = (sec % 3600L) / 60L
    val secs = sec % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}

data class EngineSettings(
    val autopilotEnabled: Boolean = true,
    val requireYoutube6s: Boolean = true,
    val verifyUploadStream: Boolean = true,
    val selfHealingWatchdog: Boolean = true,
    val themeMode: String = "dark",
    val selectedProfileUid: String = "",
    val selectedIndex: Int = 0,
    val engineMode: String = "auto_hybrid",
    val manualVectorId: String = "VEC-01",
    val fingerprint: String = "chrome",
    val alpnOverride: String = "auto",
    val wsEarlyData: Int = 0,
    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "5-16",
    val fragmentInterval: String = "2-3",
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val forwardStrideBytes: Int = 400,
    val strideJitterBytes: Int = 20,
    val tunMtu: Int = 1400,
    val tunDns: String = "1.1.1.1",
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val bridgePort: Int = 20809
)

data class BypassVector(
    val vectorId: String,
    val codeName: String,
    val family: String,
    val summary: String,
    val preamble: String = "none",
    val outerVersion: Int = 0x0304,
    val urgentMode: String = "none",
    val forwardCuts: List<Int> = emptyList(),
    val secondUrgentDelta: Int = 0,
    val splitMode: String = "none",
    val coalesceFlight2: Boolean = true,
    val fragSliceMin: Int = 0,
    val fragSliceMax: Int = 0,
    val fragDelayMinMs: Int = 0,
    val fragDelayMaxMs: Int = 0,
    val fragFlights: Int = 1
)

data class ProfileNode(
    val uri: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val sni: String,
    val uid: String = UUID.randomUUID().toString().replace("-", "").take(10),
    val groupTag: String = "Manual",
    val cachedVectorId: String = "",
    val lastPingMs: Int = -1,
    val healthStatus: String = "UNTESTED",
    val lastTestedTs: Long = 0L,
    val isSelected: Boolean = false
)

data class EditableConfigFields(
    val uid: String = "",
    val name: String = "Profile",
    val groupTag: String = "Manual",
    val protocol: String = "vless",
    val address: String = "",
    val port: Int = 443,
    val uuidOrPassword: String = "",
    val network: String = "ws",
    val headerType: String = "none",
    val security: String = "tls",
    val allowInsecure: Boolean = false,
    val sni: String = "",
    val hostHeader: String = "",
    val path: String = "/",
    val alpn: String = "http/1.1",
    val fingerprint: String = "chrome",
    val flow: String = "",
    val pbk: String = "",
    val sid: String = "",
    val scy: String = "auto",
    val preferredVectorId: String = "AUTO",
    val rawUri: String = "",
    val useRawUri: Boolean = false
)

data class SubscriptionItem(
    val url: String,
    val title: String,
    val lastUpdatedTs: Long = 0L,
    val nodeCount: Int = 0,
    val enabled: Boolean = true
)

data class ScanResultRow(
    val scanType: String,
    val rank: Int = 0,
    val targetIndex: Int = -1,
    val uid: String = "",
    val itemId: String,
    val boundVector: String = "",
    val title: String,
    val category: String,
    val endpoint: String,
    val tcpMs: Int = -1,
    val tunnelMs: Int = -1,
    val status: String,
    val score: Int = 0,
    val detail: String = ""
)

data class ScannerUiState(
    val isRunning: Boolean = false,
    val mode: String = "active_profile",
    val progressPercent: Int = 0,
    val statusSummary: String = "آماده شروع اسکن شبکه و متد",
    val rows: List<ScanResultRow> = emptyList(),
    val totalCount: Int = 0,
    val verifiedCount: Int = 0,
    val deadCount: Int = 0
)

data class TaskProgressState(
    val active: Boolean = false,
    val percent: Int = 0,
    val message: String = ""
)

data class TelemetrySnapshot(
    val state: String = EngineState.DISCONNECTED.name,
    val uiPhase: String = "IDLE",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val statusHeadline: String = "آماده اتصال",
    val statusDetail: String = "موتور هوشمند آماده برقراری تونل امن است",
    val buttonText: String = "اتصال هوشمند",
    val uptimeSeconds: Long = 0L,
    val uptimeLabel: String = "00:00:00",
    val selectedIndex: Int = 0,
    val activeProfileUid: String = "",
    val activeProfileName: String = "---",
    val protocolLabel: String = "---",
    val egressIp: String = "---",
    val endpointIp: String = "---",
    val activeVectorId: String = "AUTO",
    val activeVectorName: String = "Auto-Adaptive Core",
    val pingMs: Int = -1,
    val txRateKbps: Double = 0.0,
    val rxRateKbps: Double = 0.0,
    val txRateLabel: String = "0 B/s",
    val rxRateLabel: String = "0 B/s",
    val txTotalBytes: Long = 0L,
    val rxTotalBytes: Long = 0L
)

object VectorCatalog {
    val VECTOR_REGISTRY: List<BypassVector> = listOf(
        BypassVector("VEC-01", "Adaptive Core Alpha", "PROPRIETARY", "Primary structural stream desync with stride slicing", "none", 0x0304, "sni_tag", listOf(400)),
        BypassVector("VEC-02", "Adaptive Core Sigma", "PROPRIETARY", "Mid-label structural alignment with stride slicing", "none", 0x0304, "mid_host", listOf(400)),
        BypassVector("VEC-03", "Dual-Phase Boundary", "PROPRIETARY", "Bimodal boundary pointer with forward delta offset", "none", 0x0304, "sni_tag", emptyList(), 400),
        BypassVector("VEC-04", "Preamble Fusion + Stride", "PROPRIETARY", "Empty TLS 1.3 record fusion with forward stride cut", "zero0304", 0x0304, "sni_tag", listOf(400)),
        BypassVector("VEC-05", "Stepped Cascade 200/400", "PROPRIETARY", "Multi-stage forward window segmentation", "none", 0x0304, "sni_tag", listOf(200, 400)),
        BypassVector("VEC-06", "Dual-Tag Vector", "PROPRIETARY", "Dual structural header offset alignment", "none", 0x0304, "dual", listOf(400)),
        BypassVector("VEC-07", "Length-Field Desync", "PROPRIETARY", "Extension list length boundary segmentation", "none", 0x0304, "sni_len", listOf(400)),
        BypassVector("VEC-08", "Zero-Drift Dual Preamble", "COALESCE", "Sequence-aligned dual empty record coalescing", "2xzero", 0x0304, "none", listOf(400)),
        BypassVector("VEC-09", "Atomic Mid-SNI Record Split", "COALESCE", "Single-buffer TLS record split with forward stride", "zero0304", 0x0304, "none", listOf(400), 0, "1pkt_midsni"),
        BypassVector("VEC-10", "Tri-Stage Structural Core", "PROPRIETARY", "Three-point extension header boundary alignment", "zero0304", 0x0304, "triple", listOf(400)),
        BypassVector("VEC-11", "Micro-Fragment Header (5-16B)", "FRAGMENT", "TLS Hello micro-slicing (5-16B, 2-3ms interval)", "none", 0x0303, "none", emptyList(), 0, "none", false, 5, 16, 2, 3, 1),
        BypassVector("VEC-12", "Window-5B Multi-Flight (1-3)", "FRAGMENT", "5-byte fixed header window exhaustion across flights 1-3", "none", 0x0303, "none", emptyList(), 0, "none", false, 5, 5, 12, 20, 3),
        BypassVector("VEC-13", "Rapid TLS Fragment (12-28B)", "FRAGMENT", "Low-latency TLS Hello slicing (12-28B, 1-2ms interval)", "zero0304", 0x0304, "none", emptyList(), 0, "none", true, 12, 28, 1, 2, 1),
        BypassVector("VEC-14", "Deep Frame Fragment (24-64B)", "FRAGMENT", "Medium-chunk multi-flight TLS fragmentation (flights 1-2)", "none", 0x0304, "none", emptyList(), 0, "none", false, 24, 64, 3, 6, 2),
        BypassVector("VEC-15", "Window-Boundary 512", "PROPRIETARY", "DPI 512-byte inspection buffer edge cut", "zero0304", 0x0304, "sni_tag", listOf(512)),
        BypassVector("VEC-16", "Quad-Preamble Burst", "COALESCE", "4x mixed-version empty records with single-burst flush", "4xzero", 0x0304, "none", listOf(400)),
        BypassVector("VEC-00", "Direct Tunnel Passthrough", "DIRECT", "Zero-mutation kernel relay for domestic tunnels and encrypted streams", "none", 0x0303, "none", emptyList(), 0, "none", false)
    )

    fun findVector(vectorId: String): BypassVector =
        VECTOR_REGISTRY.firstOrNull { it.vectorId == vectorId } ?: VECTOR_REGISTRY.first()

    fun filterByMode(engineMode: String): List<BypassVector> = when (engineMode) {
        "fragment_only" -> VECTOR_REGISTRY.filter { it.family in setOf("FRAGMENT", "COALESCE", "DIRECT") }
        "proprietary_only" -> VECTOR_REGISTRY.filter { it.family in setOf("PROPRIETARY", "COALESCE", "DIRECT") }
        else -> VECTOR_REGISTRY
    }
}
MODELS

cat << 'CONTROLLER' > app/src/main/java/com/alan/ripdpi/ui/AlanEngineController.kt
package com.alan.ripdpi.ui

import com.alan.ripdpi.model.BypassVector
import com.alan.ripdpi.model.EditableConfigFields
import com.alan.ripdpi.model.EngineSettings
import com.alan.ripdpi.model.ProfileNode
import com.alan.ripdpi.model.ScannerUiState
import com.alan.ripdpi.model.SubscriptionItem
import com.alan.ripdpi.model.TaskProgressState
import com.alan.ripdpi.model.TelemetrySnapshot
import com.alan.ripdpi.model.VectorCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface AlanEngineController {
    val telemetry: StateFlow<TelemetrySnapshot>
    val profiles: StateFlow<List<ProfileNode>>
    val settings: StateFlow<EngineSettings>
    val subscriptions: StateFlow<List<SubscriptionItem>>
    val scannerState: StateFlow<ScannerUiState>
    val taskProgress: StateFlow<TaskProgressState>
    val logs: StateFlow<List<String>>

    fun getVectors(): List<BypassVector> = VectorCatalog.VECTOR_REGISTRY

    fun toggleConnection()
    fun requestConnect(uri: String = "")
    fun requestDisconnect()
    fun huntBestAndConnect()

    fun updateSettings(transform: (EngineSettings) -> EngineSettings)
    fun setSingleSetting(key: String, value: Any)

    fun selectConfig(indexOrUid: Any)
    fun importConfigs(rawText: String, groupTag: String = "Manual"): Int
    fun deleteConfig(indexOrUid: Any)
    fun clearAllConfigs(): Int
    fun deduplicateConfigs(): Int
    fun removeDeadConfigs(): Int
    fun sortConfigs(sortBy: String = "ping")
    fun quickPingConfigs()
    fun exportConfigs(mode: String = "verified", injectEarlyData: Int = 0): String

    fun getConfigEditable(indexOrUid: Any): EditableConfigFields
    fun saveConfigEditable(indexOrUid: Any, fields: EditableConfigFields): Boolean

    fun addSubscription(url: String, title: String = "Subscription")
    fun refreshSubscriptions()
    fun deleteSubscription(index: Int)

    fun startScanner(
        mode: String = "active_profile",
        targetIndex: Int = -1,
        autoConnect: Boolean = false,
        pruneDead: Boolean = false
    )
    fun stopScanner()

    fun applyScannedVector(vectorId: String, targetIndex: Int = -1, connectNow: Boolean = false)
    fun applyVectorToAllConfigs(vectorId: String): Int
    fun connectScannedProfile(targetIndexOrUid: Any, vectorId: String = "")

    fun clearLogs()
}

class PreviewEngineController : AlanEngineController {
    override val telemetry = MutableStateFlow(TelemetrySnapshot())
    override val profiles = MutableStateFlow<List<ProfileNode>>(emptyList())
    override val settings = MutableStateFlow(EngineSettings())
    override val subscriptions = MutableStateFlow<List<SubscriptionItem>>(emptyList())
    override val scannerState = MutableStateFlow(ScannerUiState())
    override val taskProgress = MutableStateFlow(TaskProgressState())
    override val logs = MutableStateFlow(listOf("[00:00:00] [Alan-RIP-Dpi v1.0.0] Android Core Ready."))

    override fun toggleConnection() {}
    override fun requestConnect(uri: String) {}
    override fun requestDisconnect() {}
    override fun huntBestAndConnect() {}
    override fun updateSettings(transform: (EngineSettings) -> EngineSettings) {
        settings.value = transform(settings.value)
    }
    override fun setSingleSetting(key: String, value: Any) {}
    override fun selectConfig(indexOrUid: Any) {}
    override fun importConfigs(rawText: String, groupTag: String): Int = 0
    override fun deleteConfig(indexOrUid: Any) {}
    override fun clearAllConfigs(): Int = 0
    override fun deduplicateConfigs(): Int = 0
    override fun removeDeadConfigs(): Int = 0
    override fun sortConfigs(sortBy: String) {}
    override fun quickPingConfigs() {}
    override fun exportConfigs(mode: String, injectEarlyData: Int): String = ""
    override fun getConfigEditable(indexOrUid: Any): EditableConfigFields = EditableConfigFields()
    override fun saveConfigEditable(indexOrUid: Any, fields: EditableConfigFields): Boolean = true
    override fun addSubscription(url: String, title: String) {}
    override fun refreshSubscriptions() {}
    override fun deleteSubscription(index: Int) {}
    override fun startScanner(mode: String, targetIndex: Int, autoConnect: Boolean, pruneDead: Boolean) {}
    override fun stopScanner() {}
    override fun applyScannedVector(vectorId: String, targetIndex: Int, connectNow: Boolean) {}
    override fun applyVectorToAllConfigs(vectorId: String): Int = 0
    override fun connectScannedProfile(targetIndexOrUid: Any, vectorId: String) {}
    override fun clearLogs() { logs.value = emptyList() }
}
CONTROLLER

"$HOME/.gradle-dist/gradle-8.10.2/bin/gradle" wrapper --gradle-version 8.10.2
chmod +x ./gradlew
echo "CONTRACT & GRADLE WRAPPER READY!"
