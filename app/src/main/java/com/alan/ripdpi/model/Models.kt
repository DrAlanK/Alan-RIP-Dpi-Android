package com.alan.ripdpi.model

import java.util.Locale

const val APP_NAME = "Alan-RIP-Dpi"
const val APP_VERSION = "1.4.2"

enum class EngineState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    ERROR,
    FAST_RESUME,
    HUNTING_BEST,
    PROBING_VECTORS,
    RECOVERING,
    SCANNING_CLEAN_IP,
    SCANNING_REPOSITORY,
    VERIFYING_STABILITY
}

fun formatByteRate(kbps: Double): String {
    if (kbps <= 0.01) return "0 B/s"
    if (kbps < 1.0) return String.format(Locale.US, "%d B/s", (kbps * 1024.0).toInt())
    if (kbps < 1024.0) return String.format(Locale.US, "%.1f KB/s", kbps)
    return String.format(Locale.US, "%.2f MB/s", kbps / 1024.0)
}

fun formatByteRate(kbps: Long): String = formatByteRate(kbps.toDouble())
fun formatByteRate(kbps: Float): String = formatByteRate(kbps.toDouble())

fun formatUptimeHms(totalSeconds: Long): String {
    val sec = totalSeconds.coerceAtLeast(0L)
    val hrs = sec / 3600L
    val rem = sec % 3600L
    val mins = rem / 60L
    val secs = rem % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}

fun formatUptimeHms(totalSeconds: Int): String = formatUptimeHms(totalSeconds.toLong())

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
    val fragFlights: Int = 1,
    val recordEnvelope: String = "none",
    val uplinkOobShield: Boolean = false,
    val uplinkMssClamp: Int = 0,
    val tailAnchorBytes: Int = 0,
    val hybridMode: String = "none"
)

object VectorCatalog {

    val AUTOPILOT_PRIORITY_IDS: List<String> = listOf(
        "VEC-21",
        "VEC-22",
        "VEC-01",
        "VEC-18",
        "VEC-17",
        "VEC-19",
        "VEC-20",
        "VEC-23",
        "VEC-04",
        "VEC-02",
        "VEC-03",
        "VEC-05",
        "VEC-06",
        "VEC-07",
        "VEC-08",
        "VEC-09",
        "VEC-10",
        "VEC-15",
        "VEC-16",
        "VEC-11",
        "VEC-13",
        "VEC-14",
        "VEC-12",
        "VEC-00"
    )

    val FAST_LAB_VECTOR_IDS: List<String> = listOf(
        "VEC-21",
        "VEC-22",
        "VEC-23",
        "VEC-01",
        "VEC-18",
        "VEC-17",
        "VEC-19",
        "VEC-20",
        "VEC-04",
        "VEC-00"
    )

    private val RAW_REGISTRY: List<BypassVector> = listOf(
        BypassVector(
            vectorId = "VEC-01",
            codeName = "Adaptive Core Alpha",
            family = "PROPRIETARY",
            summary = "Primary structural stream desync with stride slicing",
            preamble = "none",
            outerVersion = 0x0304,
            urgentMode = "sni_tag",
            forwardCuts = listOf(400),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-02",
            codeName = "Adaptive Core Sigma",
            family = "PROPRIETARY",
            summary = "Mid-label structural alignment with stride slicing",
            preamble = "none",
            outerVersion = 0x0304,
            urgentMode = "mid_host",
            forwardCuts = listOf(400),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-03",
            codeName = "Dual-Phase Boundary",
            family = "PROPRIETARY",
            summary = "Bimodal boundary pointer with forward delta offset",
            preamble = "none",
            outerVersion = 0x0304,
            urgentMode = "sni_tag",
            secondUrgentDelta = 400,
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-04",
            codeName = "Preamble Fusion + Stride",
            family = "PROPRIETARY",
            summary = "Empty TLS 1.3 record fusion with forward stride cut",
            preamble = "zero0304",
            outerVersion = 0x0304,
            urgentMode = "sni_tag",
            forwardCuts = listOf(400),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-05",
            codeName = "Stepped Cascade 200/400",
            family = "PROPRIETARY",
            summary = "Multi-stage forward window segmentation",
            preamble = "none",
            outerVersion = 0x0304,
            urgentMode = "sni_tag",
            forwardCuts = listOf(200, 400),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-06",
            codeName = "Dual-Tag Vector",
            family = "PROPRIETARY",
            summary = "Dual structural header offset alignment",
            preamble = "none",
            outerVersion = 0x0304,
            urgentMode = "dual",
            forwardCuts = listOf(400),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-07",
            codeName = "Length-Field Desync",
            family = "PROPRIETARY",
            summary = "Extension list length boundary segmentation",
            preamble = "none",
            outerVersion = 0x0304,
            urgentMode = "sni_len",
            forwardCuts = listOf(400),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-08",
            codeName = "Zero-Drift Dual Preamble",
            family = "COALESCE",
            summary = "Sequence-aligned dual empty record coalescing",
            preamble = "2xzero",
            outerVersion = 0x0304,
            urgentMode = "none",
            forwardCuts = listOf(400),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-09",
            codeName = "Atomic Mid-SNI Record Split",
            family = "COALESCE",
            summary = "Single-buffer TLS record split with forward stride",
            preamble = "zero0304",
            outerVersion = 0x0304,
            urgentMode = "none",
            forwardCuts = listOf(400),
            splitMode = "1pkt_midsni",
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-10",
            codeName = "Tri-Stage Structural Core",
            family = "PROPRIETARY",
            summary = "Three-point extension header boundary alignment",
            preamble = "zero0304",
            outerVersion = 0x0304,
            urgentMode = "triple",
            forwardCuts = listOf(400),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-11",
            codeName = "Micro-Fragment Header (5-16B)",
            family = "FRAGMENT",
            summary = "TLS Hello micro-slicing (5-16B, 2-3ms interval)",
            preamble = "none",
            outerVersion = 0x0303,
            urgentMode = "none",
            fragSliceMin = 5,
            fragSliceMax = 16,
            fragDelayMinMs = 2,
            fragDelayMaxMs = 3,
            fragFlights = 1,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-12",
            codeName = "Window-5B Multi-Flight (1-3)",
            family = "FRAGMENT",
            summary = "5-byte fixed header window exhaustion across flights 1-3",
            preamble = "none",
            outerVersion = 0x0303,
            urgentMode = "none",
            fragSliceMin = 5,
            fragSliceMax = 5,
            fragDelayMinMs = 12,
            fragDelayMaxMs = 20,
            fragFlights = 3,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-13",
            codeName = "Rapid TLS Fragment (12-28B)",
            family = "FRAGMENT",
            summary = "Low-latency TLS Hello slicing (12-28B, 1-2ms interval)",
            preamble = "zero0304",
            outerVersion = 0x0304,
            urgentMode = "none",
            fragSliceMin = 12,
            fragSliceMax = 28,
            fragDelayMinMs = 1,
            fragDelayMaxMs = 2,
            fragFlights = 1,
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-14",
            codeName = "Deep Frame Fragment (24-64B)",
            family = "FRAGMENT",
            summary = "Medium-chunk multi-flight TLS fragmentation (flights 1-2)",
            preamble = "none",
            outerVersion = 0x0304,
            urgentMode = "none",
            fragSliceMin = 24,
            fragSliceMax = 64,
            fragDelayMinMs = 3,
            fragDelayMaxMs = 6,
            fragFlights = 2,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-15",
            codeName = "Window-Boundary 512",
            family = "PROPRIETARY",
            summary = "DPI 512-byte inspection buffer edge cut",
            preamble = "zero0304",
            outerVersion = 0x0304,
            urgentMode = "sni_tag",
            forwardCuts = listOf(512),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-16",
            codeName = "Quad-Preamble Burst",
            family = "COALESCE",
            summary = "4x mixed-version empty records with single-burst flush",
            preamble = "4xzero",
            outerVersion = 0x0304,
            urgentMode = "none",
            forwardCuts = listOf(400),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-17",
            codeName = "Aligned Record Relay",
            family = "COALESCE",
            summary = "Multi-frame record boundary alignment over single buffer flush",
            outerVersion = 0x0303,
            recordEnvelope = "sni_dot_3rec",
            uplinkMssClamp = 1380,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-18",
            codeName = "Adaptive Core Delta",
            family = "PROPRIETARY",
            summary = "Early header pointer alignment with trailing segment split",
            outerVersion = 0x0304,
            urgentMode = "cipher_pivot",
            tailAnchorBytes = 36,
            uplinkMssClamp = 1360,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-19",
            codeName = "Stream Window Balance",
            family = "PROPRIETARY",
            summary = "Dual-frame handshake alignment with outbound stream pacing",
            outerVersion = 0x0303,
            recordEnvelope = "sni_mid_2rec",
            uplinkOobShield = true,
            uplinkMssClamp = 1360,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-20",
            codeName = "Progressive Frame Sync",
            family = "COALESCE",
            summary = "Stepped multi-record frame segmentation with tail pacing",
            outerVersion = 0x0303,
            recordEnvelope = "fibonacci_7rec",
            uplinkMssClamp = 1380,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-21",
            codeName = "Adaptive Core Omega",
            family = "PROPRIETARY",
            summary = "Composite header boundary shift with forward window cut",
            outerVersion = 0x0303,
            hybridMode = "phantom_seam_oob",
            uplinkMssClamp = 1380,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-22",
            codeName = "Dual-Window Stream Core",
            family = "PROPRIETARY",
            summary = "Segmented header cell alignment with secondary window cut",
            outerVersion = 0x0304,
            hybridMode = "sni_microcell_pincer",
            uplinkMssClamp = 1360,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-23",
            codeName = "Interleaved Record Sync",
            family = "COALESCE",
            summary = "Alternating record header framing with forward segment split",
            outerVersion = 0x0301,
            hybridMode = "chameleon_interleave",
            uplinkMssClamp = 1380,
            coalesceFlight2 = false
        ),
        BypassVector(
            vectorId = "VEC-00",
            codeName = "Direct Tunnel Passthrough",
            family = "DIRECT",
            summary = "Zero-mutation kernel relay for domestic tunnels and encrypted streams",
            preamble = "none",
            outerVersion = 0x0303,
            urgentMode = "none",
            forwardCuts = emptyList(),
            coalesceFlight2 = false
        )
    )

    private val PRIORITY_RANK: Map<String, Int> =
        AUTOPILOT_PRIORITY_IDS.withIndex().associate { (idx, vid) -> vid to idx }

    val VECTOR_REGISTRY: List<BypassVector> =
        RAW_REGISTRY.sortedBy { PRIORITY_RANK[it.vectorId] ?: 999 }

    fun findVector(vectorId: String): BypassVector =
        RAW_REGISTRY.firstOrNull { it.vectorId.equals(vectorId.trim(), ignoreCase = true) }
            ?: VECTOR_REGISTRY.first()

    fun findById(vectorId: String): BypassVector = findVector(vectorId)

    fun getById(vectorId: String): BypassVector = findVector(vectorId)

    fun filterVectorsByMode(engineMode: String): List<BypassVector> {
        val pool = when (engineMode.lowercase(Locale.US)) {
            "fragment_only" -> RAW_REGISTRY.filter { it.family in setOf("FRAGMENT", "COALESCE", "DIRECT") }
            "proprietary_only" -> RAW_REGISTRY.filter { it.family in setOf("PROPRIETARY", "COALESCE", "DIRECT") }
            else -> RAW_REGISTRY
        }
        return pool.sortedBy { PRIORITY_RANK[it.vectorId] ?: 999 }
    }

    fun filterByMode(engineMode: String): List<BypassVector> = filterVectorsByMode(engineMode)
}
