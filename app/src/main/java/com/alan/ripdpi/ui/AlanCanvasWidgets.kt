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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

    val flagShape = RoundedCornerShape(4.dp)
    Canvas(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(flagShape)
    ) {
        val w = size.width
        val h = size.height
        val h3 = h / 3f
        drawRect(color = resolved[0], topLeft = Offset(0f, 0f), size = Size(w, h3 + 0.5f))
        drawRect(color = resolved[1], topLeft = Offset(0f, h3), size = Size(w, h3 + 0.5f))
        drawRect(color = resolved[2], topLeft = Offset(0f, h3 * 2f), size = Size(w, h3))
    }
}

@Composable
fun StatusPillBadge(status: String) {
    val p = LocalAlanPalette.current
    val up = status.uppercase(Locale.US)
    val (label, col) = when (up) {
        "ONLINE", "CONNECTED" -> "متصل" to p.emerald
        "VERIFIED", "HEALTHY" -> "تایید شده" to p.emerald
        "PROBING", "TESTING" -> "در حال تست" to p.accentGold
        "DEAD", "ERROR", "FAILED" -> "بدون پینگ" to p.danger
        else -> "تست‌نشده" to p.textMuted
    }

    Box(
        modifier = Modifier
            .alanNeuInset(cornerRadius = 8.dp)
            .padding(horizontal = 9.dp, vertical = 3.dp)
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
    val isDark = p.isDark
    val isError = uiPhase.equals("ERROR", ignoreCase = true)

    val inf = rememberInfiniteTransition(label = "wideCosmicBubbleField")
    val masterClock by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isConnecting -> 4000
                    isConnected -> 6800
                    else -> 9200
                },
                easing = LinearEasing
            )
        ),
        label = "masterClock"
    )
    val morphPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isConnecting -> 2100
                    isConnected -> 3900
                    else -> 5400
                },
                easing = LinearEasing
            )
        ),
        label = "morphPhase"
    )
    val breathScale by inf.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val centerLabel = when {
        isConnecting -> "در حال برقراری"
        isConnected -> "قطع اتصال"
        isError -> "تلاش مجدد"
        else -> "اتصال"
    }

    val appliedModifier = if (modifier === Modifier) {
        Modifier
            .fillMaxWidth()
            .height(242.dp)
    } else {
        modifier
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = appliedModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.minDimension <= 12f) return@Canvas
            val cx = size.width / 2f
            val cy = size.height / 2f
            val minDim = size.minDimension

            // Wide reach across the entire canvas so bubbles spread far & wide
            val maxReachX = (size.width * 0.49f).coerceAtLeast(minDim * 0.65f)
            val maxReachY = (size.height * 0.48f).coerceAtLeast(minDim * 0.48f)

            val lightShadowCol = if (isDark) Color(0xFF30394D).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.98f)
            val darkShadowCol = if (isDark) Color(0xFF0A0D12).copy(alpha = 0.95f) else Color(0xFFB0BED4).copy(alpha = 0.88f)

            // 1. 36 Wide-Spread Floating 3D Neumorphic Bubbles with Real Bubble-Pop Physics
            val bubbleCount = 36
            for (idx in 0 until bubbleCount) {
                val goldenDeg = (idx * 137.508f) % 360f
                val startFrac = 0.34f + (idx % 5) * 0.045f
                val endFrac = 0.72f + (idx % 7) * 0.045f
                val baseDp = 3.0f + (idx % 7) * 1.35f
                val speed = 0.55f + (idx % 6) * 0.16f
                val phaseOff = (idx * 0.17f) % 1f
                val driftDeg = 11f + (idx % 5) * 5f

                val rawLife = (masterClock * speed + phaseOff) % 1f
                val angleRad = (goldenDeg + sin(morphPhase + idx * 0.7f) * driftDeg) * (PI / 180.0)
                val radialProg = startFrac + (endFrac - startFrac) * rawLife

                val bx = cx + (cos(angleRad) * maxReachX * radialProg).toFloat()
                val by = cy + (sin(angleRad) * maxReachY * radialProg).toFloat()

                val bubbleTint = when {
                    isConnected -> if (idx % 3 == 0) p.emerald else if (idx % 2 == 0) Color(0xFF34D399) else p.cardBg
                    isConnecting -> if (idx % 2 == 0) p.accentGold else p.emerald
                    isError -> if (idx % 2 == 0) p.danger else p.cardBg
                    else -> if (idx % 4 == 0) p.accentGold.copy(alpha = 0.80f) else p.cardBg
                }

                if (rawLife < 0.82f) {
                    val norm = (rawLife / 0.82f).coerceIn(0f, 1f)
                    val rPx = (baseDp.dp.toPx() * (0.42f + 0.75f * sin(norm * (PI / 2.0)).toFloat())).coerceAtLeast(2.2f)
                    val alpha = (0.28f + 0.72f * sin(norm * PI).toFloat()).coerceIn(0f, 1f)

                    drawCircle(
                        color = darkShadowCol.copy(alpha = 0.58f * alpha),
                        radius = rPx,
                        center = Offset(bx + 2.2.dp.toPx(), by + 2.2.dp.toPx())
                    )
                    drawCircle(
                        color = lightShadowCol.copy(alpha = 0.68f * alpha),
                        radius = rPx,
                        center = Offset(bx - 1.5.dp.toPx(), by - 1.5.dp.toPx())
                    )

                    val specCol = if (isDark) Color(0xFF465470) else Color.White
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                specCol.copy(alpha = alpha),
                                bubbleTint.copy(alpha = alpha),
                                (if (isDark) Color(0xFF141821) else Color(0xFFCFD9E8)).copy(alpha = alpha)
                            ),
                            center = Offset(bx - rPx * 0.35f, by - rPx * 0.35f),
                            radius = rPx * 1.6f
                        ),
                        radius = rPx,
                        center = Offset(bx, by)
                    )
                } else {
                    // Bubble POP Burst Phase (expanding ring + 6 micro droplets)
                    val popFrac = ((rawLife - 0.82f) / 0.18f).coerceIn(0f, 1f)
                    val fade = (1f - popFrac).coerceIn(0f, 1f)
                    val baseR = baseDp.dp.toPx() * 1.18f
                    val popCol = when {
                        isConnected -> p.emerald
                        isError -> p.danger
                        else -> p.accentGold
                    }

                    drawCircle(
                        color = popCol.copy(alpha = 0.70f * fade),
                        radius = baseR * (1.0f + 1.15f * popFrac),
                        center = Offset(bx, by),
                        style = Stroke(width = (1.6f * fade).dp.toPx().coerceAtLeast(0.5f))
                    )
                    for (d in 0 until 6) {
                        val dropAng = (d * 60f + goldenDeg) * (PI / 180.0)
                        val dropDist = baseR * (0.75f + 1.65f * popFrac)
                        drawCircle(
                            color = popCol.copy(alpha = 0.85f * fade),
                            radius = (1.9f * fade).dp.toPx().coerceAtLeast(0.5f),
                            center = Offset(
                                bx + (cos(dropAng) * dropDist).toFloat(),
                                by + (sin(dropAng) * dropDist).toFloat()
                            )
                        )
                    }
                }
            }

            // 2. Seamless One-Piece Morphing Neumorphic Center Orb (No inner border, no cage!)
            val baseBlobR = (minDim * 0.32f).coerceIn(58.dp.toPx(), 78.dp.toPx()) * breathScale
            val morphAmp = when {
                isConnecting -> baseBlobR * 0.115f
                isConnected -> baseBlobR * 0.085f
                else -> baseBlobR * 0.066f
            }

            val blobPath = Path()
            val steps = 90
            for (s in 0..steps) {
                val theta = (s.toFloat() / steps.toFloat()) * (2.0 * PI).toFloat()
                val harmonic =
                    0.48f * sin(3f * theta + morphPhase) +
                    0.34f * cos(2f * theta - morphPhase * 1.35f) +
                    0.18f * sin(4f * theta + morphPhase * 0.85f)
                val r = baseBlobR + morphAmp * harmonic
                val x = cx + r * cos(theta)
                val y = cy + r * sin(theta)
                if (s == 0) blobPath.moveTo(x, y) else blobPath.lineTo(x, y)
            }
            blobPath.close()

            val shadowOff = 7.5.dp.toPx()
            drawIntoCanvas { canvas ->
                val dp = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = darkShadowCol.toArgb()
                    maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawCircle(cx + shadowOff, cy + shadowOff, baseBlobR * 0.96f, dp)

                val lp = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = (if (isConnected) p.emerald.copy(alpha = 0.36f) else lightShadowCol).toArgb()
                    maskFilter = BlurMaskFilter(26f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawCircle(cx - shadowOff, cy - shadowOff, baseBlobR * 0.96f, lp)
            }

            val unifiedBrush = when {
                isConnected -> Brush.linearGradient(
                    colors = listOf(Color(0xFF4ADE80), Color(0xFF10B981), Color(0xFF059669)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
                isConnecting -> Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE047), p.accentGold, Color(0xFFD97706)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
                isError -> Brush.linearGradient(
                    colors = listOf(Color(0xFFF87171), p.danger, Color(0xFFB91C1C)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
                isDark -> Brush.linearGradient(
                    colors = listOf(Color(0xFF283042), Color(0xFF1E232E), Color(0xFF161A23)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
                else -> Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFE6ECF5), Color(0xFFD3DFEE)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
            }
            drawPath(path = blobPath, brush = unifiedBrush)
        }

        val contentColor = when {
            isConnected || isConnecting -> Color(0xFF08130E)
            isError -> Color.White
            else -> p.textPrimary
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = centerLabel,
                tint = if (!isConnected && !isConnecting && !isError) p.accentGold else contentColor,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = centerLabel,
                color = contentColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.5.sp,
                maxLines = 1
            )
            if (isConnecting && progressPercent > 0) {
                LtrContent {
                    Text(
                        text = "${progressPercent.coerceIn(1, 99)}%",
                        color = contentColor.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                }
            }
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
    val capsuleColor = if (isConnected) p.emerald else if (isConnecting) p.accentGold else p.textSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(highlighted = isConnected, cornerRadius = 16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "مبدا: ${originCountryName.ifBlank { "ایران" }}",
            color = p.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.Lock, null, tint = capsuleColor, modifier = Modifier.size(12.dp))
            Text(
                text = if (isConnected && pingMs > 0) "$pingMs ms" else if (isConnected) "متصل" else "آماده",
                color = capsuleColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.5.sp
            )
        }
        Text(
            text = "مقصد: ${destCountryName.ifBlank { "خودکار" }}",
            color = p.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
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
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "wavePhase"
    )
    Canvas(modifier = modifier.fillMaxWidth().height(20.dp)) {
        val w = size.width
        val h = size.height
        val path = Path()
        for (i in 0..28) {
            val f = i / 28f
            val x = f * w
            val y = (h * 0.55f) + sin(f * 4f * PI.toFloat() - wavePhase) * (if (isConnected) 3.dp.toPx() else 1.5.dp.toPx())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor.copy(alpha = 0.65f), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
