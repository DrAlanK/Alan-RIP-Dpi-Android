package com.alan.ripdpi.ui

import android.graphics.BlurMaskFilter
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alan.ripdpi.model.CountryPaletteRegistry
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val cachedOrbBlur24 = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
private val cachedOrbBlur22 = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)

private class LiveBubbleParticle(
    var angleRad: Float = 0f,
    var angularVel: Float = 0f,
    var startFrac: Float = 0.36f,
    var endFrac: Float = 0.90f,
    var sizeDp: Float = 5f,
    var life: Float = 0f,
    var lifeSpeed: Float = 0.20f,
    var colorVariant: Int = 0
) {
    fun respawn(rng: Random, initialRandomLife: Boolean = false) {
        angleRad = rng.nextFloat() * (2.0 * PI).toFloat()
        angularVel = (rng.nextFloat() - 0.5f) * 0.45f
        startFrac = 0.36f + rng.nextFloat() * 0.14f
        endFrac = 0.72f + rng.nextFloat() * 0.24f
        sizeDp = 3.2f + rng.nextFloat() * 5.8f
        lifeSpeed = 0.16f + rng.nextFloat() * 0.20f
        life = if (initialRandomLife) rng.nextFloat() else 0f
        colorVariant = rng.nextInt(4)
    }
}

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

    val flagShape = remember { RoundedCornerShape(4.dp) }
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
    val isDisconnecting = uiPhase.equals("DISCONNECTING", ignoreCase = true)
    val isAnimating = isConnecting || isDisconnecting

    val rng = remember { Random(4207L) }
    val particles = remember {
        List(14) {
            LiveBubbleParticle().apply { respawn(rng, initialRandomLife = true) }
        }
    }

    var phase1 by remember { mutableFloatStateOf(0.4f) }
    var phase2 by remember { mutableFloatStateOf(1.9f) }
    var breathPhase by remember { mutableFloatStateOf(0f) }
    var animTick by remember { mutableIntStateOf(0) }

    // ZERO-CPU when Idle or Connected! Only animates smoothly (~28fps) while connecting/disconnecting!
    LaunchedEffect(isAnimating) {
        if (!isAnimating) return@LaunchedEffect
        val dt = 0.036f
        while (true) {
            phase1 += dt * 1.9f
            phase2 += dt * 1.35f
            breathPhase += dt * 2.1f
            for (b in particles) {
                b.life += dt * b.lifeSpeed * 1.6f
                b.angleRad += dt * b.angularVel * 1.6f
                if (b.life >= 1.0f) {
                    b.respawn(rng, initialRandomLife = false)
                }
            }
            animTick++
            delay(36L)
        }
    }

    val lightShadowCol = if (isDark) Color(0xFF30394D).copy(alpha = 0.84f) else Color.White.copy(alpha = 0.96f)
    val darkShadowCol = if (isDark) Color(0xFF0A0D12).copy(alpha = 0.94f) else Color(0xFFB0BED4).copy(alpha = 0.86f)
    val lightGlowCol = if (isConnected) p.emerald.copy(alpha = 0.34f) else lightShadowCol

    val darkShadowPaint = remember(darkShadowCol) {
        Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            color = darkShadowCol.toArgb()
            maskFilter = cachedOrbBlur24
        }
    }
    val lightShadowPaint = remember(lightGlowCol) {
        Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            color = lightGlowCol.toArgb()
            maskFilter = cachedOrbBlur22
        }
    }
    val blobPath = remember { Path() }

    val centerLabel = when {
        isDisconnecting -> "در حال قطع..."
        isConnecting -> "لغو اتصال"
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
            val tickRead = animTick
            if (size.minDimension <= 12f && tickRead >= 0) return@Canvas

            val cx = size.width / 2f
            val cy = size.height / 2f
            val minDim = size.minDimension
            val maxReachX = (size.width * 0.48f).coerceAtLeast(minDim * 0.62f)
            val maxReachY = (size.height * 0.46f).coerceAtLeast(minDim * 0.46f)
            val specCol = if (isDark) Color(0xFF4B5B78) else Color.White

            // 1. 14 Lightweight Neumorphic Decorative Bubbles (zero per-frame gradient shaders!)
            for (b in particles) {
                val rawLife = b.life.coerceIn(0f, 1f)
                val radialProg = b.startFrac + (b.endFrac - b.startFrac) * rawLife
                val bx = cx + (cos(b.angleRad.toDouble()) * maxReachX * radialProg).toFloat()
                val by = cy + (sin(b.angleRad.toDouble()) * maxReachY * radialProg).toFloat()

                val bubbleTint = when {
                    isDisconnecting -> if (b.colorVariant % 2 == 0) p.danger else p.accentGold
                    isConnected -> if (b.colorVariant == 0) p.emerald else if (b.colorVariant == 1) Color(0xFF34D399) else p.cardBg
                    isConnecting -> if (b.colorVariant % 2 == 0) p.accentGold else p.emerald
                    isError -> if (b.colorVariant % 2 == 0) p.danger else p.cardBg
                    else -> if (b.colorVariant == 0) p.accentGold.copy(alpha = 0.78f) else p.cardBg
                }

                val norm = (rawLife / 0.85f).coerceIn(0f, 1f)
                val rPx = (b.sizeDp.dp.toPx() * (0.48f + 0.62f * sin(norm * (PI / 2.0)).toFloat())).coerceAtLeast(2.4f)
                val alpha = (0.35f + 0.65f * sin(norm * PI).toFloat()).coerceIn(0.25f, 1f)

                drawCircle(
                    color = darkShadowCol.copy(alpha = 0.48f * alpha),
                    radius = rPx,
                    center = Offset(bx + 1.8.dp.toPx(), by + 1.8.dp.toPx())
                )
                drawCircle(
                    color = bubbleTint.copy(alpha = alpha),
                    radius = rPx,
                    center = Offset(bx, by)
                )
                drawCircle(
                    color = specCol.copy(alpha = 0.55f * alpha),
                    radius = rPx * 0.36f,
                    center = Offset(bx - rPx * 0.30f, by - rPx * 0.30f)
                )
            }

            // 2. Center Neumorphic Orb (Crisp circle when resting/connected, organic morph while connecting!)
            val baseBlobR = (minDim * 0.32f).coerceIn(58.dp.toPx(), 78.dp.toPx()) *
                (if (isAnimating) (1.0f + 0.028f * sin(breathPhase)) else 1.0f)

            val shadowOff = 7.dp.toPx()
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawCircle(cx + shadowOff, cy + shadowOff, baseBlobR * 0.96f, darkShadowPaint)
                canvas.nativeCanvas.drawCircle(cx - shadowOff, cy - shadowOff, baseBlobR * 0.96f, lightShadowPaint)
            }

            val unifiedBrush = when {
                isDisconnecting -> Brush.linearGradient(
                    colors = listOf(Color(0xFFFB923C), Color(0xFFEF4444), Color(0xFFB91C1C)),
                    start = Offset(cx - baseBlobR, cy - baseBlobR),
                    end = Offset(cx + baseBlobR, cy + baseBlobR)
                )
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

            if (isAnimating) {
                blobPath.reset()
                val morphAmp = baseBlobR * 0.095f
                val steps = 48
                for (s in 0..steps) {
                    val theta = (s.toFloat() / steps.toFloat()) * (2.0 * PI).toFloat()
                    val harmonic = 0.55f * sin(3f * theta + phase1) + 0.45f * cos(2f * theta - phase2)
                    val r = baseBlobR + morphAmp * harmonic
                    val x = cx + r * cos(theta)
                    val y = cy + r * sin(theta)
                    if (s == 0) blobPath.moveTo(x, y) else blobPath.lineTo(x, y)
                }
                blobPath.close()
                drawPath(path = blobPath, brush = unifiedBrush)
            } else {
                drawCircle(brush = unifiedBrush, radius = baseBlobR, center = Offset(cx, cy))
            }
        }

        val contentColor = when {
            isDisconnecting || isError -> Color.White
            isConnected || isConnecting -> Color(0xFF08130E)
            else -> p.textPrimary
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = centerLabel,
                tint = if (!isConnected && !isConnecting && !isDisconnecting && !isError) p.accentGold else contentColor,
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
    val path = remember { Path() }
    Canvas(modifier = modifier.fillMaxWidth().height(20.dp)) {
        val w = size.width
        val h = size.height
        path.reset()
        for (i in 0..24) {
            val f = i / 24f
            val x = f * w
            val y = (h * 0.55f) + sin(f * 4f * PI.toFloat()) * (if (isConnected) 3.dp.toPx() else 1.5.dp.toPx())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor.copy(alpha = 0.65f), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
