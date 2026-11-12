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
import androidx.compose.foundation.border
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
    val p = LocalAlanPalette.current
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
            .border(0.8.dp, p.cardBorder, flagShape)
    ) {
        val w = size.width
        val h = size.height

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

    val inf = rememberInfiniteTransition(label = "wideCosmicNeuOrb")
    val phaseRad by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isConnecting -> 2100
                    isConnected -> 4000
                    else -> 5400
                },
                easing = LinearEasing
            )
        ),
        label = "phaseRad"
    )
    val orbitDeg by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 5800 else 13500,
                easing = LinearEasing
            )
        ),
        label = "orbitDeg"
    )
    val breathScale by inf.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
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
            .height(220.dp)
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
            val halfW = size.width / 2f

            val lightShadowCol = if (isDark) Color(0xFF2F384B).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.98f)
            val darkShadowCol = if (isDark) Color(0xFF0C0F14).copy(alpha = 0.94f) else Color(0xFFB2C0D4).copy(alpha = 0.88f)

            // 1. Wide Elliptical & Tilted Planetary Tracks (filling horizontal space around the orb, NO circular cage!)
            val rxWide = (halfW * 0.88f).coerceAtLeast(minDim * 0.62f)
            val ryFlat = minDim * 0.24f
            val rxDiag = (halfW * 0.76f).coerceAtLeast(minDim * 0.56f)
            val ryDiag = minDim * 0.34f

            val trackStrokeColor = p.cardBorder.copy(alpha = if (isDark) 0.28f else 0.42f)

            // Horizontal wide ellipse
            drawOval(
                color = trackStrokeColor,
                topLeft = Offset(cx - rxWide, cy - ryFlat),
                size = Size(rxWide * 2f, ryFlat * 2f),
                style = Stroke(width = 1.dp.toPx())
            )
            // Tilted ellipse +19 deg
            rotate(degrees = 19f, pivot = Offset(cx, cy)) {
                drawOval(
                    color = trackStrokeColor.copy(alpha = if (isDark) 0.20f else 0.30f),
                    topLeft = Offset(cx - rxDiag, cy - ryDiag),
                    size = Size(rxDiag * 2f, ryDiag * 2f),
                    style = Stroke(width = 0.9.dp.toPx())
                )
            }
            // Tilted ellipse -19 deg
            rotate(degrees = -19f, pivot = Offset(cx, cy)) {
                drawOval(
                    color = trackStrokeColor.copy(alpha = if (isDark) 0.20f else 0.30f),
                    topLeft = Offset(cx - rxDiag, cy - ryDiag),
                    size = Size(rxDiag * 2f, ryDiag * 2f),
                    style = Stroke(width = 0.9.dp.toPx())
                )
            }

            // 2. 22 Tactile 3D Neumorphic Micro-Orbs gliding along the wide horizontal & tilted orbits
            val totalMicroOrbs = 22
            for (i in 0 until totalMicroOrbs) {
                val band = i % 3
                val speedMul = when (band) {
                    0 -> 1.0f
                    1 -> -0.75f
                    else -> 0.55f
                }
                val deg = (i * (360f / totalMicroOrbs)) + (orbitDeg * speedMul)
                val rad = deg * (PI / 180.0)

                val aRx = when (band) {
                    0 -> rxWide + sin(phaseRad + i) * 6.dp.toPx()
                    1 -> rxDiag + cos(phaseRad + i * 0.7f) * 8.dp.toPx()
                    else -> (halfW * 0.64f).coerceAtLeast(minDim * 0.48f) + sin(phaseRad * 1.3f + i) * 7.dp.toPx()
                }
                val bRy = when (band) {
                    0 -> ryFlat + cos(phaseRad + i) * 4.dp.toPx()
                    1 -> ryDiag
                    else -> minDim * 0.43f
                }
                val tiltDeg = when (band) {
                    0 -> 0.0
                    1 -> 19.0 * (PI / 180.0)
                    else -> -19.0 * (PI / 180.0)
                }

                val ex = (aRx * cos(rad)).toFloat()
                val ey = (bRy * sin(rad)).toFloat()
                val px = cx + (ex * cos(tiltDeg) - ey * sin(tiltDeg)).toFloat()
                val py = cy + (ex * sin(tiltDeg) + ey * cos(tiltDeg)).toFloat()

                val baseDotDp = when {
                    i % 5 == 0 -> 6.8.dp
                    i % 3 == 0 -> 5.0.dp
                    i % 2 == 0 -> 3.8.dp
                    else -> 2.8.dp
                }
                val pulseFactor = 0.84f + 0.28f * sin(phaseRad * 1.6f + i * 0.8f)
                val dotR = baseDotDp.toPx() * pulseFactor

                // 3D Neumorphic shadow & highlight for every micro-orb
                drawCircle(
                    color = darkShadowCol.copy(alpha = 0.65f),
                    radius = dotR,
                    center = Offset(px + 2.2.dp.toPx(), py + 2.2.dp.toPx())
                )
                drawCircle(
                    color = lightShadowCol.copy(alpha = 0.75f),
                    radius = dotR,
                    center = Offset(px - 1.5.dp.toPx(), py - 1.5.dp.toPx())
                )

                val orbCoreColor = when {
                    isConnected -> if (i % 3 == 0) p.emerald else if (i % 2 == 0) Color(0xFF34D399) else p.cardBg
                    isConnecting -> if (i % 2 == 0) p.accentGold else p.emerald
                    isError -> if (i % 2 == 0) p.danger else p.cardBg
                    else -> if (i % 3 == 0) p.accentGold.copy(alpha = 0.82f) else p.cardBg
                }
                val rimHighlight = if (isDark) Color(0xFF384259) else Color.White

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            rimHighlight,
                            orbCoreColor,
                            if (isDark) Color(0xFF151922) else Color(0xFFCFD9E8)
                        ),
                        center = Offset(px - dotR * 0.32f, py - dotR * 0.32f),
                        radius = dotR * 1.55f
                    ),
                    radius = dotR,
                    center = Offset(px, py)
                )
            }

            // 3. Large Free-Floating Organic Morphing Neumorphic Center Orb (Un-caged!)
            val baseBlobR = minDim * 0.385f * breathScale
            val morphAmp = when {
                isConnecting -> baseBlobR * 0.115f
                isConnected -> baseBlobR * 0.085f
                else -> baseBlobR * 0.068f
            }

            fun buildMorphPath(offsetX: Float, offsetY: Float, scale: Float = 1f): Path {
                val path = Path()
                val steps = 84
                for (s in 0..steps) {
                    val theta = (s.toFloat() / steps.toFloat()) * (2.0 * PI).toFloat()
                    val harmonic =
                        0.48f * sin(3f * theta + phaseRad) +
                        0.34f * cos(2f * theta - phaseRad * 1.35f) +
                        0.18f * sin(4f * theta + phaseRad * 0.85f)
                    val r = (baseBlobR * scale) + morphAmp * harmonic
                    val x = cx + offsetX + r * cos(theta)
                    val y = cy + offsetY + r * sin(theta)
                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                return path
            }

            val shadowOff = 7.dp.toPx()
            val outerMorphShadowDark = buildMorphPath(shadowOff, shadowOff, 1.01f)
            val outerMorphShadowLight = buildMorphPath(-shadowOff, -shadowOff, 1.01f)

            drawIntoCanvas { canvas ->
                val darkPaint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = darkShadowCol.toArgb()
                    maskFilter = BlurMaskFilter(26f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawCircle(cx + shadowOff, cy + shadowOff, baseBlobR * 0.98f, darkPaint)

                val lightPaint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = (if (isConnected) p.emerald.copy(alpha = 0.38f) else lightShadowCol).toArgb()
                    maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawCircle(cx - shadowOff, cy - shadowOff, baseBlobR * 0.98f, lightPaint)
            }

            drawPath(
                path = outerMorphShadowDark,
                color = darkShadowCol.copy(alpha = 0.36f)
            )
            drawPath(
                path = outerMorphShadowLight,
                color = lightShadowCol.copy(alpha = 0.42f)
            )

            val mainBlobPath = buildMorphPath(0f, 0f, 1.0f)
            val blobBrush = when {
                isConnected -> Brush.linearGradient(
                    colors = listOf(Color(0xFF4ADE80), Color(0xFF10B981), Color(0xFF047857)),
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
                    colors = listOf(Color(0xFF2A3244), Color(0xFF1E232E), Color(0xFF151922)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
                else -> Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFE8EEF7), Color(0xFFD0DCEC)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
            }
            drawPath(path = mainBlobPath, brush = blobBrush)

            // Inner Sculpted Morphing Cushion (adds deep tactile 3D softness without any hard border)
            val innerCushionPath = buildMorphPath(0f, 0f, 0.82f)
            val cushionBrush = when {
                isConnected -> Brush.linearGradient(
                    colors = listOf(Color(0xFF059669).copy(alpha = 0.35f), Color(0xFF6EE7B7).copy(alpha = 0.25f)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
                isDark -> Brush.linearGradient(
                    colors = listOf(Color(0xFF171B24).copy(alpha = 0.55f), Color(0xFF283042).copy(alpha = 0.45f)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
                else -> Brush.linearGradient(
                    colors = listOf(Color(0xFFD5E0F0).copy(alpha = 0.50f), Color.White.copy(alpha = 0.70f)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
            }
            drawPath(path = innerCushionPath, brush = cushionBrush)
        }

        val contentColor = when {
            isConnected || isConnecting -> Color(0xFF091410)
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
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = centerLabel,
                color = contentColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1
            )
            if (isConnecting && progressPercent > 0) {
                LtrContent {
                    Text(
                        text = "${progressPercent.coerceIn(1, 99)}%",
                        color = contentColor.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Monospace,
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
    val destAccent = destAccentLong.toThemeAwareAccent(p.accentGold)

    val inf = rememberInfiniteTransition(label = "pktBridge")
    val photonFrac by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnecting) 1000 else 2100, easing = LinearEasing)
        ),
        label = "photonFrac"
    )

    val capsuleColor = when {
        isConnected -> p.emerald
        isConnecting -> p.accentGold
        else -> destAccent
    }
    val capsuleText = when {
        isConnected && pingMs > 0 -> "TUN • $pingMs ms"
        isConnected -> "TUN • ONLINE"
        isConnecting -> "ROUTING..."
        else -> "DPI READY"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alanGlassCard(
                highlighted = isConnected,
                cornerRadius = 18.dp,
                accentBorderColor = p.emerald.copy(alpha = 0.6f)
            )
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Origin Pod (Iran)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .alanNeuInset(cornerRadius = 12.dp)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) {
                FlagStripeBadge(CountryPaletteRegistry.IRAN_SPEC.stripeColors, "IR", 26.dp, 17.dp)
                Spacer(Modifier.width(7.dp))
                Column {
                    Text(
                        text = "مبدا: ${originCountryName.ifBlank { "ایران" }}",
                        color = p.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                    LtrContent {
                        Text(
                            text = "IR • LOCAL",
                            color = p.emerald,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Animated Packet Bridge
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .padding(horizontal = 6.dp)
            ) {
                val w = size.width
                val cy = size.height / 2f
                if (w > 8f) {
                    drawLine(
                        color = p.cardBorder,
                        start = Offset(0f, cy),
                        end = Offset(w, cy),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    for (k in 0..1) {
                        val f = (photonFrac + k * 0.5f) % 1f
                        val xPos = w * (1f - f)
                        drawCircle(
                            color = capsuleColor,
                            radius = 2.6.dp.toPx(),
                            center = Offset(xPos, cy)
                        )
                    }
                }
            }

            // Destination Pod
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .alanNeuInset(cornerRadius = 12.dp)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "مقصد: ${destCountryName.ifBlank { "خودکار" }}",
                        color = p.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    LtrContent {
                        Text(
                            text = if (isConnected && egressIp.isNotBlank() && egressIp != "—") {
                                egressIp
                            } else {
                                "${destCountryCode.uppercase(Locale.US)} • REMOTE"
                            },
                            color = destAccent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(7.dp))
                FlagStripeBadge(destStripeColors, destCountryCode, 26.dp, 17.dp)
            }
        }

        // Bottom Status Strip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isConnected) "مسیر رمزنگاری‌شده دوطرفه فعال" else "آماده برقراری تونل ضد DPI",
                color = p.textSecondary,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .alanNeuInset(cornerRadius = 14.dp)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Icon(Icons.Outlined.Lock, null, tint = capsuleColor, modifier = Modifier.size(10.dp))
                LtrContent {
                    Text(
                        text = capsuleText,
                        color = capsuleColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.5.sp
                    )
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
            .height(22.dp)
    ) {
        if (size.width <= 4f || size.height <= 4f) return@Canvas
        val w = size.width
        val h = size.height
        val baselineY = h - 2.5f

        if (!isConnected || samples.size < 2 || samples.all { it <= 0.01f }) {
            val idlePath = Path()
            val segments = 32
            for (i in 0..segments) {
                val frac = i.toFloat() / segments.toFloat()
                val x = frac * w
                val amp = if (isConnected) 2.8.dp.toPx() else 1.5.dp.toPx()
                val y = (h * 0.60f) + sin(frac * 4.5f * PI.toFloat() - wavePhase) * amp
                if (i == 0) idlePath.moveTo(x, y) else idlePath.lineTo(x, y)
            }
            drawPath(
                path = idlePath,
                color = lineColor.copy(alpha = if (isConnected) 0.65f else 0.40f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
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
            val y = baselineY - norm * (h - 5f)
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
                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color = lineColor, radius = 2.8.dp.toPx(), center = lastPt)
    }
}