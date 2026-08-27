package com.example.focusflight.ui.screens.challenge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.ChallengeType
import com.example.focusflight.ui.components.ChallengeProgressBar
import com.example.focusflight.ui.components.challengeTypeIcon
import com.example.focusflight.ui.components.challengeTypeLabel
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The per-leg tick-up beat (docs/design/challenges.md's "Per-leg progress feedback") - shown after
 * the existing rank-stamp `ArrivalCelebrationScreen`, never instead of it, whenever this landing
 * advanced an active challenge without finishing it. Animates the bar from [oldProgress] to
 * [newProgress] over roughly a second or two, per the design doc, rather than a static number -
 * "the repeated feedback loop that pulls you back for one more leg."
 */
@Composable
fun ChallengeProgressScreen(
    challengeName: String,
    challengeType: ChallengeType,
    oldProgress: Float,
    newProgress: Float,
    onContinue: () -> Unit
) {
    BackHandler { onContinue() }

    var animateIn by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animateIn) newProgress else oldProgress,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "challengeTickUpProgress"
    )

    LaunchedEffect(Unit) {
        // A short beat before the bar starts moving so the jump from the rank stamp doesn't read
        // as one continuous animation - then let the tick-up itself finish before showing CONTINUE.
        delay(250)
        animateIn = true
    }

    var showContinue by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250 + 1400L)
        showContinue = true
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Midnight),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = challengeTypeIcon(challengeType),
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.padding(bottom = Spacing.Medium)
            )
            Text(
                text = "CHALLENGE PROGRESS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                color = Haze
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = challengeName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = OffWhite,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                    .padding(24.dp)
            ) {
                ChallengeProgressBar(progress = animatedProgress, height = 16.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${(oldProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Haze
                    )
                    Text(
                        text = "${(newProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Amber
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Midnight),
                enabled = showContinue
            ) {
                Text(
                    text = "CONTINUE",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                )
            }
        }
    }
}
