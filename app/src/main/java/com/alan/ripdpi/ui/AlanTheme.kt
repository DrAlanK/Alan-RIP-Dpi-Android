package com.alan.ripdpi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.util.Base64
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.material3.Typography
import com.alan.ripdpi.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alan.ripdpi.model.CountryPaletteRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

// ============================================================================
// 1. NEUMORPHIC PALETTE & THEME (#1E232E Dark / #E6ECF5 Light)
// ============================================================================

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
    subtleBg = Color(0xFF161A23),
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
    subtleBg = Color(0xFFD7E0EE),
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

val VazirmatnFontFamily = FontFamily(
    androidx.compose.ui.text.font.Font(com.alan.ripdpi.R.font.vazirmatn_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(com.alan.ripdpi.R.font.vazirmatn_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(com.alan.ripdpi.R.font.vazirmatn_medium, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(com.alan.ripdpi.R.font.vazirmatn_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(com.alan.ripdpi.R.font.vazirmatn_extrabold, FontWeight.ExtraBold)
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
        MaterialTheme(colorScheme = materialScheme, typography = AlanVazirmatnTypography, content = content)
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
// 2. REAL TACTILE NEUMORPHIC MODIFIERS (Extruded & Inset with BlurMaskFilter)
// ============================================================================

fun Modifier.alanGlassCard(
    highlighted: Boolean = false,
    cornerRadius: Dp = 18.dp,
    accentBorderColor: Color? = null
): Modifier = composed {
    val p = LocalAlanPalette.current
    val isDark = p.isDark
    val lightShadow = if (isDark) {
        Color(0xFF2E3646).copy(alpha = if (highlighted) 0.88f else 0.72f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = if (highlighted) 1.0f else 0.92f)
    }
    val darkShadow = if (isDark) {
        Color(0xFF0E1117).copy(alpha = if (highlighted) 0.95f else 0.85f)
    } else {
        Color(0xFFB7C3D6).copy(alpha = if (highlighted) 0.90f else 0.76f)
    }

    val tintOverlay = if (highlighted) {
        (accentBorderColor ?: p.accentGold).copy(alpha = if (isDark) 0.08f else 0.06f)
    } else {
        Color.Transparent
    }

    val surfaceTop = if (isDark) Color(0xFF242B3A) else Color(0xFFF1F5FC)
    val surfaceBottom = if (isDark) Color(0xFF191E28) else Color(0xFFDEE6F2)
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
    val darkInner = if (isDark) Color(0xFF0E1117).copy(alpha = 0.92f) else Color(0xFFB6C2D5).copy(alpha = 0.88f)
    val lightInner = if (isDark) Color(0xFF2C3444).copy(alpha = 0.58f) else Color(0xFFFFFFFF).copy(alpha = 0.96f)
    val shape = RoundedCornerShape(cornerRadius)
    val bgCol = if (accented) {
        if (isDark) Color(0xFF141821) else Color(0xFFD2DCEC)
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
                        addRoundRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            cr,
                            cr,
                            android.graphics.Path.Direction.CW
                        )
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

// ============================================================================
// 3. SHARED NEUMORPHIC INPUTS, SELECTORS & QR CODE DIALOG (ZXing)
// ============================================================================

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
        targetValue = if (checked) p.accentGold.copy(alpha = if (p.isDark) 0.24f else 0.18f) else Color.Transparent,
        animationSpec = tween(180),
        label = "swTrack"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) p.accentGold else p.textMuted,
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
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = if (p.isDark) 0.28f else 0.70f),
                        shape = CircleShape
                    )
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
                Text(
                    text = label,
                    color = if (isFocused) p.accentGold else p.textMuted,
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
                        fontFamily = if (ltr) FontFamily.Monospace else VazirmatnFontFamily,
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
                                Text(
                                    text = label,
                                    color = p.textMuted,
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
            Text(
                text = label,
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
                Text(
                    text = selectedOption.label,
                    color = p.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selectedOption.subtitle.isNotBlank()) {
                    Text(
                        text = selectedOption.subtitle,
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
                    border = BorderStroke(1.2.dp, p.cardBorder),
                    shadowElevation = 10.dp,
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
                            Text(
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
                                        Text(
                                            text = opt.label,
                                            color = if (isSel) p.accentGold else p.textPrimary,
                                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (opt.subtitle.isNotBlank()) {
                                            Text(
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
                border = BorderStroke(1.2.dp, p.cardBorder),
                shadowElevation = 10.dp,
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
                        Text(
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
                            Text(
                                text = label,
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
                border = BorderStroke(1.3.dp, p.glowBorder),
                shadowElevation = 12.dp,
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
                            Text(
                                text = title,
                                color = p.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle.isNotBlank()) {
                                LtrContent {
                                    Text(
                                        text = subtitle,
                                        color = p.textMuted,
                                        fontFamily = FontFamily.Monospace,
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

                    // Raised White Neumorphic Frame for QR Code
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
                            Text("خطا در ساخت QR", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (copied) {
                        Text(
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
                                Text("کپی لینک", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                Text("اشتراک‌گذاری", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
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

// ============================================================================
// 4. v1.4.1 SMART AUTO-LOCATION & CONTROLLER EXTENSIONS (Compatible with AlanUiContract.kt)
// ============================================================================

fun setAutoRenameOnScanPreference(enabled: Boolean) {}
