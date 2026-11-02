package com.alan.ripdpi.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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

    Canvas(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .border(0.8.dp, p.cardBorder.copy(alpha = 0.95f), RoundedCornerShape(4.dp))
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

        // Subtle glossy diagonal glass highlight
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.16f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(0f, 0f),
            size = Size(w, h)
        )
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
            .clip(RoundedCornerShape(8.dp))
            .background(col.copy(alpha = 0.14f))
            .border(1.dp, col.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
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
    val isError = uiPhase.equals("ERROR", ignoreCase = true)
    val flagAccent = remember(accentColorLong, p.accentGold) {
        accentColorLong.toAdaptiveColor(p.accentGold)
    }

    val ringColor = when {
        isConnected -> p.emerald
        isConnecting -> p.accentGold
        isError -> p.danger
        else -> p.accentGold
    }
    val secondaryArcColor = when {
        isConnected -> flagAccent
        isConnecting -> p.emerald
        isError -> p.accentAmber
        else -> flagAccent.copy(alpha = 0.78f)
    }

    val inf = rememberInfiniteTransition(label = "kineticOrb")
    // Continuous 360-degree clockwise rotation in ALL states (Idle, Connecting, Connected)
    val primarySpin by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isConnecting -> 1250
                    isConnected -> 3800
                    else -> 2800
                },
                easing = LinearEasing
            )
        ),
        label = "primarySpin"
    )
    // Counter-rotating secondary orbital arc & compass dial
    val counterSpin by inf.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 2100 else 5400,
                easing = LinearEasing
            )
        ),
        label = "counterSpin"
    )
    // Expanding sonar wave ripple
    val rippleFrac by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isConnecting) 1400 else 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonarRipple"
    )
    // Core energy breathing
    val breath by inf.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreBreath"
    )

    val centerLabel = when {
        isConnecting -> "لغو"
        isConnected -> "قطع اتصال"
        isError -> "تلاش مجدد"
        else -> "اتصال"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(224.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.minDimension <= 4f) return@Canvas
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.minDimension / 2f

            // 1. Breathing radial energy aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringColor.copy(alpha = if (isConnected) 0.28f else 0.18f),
                        secondaryArcColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = maxR * breath
                ),
                radius = maxR
            )

            // 2. Expanding Sonar Pulse Wave
            val sonarR = maxR * (0.64f + 0.34f * rippleFrac)
            drawCircle(
                color = ringColor.copy(alpha = (1f - rippleFrac) * 0.36f),
                radius = sonarR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.6.dp.toPx())
            )

            // 3. Counter-rotating 48-tick Cyber Compass Dial
            val tickCount = 48
            val innerTickR = maxR * 0.89f
            val outerTickR = maxR * 0.95f
            for (i in 0 until tickCount) {
                val deg = i * (360f / tickCount) + counterSpin * 0.35f
                val rad = deg * (PI / 180.0)
                val major = i % 4 == 0
                val tAlpha = if (major) 0.62f else 0.20f
                val tCol = if (major && i % 8 == 0) secondaryArcColor else ringColor
                drawLine(
                    color = tCol.copy(alpha = tAlpha),
                    start = Offset(
                        cx + (cos(rad) * innerTickR).toFloat(),
                        cy + (sin(rad) * innerTickR).toFloat()
                    ),
                    end = Offset(
                        cx + (cos(rad) * (if (major) outerTickR + 2.dp.toPx() else outerTickR)).toFloat(),
                        cy + (sin(rad) * (if (major) outerTickR + 2.dp.toPx() else outerTickR)).toFloat()
                    ),
                    strokeWidth = if (major) 2.dp.toPx() else 1.1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. Inner Dark/Light Reactor Base
            drawCircle(
                color = if (p.isDark) Color(0xFF0A0F19) else Color(0xFFFFFFFF),
                radius = maxR * 0.77f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = ringColor.copy(alpha = 0.16f),
                radius = maxR * 0.77f,
                center = Offset(cx, cy),
                style = Stroke(width = 3.2.dp.toPx())
            )

            // 5. Secondary Counter-Rotating Flag-Tinted Arc (outer orbit)
            val subArcR = maxR * 0.83f
            drawArc(
                color = secondaryArcColor.copy(alpha = 0.72f),
                startAngle = counterSpin,
                sweepAngle = if (isConnecting) 110f else 78f,
                useCenter = false,
                topLeft = Offset(cx - subArcR, cy - subArcR),
                size = Size(subArcR * 2f, subArcR * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 6. Primary Continuously Spinning Sweep-Gradient Ring (The Circled Yellow Ring!)
            val mainArcR = maxR * 0.77f
            val mainSweep = when {
                isConnecting -> 150f
                isConnected -> 275f
                else -> 235f
            }
            rotate(degrees = primarySpin, pivot = Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.00f to Color.Transparent,
                        0.15f to ringColor.copy(alpha = 0.20f),
                        0.55f to ringColor.copy(alpha = 0.85f),
                        0.68f to ringColor,
                        1.00f to Color.Transparent,
                        center = Offset(cx, cy)
                    ),
                    startAngle = 0f,
                    sweepAngle = mainSweep,
                    useCenter = false,
                    topLeft = Offset(cx - mainArcR, cy - mainArcR),
                    size = Size(mainArcR * 2f, mainArcR * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Glowing Comet Head Dot at the leading tip of the spinning yellow/emerald arc
            val headDeg = (primarySpin + mainSweep) * (PI / 180.0)
            val headPos = Offset(
                cx + (cos(headDeg) * mainArcR).toFloat(),
                cy + (sin(headDeg) * mainArcR).toFloat()
            )
            drawCircle(
                color = ringColor.copy(alpha = 0.38f),
                radius = 7.5.dp.toPx(),
                center = headPos
            )
            drawCircle(
                color = Color.White,
                radius = 3.2.dp.toPx(),
                center = headPos
            )

            // 7. Center Core Button Dome
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (p.isDark) {
                        listOf(Color(0xFF172133), Color(0xFF0A0E17))
                    } else {
                        listOf(Color(0xFFFDFEFF), Color(0xFFEAF1FB))
                    },
                    center = Offset(cx, cy),
                    radius = maxR * 0.65f
                ),
                radius = maxR * 0.65f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = ringColor.copy(alpha = 0.32f),
                radius = maxR * 0.65f,
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
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = centerLabel,
                color = ringColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            if (isConnecting && progressPercent > 0) {
                LtrContent {
                    Text(
                        text = "${progressPercent.coerceIn(1, 99)}%",
                        color = p.textMuted,
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
    val iranGreen = Color(0xFF10B981)
    val destAccent = remember(destAccentLong, p.accentGold) {
        destAccentLong.toAdaptiveColor(p.accentGold)
    }

    val inf = rememberInfiniteTransition(label = "topologyLaser")
    val photonPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 1100 else 2200,
                easing = LinearEasing
            )
        ),
        label = "photonFlow"
    )
    val beaconPulse by inf.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconPulse"
    )

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

    val outerShape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(if (p.isDark) Color(0xFF0C121D) else p.cardBg)
            .border(
                width = 1.3.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        destAccent.copy(alpha = if (isConnected) 0.85f else 0.48f),
                        capsuleColor.copy(alpha = 0.45f),
                        iranGreen.copy(alpha = if (isConnected) 0.80f else 0.45f)
                    )
                ),
                shape = outerShape
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                // Subtle Desktop-style Cyber-Grid Matrix
                val step = 16.dp.toPx()
                val gridCol = if (p.isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.03f)
                var gx = step
                while (gx < w) {
                    drawLine(gridCol, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                    gx += step
                }
                var gy = step
                while (gy < h) {
                    drawLine(gridCol, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                    gy += step
                }
                // Dual-Side Flag Ambient Glow (Right = Iran Emerald, Left = Destination Flag Accent)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(iranGreen.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(w * 0.88f, h * 0.5f),
                        radius = w * 0.42f
                    ),
                    radius = w * 0.42f,
                    center = Offset(w * 0.88f, h * 0.5f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(destAccent.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(w * 0.12f, h * 0.5f),
                        radius = w * 0.44f
                    ),
                    radius = w * 0.44f,
                    center = Offset(w * 0.12f, h * 0.5f)
                )
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            // Top Justified Row: Origin Pod (Right) <--- Animated Laser Highway ---> Destination Pod (Left)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // RIGHT POD: Origin (Iran) — 100% Start-Aligned in RTL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (p.isDark) Color(0xFF101927).copy(alpha = 0.88f)
                            else p.subtleBg
                        )
                        .border(1.dp, iranGreen.copy(alpha = 0.42f), RoundedCornerShape(13.dp))
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        FlagStripeBadge(
                            stripeColors = CountryPaletteRegistry.IRAN_SPEC.stripeColors,
                            countryCode = "IR",
                            width = 34.dp,
                            height = 22.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(iranGreen.copy(alpha = beaconPulse))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "مبدا: ${originCountryName.ifBlank { "ایران" }}",
                            color = p.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        LtrContent {
                            Text(
                                text = "IR • LOCAL NODE",
                                color = iranGreen,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // CENTER ANIMATED LASER CONDUIT
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val cy = size.height / 2f
                        if (w > 8f) {
                            // Base dual fiber lines
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(destAccent.copy(alpha = 0.55f), iranGreen.copy(alpha = 0.55f))
                                ),
                                start = Offset(0f, cy - 3.dp.toPx()),
                                end = Offset(w, cy - 3.dp.toPx()),
                                strokeWidth = 1.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(destAccent.copy(alpha = 0.35f), iranGreen.copy(alpha = 0.35f))
                                ),
                                start = Offset(0f, cy + 3.dp.toPx()),
                                end = Offset(w, cy + 3.dp.toPx()),
                                strokeWidth = 1.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Streaming Bidirectional Photons (3 Uplink R->L, 3 Downlink L->R)
                            for (k in 0..2) {
                                val prog = (photonPhase + k / 3f) % 1f
                                val xUp = w * (1f - prog)
                                val xDown = w * prog
                                drawCircle(
                                    color = p.accentGold,
                                    radius = 2.6.dp.toPx(),
                                    center = Offset(xUp, cy - 3.dp.toPx())
                                )
                                drawCircle(
                                    color = if (isConnected) p.emerald else destAccent,
                                    radius = 2.6.dp.toPx(),
                                    center = Offset(xDown, cy + 3.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // LEFT POD: Destination Country — 100% End-Aligned in RTL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (p.isDark) Color(0xFF101927).copy(alpha = 0.88f)
                            else p.subtleBg
                        )
                        .border(1.dp, destAccent.copy(alpha = 0.65f), RoundedCornerShape(13.dp))
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "مقصد: ${destCountryName.ifBlank { "خودکار" }}",
                            color = p.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
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
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(contentAlignment = Alignment.TopStart) {
                        FlagStripeBadge(
                            stripeColors = destStripeColors,
                            countryCode = destCountryCode,
                            width = 34.dp,
                            height = 22.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    (if (isConnected) p.emerald else destAccent).copy(alpha = beaconPulse)
                                )
                        )
                    }
                }
            }

            // Bottom Justified Status & Encrypted Capsule Strip (Desktop-style footer inside topology card)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isConnected) "مسیر رمزنگاری‌شده دوطرفه فعال" else "آماده برقراری تونل ضد DPI",
                    color = p.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(capsuleColor.copy(alpha = 0.14f))
                        .border(1.dp, capsuleColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = capsuleColor,
                        modifier = Modifier.size(11.dp)
                    )
                    LtrContent {
                        Text(
                            text = capsuleText,
                            color = capsuleColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
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

        // Living animated oscilloscope wave when idle or before traffic arrives
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
            // Moving scanner dot on wave
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
        drawCircle(
            color = lineColor,
            radius = 3.2.dp.toPx(),
            center = lastPt
        )
    }
}
