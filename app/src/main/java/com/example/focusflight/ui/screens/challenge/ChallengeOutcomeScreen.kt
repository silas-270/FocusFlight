package com.example.focusflight.ui.screens.challenge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.example.focusflight.data.repository.ChallengeOutcome
import com.example.focusflight.ui.components.CaptionLabel
import com.example.focusflight.ui.components.ChallengeProgressBar
import com.example.focusflight.ui.components.challengeIcon
import com.example.focusflight.ui.components.challengeTypeLabel
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.ChallengeGold
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Green
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private const val AdvanceDurationMs = 1400
private const val CompleteDurationMs = 1200
private const val StartBeatMs = 250

/**
 * The post-landing challenge beat (docs/challenges.md's "Per-leg progress feedback" and
 * "Completion celebration") - shown after the existing rank-stamp `ArrivalCelebrationScreen`,
 * never instead of it, whenever this landing changed one or more active challenges. Replaces what
 * used to be two separate screens (a single-bar tick-up screen and a single-bar completion
 * screen): a single landing can change up to 3 active challenges at once
 * (`MAX_ACTIVE_CHALLENGES`), so this renders one card with one bar per [outcomes] entry rather
 * than picking a single winner to show.
 */
@Composable
fun ChallengeOutcomeScreen(outcomes: List<ChallengeOutcome>, onContinue: () -> Unit) {
    BackHandler { onContinue() }

    val completedCount = outcomes.count { it is ChallengeOutcome.Completed }
    val hasAdvance = outcomes.any { it is ChallengeOutcome.Advanced }
    val settleDelayMs = StartBeatMs + if (hasAdvance) AdvanceDurationMs else CompleteDurationMs

    var animateIn by remember { mutableStateOf(false) }
    var showContinue by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(StartBeatMs.toLong())
        animateIn = true
        delay((settleDelayMs - StartBeatMs).toLong())
        showContinue = true
        if (completedCount > 0) showConfetti = true
    }

    val headline = when {
        completedCount == 0 -> "CHALLENGE PROGRESS"
        completedCount == 1 -> "CHALLENGE COMPLETE"
        else -> "CHALLENGES COMPLETE"
    }
    val headlineColor by animateColorAsState(
        targetValue = if (completedCount > 0 && animateIn) ChallengeGold else Amber,
        animationSpec = tween(durationMillis = 400),
        label = "challengeOutcomeHeadlineColor"
    )
    val buttonColor = if (completedCount > 0) ChallengeGold else Amber

    Box(modifier = Modifier.fillMaxSize().background(Midnight), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CaptionLabel(text = "CHALLENGE UPDATE")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                color = headlineColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepNavy, RoundedCornerShape(20.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                outcomes.forEachIndexed { index, outcome ->
                    if (index > 0) HorizontalDivider(color = Border, thickness = 1.dp)
                    ChallengeOutcomeRow(outcome = outcome, animateIn = animateIn)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Midnight),
                enabled = showContinue
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

@Composable
private fun ChallengeOutcomeRow(outcome: ChallengeOutcome, animateIn: Boolean) {
    val isCompleted = outcome is ChallengeOutcome.Completed
    val oldProgress = when (outcome) {
        is ChallengeOutcome.Advanced -> outcome.oldProgress
        is ChallengeOutcome.Completed -> outcome.oldProgress
    }
    val newProgress = when (outcome) {
        is ChallengeOutcome.Advanced -> outcome.newProgress
        is ChallengeOutcome.Completed -> 1f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (animateIn) newProgress else oldProgress,
        animationSpec = tween(
            durationMillis = if (isCompleted) CompleteDurationMs else AdvanceDurationMs,
            easing = FastOutSlowInEasing
        ),
        label = "challengeOutcomeRowProgress"
    )
    val barColor by animateColorAsState(
        targetValue = if (isCompleted && animateIn) ChallengeGold else Amber,
        animationSpec = tween(durationMillis = 400),
        label = "challengeOutcomeRowColor"
    )

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = challengeIcon(outcome.type, outcome.iconName),
                contentDescription = null,
                tint = barColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.Small))
            Text(
                text = outcome.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = OffWhite,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                text = challengeTypeLabel(outcome.type),
                style = MaterialTheme.typography.labelSmall,
                color = Haze
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        ChallengeProgressBar(progress = animatedProgress, fillColor = barColor, height = 14.dp)

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${(oldProgress * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Haze
            )
            Text(
                text = if (isCompleted) "100%" else "${(newProgress * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = barColor
            )
        }
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
 * Lightweight particle-shower confetti - no external animation library (none is a dependency
 * here), just a single [Animatable] driving ~50 rectangles down a [Canvas]. Doesn't intercept
 * touch (a plain [Canvas] registers no pointer input), so the CONTINUE button beneath it stays
 * tappable throughout. Fires exactly once per screen regardless of how many challenges completed.
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
