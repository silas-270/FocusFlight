package com.example.focusflight.ui.screens.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.components.ChallengeProgressBar
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.components.SpeedMotionLayer
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val ReturnHomeDurationMs = 5000

/**
 * Plays for a fixed 5 seconds, reusing the in-flight speed effect (no text) over a filling
 * progress bar, then hands control back via [onComplete] — the actual teleport only happens
 * once this finishes, per docs/design/story-mode.md's "instant but not silent" ask.
 */
@Composable
fun ReturningHomeModal(onComplete: () -> Unit) {
    val intensity = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }

    // Not dismissible mid-flight: onScrimTap/back are both no-ops for the 5 seconds this runs.
    ScrimCardModal(onScrimTap = {}) {
        Text(
            text = "RETURNING HOME",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = OffWhite
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Teleporting…",
            style = MaterialTheme.typography.bodyMedium,
            color = Haze
        )
        Spacer(modifier = Modifier.height(Spacing.Medium))
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            SpeedMotionLayer(intensity.value)
        }
        Spacer(modifier = Modifier.height(Spacing.Medium))
        ChallengeProgressBar(progress = progress.value)
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                progress.animateTo(1f, animationSpec = tween(ReturnHomeDurationMs, easing = LinearEasing))
            }
            launch {
                intensity.animateTo(
                    targetValue = 0.15f,
                    animationSpec = keyframes {
                        durationMillis = ReturnHomeDurationMs
                        0f at 0
                        1f at 1500
                        0.15f at ReturnHomeDurationMs
                    }
                )
            }
        }
        onComplete()
    }
}
