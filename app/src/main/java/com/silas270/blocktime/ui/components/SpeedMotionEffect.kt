package com.silas270.blocktime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.Border
import com.silas270.blocktime.ui.theme.DeepNavy
import com.silas270.blocktime.ui.theme.Dim
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.Slate
import kotlin.random.Random

/** Full-speed streak travel, in screen-widths per second. */
private const val BaseSpeedWidthsPerSec = 3.5f

private const val IdleShakeIntervalSec = 0.045f
private const val FullSpeedShakeIntervalSec = 0.010f

private const val ParticleCount = 14
private const val ShakeAmplitudeDp = 1.6f

private class StreakParticle(
    val slotIndex: Int,
    var x: Float,
    var yFraction: Float,
    var lengthDp: Float,
    var strokeWidthDp: Float,
    var speedMultiplier: Float,
    var color: Color,
    var alphaScale: Float
)

/**
 * Wind streaks + airframe buffet simulating background motion using muted leather/espresso tones.
 */
@Composable
fun BoxScope.SpeedMotionLayer(
    intensity: () -> Float,
    iconSize: Dp = 28.dp,
    iconAlignment: Alignment = BiasAlignment(0f, -0.44f),
    shakeScale: Float = 1f,
    // Vertical band (fractions of the height) kept free of streaks, e.g. behind a text readout
    // drawn on top of this layer, so no line runs through the glyphs.
    clearBand: ClosedFloatingPointRange<Float>? = null
) {
    val currentIntensity by rememberUpdatedState(intensity)

    val frameTick = remember { mutableIntStateOf(0) }
    val shakeX = remember { mutableFloatStateOf(0f) }
    val shakeY = remember { mutableFloatStateOf(0f) }

    // Muted background palette to blend naturally into the leather/slate card
    val palette = remember {
        listOf(
            Dim,
            Slate,
            Border,
            Haze.copy(alpha = 0.45f),
            Dim.copy(alpha = 0.7f)
        )
    }
    val slotHeight = 0.88f / ParticleCount

    // Stratified particle pool: each particle owns a vertical band so lines never stack or clump
    val particles = remember {
        val random = Random(42)
        List(ParticleCount) { index ->
            val baseY = 0.06f + (index + 0.5f) * slotHeight
            StreakParticle(
                slotIndex = index,
                x = random.nextFloat() * 1.5f - 0.2f,
                yFraction = baseY + (random.nextFloat() - 0.5f) * slotHeight * 0.6f,
                lengthDp = 20f + random.nextFloat() * 26f,
                strokeWidthDp = 1.2f + random.nextFloat() * 1.0f,
                speedMultiplier = 0.9f + random.nextFloat() * 0.25f,
                color = palette[random.nextInt(palette.size)],
                alphaScale = 0.5f + random.nextFloat() * 0.5f
            )
        }
    }

    LaunchedEffect(Unit) {
        val random = Random(31)
        var lastFrameNanos = 0L
        var sinceShake = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastFrameNanos == 0L) 0f
                else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
                lastFrameNanos = now
                val i = currentIntensity().coerceIn(0f, 1f)

                val speedFactor = 0.15f * i + 0.85f * i * i
                if (speedFactor > 0f && dt > 0f) {
                    for (p in particles) {
                        p.x -= dt * BaseSpeedWidthsPerSec * speedFactor * p.speedMultiplier
                        if (p.x < -0.4f) {
                            // Respawn safely outside right edge so it glides in seamlessly
                            p.x = 1.20f + random.nextFloat() * 0.35f
                            p.lengthDp = 20f + random.nextFloat() * 26f
                            p.strokeWidthDp = 1.2f + random.nextFloat() * 1.0f
                            p.speedMultiplier = 0.9f + random.nextFloat() * 0.25f
                            p.color = palette[random.nextInt(palette.size)]
                            p.alphaScale = 0.5f + random.nextFloat() * 0.5f
                            val baseY = 0.06f + (p.slotIndex + 0.5f) * slotHeight
                            p.yFraction = baseY + (random.nextFloat() - 0.5f) * slotHeight * 0.6f
                        }
                    }
                    frameTick.intValue++
                }

                if (i <= 0f) {
                    shakeX.floatValue = 0f
                    shakeY.floatValue = 0f
                    sinceShake = 0f
                } else {
                    sinceShake += dt
                    val interval =
                        IdleShakeIntervalSec + (FullSpeedShakeIntervalSec - IdleShakeIntervalSec) * i
                    if (sinceShake >= interval) {
                        sinceShake = 0f
                        shakeX.floatValue = random.nextFloat() * 2f - 1f
                        shakeY.floatValue = random.nextFloat() * 2f - 1f
                    }
                }
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                val i = currentIntensity().coerceIn(0f, 1f)
                val amp = i * shakeScale * ShakeAmplitudeDp.dp.toPx() * 0.25f
                translationX = shakeX.floatValue * amp
                translationY = shakeY.floatValue * amp
            }
    ) {
        val _tick = frameTick.intValue
        val i = currentIntensity().coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        val globalAlpha = (0.25f + 0.75f * i).coerceIn(0f, 1f)

        for (p in particles) {
            if (clearBand != null && p.yFraction in clearBand) continue
            val strokeLengthPx = p.lengthDp.dp.toPx()
            val xPx = p.x * w
            val yPx = p.yFraction * h

            if (xPx > -strokeLengthPx && xPx < w + strokeLengthPx) {
                drawLine(
                    color = p.color.copy(alpha = (p.alphaScale * globalAlpha).coerceIn(0f, 1f)),
                    start = Offset(xPx, yPx),
                    end = Offset(xPx - strokeLengthPx, yPx),
                    strokeWidth = p.strokeWidthDp.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }

    Icon(
        imageVector = Icons.Outlined.Flight,
        contentDescription = null,
        tint = Amber,
        modifier = Modifier
            .align(iconAlignment)
            .size(iconSize)
            .graphicsLayer {
                val i = currentIntensity().coerceIn(0f, 1f)
                val amplitude = i * shakeScale * ShakeAmplitudeDp.dp.toPx()
                translationX = shakeX.floatValue * amplitude
                translationY = shakeY.floatValue * amplitude
                rotationZ = 90f
            }
    )
}
