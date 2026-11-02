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

data class BypassVector(
    val vectorId: String,
    val codeName: String,
    val family: String,
    val summary: String,
    val preamble: String = "none",
    val outerVersion: Int = 0,
    val urgentMode: String = "none",
    val forwardCuts: List<Int> = emptyList(),
    val secondUrgentDelta: Int = 0,
    val splitMode: String = "none",
    val coalesceFlight2: Boolean = false,
    val fragSliceMin: Int = 0,
    val fragSliceMax: Int = 0,
    val fragDelayMinMs: Int = 0,
    val fragDelayMaxMs: Int = 0,
    val fragFlights: Int = 1
)

object VectorCatalog {
    val VECTOR_REGISTRY: List<BypassVector> = listOf(
        BypassVector(
            vectorId = "VEC-01",
            codeName = "SNI Mid-Host Split",
            family = "TCP_SNI",
            summary = "برش دقیق سگمنت TCP در میانه نام دامنه SNI (سازگار با همه سرورها)",
            splitMode = "tcp_sni_mid",
            forwardCuts = listOf(400)
        ),
        BypassVector(
            vectorId = "VEC-02",
            codeName = "Dual TLS Record Split",
            family = "TLS_RECORD",
            summary = "شکستن قانونی ClientHello به ۲ رکورد مجزای TLS در مرز میانی SNI",
            splitMode = "rec_sni_mid"
        ),
        BypassVector(
            vectorId = "VEC-03",
            codeName = "SNI 2-Byte Micro-Slicer",
            family = "TCP_SNI",
            summary = "خرد کردن ناحیه دامنه SNI به سگمنت‌های ۲ بایتی با کمترین تاخیر",
            splitMode = "tcp_sni_micro"
        ),
        BypassVector(
            vectorId = "VEC-04",
            codeName = "Rapid Hello Fragment (5-16B)",
            family = "FRAGMENT",
            summary = "فرگمنت سریع هندشیک TLS در قطعات ۵ تا ۱۶ بایت با وقفه ۳ تا ۵ میلی‌ثانیه",
            fragSliceMin = 5,
            fragSliceMax = 16,
            fragDelayMinMs = 3,
            fragDelayMaxMs = 5,
            fragFlights = 1
        ),
        BypassVector(
            vectorId = "VEC-05",
            codeName = "SNI Dot-Label Cascade",
            family = "TCP_SNI",
            summary = "برش زنجیره‌ای سگمنت TCP دقیقاً روی نقطه‌های جداکننده دامنه SNI",
            splitMode = "tcp_sni_dots"
        ),
        BypassVector(
            vectorId = "VEC-06",
            codeName = "Tri-Record TLS Split",
            family = "TLS_RECORD",
            summary = "تقسیم هندشیک به ۳ رکورد استاندارد TLS در هدر اکستنشن و میانه SNI",
            splitMode = "rec_sni_tri"
        ),
        BypassVector(
            vectorId = "VEC-07",
            codeName = "SNI 1-Byte Head Desync",
            family = "TCP_SNI",
            summary = "جداسازی بایت اول و چهارم نام دامنه SNI در پاکت‌های مستقل TCP",
            splitMode = "tcp_sni_1b"
        ),
        BypassVector(
            vectorId = "VEC-08",
            codeName = "Record Split + Fragment",
            family = "TLS_RECORD",
            summary = "ترکیب برش رکورد TLS در میانه SNI به همراه فرگمنت ۱۰ تا ۲۴ بایتی",
            splitMode = "rec_sni_frag",
            fragSliceMin = 10,
            fragSliceMax = 24,
            fragDelayMinMs = 2,
            fragDelayMaxMs = 4
        ),
        BypassVector(
            vectorId = "VEC-09",
            codeName = "Multi-Flight Fragment (1-3)",
            family = "FRAGMENT",
            summary = "فرگمنت ۸ تا ۲۰ بایتی روی ۳ پرواز اول (هندشیک و درخواست WebSocket)",
            fragSliceMin = 8,
            fragSliceMax = 20,
            fragDelayMinMs = 3,
            fragDelayMaxMs = 6,
            fragFlights = 3
        ),
        BypassVector(
            vectorId = "VEC-10",
            codeName = "Ultra-Fine Fragment (2-7B)",
            family = "FRAGMENT",
            summary = "ریزش شدید هندشیک در قطعات ۲ تا ۷ بایت برای فیلترینگ سنگین موبایل",
            fragSliceMin = 2,
            fragSliceMax = 7,
            fragDelayMinMs = 2,
            fragDelayMaxMs = 5,
            fragFlights = 1
        ),
        BypassVector(
            vectorId = "VEC-11",
            codeName = "Deep Frame Fragment (20-48B)",
            family = "FRAGMENT",
            summary = "فرگمنت کم‌تاخیر ۲۰ تا ۴۸ بایتی روی پرواز ۱ و ۲ مناسب Reality و CDN",
            fragSliceMin = 20,
            fragSliceMax = 48,
            fragDelayMinMs = 2,
            fragDelayMaxMs = 4,
            fragFlights = 2
        ),
        BypassVector(
            vectorId = "VEC-12",
            codeName = "TLS 1.2 Record Split + Coalesce",
            family = "TLS_RECORD",
            summary = "نرمال‌سازی هدر بیرونی به 0x0303 همراه با برش رکورد SNI و ادغام پرواز دوم",
            outerVersion = 0x0303,
            splitMode = "rec_sni_mid",
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-13",
            codeName = "Stepped Window Cascade",
            family = "TCP_SNI",
            summary = "برش پله‌ای پنجره TCP در آفست‌های 64، 140 و 320 بایت",
            forwardCuts = listOf(64, 140, 320),
            coalesceFlight2 = true
        ),
        BypassVector(
            vectorId = "VEC-14",
            codeName = "Kernel OOB @ SNI Tag",
            family = "PROPRIETARY",
            summary = "تزریق اشاره‌گر اضطراری کرنل (MSG_OOB) روی تگ اکستنشن SNI سرور مستقیم",
            urgentMode = "sni_tag",
            forwardCuts = listOf(400)
        ),
        BypassVector(
            vectorId = "VEC-15",
            codeName = "Kernel OOB @ Mid-Host",
            family = "PROPRIETARY",
            summary = "تزریق بایت اضطراری کرنل (MSG_OOB) در میانه نام دامنه سرور مستقیم",
            urgentMode = "mid_host",
            forwardCuts = listOf(400)
        ),
        BypassVector(
            vectorId = "VEC-16",
            codeName = "Dual Kernel OOB Desync",
            family = "PROPRIETARY",
            summary = "تزریق دوگانه MSG_OOB در مرز اکستنشن و دامنه برای عبور از DPI حالت‌مند",
            urgentMode = "dual",
            forwardCuts = listOf(400)
        ),
        BypassVector(
            vectorId = "VEC-00",
            codeName = "Direct Passthrough",
            family = "DIRECT",
            summary = "عبور مستقیم بدون دستکاری بایتی (مخصوص تانل ایران و Shadowsocks)"
        )
    )

    fun findVector(vectorId: String): BypassVector =
        VECTOR_REGISTRY.firstOrNull { it.vectorId == vectorId } ?: VECTOR_REGISTRY.first()

    fun filterByMode(engineMode: String): List<BypassVector> = when (engineMode) {
        "fragment_only" -> VECTOR_REGISTRY.filter {
            it.family in setOf("FRAGMENT", "TCP_SNI", "TLS_RECORD", "DIRECT")
        }
        "proprietary_only" -> VECTOR_REGISTRY.filter {
            it.family in setOf("TCP_SNI", "PROPRIETARY", "TLS_RECORD", "DIRECT")
        }
        else -> VECTOR_REGISTRY
    }
}
