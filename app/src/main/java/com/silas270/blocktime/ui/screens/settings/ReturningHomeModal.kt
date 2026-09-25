package com.silas270.blocktime.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.ui.components.ScrimCardModal
import com.silas270.blocktime.ui.components.SpeedMotionLayer
import com.silas270.blocktime.ui.components.WidgetCard
import com.silas270.blocktime.ui.theme.OffWhite
import com.silas270.blocktime.ui.theme.Spacing

private const val ReturnHomeDurationMs = 10000

/**
 * Teleport animation modal: plays an accelerating and decelerating wind streak effect over the
 * aircraft for 10 seconds, then hands control back via [onComplete].
 */
@Composable
fun ReturningHomeModal(onComplete: () -> Unit) {
    val intensity = remember { Animatable(0f) }

    // Not dismissible mid-flight: onScrimTap is a no-op while this runs.
    ScrimCardModal(onScrimTap = {}) {
        Text(
            text = "RETURNING HOME",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = OffWhite
        )
        Spacer(modifier = Modifier.height(Spacing.Medium))
        WidgetCard(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                SpeedMotionLayer(
                    intensity = { intensity.value },
                    iconSize = 96.dp,
                    iconAlignment = Alignment.Center,
                    shakeScale = 1f
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        intensity.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = ReturnHomeDurationMs
                0f at 0
                0.12f at 1200 with FastOutSlowInEasing
                1.0f at 4000 with LinearEasing
                1.0f at 6500 with FastOutSlowInEasing
                0.12f at 8800 with FastOutSlowInEasing
                0f at 10000
            }
        )
        onComplete()
    }
}
