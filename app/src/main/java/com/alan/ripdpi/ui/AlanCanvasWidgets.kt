package com.alan.ripdpi.ui

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
        "ONLINE", "CONNECTED" -> tr("متصل", "Online") to p.emerald
        "VERIFIED", "HEALTHY" -> tr("تایید شده", "Verified") to p.emerald
        "PROBING", "TESTING" -> tr("بررسی...", "Testing") to p.accentGold
        "DEAD", "ERROR", "FAILED" -> tr("خطا", "Dead") to p.danger
        else -> tr("آماده", "Ready") to p.textMuted
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

    val ringColor = when {
        isConnected -> p.emerald
        isConnecting -> p.accentGold
        isError -> p.danger
        else -> if (isDark) p.accentGold else Color(0xFFD97706)
    }

    val inf = rememberInfiniteTransition(label = "neuOrb")
    val spinDeg by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isConnecting -> 1200
                    isConnected -> 4000
                    else -> 3200
                },
                easing = LinearEasing
            )
        ),
        label = "spinDeg"
    )
    val breathAlpha by inf.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )

    val centerLabel = when {
        isConnecting -> tr("لغو", "Cancel")
        isConnected -> tr("قطع اتصال", "Disconnect")
        isError -> tr("تلاش مجدد", "Retry")
        else -> tr("اتصال", "Connect")
    }

    val appliedModifier = if (modifier === Modifier) Modifier.size(176.dp) else modifier

    Box(
        contentAlignment = Alignment.Center,
        modifier = appliedModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.minDimension <= 8f) return@Canvas
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.minDimension / 2f

            // 1. Single Unified Gravity Drop-Shadow (NO split double-shadow!)
            val shadowCenter = Offset(cx, cy + maxR * 0.045f)
            val ambientCol = if (isConnected || isConnecting) {
                ringColor.copy(alpha = breathAlpha)
            } else if (isDark) {
                Color(0xFF07090E).copy(alpha = 0.62f)
            } else {
                Color(0xFF96A8C2).copy(alpha = 0.52f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to ambientCol,
                    0.76f to ambientCol.copy(alpha = ambientCol.alpha * 0.55f),
                    1.0f to Color.Transparent,
                    center = shadowCenter,
                    radius = maxR * 0.98f
                ),
                radius = maxR * 0.98f,
                center = shadowCenter
            )

            // 2. Outer Tactile Bezel Ring
            val bezelR = maxR * 0.87f
            val bezelTop = if (isDark) Color(0xFF282F3E) else Color(0xFFF6F9FD)
            val bezelBottom = if (isDark) Color(0xFF161A23) else Color(0xFFD2DCEB)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(bezelTop, p.cardBg, bezelBottom),
                    start = Offset(cx, cy - bezelR),
                    end = Offset(cx, cy + bezelR)
                ),
                radius = bezelR,
                center = Offset(cx, cy)
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.14f else 0.90f),
                        if (isDark) Color.Black.copy(alpha = 0.50f) else Color(0xFFB3C2D8)
                    ),
                    start = Offset(cx - bezelR, cy - bezelR),
                    end = Offset(cx + bezelR, cy + bezelR)
                ),
                radius = bezelR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.3.dp.toPx())
            )

            // 3. Recessed LED Groove Channel
            val grooveOuterR = maxR * 0.76f
            val ledRadius = maxR * 0.705f
            drawCircle(
                color = p.subtleBg,
                radius = grooveOuterR,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = ringColor.copy(alpha = if (isDark) 0.16f else 0.20f),
                radius = ledRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 4.2.dp.toPx())
            )

            val sweepDeg = when {
                isConnecting -> 145f
                isConnected -> 290f
                else -> 210f
            }
            rotate(degrees = spinDeg, pivot = Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.00f to Color.Transparent,
                        0.18f to ringColor.copy(alpha = 0.22f),
                        0.68f to ringColor,
                        1.00f to Color.Transparent,
                        center = Offset(cx, cy)
                    ),
                    startAngle = 0f,
                    sweepAngle = sweepDeg,
                    useCenter = false,
                    topLeft = Offset(cx - ledRadius, cy - ledRadius),
                    size = Size(ledRadius * 2f, ledRadius * 2f),
                    style = Stroke(width = 4.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Glowing Comet Tip on LED Ring
            val tipRad = (spinDeg + sweepDeg) * (PI / 180.0)
            val tipOffset = Offset(
                cx + (cos(tipRad) * ledRadius).toFloat(),
                cy + (sin(tipRad) * ledRadius).toFloat()
            )
            drawCircle(
                color = ringColor.copy(alpha = 0.40f),
                radius = 6.dp.toPx(),
                center = tipOffset
            )
            drawCircle(
                color = Color.White,
                radius = 2.4.dp.toPx(),
                center = tipOffset
            )

            // 4. Center Tactile Button Cap (Pressed feel when connected, raised dome when idle)
            val capR = maxR * 0.63f
            val capTop = when {
                isConnected && isDark -> Color(0xFF151B24)
                isConnected && !isDark -> Color(0xFFD9E3F0)
                isDark -> Color(0xFF262D3B)
                else -> Color(0xFFF7FAFE)
            }
            val capBottom = when {
                isConnected && isDark -> Color(0xFF222938)
                isConnected && !isDark -> Color(0xFFF2F6FC)
                isDark -> Color(0xFF181C26)
                else -> Color(0xFFD8E2F0)
            }
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(capTop, capBottom),
                    start = Offset(cx, cy - capR),
                    end = Offset(cx, cy + capR)
                ),
                radius = capR,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = if (isConnected) ringColor.copy(alpha = 0.45f) else p.cardBorder,
                radius = capR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = centerLabel,
                tint = ringColor,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = centerLabel,
                color = if (isConnected || isError) ringColor else p.textPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp,
                maxLines = 1
            )
            if (isConnecting && progressPercent > 0) {
                LtrContent {
                    Text(
                        text = "${progressPercent.coerceIn(1, 99)}%",
                        color = p.accentGold,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
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
    val isFa = LocalAlanLang.current == AlanLang.FA
    val destAccent = destAccentLong.toThemeAwareAccent(p.accentGold)
    val destSpec = remember(destCountryCode) { CountryPaletteRegistry.resolveByCode(destCountryCode) }

    val originDisplay = if (isFa) {
        "مبدا: ${originCountryName.ifBlank { "ایران" }}"
    } else {
        "Origin: Iran"
    }

    val destResolvedName = if (isFa) {
        destCountryName.ifBlank { "خودکار" }
    } else {
        if (destCountryCode.equals("UN", true) || destCountryName == "خودکار") "Auto" else destSpec.nameEn
    }
    val destDisplay = if (isFa) "مقصد: $destResolvedName" else "Target: $destResolvedName"

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
            .alanGlassCard(highlighted = isConnected, cornerRadius = 16.dp, accentBorderColor = p.emerald.copy(alpha = 0.55f))
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Origin Pod
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .alanNeuInset(cornerRadius = 11.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                FlagStripeBadge(CountryPaletteRegistry.IRAN_SPEC.stripeColors, "IR", 26.dp, 17.dp)
                Spacer(Modifier.width(7.dp))
                Column {
                    Text(
                        text = originDisplay,
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
                        val xPos = if (isFa) w * (1f - f) else w * f
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
                    .alanNeuInset(cornerRadius = 11.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = destDisplay,
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

        // Compact Status Strip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isConnected) {
                    tr("تونل رمزنگاری‌شده دوطرفه فعال", "Encrypted bidirectional tunnel active")
                } else {
                    tr("آماده عبور از فیلترینگ DPI", "Ready for anti-DPI tunnel")
                },
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