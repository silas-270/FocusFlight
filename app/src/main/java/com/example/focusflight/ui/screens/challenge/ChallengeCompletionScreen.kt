package com.example.focusflight.ui.screens.challenge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.ChallengeType
import com.example.focusflight.ui.components.ChallengeProgressBar
import com.example.focusflight.ui.components.challengeTypeIcon
import com.example.focusflight.ui.components.challengeTypeLabel
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Green
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.ChallengeGold
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The dedicated completion celebration (docs/design/challenges.md's "Completion celebration") -
 * shown after the existing rank-stamp `ArrivalCelebrationScreen`, never instead of it, whenever
 * this landing pushed an active challenge to 100%. Deliberately its own screen rather than a reuse
 * of `ArrivalCelebrationScreen`: a challenge can take real weeks of sessions to finish, and
 * dressing it in the same beat as an ordinary landing would understate that.
 */
@Composable
fun ChallengeCompletionScreen(
    challengeName: String,
    challengeType: ChallengeType,
    onContinue: () -> Unit
) {
    BackHandler { onContinue() }

    var animateIn by remember { mutableStateOf(false) }
    var golden by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    val barProgress by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 1200),
        label = "challengeCompletionBar"
    )
    val barColor by animateColorAsState(
        targetValue = if (golden) ChallengeGold else Amber,
        animationSpec = tween(durationMillis = 400),
        label = "challengeCompletionBarColor"
    )

    LaunchedEffect(Unit) {
        delay(200)
        animateIn = true
        delay(1200)
        golden = true
        showConfetti = true
    }

    Box(modifier = Modifier.fillMaxSize().background(Midnight), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = challengeTypeIcon(challengeType),
                contentDescription = null,
                tint = barColor,
                modifier = Modifier.padding(bottom = Spacing.Medium)
            )
            Text(
                text = "CHALLENGE COMPLETE",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                color = barColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = challengeName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = OffWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = challengeTypeLabel(challengeType),
                style = MaterialTheme.typography.labelSmall,
                color = Haze
            )

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepNavy, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ChallengeProgressBar(progress = barProgress, fillColor = barColor, height = 18.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "100%",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    ),
                    color = barColor
                )
            }

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ChallengeGold, contentColor = Midnight)
            ) {
                Text(
                    text = "CONTINUE",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                )
            }
        }

        ConfettiOverlay(trigger = showConfetti)
    }
}

private data class ConfettiParticle(
    val startXFraction: Float,
    val fallDurationMs: Int,
    val delayMs: Int,
    val color: Color,
    val sizePx: Float,
    val driftFraction: Float,
    val rotationSpeedDegPerMs: Float
)

private val ConfettiColors = listOf(ChallengeGold, Amber, Green, OffWhite)
private const val ConfettiDurationMs = 3200

/**
 * Lightweight particle-shower confetti for [ChallengeCompletionScreen] - no external animation
 * library (none is a dependency here), just a single [Animatable] driving ~50 rectangles down a
 * [Canvas]. Doesn't intercept touch (a plain [Canvas] registers no pointer input), so the
 * CONTINUE button beneath it stays tappable throughout.
 */
@Composable
private fun ConfettiOverlay(trigger: Boolean) {
    if (!trigger) return

    val particles = remember {
        List(50) {
            ConfettiParticle(
                startXFraction = Random.nextFloat(),
                fallDurationMs = Random.nextInt(1800, ConfettiDurationMs),
                delayMs = Random.nextInt(0, 500),
                color = ConfettiColors[Random.nextInt(ConfettiColors.size)],
                sizePx = Random.nextInt(6, 14).toFloat(),
                driftFraction = Random.nextFloat() * 0.24f - 0.12f,
                rotationSpeedDegPerMs = Random.nextFloat() * 0.5f - 0.25f
            )
        }
    }
    val elapsedMs = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        elapsedMs.animateTo(
            targetValue = ConfettiDurationMs.toFloat(),
            animationSpec = tween(durationMillis = ConfettiDurationMs, easing = LinearEasing)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val now = elapsedMs.value
        particles.forEach { p ->
            val localElapsed = now - p.delayMs
            if (localElapsed < 0f) return@forEach
            val fall = (localElapsed / p.fallDurationMs).coerceIn(0f, 1f)

            val y = fall * (size.height + 60f) - 30f
            val drift = kotlin.math.sin(fall * Math.PI.toFloat() * 2f) * p.driftFraction * size.width
            val x = p.startXFraction * size.width + drift

            rotate(degrees = localElapsed * p.rotationSpeedDegPerMs, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = 1f - fall * 0.3f),
                    topLeft = Offset(x - p.sizePx / 2f, y - p.sizePx),
                    size = Size(p.sizePx, p.sizePx * 1.8f)
                )
            }
        }
    }
}
