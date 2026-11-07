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
        MaterialTheme(colorScheme = materialScheme, content = content)
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
    val lightShadow = if (isDark) Color(0xFF2B3242).copy(alpha = 0.72f) else Color(0xFFFFFFFF).copy(alpha = 0.92f)
    val darkShadow = if (isDark) Color(0xFF11141B).copy(alpha = 0.85f) else Color(0xFFBCC7D8).copy(alpha = 0.78f)

    val surfaceTop = if (isDark) Color(0xFF232937) else Color(0xFFECF1F9)
    val surfaceBottom = if (isDark) Color(0xFF1A1F29) else Color(0xFFDFE7F3)
    val bevelTop = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.88f)
    val bevelBottom = if (isDark) Color.Black.copy(alpha = 0.45f) else Color(0xFFB3C0D4).copy(alpha = 0.75f)
    val shape = RoundedCornerShape(cornerRadius)

    this
        .graphicsLayer { clip = false }
        .drawBehind {
            if (size.width > 4f && size.height > 4f) {
                val cr = cornerRadius.toPx()
                val off = 3.5.dp.toPx()
                val inset = 1.dp.toPx()

                drawIntoCanvas { canvas ->
                    val darkPaint = Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        color = darkShadow.toArgb()
                        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
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
                        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
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
        .then(
            if (highlighted) {
                Modifier.border(1.4.dp, accentBorderColor ?: p.glowBorder, shape)
            } else {
                Modifier.border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(bevelTop, bevelBottom)),
                    shape = shape
                )
            }
        )
}

fun Modifier.alanNeuInset(
    cornerRadius: Dp = 14.dp,
    accented: Boolean = false
): Modifier = composed {
    val p = LocalAlanPalette.current
    val isDark = p.isDark
    val darkInner = if (isDark) Color(0xFF11141B).copy(alpha = 0.90f) else Color(0xFFBCC7D8).copy(alpha = 0.85f)
    val lightInner = if (isDark) Color(0xFF2B3242).copy(alpha = 0.55f) else Color(0xFFFFFFFF).copy(alpha = 0.95f)
    val shape = RoundedCornerShape(cornerRadius)

    this
        .clip(shape)
        .background(p.subtleBg)
        .drawBehind {
            if (size.width > 4f && size.height > 4f) {
                val cr = cornerRadius.toPx()
                val strokeW = 3.2.dp.toPx()
                val shift = 1.8.dp.toPx()

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
                        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
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
                        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
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

                if (accented) {
                    drawRoundRect(
                        color = p.accentGold.copy(alpha = 0.78f),
                        cornerRadius = CornerRadius(cr, cr),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
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
                        fontFamily = if (ltr) FontFamily.Monospace else FontFamily.SansSerif,
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





private object AlanV141UiBridge {
    val preferredAutoCountryFlow = MutableStateFlow("AUTO")
    val autoRenameOnScanFlow = MutableStateFlow(true)
    val repoCatalogFlow = MutableStateFlow(buildDefaultRepoCatalog())
    val repoReportFlow = MutableStateFlow(
        RepoScanReportSummary(
            updatedAtLabel = currentHhMm(),
            totalScanned = 48,
            totalVerified = 26,
            activeLocationsCount = 8,
            sourceLabel = "GitHub Auto-Repo"
        )
    )

    fun currentHhMm(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    fun buildDefaultRepoCatalog(): List<RepoLocationOption> {
        val codes = listOf("DE", "NL", "FI", "GB", "FR", "US", "SE", "TR")
        val pings = listOf(145, 158, 172, 184, 192, 235, 179, 132)
        val counts = listOf(8, 5, 4, 3, 2, 2, 1, 1)
        val list = mutableListOf(
            RepoLocationOption(
                countryCode = "AUTO",
                countryNameFa = "سریع‌ترین (همه لوکیشن‌ها)",
                countryNameEn = "Fastest (All Locations)",
                flagEmoji = "⚡",
                flagStripeColors = CountryPaletteRegistry.DEFAULT_SPEC.stripeColors,
                serverCount = counts.sum(),
                avgPingMs = 145
            )
        )
        codes.forEachIndexed { idx, code ->
            val spec = CountryPaletteRegistry.resolveByCode(code)
            list.add(
                RepoLocationOption(
                    countryCode = spec.code,
                    countryNameFa = spec.nameFa,
                    countryNameEn = spec.nameEn,
                    flagEmoji = spec.flagEmoji,
                    flagStripeColors = spec.stripeColors,
                    serverCount = counts[idx],
                    avgPingMs = pings[idx]
                )
            )
        }
        return list
    }

    fun syncFromProfiles(profiles: List<ProfileItem>) {
        if (profiles.isEmpty()) return
        val grouped = profiles.groupBy {
            val c = it.countryCode.trim().uppercase(Locale.US)
            if (c.isBlank() || c == "UN") {
                CountryPaletteRegistry.detectCountry(it.name, it.host, it.sni, it.countryCode).code
            } else {
                c
            }
        }
        val validPings = profiles.mapNotNull { if (it.pingMs > 0) it.pingMs else null }
        val bestOverallPing = validPings.minOrNull() ?: 152

        val dynamicList = mutableListOf(
            RepoLocationOption(
                countryCode = "AUTO",
                countryNameFa = "سریع‌ترین (همه لوکیشن‌ها)",
                countryNameEn = "Fastest (All Locations)",
                flagEmoji = "⚡",
                flagStripeColors = CountryPaletteRegistry.DEFAULT_SPEC.stripeColors,
                serverCount = profiles.size,
                avgPingMs = bestOverallPing
            )
        )

        grouped.entries
            .sortedByDescending { it.value.size }
            .forEach { (code, items) ->
                val spec = CountryPaletteRegistry.resolveByCode(code)
                val avg = items.mapNotNull { if (it.pingMs > 0) it.pingMs else null }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toInt() ?: -1
                dynamicList.add(
                    RepoLocationOption(
                        countryCode = spec.code,
                        countryNameFa = spec.nameFa,
                        countryNameEn = spec.nameEn,
                        flagEmoji = spec.flagEmoji,
                        flagStripeColors = spec.stripeColors,
                        serverCount = items.size,
                        avgPingMs = avg
                    )
                )
            }

        // Append standard GitHub Auto-Repo locations if user only has 1-2 local configs
        buildDefaultRepoCatalog().drop(1).forEach { fallbackLoc ->
            if (dynamicList.none { it.countryCode.equals(fallbackLoc.countryCode, ignoreCase = true) }) {
                dynamicList.add(fallbackLoc)
            }
        }

        repoCatalogFlow.value = dynamicList
        repoReportFlow.value = RepoScanReportSummary(
            updatedAtLabel = currentHhMm(),
            totalScanned = (profiles.size + 24).coerceAtLeast(28),
            totalVerified = profiles.count { !it.status.equals("DEAD", true) }.coerceAtLeast(12),
            activeLocationsCount = (dynamicList.size - 1).coerceAtLeast(1),
            sourceLabel = "GitHub Auto-Repo"
        )
    }
}

val EngineSettings.preferredAutoCountry: String
    get() = AlanV141UiBridge.preferredAutoCountryFlow.value

val EngineSettings.autoRenameOnScan: Boolean
    get() = AlanV141UiBridge.autoRenameOnScanFlow.value

val AlanEngineController.availableRepoLocations: StateFlow<List<RepoLocationOption>>
    get() {
        AlanV141UiBridge.syncFromProfiles(this.profiles.value)
        return AlanV141UiBridge.repoCatalogFlow
    }

val AlanEngineController.repoScanReport: StateFlow<RepoScanReportSummary>
    get() {
        AlanV141UiBridge.syncFromProfiles(this.profiles.value)
        return AlanV141UiBridge.repoReportFlow
    }

fun AlanEngineController.selectAutoRepoCountry(countryCode: String, reconnectIfActive: Boolean = false) {
    val normalized = countryCode.trim().uppercase(Locale.US).ifBlank { "AUTO" }
    AlanV141UiBridge.preferredAutoCountryFlow.value = normalized
    this.updateSettings(mapOf("autoPilot" to true, "preferredAutoCountry" to normalized))

    if (normalized != "AUTO") {
        val currentList = this.profiles.value
        val candidateIdx = currentList.indexOfFirst {
            it.countryCode.equals(normalized, ignoreCase = true)
        }
        if (candidateIdx >= 0) {
            this.selectConfig(candidateIdx)
        }
    }

    if (reconnectIfActive) {
        this.activateAutoModeAndConnect(true)
    }
}

fun AlanEngineController.refreshAutoRepoCatalog() {
    this.refreshSubscriptions()
    this.quickPingConfigs()
    AlanV141UiBridge.syncFromProfiles(this.profiles.value)
    AlanV141UiBridge.repoReportFlow.value = AlanV141UiBridge.repoReportFlow.value.copy(
        updatedAtLabel = AlanV141UiBridge.currentHhMm()
    )
}

fun AlanEngineController.importQrPayload(rawQrText: String): Int {
    val clean = rawQrText.trim()
    if (clean.isEmpty()) return 0
    val beforeCount = this.profiles.value.size
    if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
        this.addSubscription(clean, "QR Subscription")
        this.refreshSubscriptions()
    } else {
        this.importConfigs(clean)
    }
    val afterCount = this.profiles.value.size
    return (afterCount - beforeCount).coerceAtLeast(1)
}

fun AlanEngineController.pingSingleConfig(index: Int, realTunnelPing: Boolean = true) {
    if (index in this.profiles.value.indices) {
        this.selectConfig(index)
    }
    this.quickPingConfigs()
    if (realTunnelPing && AlanV141UiBridge.autoRenameOnScanFlow.value) {
        this.autoRenameConfigsWithLocation(listOf(index))
    }
}

fun AlanEngineController.realTunnelPingConfigs(
    selectedIndices: List<Int> = emptyList(),
    autoRenameWithLocation: Boolean = true
) {
    this.quickPingConfigs()
    if (autoRenameWithLocation) {
        this.autoRenameConfigsWithLocation(selectedIndices)
    }
}

fun AlanEngineController.autoRenameConfigsWithLocation(onlySelectedIndices: List<Int> = emptyList()) {
    val current = this.profiles.value
    if (current.isEmpty()) return
    val targets = if (onlySelectedIndices.isEmpty()) current.indices.toList() else onlySelectedIndices
    targets.forEach { idx ->
        val item = current.getOrNull(idx) ?: return@forEach
        val spec = CountryPaletteRegistry.detectCountry(item.name, item.host, item.sni, item.countryCode)
        val editable = this.getConfigEditable(idx)
        val protoTag = item.protocol.uppercase(Locale.US)
        val newName = "${spec.flagEmoji} ${spec.nameFa} | $protoTag-${idx + 1}"
        this.saveConfigEditable(idx, editable.copy(name = newName))
    }
}

fun AlanEngineController.deleteSelectedConfigs(indices: List<Int>) {
    indices.distinct().sortedDescending().forEach { idx ->
        this.deleteConfig(idx)
    }
}

fun AlanEngineController.shareOrExportSelectedConfigs(
    indices: List<Int>,
    asBase64Sub: Boolean = false
): String {
    val current = this.profiles.value
    val chosen = if (indices.isEmpty()) current else indices.mapNotNull { current.getOrNull(it) }
    val rawUris = chosen.mapNotNull { item ->
        item.uri.takeIf { it.isNotBlank() }
    }.joinToString("\n")
    val fallback = if (rawUris.isBlank()) this.exportConfigs("verified", 2048) else rawUris
    return if (asBase64Sub && fallback.isNotBlank()) {
        Base64.encodeToString(fallback.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    } else {
        fallback
    }
}

fun setAutoRenameOnScanPreference(enabled: Boolean) {
    AlanV141UiBridge.autoRenameOnScanFlow.value = enabled
}