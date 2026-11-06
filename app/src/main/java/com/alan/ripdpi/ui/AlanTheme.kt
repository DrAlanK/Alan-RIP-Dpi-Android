package com.alan.ripdpi.ui

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

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
    rootBg = Color(0xFF1E222B),
    surfaceBg = Color(0xFF1E222B),
    cardBg = Color(0xFF1E222B),
    subtleBg = Color(0xFF1E222B),
    cardBorder = Color(0x00000000), // نامرئی در نئومورفیسم مگر در حالت highlight
    glowBorder = Color(0x99F5C451),
    accentGold = Color(0xFFF5C451),
    accentAmber = Color(0xFFE59A1A),
    accentSoftBg = Color(0x1AF5C451),
    onAccent = Color(0xFF1E222B),
    emerald = Color(0xFF10B981),
    cyan = Color(0xFF38BDF8),
    danger = Color(0xFFEF4444),
    textPrimary = Color(0xFFF3F6FC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF8B9BB4)
)

private val LightAlanPalette = AlanPalette(
    isDark = false,
    rootBg = Color(0xFFE6EBF2),
    surfaceBg = Color(0xFFE6EBF2),
    cardBg = Color(0xFFE6EBF2),
    subtleBg = Color(0xFFE6EBF2),
    cardBorder = Color(0x00000000), // نامرئی در نئومورفیسم
    glowBorder = Color(0x99D99B16),
    accentGold = Color(0xFFD99B16),
    accentAmber = Color(0xFFB47806),
    accentSoftBg = Color(0x1AD99B16),
    onAccent = Color(0xFFFFFFFF),
    emerald = Color(0xFF059669),
    cyan = Color(0xFF0284C7),
    danger = Color(0xFFDC2626),
    textPrimary = Color(0xFF1E222B),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF75859D)
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
            surfaceContainerHigh = palette.cardBg,
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

// Extruded Soft Shadow (Neumorphism Convex)
fun Modifier.alanGlassCard(
    highlighted: Boolean = false,
    cornerRadius: Dp = 16.dp,
    accentBorderColor: Color? = null
): Modifier = composed {
    val p = LocalAlanPalette.current
    val isDark = p.isDark
    val lightShadowCol = if (isDark) Color(0xFF2A303D) else Color(0xFFFFFFFF)
    val darkShadowCol = if (isDark) Color(0xFF12151B) else Color(0xFFC3C9D4)

    this.drawBehind {
        val cr = cornerRadius.toPx()
        val blur = 14f
        val offset = 7f

        drawIntoCanvas { canvas ->
            val darkPaint = Paint().apply {
                color = darkShadowCol
            }.asFrameworkPaint().apply {
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawRoundRect(
                offset, offset, size.width + offset, size.height + offset, cr, cr, darkPaint
            )

            val lightPaint = Paint().apply {
                color = lightShadowCol
            }.asFrameworkPaint().apply {
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawRoundRect(
                -offset, -offset, size.width - offset, size.height - offset, cr, cr, lightPaint
            )
        }
    }
    .clip(RoundedCornerShape(cornerRadius))
    .background(p.cardBg)
    .run {
        if (highlighted) {
            border(1.2.dp, accentBorderColor ?: p.glowBorder, RoundedCornerShape(cornerRadius))
        } else this
    }
}

// Inset Soft Shadow (Neumorphism Concave - Pressed State / Inputs)
fun Modifier.alanNeuInset(cornerRadius: Dp = 12.dp): Modifier = composed {
    val p = LocalAlanPalette.current
    val isDark = p.isDark
    val lightShadowCol = if (isDark) Color(0xFF2A303D) else Color(0xFFFFFFFF)
    val darkShadowCol = if (isDark) Color(0xFF12151B) else Color(0xFFC3C9D4)

    this.clip(RoundedCornerShape(cornerRadius))
        .background(p.subtleBg)
        .drawWithContent {
            drawContent()
            val cr = cornerRadius.toPx()
            val strokeW = 4.dp.toPx()
            val blur = 8f

            drawIntoCanvas { canvas ->
                canvas.save()
                val path = android.graphics.Path().apply {
                    addRoundRect(
                        0f, 0f, size.width, size.height,
                        floatArrayOf(cr, cr, cr, cr, cr, cr, cr, cr),
                        android.graphics.Path.Direction.CW
                    )
                }
                canvas.nativeCanvas.clipPath(path)

                // Inner Dark Shadow (Top Left)
                val darkPaint = Paint().apply {
                    color = darkShadowCol
                }.asFrameworkPaint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = strokeW
                    maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawRoundRect(
                    -strokeW / 2, -strokeW / 2, size.width + strokeW, size.height + strokeW, cr, cr, darkPaint
                )

                // Inner Light Shadow (Bottom Right)
                val lightPaint = Paint().apply {
                    color = lightShadowCol
                }.asFrameworkPaint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = strokeW
                    maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawRoundRect(
                    -strokeW, -strokeW, size.width + strokeW / 2, size.height + strokeW / 2, cr, cr, lightPaint
                )
                canvas.restore()
            }
        }
}

@Composable
fun AlanPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val p = LocalAlanPalette.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(200),
        label = "swThumb"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) p.accentGold else p.textMuted,
        animationSpec = tween(200),
        label = "swThumbCol"
    )

    LtrContent {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .alanNeuInset(cornerRadius = 12.dp)
                .clickable { onCheckedChange(!checked) }
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(18.dp)
                    // The thumb is an extruded pill inside a concave track
                    .alanGlassCard(cornerRadius = 9.dp)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = p.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .alanNeuInset(cornerRadius = 12.dp)
                .clickable { if (options.isNotEmpty()) dialogOpen = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedOption.label,
                    color = p.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
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
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .alanGlassCard(cornerRadius = 22.dp, highlighted = true)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = label.ifBlank { "انتخاب گزینه" },
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            options.forEach { opt ->
                                val isSel = opt.value == selectedValue
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (isSel) Modifier.alanNeuInset(11.dp) else Modifier.alanGlassCard(cornerRadius = 11.dp))
                                        .clickable {
                                            dialogOpen = false
                                            onSelect(opt.value)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
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
                                            modifier = Modifier.size(18.dp)
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