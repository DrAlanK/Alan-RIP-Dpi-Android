package com.alan.ripdpi.ui

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alan.ripdpi.model.CountryPaletteRegistry
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FlagStripeBadge(
    stripeColors: List<Long>,
    countryCode: String,
    width: Dp = 26.dp,
    height: Dp = 17.dp
) {
    val iso = countryCode.trim().uppercase(Locale.US)
    val resolved = remember(stripeColors, iso) {
        val base = if (stripeColors.size >= 3) {
            stripeColors.take(3).map { Color(if ((it ushr 24) == 0L) (it or 0xFF000000L).toInt() else it.toInt()) }
        } else {
            val spec = CountryPaletteRegistry.resolveByCode(iso)
            spec.stripeColors.take(3).map { Color(it.toInt()) }
        }
        if (base.size >= 3) base else listOf(Color(0xFF1E293B), Color(0xFFF5C451), Color(0xFF10B981))
    }

    Canvas(
        modifier = Modifier
            .width(width)
            .height(height)
            .alanGlassCard(cornerRadius = 4.dp)
    ) {
        val w = size.width
        val h = size.height
        val clipPath = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())))
        }

        drawIntoCanvas {
            it.clipPath(clipPath)
        }

        when (iso) {
            "FR", "IT", "BE", "IE", "RO" -> {
                val w3 = w / 3f
                drawRect(color = resolved[0], topLeft = Offset(0f, 0f), size = Size(w3 + 0.5f, h))
                drawRect(color = resolved[1], topLeft = Offset(w3, 0f), size = Size(w3 + 0.5f, h))
                drawRect(color = resolved[2], topLeft = Offset(w3 * 2f, 0f), size = Size(w3, h))
            }
            "ES" -> {
                drawRect(color = Color(0xFFAA151B), topLeft = Offset(0f, 0f), size = Size(w, h))
                drawRect(color = Color(0xFFF1BF00), topLeft = Offset(0f, h * 0.25f), size = Size(w, h * 0.50f))
                drawCircle(color = Color(0xFFAA151B), radius = h * 0.11f, center = Offset(w * 0.30f, h * 0.50f))
            }
            "IR" -> {
                val h3 = h / 3f
                drawRect(color = Color(0xFF239F40), topLeft = Offset(0f, 0f), size = Size(w, h3 + 0.5f))
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, h3), size = Size(w, h3 + 0.5f))
                drawRect(color = Color(0xFFDA0000), topLeft = Offset(0f, h3 * 2f), size = Size(w, h3))
                drawCircle(color = Color(0xFFDA0000), radius = h * 0.10f, center = Offset(w * 0.50f, h * 0.50f))
            }
            "US" -> {
                val stripeH = h / 7f
                for (s in 0 until 7) {
                    drawRect(
                        color = if (s % 2 == 0) Color(0xFFDC2626) else Color(0xFFF8FAFC),
                        topLeft = Offset(0f, s * stripeH),
                        size = Size(w, stripeH + 0.4f)
                    )
                }
                drawRect(color = Color(0xFF1E3A8A), topLeft = Offset(0f, 0f), size = Size(w * 0.46f, h * 0.55f))
            }
            "GB", "UK" -> {
                drawRect(color = Color(0xFF012169), topLeft = Offset(0f, 0f), size = Size(w, h))
                drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.18f)
                drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.18f)
                drawRect(color = Color.White, topLeft = Offset(0f, h * 0.36f), size = Size(w, h * 0.28f))
                drawRect(color = Color.White, topLeft = Offset(w * 0.40f, 0f), size = Size(w * 0.20f, h))
                drawRect(color = Color(0xFFC8102E), topLeft = Offset(0f, h * 0.42f), size = Size(w, h * 0.16f))
                drawRect(color = Color(0xFFC8102E), topLeft = Offset(w * 0.44f, 0f), size = Size(w * 0.12f, h))
            }
            "SE", "FI" -> {
                val bgCol = if (iso == "SE") Color(0xFF006AA7) else Color(0xFFF8FAFC)
                val crossCol = if (iso == "SE") Color(0xFFFECC02) else Color(0xFF003580)
                drawRect(color = bgCol, topLeft = Offset(0f, 0f), size = Size(w, h))
                drawRect(color = crossCol, topLeft = Offset(0f, h * 0.38f), size = Size(w, h * 0.24f))
                drawRect(color = crossCol, topLeft = Offset(w * 0.30f, 0f), size = Size(w * 0.16f, h))
            }
            "TR" -> {
                drawRect(color = Color(0xFFE30A17), topLeft = Offset(0f, 0f), size = Size(w, h))
                drawCircle(color = Color.White, radius = h * 0.24f, center = Offset(w * 0.42f, h * 0.50f))
                drawCircle(color = Color(0xFFE30A17), radius = h * 0.19f, center = Offset(w * 0.47f, h * 0.50f))
            }
            "JP" -> {
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, 0f), size = Size(w, h))
                drawCircle(color = Color(0xFFBC002D), radius = h * 0.27f, center = Offset(w * 0.50f, h * 0.50f))
            }
            else -> {
                val h3 = h / 3f
                drawRect(color = resolved[0], topLeft = Offset(0f, 0f), size = Size(w, h3 + 0.5f))
                drawRect(color = resolved[1], topLeft = Offset(0f, h3), size = Size(w, h3 + 0.5f))
                drawRect(color = resolved[2], topLeft = Offset(0f, h3 * 2f), size = Size(w, h3))
            }
        }
    }
}

@Composable
fun StatusPillBadge(status: String) {
    val p = LocalAlanPalette.current
    val up = status.uppercase(Locale.US)
    val (label, col) = when (up) {
        "ONLINE", "CONNECTED" -> "متصل" to p.emerald
        "VERIFIED" -> "تایید شده" to p.accentGold
        "PROBING", "TESTING" -> "در حال بررسی" to p.accentGold
        "DEAD", "ERROR", "FAILED" -> "خطا" to p.danger
        else -> "آماده" to p.textMuted
    }

    Box(
        modifier = Modifier
            .alanNeuInset(cornerRadius = 8.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = col,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun CenterPowerOrbButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    uiPhase: String,
    progressPercent: Int,
    accentColorLong: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalAlanPalette.current
    val isDark = (p.rootBg.red * 0.299f + p.rootBg.green * 0.587f + p.rootBg.blue * 0.114f) < 0.5f
    val lightShadow = if (isDark) androidx.compose.ui.graphics.Color(0xFF2E3646) else androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val darkShadow = if (isDark) androidx.compose.ui.graphics.Color(0xFF0F1218) else androidx.compose.ui.graphics.Color(0xFFB8C3D4)

    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "neuOrbSpin")
    val sweepAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "orbAngle"
    )
    val breathe by inf.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1400),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "orbBreathe"
    )
    val animPct by androidx.compose.animation.core.animateFloatAsState(
        targetValue = (progressPercent.coerceIn(0, 100)) / 100f,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "orbPct"
    )

    val activeTint = when {
        isConnected -> p.emerald
        uiPhase.equals("ERROR", ignoreCase = true) -> p.danger
        isConnecting -> p.accentGold
        accentColorLong != 0L -> androidx.compose.ui.graphics.Color(accentColorLong)
        else -> p.accentGold
    }

    androidx.compose.foundation.layout.Box(
        contentAlignment = androidx.compose.ui.Alignment.Center,
        modifier = androidx.compose.ui.Modifier
            .size(190.dp)
            .then(modifier)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick)
    ) {
        androidx.compose.foundation.Canvas(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = androidx.compose.ui.geometry.Offset(cx, cy)
            val maxR = size.minDimension / 2f

            // 1. Extruded outer Neumorphic circular chassis shadows
            val shift = maxR * 0.065f
            drawCircle(
                color = darkShadow.copy(alpha = if (isDark) 0.68f else 0.52f),
                radius = maxR * 0.88f,
                center = androidx.compose.ui.geometry.Offset(cx + shift, cy + shift)
            )
            drawCircle(
                color = lightShadow.copy(alpha = if (isDark) 0.48f else 0.92f),
                radius = maxR * 0.88f,
                center = androidx.compose.ui.geometry.Offset(cx - shift * 0.85f, cy - shift * 0.85f)
            )

            // 2. Main matte Neumorphic platter
            drawCircle(
                color = p.cardBg,
                radius = maxR * 0.87f,
                center = center
            )
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        lightShadow.copy(alpha = if (isDark) 0.65f else 0.95f),
                        darkShadow.copy(alpha = if (isDark) 0.70f else 0.48f)
                    ),
                    start = androidx.compose.ui.geometry.Offset(cx - maxR, cy - maxR),
                    end = androidx.compose.ui.geometry.Offset(cx + maxR, cy + maxR)
                ),
                radius = maxR * 0.87f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
            )

            // 3. Recessed concave LED groove ring
            val grooveR = maxR * 0.73f
            val grooveW = (maxR * 0.105f).coerceAtLeast(6.dp.toPx())
            drawCircle(
                color = p.subtleBg,
                radius = grooveR,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = grooveW)
            )

            val arcTopLeft = androidx.compose.ui.geometry.Offset(cx - grooveR, cy - grooveR)
            val arcSize = androidx.compose.ui.geometry.Size(grooveR * 2f, grooveR * 2f)

            if (isConnected) {
                drawArc(
                    color = activeTint.copy(alpha = 0.25f * breathe),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = grooveW * 1.45f)
                )
                drawArc(
                    color = activeTint,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = grooveW * 0.68f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            } else if (isConnecting) {
                val pctSweep = (animPct * 300f).coerceAtLeast(68f)
                drawArc(
                    color = activeTint,
                    startAngle = sweepAngle - 90f,
                    sweepAngle = pctSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = grooveW * 0.72f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            } else {
                drawArc(
                    color = activeTint.copy(alpha = 0.42f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }

            // 4. Inner tactile convex push-button cap (pressed slightly inward when connected)
            val btnR = maxR * 0.56f
            val innerShift = if (isConnected) -shift * 0.35f else shift * 0.55f
            drawCircle(
                color = darkShadow.copy(alpha = if (isDark) 0.62f else 0.45f),
                radius = btnR,
                center = androidx.compose.ui.geometry.Offset(cx + innerShift, cy + innerShift)
            )
            drawCircle(
                color = lightShadow.copy(alpha = if (isDark) 0.42f else 0.88f),
                radius = btnR,
                center = androidx.compose.ui.geometry.Offset(cx - innerShift, cy - innerShift)
            )
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = if (isConnected) {
                        listOf(p.subtleBg, p.cardBg)
                    } else {
                        listOf(
                            if (isDark) androidx.compose.ui.graphics.Color(0xFF252B37) else androidx.compose.ui.graphics.Color(0xFFF0F5FC),
                            p.cardBg
                        )
                    },
                    start = androidx.compose.ui.geometry.Offset(cx - btnR, cy - btnR),
                    end = androidx.compose.ui.geometry.Offset(cx + btnR, cy + btnR)
                ),
                radius = btnR,
                center = center
            )

            // 5. Crisp Power Glyph at center
            val glyphR = btnR * 0.34f
            val strokeGlyph = 3.2.dp.toPx()
            drawArc(
                color = activeTint,
                startAngle = -52f,
                sweepAngle = 284f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - glyphR, cy - glyphR),
                size = androidx.compose.ui.geometry.Size(glyphR * 2f, glyphR * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeGlyph,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
            drawLine(
                color = activeTint,
                start = androidx.compose.ui.geometry.Offset(cx, cy - glyphR * 1.18f),
                end = androidx.compose.ui.geometry.Offset(cx, cy - glyphR * 0.12f),
                strokeWidth = strokeGlyph,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun JustifiedCountryFlagsBar(
    originCountryName: String,
    destCountryCode: String,
    destCountryName: String,
    destFlagEmoji: String,
    destStripeColors: List<Long>,
    destAccentLong: Long,
    egressIp: String,
    pingMs: Int,
    isConnected: Boolean,
    isConnecting: Boolean
) {
    val p = LocalAlanPalette.current
    val destAccent = remember(destAccentLong, p.accentGold) { destAccentLong.toAdaptiveColor(p.accentGold) }

    val capsuleColor = when {
        isConnected -> p.emerald
        isConnecting -> p.accentGold
        else -> destAccent
    }
    val capsuleText = when {
        isConnected && pingMs > 0 -> "ENCRYPTED • $pingMs ms"
        isConnected -> "ENCRYPTED • TUN"
        isConnecting -> "ROUTING..."
        else -> "READY • DPI"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(cornerRadius = 18.dp)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Origin Pod (IRAN) - Concave Neumorphic Inset
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .alanNeuInset(cornerRadius = 13.dp)
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    FlagStripeBadge(CountryPaletteRegistry.IRAN_SPEC.stripeColors, "IR", 32.dp, 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "مبدا: ${originCountryName.ifBlank { "ایران" }}",
                            color = p.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                        LtrContent {
                            Text("IR • LOCAL", color = p.emerald, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Laser path bridging the two inset pods
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 8.dp)
                        .background(p.textMuted.copy(alpha = 0.2f))
                )

                // Destination Pod - Concave Neumorphic Inset
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .alanNeuInset(cornerRadius = 13.dp)
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "مقصد: ${destCountryName.ifBlank { "خودکار" }}",
                            color = p.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.End
                        )
                        LtrContent {
                            Text(
                                text = if (isConnected && egressIp.isNotBlank() && egressIp != "—") egressIp else "${destCountryCode.uppercase(Locale.US)} • REMOTE",
                                color = destAccent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FlagStripeBadge(destStripeColors, destCountryCode, 32.dp, 20.dp)
                }
            }

            // Bottom Capsule
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isConnected) "مسیر رمزنگاری‌شده دوطرفه فعال" else "آماده برقراری تونل ضد DPI",
                    color = p.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .alanNeuInset(cornerRadius = 20.dp)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = capsuleColor, modifier = Modifier.size(12.dp))
                    LtrContent {
                        Text(capsuleText, color = capsuleColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveSparklineCanvas(
    samples: List<Float>,
    isConnected: Boolean,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "waveOsc")
    val wavePhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing)
        ),
        label = "wavePhase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
    ) {
        if (size.width <= 4f || size.height <= 4f) return@Canvas
        val w = size.width
        val h = size.height
        val baselineY = h - 3f

        if (!isConnected || samples.size < 2 || samples.all { it <= 0.01f }) {
            val idlePath = Path()
            val segments = 32
            for (i in 0..segments) {
                val frac = i.toFloat() / segments.toFloat()
                val x = frac * w
                val amp = if (isConnected) 3.2.dp.toPx() else 1.8.dp.toPx()
                val y = (h * 0.62f) + sin(frac * 4.5f * PI.toFloat() - wavePhase) * amp
                if (i == 0) idlePath.moveTo(x, y) else idlePath.lineTo(x, y)
            }
            drawPath(
                path = idlePath,
                color = lineColor.copy(alpha = if (isConnected) 0.55f else 0.32f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            val dotFrac = (wavePhase / (2f * PI.toFloat())).coerceIn(0f, 1f)
            val dotX = dotFrac * w
            val dotY = (h * 0.62f) + sin(dotFrac * 4.5f * PI.toFloat() - wavePhase) * (if (isConnected) 3.2.dp.toPx() else 1.8.dp.toPx())
            drawCircle(color = lineColor.copy(alpha = 0.8f), radius = 2.6.dp.toPx(), center = Offset(dotX, dotY))
            return@Canvas
        }

        val maxVal = (samples.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = w / (samples.size - 1).coerceAtLeast(1)
        val linePath = Path()
        val fillPath = Path()
        var lastPt = Offset(0f, baselineY)

        samples.forEachIndexed { idx, v ->
            val x = idx * stepX
            val norm = (v / maxVal).coerceIn(0f, 1f)
            val y = baselineY - norm * (h - 6f)
            lastPt = Offset(x, y)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, baselineY)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(lastPt.x, baselineY)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color = lineColor, radius = 3.2.dp.toPx(), center = lastPt)
    }
}