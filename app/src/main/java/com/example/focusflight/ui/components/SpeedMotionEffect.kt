package com.example.focusflight.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.OffWhite
import kotlin.math.roundToInt

/** Wind streaks + airframe buffet. Split out so it can be removed from composition
 *  entirely at a standstill, and so every animated value is read in a draw or layer
 *  scope — those invalidate drawing only, never composition or layout. Shared between
 *  the in-flight speed instrument and the Return Home modal, both of which just supply
 *  an [intensity] curve. */
@Composable
fun BoxScope.SpeedMotionLayer(intensity: Float) {
    // Durations are quantised into speed bands. Deriving them straight from `intensity`
    // rebuilt the animation spec on every telemetry tick, which restarted both
    // animations 30x/second — the streaks could never actually complete a sweep.
    val band = (intensity * 8f).roundToInt()
    val streamDurationMs = (1400 - band * 130).coerceAtLeast(320)
    // Buffet gets faster as well as stronger with speed, which is what sells it as
    // airflow rather than a fixed-rate wobble.
    val buffetDurationMs = (210 - band * 14).coerceAtLeast(95)

    val infiniteTransition = rememberInfiniteTransition(label = "speedFx")
    val streamPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = streamDurationMs, easing = LinearEasing)
        ),
        label = "streamPhase"
    )
    val buffet by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = buffetDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buffet"
    )
    val laneSeeds = remember { List(5) { kotlin.random.Random(it * 91 + 7).nextFloat() } }

    // The streak field is drawn once as a horizontally repeating pattern and then simply
    // translated. Redrawing it at new X positions each frame (as this used to) forced a
    // display-list re-record of the whole HUD on every single display refresh; moving a
    // graphics layer is a render-node property update instead, so per-frame cost drops to
    // effectively nothing and the canvas only re-records when speed actually changes.
    // Three copies are drawn so that a full tile of travel always has content on both
    // sides; the instrument face clips the overhang.
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = -streamPhase * size.width }
    ) {
        val tile = size.width
        val len = 10.dp.toPx() + 16.dp.toPx() * intensity
        val alpha = 0.25f + 0.35f * intensity
        laneSeeds.forEachIndexed { index, seed ->
            val laneY = size.height * (0.2f + 0.6f * (index / (laneSeeds.size - 1f)))
            // Seeded offset per lane so the streaks form a scattered field rather than a
            // rigid comb, and each lane's own length varies a little with its seed.
            val baseX = tile * seed
            val laneLen = len * (0.75f + seed * 0.5f)
            for (copy in 0..2) {
                val x = baseX + tile * copy
                drawLine(
                    color = Amber.copy(alpha = alpha),
                    start = Offset(x, laneY),
                    end = Offset(x - laneLen, laneY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }

    Icon(
        imageVector = Icons.Outlined.Flight,
        contentDescription = null,
        tint = OffWhite,
        modifier = Modifier
            .align(BiasAlignment(0f, -0.44f))
            .size(28.dp)
            .graphicsLayer {
                // Buffet grows super-linearly with speed so the difference between
                // cruise and approach is actually visible.
                val amplitude = intensity * intensity
                translationX = buffet * 5f * amplitude
                translationY = buffet * 3f * amplitude
                rotationZ = 90f + buffet * 1.5f * amplitude
            }
    )
}
