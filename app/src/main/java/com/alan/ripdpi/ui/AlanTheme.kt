package com.alan.ripdpi.ui

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
import androidx.compose.ui.graphics.Color
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
    rootBg = Color(0xFF070A10),
    surfaceBg = Color(0xFF0D111A),
    cardBg = Color(0xFF111723),
    subtleBg = Color(0xFF0A0E16),
    cardBorder = Color(0xFF1E2738),
    glowBorder = Color(0x88F5C451),
    accentGold = Color(0xFFF5C451),
    accentAmber = Color(0xFFE59A1A),
    accentSoftBg = Color(0x24F5C451),
    onAccent = Color(0xFF090C12),
    emerald = Color(0xFF10B981),
    cyan = Color(0xFF38BDF8),
    danger = Color(0xFFEF4444),
    textPrimary = Color(0xFFF3F6FC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF7B89A1)
)

private val LightAlanPalette = AlanPalette(
    isDark = false,
    rootBg = Color(0xFFF4F6FB),
    surfaceBg = Color(0xFFFFFFFF),
    cardBg = Color(0xFFFFFFFF),
    subtleBg = Color(0xFFEEF2F9),
    cardBorder = Color(0xFFD8E1EE),
    glowBorder = Color(0x99D99B16),
    accentGold = Color(0xFFC98A09),
    accentAmber = Color(0xFFB47806),
    accentSoftBg = Color(0x22EAB308),
    onAccent = Color(0xFFFFFFFF),
    emerald = Color(0xFF059669),
    cyan = Color(0xFF0284C7),
    danger = Color(0xFFDC2626),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B)
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

fun Modifier.alanGlassCard(
    highlighted: Boolean = false,
    cornerRadius: Dp = 16.dp,
    accentBorderColor: Color? = null
): Modifier = composed {
    val p = LocalAlanPalette.current
    val bg = when {
        highlighted && p.isDark -> Color(0xFF151D2C)
        highlighted && !p.isDark -> Color(0xFFFFFBEB)
        else -> p.cardBg
    }
    val borderCol = accentBorderColor ?: if (highlighted) p.glowBorder else p.cardBorder
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(bg)
        .border(
            width = if (highlighted) 1.3.dp else 1.dp,
            color = borderCol,
            shape = RoundedCornerShape(cornerRadius)
        )
}

@Composable
fun AlanPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val p = LocalAlanPalette.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) p.accentGold else p.subtleBg,
        animationSpec = tween(180),
        label = "swTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(180),
        label = "swThumb"
    )

    LtrContent {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(trackColor)
                .border(1.dp, if (checked) p.accentGold else p.cardBorder, RoundedCornerShape(12.dp))
                .clickable { onCheckedChange(!checked) }
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (checked) p.onAccent else p.textMuted)
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

    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = p.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.subtleBg)
                .border(1.dp, p.cardBorder, RoundedCornerShape(12.dp))
                .clickable { if (options.isNotEmpty()) dialogOpen = true }
                .padding(horizontal = 12.dp, vertical = 11.dp)
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
                    shape = RoundedCornerShape(18.dp),
                    color = p.surfaceBg,
                    border = BorderStroke(1.2.dp, p.glowBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = label.ifBlank { "انتخاب گزینه" },
                            color = p.accentGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            options.forEach { opt ->
                                val isSel = opt.value == selectedValue
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (isSel) p.accentSoftBg else p.subtleBg)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSel) p.accentGold else p.cardBorder,
                                            shape = RoundedCornerShape(11.dp)
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
