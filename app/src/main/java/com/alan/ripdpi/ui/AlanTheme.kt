package com.alan.ripdpi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

enum class AlanLang { FA, EN }

val LocalAlanLang = staticCompositionLocalOf { AlanLang.FA }

@Composable
fun tr(fa: String, en: String): String =
    if (LocalAlanLang.current == AlanLang.FA) fa else en

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
    surfaceBg = Color(0xFF222836),
    cardBg = Color(0xFF1E232E),
    subtleBg = Color(0xFF161A23),
    cardBorder = Color(0xFF2E3647),
    glowBorder = Color(0x99F5C451),
    accentGold = Color(0xFFF5C451),
    accentAmber = Color(0xFFE59A1A),
    accentSoftBg = Color(0x24F5C451),
    onAccent = Color(0xFF141820),
    emerald = Color(0xFF10B981),
    cyan = Color(0xFF38BDF8),
    danger = Color(0xFFEF4444),
    textPrimary = Color(0xFFF3F6FC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8)
)

private val LightAlanPalette = AlanPalette(
    isDark = false,
    rootBg = Color(0xFFE6ECF5),
    surfaceBg = Color(0xFFEDF2F9),
    cardBg = Color(0xFFE6ECF5),
    subtleBg = Color(0xFFD7E0EE),
    cardBorder = Color(0xFFC3D0E2),
    glowBorder = Color(0x88B45309),
    accentGold = Color(0xFFB45309),
    accentAmber = Color(0xFF92400E),
    accentSoftBg = Color(0x22D97706),
    onAccent = Color(0xFFFFFFFF),
    emerald = Color(0xFF047857),
    cyan = Color(0xFF0369A1),
    danger = Color(0xFFDC2626),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF52637A)
)


val VazirmatnFontFamily = FontFamily(
    androidx.compose.ui.text.font.Font(com.alan.ripdpi.R.font.vazirmatn_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(com.alan.ripdpi.R.font.vazirmatn_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(com.alan.ripdpi.R.font.vazirmatn_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(com.alan.ripdpi.R.font.vazirmatn_extrabold, FontWeight.ExtraBold)
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
                red = (raw.red * 0.56f).coerceIn(0f, 1f),
                green = (raw.green * 0.50f).coerceIn(0f, 1f),
                blue = (raw.blue * 0.56f).coerceIn(0f, 1f),
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

// Extruded Soft Neumorphic Card (Convex) - Clean controlled halo + directional 3D bevel
fun Modifier.alanGlassCard(
    highlighted: Boolean = false,
    cornerRadius: Dp = 16.dp,
    accentBorderColor: Color? = null
): Modifier = composed {
    val p = LocalAlanPalette.current
    val isDark = p.isDark
    val lightShadow = if (isDark) Color(0xFF323B4E).copy(alpha = 0.34f) else Color(0xFFFFFFFF).copy(alpha = 0.85f)
    val darkShadow = if (isDark) Color(0xFF0A0D13).copy(alpha = 0.56f) else Color(0xFFA8B8CE).copy(alpha = 0.50f)

    val topBevel = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.82f)
    val bottomBevel = if (isDark) Color.Black.copy(alpha = 0.42f) else Color(0xFFB5C4D9).copy(alpha = 0.72f)
    val shape = RoundedCornerShape(cornerRadius)

    this
        .drawBehind {
            if (size.width > 4f && size.height > 4f) {
                val cr = cornerRadius.toPx()
                val blur = 6.dp.toPx()
                val off = 2.8.dp.toPx()
                val inset = 1.dp.toPx()

                drawIntoCanvas { canvas ->
                    val darkPaint = Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        color = darkShadow.toArgb()
                        maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
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
                        maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
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
        .background(p.cardBg)
        
}

// Recessed Soft Neumorphic Well (Concave) - Drawn strictly behind content so text NEVER fades!
fun Modifier.alanNeuInset(cornerRadius: Dp = 14.dp, accented: Boolean = false): Modifier = composed {
    val p = LocalAlanPalette.current
    val isDark = p.isDark
    val innerShade = if (isDark) Color.Black.copy(alpha = 0.34f) else Color(0xFF9FB0C7).copy(alpha = 0.36f)
    val borderTopLeft = if (isDark) Color.Black.copy(alpha = 0.50f) else Color(0xFFB0C0D6).copy(alpha = 0.85f)
    val borderBottomRight = if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.92f)
    val shape = RoundedCornerShape(cornerRadius)

    this
        .clip(shape)
        .background(p.subtleBg)
        .drawBehind {
            val band = 5.dp.toPx().coerceAtMost(size.minDimension / 2f)
            if (band > 1f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(innerShade, Color.Transparent),
                        startY = 0f,
                        endY = band
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, band)
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(innerShade.copy(alpha = innerShade.alpha * 0.7f), Color.Transparent),
                        startX = 0f,
                        endX = band
                    ),
                    topLeft = Offset.Zero,
                    size = Size(band, size.height)
                )
            }
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(borderTopLeft, borderBottomRight)),
            shape = shape
        )
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
                .alanNeuInset(cornerRadius = 13.dp)
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
                        color = Color.White.copy(alpha = if (p.isDark) 0.25f else 0.65f),
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
    val shape = RoundedCornerShape(12.dp)

    Box(
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        modifier = modifier
            .then(if (singleLine) Modifier.height(48.dp) else Modifier.heightIn(min = 84.dp))
            .alanNeuInset(cornerRadius = 12.dp)
            .then(
                if (isFocused) Modifier.border(1.3.dp, p.accentGold, shape) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = if (singleLine) 5.dp else 8.dp)
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
                    maxLines = if (singleLine) 1 else 5,
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
    val layoutDir = if (LocalAlanLang.current == AlanLang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
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
                .alanNeuInset(cornerRadius = 12.dp)
                .clickable { if (options.isNotEmpty()) dialogOpen = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
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
            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = p.surfaceBg,
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, p.cardBorder),
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
                                text = label.ifBlank { tr("انتخاب گزینه", "Select Option") },
                                color = p.accentGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .alanNeuInset(cornerRadius = 15.dp)
                                    .clickable { dialogOpen = false }
                            ) {
                                Icon(Icons.Outlined.Close, null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(7.dp),
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
                                                Modifier
                                                    .alanNeuInset(11.dp)
                                                    .border(1.1.dp, p.accentGold, RoundedCornerShape(11.dp))
                                            } else {
                                                Modifier.alanGlassCard(cornerRadius = 11.dp)
                                            }
                                        )
                                        .clickable {
                                            dialogOpen = false
                                            onSelect(opt.value)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
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
    val layoutDir = if (LocalAlanLang.current == AlanLang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = p.surfaceBg,
                border = androidx.compose.foundation.BorderStroke(1.2.dp, p.cardBorder),
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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .alanNeuInset(cornerRadius = 15.dp)
                                .clickable { onDismiss() }
                        ) {
                            Icon(Icons.Outlined.Close, null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    actions.forEach { (label, act) ->
                        val isDanger = label.contains("حذف") || label.contains("پاک") ||
                            label.contains("Delete", ignoreCase = true) || label.contains("Clear", ignoreCase = true) ||
                            label.contains("Remove", ignoreCase = true)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alanGlassCard(cornerRadius = 11.dp)
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
fun AlanNeuIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    size: Dp = 42.dp,
    isPressed: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .then(if (isPressed) Modifier.alanNeuInset(cornerRadius = 13.dp, accented = true) else Modifier.alanGlassCard(cornerRadius = 13.dp))
            .clickable(onClick = onClick)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size((size.value * 0.46f).dp))
    }
}
