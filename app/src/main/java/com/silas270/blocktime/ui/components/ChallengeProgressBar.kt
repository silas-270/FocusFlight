package com.silas270.blocktime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.Slate

/**
 * The one progress-bar visual for every challenge display - the quest log's static per-card bar,
 * and (with an animated [progress] value driven by the caller) the per-leg tick-up and completion
 * celebration screens. [progress] is always 0f..1f, per
 * [com.silas270.blocktime.data.model.progressFraction]; callers animate it themselves (e.g. via
 * `animateFloatAsState`) rather than this composable owning any animation, so the exact same bar
 * can be either a static snapshot (quest log) or a moving one (landing screens).
 */
@Composable
fun ChallengeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Slate,
    fillColor: Color = Amber,
    height: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(height / 2))
                .background(fillColor)
        )
    }
}

/**
 * Deferred-read sibling of [ChallengeProgressBar], for callers driving it from a long-running
 * animation. Taking [progress] by value means every frame recomposes the caller *and* re-measures
 * the fill (it sizes via `fillMaxWidth(fraction)`); taking a lambda and resolving it inside
 * `drawBehind` keeps the whole thing to a draw-phase invalidation instead.
 *
 * The Return Home teleport uses this - ten seconds of a 60Hz `Animatable` is exactly the case where
 * the by-value overload's per-frame re-layout starts costing visible smoothness in the animation
 * sitting next to it.
 */
@Composable
fun ChallengeProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Slate,
    fillColor: Color = Amber,
    height: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Outer clip rounds the fill's leading edge for free, so the fill is a plain rect.
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
            .drawBehind {
                drawRect(
                    color = fillColor,
                    size = Size(size.width * progress().coerceIn(0f, 1f), size.height)
                )
            }
    )
}
