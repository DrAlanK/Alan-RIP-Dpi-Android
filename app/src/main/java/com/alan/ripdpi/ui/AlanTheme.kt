package com.alan.ripdpi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as M3Text
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alan.ripdpi.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

@Immutable
data class AlanPalette(
    val isDark: Boolean,
    val rootBg: Color,
    val surfaceBg: Color,
    val cardBg: Color,
    val subtleBg: Color,
    val cardBorder: Color,
    val glowBorder: Color,
    val accentGold: Color,
    val accentAmber: Color,
    val accentSoftBg: Color,
    val onAccent: Color,
    val emerald: Color,
    val cyan: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

private val DarkAlanPalette = AlanPalette(
    isDark = true,
    rootBg = Color(0xFF1E232E),
    surfaceBg = Color(0xFF222834),
    cardBg = Color(0xFF1E232E),
    subtleBg = Color(0xFF151922),
    cardBorder = Color(0xFF2B3242),
    glowBorder = Color(0x99F5C451),
    accentGold = Color(0xFFF5C451),
    accentAmber = Color(0xFFE59A1A),
    accentSoftBg = Color(0x24F5C451),
    onAccent = Color(0xFF11141B),
    emerald = Color(0xFF10B981),
    cyan = Color(0xFF38BDF8),
    danger = Color(0xFFEF4444),
    textPrimary = Color(0xFFF3F6FC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF8E9DB5)
)

private val LightAlanPalette = AlanPalette(
    isDark = false,
    rootBg = Color(0xFFE6ECF5),
    surfaceBg = Color(0xFFEDF2FA),
    cardBg = Color(0xFFE6ECF5),
    subtleBg = Color(0xFFD5DFEE),
    cardBorder = Color(0xFFBCC7D8),
    glowBorder = Color(0x99C27803),
    accentGold = Color(0xFFB45309),
    accentAmber = Color(0xFF92400E),
    accentSoftBg = Color(0x22D97706),
    onAccent = Color(0xFFFFFFFF),
    emerald = Color(0xFF047857),
    cyan = Color(0xFF0369A1),
    danger = Color(0xFFDC2626),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF55667E)
)

val LocalAlanPalette = staticCompositionLocalOf { DarkAlanPalette }

object AlanI18n {
    var lang by mutableStateOf("fa")

    private val exactMap = mapOf(
                "در حال قطع..." to "Disconnecting...",
        "در حال قطع اتصال..." to "Disconnecting tunnel...",
        "در حال توقف هسته و بستن تونل..." to "Stopping core & closing tunnel...",
        "تونل امن روی پروفایل شخصی فعال است" to "Secure Tunnel Active on Personal Profile",
        "آماده برقراری ارتباط هوشمند" to "Ready for Smart Connection",
        "یافتن بهترین کانفیگ شما..." to "Finding Your Best Profile...",
        "تست پینگ و آزمایشگاه DPI" to "Ping & DPI Method Lab",
        "ابزارها و پاک‌سازی مخزن" to "Repository Tools & Cleanup",
        "افزودن کانفیگ یا اشتراک" to "Add Profile or Subscription",
        "مرتب‌سازی لیست کانفیگ‌ها" to "Sort Profiles",
        "انتخاب چندتایی (Multi-Select)" to "Multi-Select Mode",
        "غیرفعال کردن انتخاب چندتایی" to "Exit Multi-Select Mode",
        "بروزرسانی این اشتراک" to "Refresh This Subscription",
        "اتصال خودکار به بهترین کانفیگ شخصی" to "Auto-Connect to Best Personal Profile",
        "شکار بهترین سرور از مخزن ابری گیت‌هاب" to "Hunt Best Server from GitHub Cloud Repo",
        "تغییر حالت اتصال شخصی (ضد DPI / خالص v2ray)" to "Toggle Personal Mode (Anti-DPI / Pure v2ray)",
        "تست پینگ واقعی تونل (Real-Ping)" to "Real Tunnel Ping (HTTP 204)",
        "پینگ سریع TCP همه کانفیگ‌ها" to "Fast TCP Ping All Profiles",
        "ارسال کل مخزن به آزمایشگاه متد ضد DPI" to "Send Entire Repo to 6-Lane DPI Lab",
        "کلون سریع با IP تمیز کلادفلر" to "Quick Clone with Cloudflare Clean IP",
        "ارسال به آزمایشگاه متد ضد DPI" to "Send to Anti-DPI Method Lab",
        "نمایش QR Code کانفیگ" to "Show Profile QR Code",
        "اشتراک‌گذاری متنی" to "Share URI Text",
        "حذف کانفیگ" to "Delete Profile",
        "وارد کردن لینک یا متن گروهی" to "Batch Import Links / Text",
        "افزودن به مخزن" to "Add to Repository",
        "تایید و اجرا" to "Confirm & Apply",
        "پاک کردن" to "Clear",
        "انتخاب" to "Select",
        "تنظیمات به حالت پیش‌فرض برگشت." to "Settings reset to defaults.",
        "تنظیمات شبکه ذخیره شد." to "Network settings saved.",
        "تنظیمات Xray ذخیره شد." to "Xray settings saved.",
        "متد عبور از فیلتر" to "Anti-DPI Bypass Method",
        "انتخاب متد دستی" to "Manual Vector Selection",
        "پیدا کردن خودکار بهترین متد" to "Auto-Detect Best Method",
        "توقف جستجوی متد" to "Stop Method Detection",
        "حالت کاری موتور" to "Engine Operating Mode",
        "ترکیبی هوشمند (پیشنهادی)" to "Smart Hybrid (Recommended)",
        "استفاده هم‌زمان از تمام متدها" to "Use all anti-DPI vectors adaptively",
        "فقط متدهای تزریق پکت" to "Packet Injection Vectors Only",
        "مخصوص عبور از فیلترینگ شدید SNI" to "Designed for strict SNI inspection",
        "فقط متدهای فرگمنت" to "Fragment Vectors Only",
        "مناسب سرورهای کلادفلر و CDN" to "Optimized for Cloudflare & CDN edges",
        "ظاهر برنامه" to "Appearance",
        "اتصال هوشمند و مخزن" to "Smart Connect & Repository",
        "خاموش = اتصال خالص استاندارد v2rayNG به بهترین کانفیگ شما" to "Off = Pure standard v2rayNG connection to your best profile",
        "استفاده از مخزن ابری گیت‌هاب در دکمه اصلی اتصال" to "Use GitHub cloud repository on main connect button",
        "ثبت خودکار پرچم و نام کشور پس از تست پینگ واقعی" to "Auto-tag country flag & name after Real-Ping",
        "جایگزینی خودکار متد در صورت افت اینترنت" to "Auto-switch anti-DPI vector if stream drops",
        "شبکه و پورت‌ها" to "Network & Local Ports",
        "MTU تونل" to "Tunnel MTU",
        "آدرس DNS" to "DNS Address",
        "پورت SOCKS5" to "SOCKS5 Port",
        "پورت HTTP" to "HTTP Port",
        "لینک فایل ساب‌نت گیت‌هاب" to "GitHub Subnets URL",
        "تنظیمات TLS و مرورگر" to "TLS & Browser Fingerprint",
        "شبیه‌سازی مرورگر (Fingerprint)" to "uTLS Browser Fingerprint",
        "تنظیم ALPN" to "ALPN Override",
        "مقدار Early Data وب‌سوکت" to "WebSocket Early Data",
        "فرگمنت و برش پکت" to "Fragmentation & Packet Slicing",
        "طول گام برش (Bytes)" to "Forward Stride (Bytes)",
        "نوسان تصادفی (Jitter)" to "Stride Jitter (Bytes)",
        "فرگمنت داخلی Xray (در حالت خالص)" to "Internal Xray Fragment (Pure Mode)",
        "خرد کردن پکت‌های هندشیک TLS توسط هسته Xray" to "Slice TLS ClientHello packets inside Xray core",
        "طول تکه (Length)" to "Fragment Length",
        "وقفه (ms)" to "Fragment Interval (ms)",
        "ترکیب کانکشن‌ها (Mux)" to "Connection Multiplexing (Mux)",
        "ارسال هم‌زمان چند درخواست در یک تونل" to "Multiplex concurrent streams over one tunnel",
        "تعداد هم‌زمانی Mux" to "Mux Concurrency",
        "مسیر زنده اتصال" to "Live Connection Route",
        "انجام شد" to "Done",
        "در حال اجرا" to "Active",
        "ناموفق" to "Failed",
        "در انتظار" to "Pending",
        "همه" to "All",
        "خطاها" to "Errors",
        "لاگی در این دسته وجود ندارد." to "No logs in this category.",
        "دامنه تست (github.com / youtube.com)" to "Test Domain (github.com / youtube.com)",
        "آماده اسکن سرورهای DoH" to "Ready to scan DoH servers",
        "آماده اسکن سرورهای DoH (DNSveil)" to "Ready to scan DoH servers (DNSveil)",
        "آماده اسکن IP تمیز کلادفلر" to "Ready to scan Cloudflare Clean IPs",
        "آماده شروع اسکن شبکه و متد" to "Ready to scan anti-DPI vectors",
        "آماده تست" to "Ready",
        "آماده" to "Ready",
        "خانه" to "Home",
        "کانفیگ‌ها" to "Profiles",
        "اسکنر متد" to "DPI Method Lab",
        "IP تمیز و DoH" to "Clean IP & DoH",
        "اسکنر IP تمیز" to "Clean IP Scanner",
        "تنظیمات عمومی" to "General Settings",
        "تنظیمات Xray" to "Xray Settings",
        "تنظیمات هسته Xray" to "Xray Core Settings",
        "لاگ‌ها" to "Live Logs",
        "کنسول لاگ عمیق مهندسی" to "Deep Engineering Logs",
        "موتور هوشمند ضد فیلترینگ و DPI" to "Adaptive Anti-DPI & Tunnel Engine",
        "حالت خودکار ابری" to "Smart Cloud Auto-Pilot",
        "حالت خودکار هوشمند" to "Smart Auto-Pilot",
        "اتصال" to "Connect",
        "قطع اتصال" to "Disconnect",
        "در حال برقراری" to "Connecting",
        "تلاش مجدد" to "Retry",
        "لغو اتصال" to "Cancel",
        "آماده برقراری ارتباط" to "Ready to Connect",
        "آماده اتصال هوشمند" to "Smart Auto-Connect Ready",
        "متصل از طریق شبکه ابری هوشمند" to "Connected via Smart Cloud Network",
        "تونل امن روی پروفایل شخصی برقرار است" to "Secure Tunnel Active on Personal Profile",
        "در حال آماده‌سازی هسته و بررسی شبکه..." to "Initializing core & probing network...",
        "در حال تست موازی ۶ مسیره و یافتن بهترین متد..." to "Running 6-lane parallel DPI method race...",
        "در حال مذاکره TLS و تایید سلامت تونل..." to "Negotiating TLS & verifying tunnel stream...",
        "در حال برقراری و قفل نهایی تونل امن..." to "Locking final verified anti-DPI tunnel...",
        "ارتباط برقرار نشد؛ متد یا سرور دیگری را امتحان کنید" to "Connection failed; try another method or server",
        "اتصال مجدد" to "Reconnect",
        "بهترینِ کانفیگ من" to "Best Personal",
        "دریافت" to "Download",
        "ارسال" to "Upload",
        "وی‌پی‌ان" to "VPN",
        "پروکسی" to "Proxy",
        "دی‌ان‌اس DoH" to "DoH DNS",
        "موقعیت سرور" to "Server Location",
        "خودکار (سریع‌ترین)" to "Auto (Fastest)",
        "خودکار" to "Auto",
        "سرور انتخابی" to "Selected Server",
        "پروفایل و متد" to "Profile & Method",
        "ضد DPI فعال" to "Anti-DPI ON",
        "ضد DPI" to "Anti-DPI",
        "متد DPI: فعال" to "DPI Method: ON",
        "متد DPI: روشن" to "DPI Method: ON",
        "خالص v2ray" to "Pure v2ray",
        "مخزن ابری هوشمند" to "Smart Cloud Repo",
        "همه برنامه‌ها" to "All Apps",
        "تفکیک برنامه‌ها" to "Per-App Proxy",
        "تفکیک برنامه‌ها (Per-App Proxy)" to "Per-App Proxy (Split Tunnel)",
        "فقط انتخابی‌ها" to "Include Selected",
        "عبور مستقیم" to "Bypass Selected",
        "عبور مستقیم (بدون VPN)" to "Bypass VPN (Direct)",
        "نمایش برنامه‌های سیستمی اندروید" to "Show Android System Apps",
        "اتصال خودکار به بهترینِ من" to "Auto-Connect Best Personal",
        "اتصال خودکار به بهترین کانفیگ من (با متد DPI)" to "Auto-Connect Best Personal (with Anti-DPI)",
        "اتصال خودکار به بهترین کانفیگ من (خالص v2ray)" to "Auto-Connect Best Personal (Pure v2ray)",
        "دستی" to "Manual",
        "دستی (Manual)" to "Manual",
        "انتخاب همه" to "Select All",
        "همه" to "All",
        "لغو" to "Clear",
        "انصراف" to "Cancel",
        "بستن" to "Close",
        "ثبت" to "Save",
        "ذخیره تغییرات" to "Save Changes",
        "بروزرسانی" to "Refresh",
        "بروزرسانی همه" to "Refresh All",
        "پاک کردن" to "Clear",
        "پاک‌سازی" to "Clear Logs",
        "کپی لاگ" to "Copy Logs",
        "کپی لینک" to "Copy Link",
        "اشتراک‌گذاری" to "Share",
        "افزودن کلیپ‌بورد" to "Paste Clipboard",
        "مخزن ابری گیت‌هاب" to "GitHub Cloud Repo",
        "انتخاب از کانفیگ‌های شخصی من" to "Switch to My Personal Profiles",
        "انتخاب لوکیشن مخزن هوشمند" to "Select Smart Cloud Location",
        "کانفیگ‌های من و اتصال هوشمند" to "My Profiles & Smart Connect",
        "اعمال متدهای ضد DPI و IP تمیز" to "Apply Anti-DPI Vectors & Clean IP",
        "فعال: تست خودکار متدهای ضد فیلتر" to "Enabled: Auto-probe 24 Anti-DPI vectors",
        "غیرفعال: اتصال خالص استاندارد v2rayNG" to "Disabled: Pure standard v2rayNG connection",
        "مدیریت استاندارد ساب‌ها، پینگ واقعی و متد ضد DPI" to "Manage Subscriptions, Real-Ping & Anti-DPI",
        "جستجوی نام، آدرس، SNI یا پروتکل..." to "Search remark, host, SNI or protocol...",
        "مدیریت لینک‌های اشتراک (Sub)" to "Subscription Manager",
        "عنوان اشتراک (نام گروه)" to "Subscription Title (Group Name)",
        "لینک اشتراک (https://...)" to "Subscription URL (https://...)",
        "افزودن و دریافت کانفیگ‌های اشتراک" to "Add & Fetch Subscription",
        "ساخت کانفیگ جدید" to "Create New Profile",
        "ویرایش کامل کانفیگ" to "Edit Profile",
        "وارد کردن مستقیم لینک خام" to "Direct Raw URI Input",
        "نام کانفیگ (Remark)" to "Profile Remark Name",
        "پروتکل" to "Protocol",
        "نوع شبکه" to "Transport",
        "متد عبور ضد DPI" to "Anti-DPI Vector",
        "آدرس سرور (Host / IP)" to "Server Host / IP",
        "پورت" to "Port",
        "شناسه UUID / رمز عبور" to "UUID / Password",
        "نام کاربری و رمز عبور (فرمت user:password)" to "Username & Password (user:password)",
        "SNI / شناسه سرور (Peer ID)" to "SNI / Peer ID",
        "گروه / کلید مشترک (Group / PSK)" to "Auth Group / IPsec PSK",
        "امنیت" to "Security",
        "مسیر (Path)" to "Path",
        "یک کانفیگ" to "Single Profile",
        "چند کانفیگ" to "Selected Profiles",
        "کل مخزن" to "Entire Repo",
        "انتخاب کانفیگ هدف" to "Select Target Profile",
        "اتصال خودکار به سریع‌ترین متد تاییدشده" to "Auto-connect to fastest verified vector",
        "تزریق خودکار IP تمیز کلادفلر حین تست" to "Auto-inject Cloudflare Clean IP during scan",
        "شروع اسکن متد" to "Start Method Scan",
        "توقف اسکن" to "Stop Scan",
        "ثبت متد" to "Save Vector",
        "ثبت و اتصال فوری" to "Save & Connect Now",
        "رادار IP تمیز کلادفلر" to "Cloudflare Clean IP Radar",
        "اسکنر و تنظیم DoH (DNSveil)" to "DoH Scanner & Setter (DNSveil)",
        "فایل گیت‌هاب" to "GitHub Subnets",
        "ساب‌نت پیش‌فرض" to "Default Subnets",
        "رنج دلخواه" to "Custom CIDR",
        "پورت (443/8443)" to "Port (443/8443)",
        "تعداد هدف" to "Max Hits",
        "شروع اسکن" to "Start Scan",
        "توقف" to "Stop",
        "اعمال و کلون روی کانفیگ‌ها" to "Apply / Clone to Profiles",
        "افزودن مخزن DoH" to "Add Custom DoH",
        "اسکن DoH" to "Scan DoH",
        "ست روی نت" to "Set Active",
        "فعال" to "Active",
        "بازنشانی پیش‌فرض" to "Reset Defaults",
        "ذخیره تنظیمات شبکه" to "Save Network Settings",
        "ذخیره تنظیمات Xray" to "Save Xray Settings",
        "مدیریت و انتخاب برنامه‌ها (Per-App Proxy)" to "Select Apps for Per-App Proxy",
        "تم تیره نئومورفیک (حالت شب)" to "Dark Neumorphic Theme (Night Mode)",
        "با خاموش کردن این گزینه، تم روشن نئومورفیک فعال می‌شود" to "Disable to switch to Light Neumorphic Theme",
        "تزریق خودکار IP تمیز روی کلادفلر" to "Auto-Inject Clean IP on Cloudflare/CDN",
        "اعمال متد ضد DPI در اتصال خودکار کانفیگ شخصی" to "Apply Anti-DPI in Personal Auto-Connect",
        "انتخاب خودکار مخزن ابری هنگام اتصال" to "Use Smart Cloud Repo on Main Connect",
        "نام‌گذاری خودکار با پرچم هنگام اسکن" to "Auto-Rename with Country Flag on Scan",
        "تعمیر خودکار اتصال (Watchdog)" to "Self-Healing Connection Watchdog",
        "آلمان" to "Germany",
        "هلند" to "Netherlands",
        "فنلاند" to "Finland",
        "انگلستان" to "United Kingdom",
        "فرانسه" to "France",
        "آمریکا" to "United States",
        "سوئد" to "Sweden",
        "ترکیه" to "Turkey",
        "ایران" to "Iran",
        "بین‌الملل" to "International",
        "تست پینگ واقعی تونل (Real-Ping)" to "Real Tunnel Ping (HTTP 204)",
        "پینگ سریع TCP همه کانفیگ‌ها" to "Fast TCP Ping All Profiles",
        "ارسال کل مخزن به آزمایشگاه متد ضد DPI" to "Send Entire Repo to 6-Lane DPI Lab",
        "تشخیص کشور و نام‌گذاری خودکار با پرچم" to "Detect Country & Auto-Rename with Flag",
        "اسکن QR Code با دوربین" to "Scan QR Code with Camera",
        "خواندن QR Code از تصویر گالری" to "Import QR Code from Gallery Image",
        "افزودن از کلیپ‌بورد" to "Import from Clipboard",
        "وارد کردن متنی / لینک گروهی" to "Batch Text / URI Import",
        "ساخت کانفیگ دستی (v2ray / AnyConnect / IKEv2 / L2TP)" to "Create Manual Profile (v2ray / AnyConnect / IKEv2 / L2TP)",
        "مدیریت لینک‌های اشتراک (Subscriptions)" to "Manage Subscriptions",
        "مرتب‌سازی بر اساس پینگ (سریع‌ترین)" to "Sort by Ping (Fastest First)",
        "مرتب‌سازی بر اساس گروه اشتراک" to "Sort by Subscription Group",
        "مرتب‌سازی بر اساس نام" to "Sort by Remark Name",
        "مرتب‌سازی بر اساس پروتکل" to "Sort by Protocol",
        "حذف کانفیگ‌های تکراری" to "Deduplicate Profiles",
        "حذف کانفیگ‌های ازکارافتاده (DEAD)" to "Remove Dead Profiles",
        "کپی همه کانفیگ‌های سالم" to "Copy All Verified Profiles",
        "خروجی Base64 همه کانفیگ‌ها" to "Export All as Base64 Subscription",
        "حذف همه کانفیگ‌ها" to "Clear All Profiles"
    )

    fun tr(input: String): String {
        if (lang != "en" || input.isEmpty()) return input
        exactMap[input]?.let { return it }
        val trimmed = input.trim()
        exactMap[trimmed]?.let { return it }

                if (trimmed.startsWith("بررسی موازی")) return "Probing personal profiles in parallel..."
        if (trimmed.startsWith("در حال اسکن")) return "Scanning in progress..."
        if (trimmed.startsWith("سرورهای تاییدشده:")) return "Verified Servers: " + trimmed.substringAfter(":").trim()
        if (trimmed.startsWith("بروزرسانی:")) return "Updated: " + trimmed.substringAfter(":").trim()
        if (trimmed.startsWith("فایل ساب‌نت گیت‌هاب (")) {
            val n = trimmed.substringAfter("(").substringBefore(" ")
            return "GitHub Subnets ($n active ranges)"
        }
        if (trimmed.startsWith("عبور فقط برنامه‌های انتخابی")) return "Route only selected apps through VPN"
        if (trimmed.startsWith("عبور مستقیم برنامه‌های انتخابی")) return "Bypass VPN for selected apps"
        if (trimmed.startsWith("عبور تمام برنامه‌های دستگاه")) return "Route all device apps through VPN"
        if (trimmed.startsWith("فقط ") && trimmed.endsWith("برنامه")) {
            val n = trimmed.removePrefix("فقط ").removeSuffix("برنامه").trim()
            return "Only $n Apps"
        }
        if (trimmed.startsWith("استثنا:") && trimmed.endsWith("برنامه")) {
            val n = trimmed.substringAfter(":").removeSuffix("برنامه").trim()
            return "Exclude $n Apps"
        }
        if (trimmed.startsWith("اسکن هوشمند متدها روی تمام")) {
            return "Smart 6-lane vector scan across all repository profiles"
        }

        if (trimmed.startsWith("پروفایل‌ها و اشتراک‌ها (")) {
            val n = trimmed.substringAfter("(").substringBefore(")")
            return "Profiles & Subscriptions ($n)"
        }
        if (trimmed.startsWith("همه گروه‌ها (")) {
            val n = trimmed.substringAfter("(").substringBefore(")")
            return "All Groups ($n)"
        }
        if (trimmed.startsWith("+ اشتراک‌ها (")) {
            val n = trimmed.substringAfter("(").substringBefore(")")
            return "+ Subscriptions ($n)"
        }
        if (trimmed.startsWith("کانفیگ‌های من (")) {
            val n = trimmed.substringAfter("(").substringBefore(")")
            return "My Profiles ($n)"
        }
        if (trimmed.endsWith("سرور مخزن")) {
            val n = trimmed.removeSuffix("سرور مخزن").trim()
            return "$n Repo Servers"
        }
        if (trimmed.endsWith("سرور آماده")) {
            val n = trimmed.removeSuffix("سرور آماده").trim()
            return "$n Ready Servers"
        }
        if (trimmed.startsWith("همه (")) {
            val n = trimmed.substringAfter("(").substringBefore(")")
            return "All ($n)"
        }
        if (trimmed.startsWith("ذخیره و اعمال (")) {
            val n = trimmed.substringAfter("(").substringBefore(")")
            return "Save & Apply ($n)"
        }
        if (trimmed.endsWith("برنامه انتخاب شده")) {
            val n = trimmed.removeSuffix("برنامه انتخاب شده").trim()
            return "$n App(s) Selected"
        }
        if (trimmed.endsWith("رویداد ثبت‌شده")) {
            val n = trimmed.removeSuffix("رویداد ثبت‌شده").trim()
            return "$n Logged Events"
        }
        if (trimmed.startsWith("دی‌ان‌اس امن فعال:")) {
            return "Active DoH: " + trimmed.substringAfter(":").trim()
        }
        return input
    }
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current
) {
    M3Text(
        text = AlanI18n.tr(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        style = style
    )
}


val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_medium, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold)
)

private val BaseM3Typography = Typography()
val AlanVazirmatnTypography = Typography(
    displayLarge = BaseM3Typography.displayLarge.copy(fontFamily = VazirmatnFontFamily),
    displayMedium = BaseM3Typography.displayMedium.copy(fontFamily = VazirmatnFontFamily),
    displaySmall = BaseM3Typography.displaySmall.copy(fontFamily = VazirmatnFontFamily),
    headlineLarge = BaseM3Typography.headlineLarge.copy(fontFamily = VazirmatnFontFamily),
    headlineMedium = BaseM3Typography.headlineMedium.copy(fontFamily = VazirmatnFontFamily),
    headlineSmall = BaseM3Typography.headlineSmall.copy(fontFamily = VazirmatnFontFamily),
    titleLarge = BaseM3Typography.titleLarge.copy(fontFamily = VazirmatnFontFamily),
    titleMedium = BaseM3Typography.titleMedium.copy(fontFamily = VazirmatnFontFamily),
    titleSmall = BaseM3Typography.titleSmall.copy(fontFamily = VazirmatnFontFamily),
    bodyLarge = BaseM3Typography.bodyLarge.copy(fontFamily = VazirmatnFontFamily),
    bodyMedium = BaseM3Typography.bodyMedium.copy(fontFamily = VazirmatnFontFamily),
    bodySmall = BaseM3Typography.bodySmall.copy(fontFamily = VazirmatnFontFamily),
    labelLarge = BaseM3Typography.labelLarge.copy(fontFamily = VazirmatnFontFamily),
    labelMedium = BaseM3Typography.labelMedium.copy(fontFamily = VazirmatnFontFamily),
    labelSmall = BaseM3Typography.labelSmall.copy(fontFamily = VazirmatnFontFamily)
)

@Composable
fun AlanCyberTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkAlanPalette else LightAlanPalette
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accentGold,
            onPrimary = palette.onAccent,
            background = palette.rootBg,
            surface = palette.surfaceBg,
            surfaceContainer = palette.cardBg,
            surfaceContainerHigh = palette.surfaceBg,
            onSurface = palette.textPrimary,
            outline = palette.cardBorder
        )
    } else {
        lightColorScheme(
            primary = palette.accentGold,
            onPrimary = palette.onAccent,
            background = palette.rootBg,
            surface = palette.surfaceBg,
            surfaceContainer = palette.cardBg,
            surfaceContainerHigh = palette.subtleBg,
            onSurface = palette.textPrimary,
            outline = palette.cardBorder
        )
    }

    CompositionLocalProvider(LocalAlanPalette provides palette) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AlanVazirmatnTypography
        ) {
            ProvideTextStyle(
                value = LocalTextStyle.current.copy(fontFamily = VazirmatnFontFamily),
                content = content
            )
        }
    }
}

@Composable
fun LtrContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

fun Long.toAdaptiveColor(fallback: Color): Color {
    if (this == 0L) return fallback
    val argb = if ((this ushr 24) == 0L) (this or 0xFF000000L) else this
    return Color(argb.toInt())
}

@Composable
fun Long.toThemeAwareAccent(fallback: Color): Color {
    val p = LocalAlanPalette.current
    val raw = this.toAdaptiveColor(fallback)
    return if (p.isDark) {
        if (raw.luminance() < 0.08f) fallback else raw
    } else {
        if (raw.luminance() > 0.44f) {
            Color(
                red = (raw.red * 0.54f).coerceIn(0f, 1f),
                green = (raw.green * 0.48f).coerceIn(0f, 1f),
                blue = (raw.blue * 0.54f).coerceIn(0f, 1f),
                alpha = 1f
            )
        } else {
            raw
        }
    }
}

fun copyPlainTextToClipboard(context: Context, label: String, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Throwable) {
    }
}

fun sharePlainText(context: Context, title: String, text: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Throwable) {
    }
}

// ============================================================================
// PURE BORDERLESS NEUMORPHIC MODIFIERS (Extruded & Inset with BlurMaskFilter)
// ============================================================================

fun Modifier.alanGlassCard(
    highlighted: Boolean = false,
    cornerRadius: Dp = 18.dp,
    accentBorderColor: Color? = null
): Modifier = composed {
    val p = LocalAlanPalette.current
    val isDark = p.isDark
    val lightShadow = if (isDark) {
        Color(0xFF2D3546).copy(alpha = if (highlighted) 0.90f else 0.74f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = if (highlighted) 1.0f else 0.94f)
    }
    val darkShadow = if (isDark) {
        Color(0xFF0D1016).copy(alpha = if (highlighted) 0.95f else 0.86f)
    } else {
        Color(0xFFB5C2D6).copy(alpha = if (highlighted) 0.90f else 0.78f)
    }

    val surfaceTop = if (isDark) Color(0xFF252C3B) else Color(0xFFF2F6FD)
    val surfaceBottom = if (isDark) Color(0xFF191E28) else Color(0xFFDEE6F3)
    val tintOverlay = if (highlighted) {
        (accentBorderColor ?: p.accentGold).copy(alpha = if (isDark) 0.08f else 0.06f)
    } else {
        Color.Transparent
    }
    val shape = RoundedCornerShape(cornerRadius)

    this
        .graphicsLayer { clip = false }
        .drawBehind {
            if (size.width > 4f && size.height > 4f) {
                val cr = cornerRadius.toPx()
                val off = (if (highlighted) 4.5.dp else 3.5.dp).toPx()
                val blurRad = if (highlighted) 17f else 14f
                val inset = 1.dp.toPx()

                drawIntoCanvas { canvas ->
                    val darkPaint = Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        color = darkShadow.toArgb()
                        maskFilter = BlurMaskFilter(blurRad, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawRoundRect(
                        inset + off,
                        inset + off,
                        size.width - inset + off,
                        size.height - inset + off,
                        cr,
                        cr,
                        darkPaint
                    )

                    val lightPaint = Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        color = lightShadow.toArgb()
                        maskFilter = BlurMaskFilter(blurRad, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawRoundRect(
                        inset - off,
                        inset - off,
                        size.width - inset - off,
                        size.height - inset - off,
                        cr,
                        cr,
                        lightPaint
                    )
                }
            }
        }
        .clip(shape)
        .background(Brush.linearGradient(listOf(surfaceTop, p.cardBg, surfaceBottom)))
        .background(tintOverlay)
}

fun Modifier.alanNeuInset(
    cornerRadius: Dp = 14.dp,
    accented: Boolean = false
): Modifier = composed {
    val p = LocalAlanPalette.current
    val isDark = p.isDark
    val darkInner = if (isDark) Color(0xFF0C0F14).copy(alpha = 0.92f) else Color(0xFFB5C2D6).copy(alpha = 0.88f)
    val lightInner = if (isDark) Color(0xFF2C3444).copy(alpha = 0.58f) else Color(0xFFFFFFFF).copy(alpha = 0.96f)
    val shape = RoundedCornerShape(cornerRadius)
    val bgCol = if (accented) {
        if (isDark) Color(0xFF131720) else Color(0xFFD0DBEC)
    } else {
        p.subtleBg
    }

    this
        .clip(shape)
        .background(bgCol)
        .drawBehind {
            if (size.width > 4f && size.height > 4f) {
                val cr = cornerRadius.toPx()
                val strokeW = (if (accented) 3.8.dp else 3.2.dp).toPx()
                val shift = 1.9.dp.toPx()

                drawIntoCanvas { canvas ->
                    canvas.save()
                    val clipPath = android.graphics.Path().apply {
                        addRoundRect(0f, 0f, size.width, size.height, cr, cr, android.graphics.Path.Direction.CW)
                    }
                    canvas.nativeCanvas.clipPath(clipPath)

                    val darkPaint = Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = strokeW
                        color = darkInner.toArgb()
                        maskFilter = BlurMaskFilter(11f, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawRoundRect(
                        -shift,
                        -shift,
                        size.width + strokeW,
                        size.height + strokeW,
                        cr,
                        cr,
                        darkPaint
                    )

                    val lightPaint = Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = strokeW
                        color = lightInner.toArgb()
                        maskFilter = BlurMaskFilter(11f, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawRoundRect(
                        -strokeW,
                        -strokeW,
                        size.width + shift,
                        size.height + shift,
                        cr,
                        cr,
                        lightPaint
                    )
                    canvas.restore()
                }
            }
        }
}

@Composable
fun AlanNeuIconButton(
    icon: ImageVector,
    tint: Color,
    size: Dp = 42.dp,
    isPressed: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .then(
                if (isPressed) {
                    Modifier.alanNeuInset(cornerRadius = 13.dp, accented = true)
                } else {
                    Modifier.alanGlassCard(cornerRadius = 13.dp)
                }
            )
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size.value * 0.46f).dp)
        )
    }
}

@Composable
fun AlanPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val p = LocalAlanPalette.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 3.dp,
        animationSpec = tween(180),
        label = "swThumb"
    )
    val trackTint by animateColorAsState(
        targetValue = if (checked) p.emerald.copy(alpha = if (p.isDark) 0.22f else 0.16f) else Color.Transparent,
        animationSpec = tween(180),
        label = "swTrack"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) p.emerald else p.textMuted,
        animationSpec = tween(180),
        label = "swThumbCol"
    )

    LtrContent {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .width(44.dp)
                .height(25.dp)
                .alanNeuInset(cornerRadius = 13.dp, accented = checked)
                .background(trackTint)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCheckedChange(!checked) }
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}

@Composable
fun AlanSheetTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    ltr: Boolean,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val p = LocalAlanPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        modifier = modifier
            .then(if (singleLine) Modifier.height(48.dp) else Modifier.heightIn(min = 88.dp))
            .alanNeuInset(cornerRadius = 14.dp, accented = isFocused)
            .padding(horizontal = 13.dp, vertical = if (singleLine) 5.dp else 9.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (label.isNotBlank() && (value.isNotEmpty() || isFocused || !singleLine)) {
                M3Text(
                    text = AlanI18n.tr(label),
                    color = if (isFocused) p.accentGold else p.textMuted,
                    fontFamily = VazirmatnFontFamily,
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val editor: @Composable () -> Unit = {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else 6,
                    interactionSource = interactionSource,
                    cursorBrush = SolidColor(p.accentGold),
                    textStyle = TextStyle(
                        color = p.textPrimary,
                        fontFamily = VazirmatnFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (value.isEmpty() && !isFocused && singleLine && label.isNotBlank()) {
                                M3Text(
                                    text = AlanI18n.tr(label),
                                    color = p.textMuted,
                                    fontFamily = VazirmatnFontFamily,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (ltr) LtrContent { editor() } else editor()
        }
    }
}

data class SelectOptionItem(
    val value: String,
    val label: String,
    val subtitle: String = ""
)

@Composable
fun AlanCustomSelectBox(
    label: String,
    selectedValue: String,
    options: List<SelectOptionItem>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalAlanPalette.current
    var dialogOpen by remember { mutableStateOf(false) }
    val selectedOption = remember(selectedValue, options) {
        options.firstOrNull { it.value == selectedValue }
            ?: SelectOptionItem(selectedValue, selectedValue)
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier) {
        if (label.isNotBlank()) {
            M3Text(
                text = AlanI18n.tr(label),
                color = p.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 14.dp)
                .clickable { if (options.isNotEmpty()) dialogOpen = true }
                .padding(horizontal = 13.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                M3Text(
                    text = AlanI18n.tr(selectedOption.label),
                    color = p.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selectedOption.subtitle.isNotBlank()) {
                    M3Text(
                        text = AlanI18n.tr(selectedOption.subtitle),
                        color = p.textMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = p.accentGold,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (dialogOpen) {
        Dialog(onDismissRequest = { dialogOpen = false }) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = p.surfaceBg,
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            M3Text(
                                text = label.ifBlank { "انتخاب گزینه" },
                                color = p.accentGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            AlanNeuIconButton(
                                icon = Icons.Outlined.Close,
                                tint = p.textSecondary,
                                size = 32.dp,
                                onClick = { dialogOpen = false }
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            options.forEach { opt ->
                                val isSel = opt.value == selectedValue
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isSel) {
                                                Modifier.alanNeuInset(cornerRadius = 12.dp, accented = true)
                                            } else {
                                                Modifier.alanGlassCard(cornerRadius = 12.dp)
                                            }
                                        )
                                        .clickable {
                                            dialogOpen = false
                                            onSelect(opt.value)
                                        }
                                        .padding(horizontal = 13.dp, vertical = 10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        M3Text(
                                            text = AlanI18n.tr(opt.label),
                                            color = if (isSel) p.accentGold else p.textPrimary,
                                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (opt.subtitle.isNotBlank()) {
                                            M3Text(
                                                text = opt.subtitle,
                                                color = p.textMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (isSel) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = p.accentGold,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DarkActionMenuDialog(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
    val p = LocalAlanPalette.current
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = p.surfaceBg,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        M3Text(
                            text = title,
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        AlanNeuIconButton(
                            icon = Icons.Outlined.Close,
                            tint = p.textSecondary,
                            size = 32.dp,
                            onClick = onDismiss
                        )
                    }

                    actions.forEach { (label, act) ->
                        val isDanger = label.contains("حذف") || label.contains("پاک")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alanGlassCard(cornerRadius = 12.dp)
                                .clickable {
                                    onDismiss()
                                    act()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            M3Text(
                                text = AlanI18n.tr(label),
                                color = if (isDanger) p.danger else p.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrCodeDisplayDialog(
    title: String,
    subtitle: String,
    qrContent: String,
    onDismiss: () -> Unit
) {
    val p = LocalAlanPalette.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    val qrBitmap = remember(qrContent) {
        generateQrCodeBitmap(qrContent, 640)
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = p.surfaceBg,
                shadowElevation = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            M3Text(
                                text = title,
                                color = p.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle.isNotBlank()) {
                                LtrContent {
                                    M3Text(
                                        text = subtitle,
                                        color = p.textMuted,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        AlanNeuIconButton(
                            icon = Icons.Outlined.Close,
                            tint = p.textSecondary,
                            size = 34.dp,
                            onClick = onDismiss
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(236.dp)
                            .alanGlassCard(highlighted = true, cornerRadius = 22.dp)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(10.dp)
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(200.dp)
                            )
                        } else {
                            M3Text("خطا در ساخت QR", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (copied) {
                        M3Text(
                            text = "لینک کانفیگ در کلیپ‌بورد کپی شد.",
                            color = p.emerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .alanGlassCard(cornerRadius = 12.dp)
                                .clickable {
                                    copyPlainTextToClipboard(context, title, qrContent)
                                    copied = true
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ContentCopy, null, tint = p.accentGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                M3Text("کپی لینک", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.accentGold)
                                .clickable {
                                    sharePlainText(context, title, qrContent)
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Share, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                M3Text("اشتراک‌گذاری", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun generateQrCodeBitmap(content: String, sizePx: Int): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
        }
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix.get(x, y)) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    } catch (_: Throwable) {
        null
    }
}

fun setAutoRenameOnScanPreference(enabled: Boolean) {}
