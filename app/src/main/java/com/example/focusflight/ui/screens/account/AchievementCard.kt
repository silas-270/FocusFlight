package com.example.focusflight.ui.screens.account

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.ui.components.ChallengeProgressBar
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Green
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import java.util.Locale

/**
 * One category's card of achievements - visually identical to [ContinentProgressCard] (same
 * DeepNavy container, same per-row progress-bar shape via [ChallengeProgressBar], same "DONE"
 * pill convention) so Achievements reads as belonging to the same screen instead of a bolted-on
 * new visual language. Every achievement renders here regardless of [AchievementStatus.isUnlocked]
 * - docs/design/achievements.md's "Reveal style": locked achievements stay fully visible with
 * progress shown, never a mystery/"???" placeholder.
 */
@Composable
internal fun AchievementsCard(achievements: List<AchievementStatus>) {
    if (achievements.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        achievements.forEach { achievement ->
            AchievementRow(achievement)
        }
    }
}

@Composable
private fun AchievementRow(achievement: AchievementStatus) {
    val animatedProgress by animateFloatAsState(
        targetValue = achievement.progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "achievement_${achievement.id}"
    )

    val barColor = if (achievement.isUnlocked) Green else Amber

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Color dot - Green once unlocked, Slate while still locked (mirrors
                // ContinentProgressRow's continent-color dot, repurposed as a locked/unlocked cue).
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (achievement.isUnlocked) Green else Slate)
                )
                Text(
                    text = achievement.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = OffWhite
                )
                if (achievement.isUnlocked) {
                    Box(
                        modifier = Modifier
                            .background(Green.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "DONE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Green,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            Text(
                text = formatAchievementProgressLabel(achievement),
                style = MaterialTheme.typography.labelMedium,
                color = Haze
            )
        }

        Text(
            text = achievement.description,
            style = MaterialTheme.typography.labelSmall,
            color = Haze.copy(alpha = 0.75f)
        )

        ChallengeProgressBar(progress = animatedProgress, fillColor = barColor)
    }
}

/**
 * "3 / 7 continents", "12,450 / 40,075 km" for a real numeric target, or "UNLOCKED"/"LOCKED" for
 * a boolean-style achievement ([AchievementStatus.target] <= 1.0, e.g. "First Flight") where a
 * literal "0 / 1" would read oddly.
 */
private fun formatAchievementProgressLabel(achievement: AchievementStatus): String {
    if (achievement.target <= 1.0) {
        return if (achievement.isUnlocked) "UNLOCKED" else "LOCKED"
    }
    val currentText = String.format(Locale.US, "%,.0f", achievement.current)
    val targetText = String.format(Locale.US, "%,.0f", achievement.target)
    val unit = achievement.unitLabel.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
    return "$currentText / $targetText$unit"
}
