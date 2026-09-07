package com.example.focusflight.ui.components

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
import com.example.focusflight.data.model.AchievementCategory
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Green
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import java.util.Locale

/**
 * One achievement rendered as name + description + progress bar.
 *
 * Shared rather than screen-local because two surfaces show it: the Challenges screen's
 * Achievements tab (still-unearned goals) and, historically, the Passport. Earned achievements on
 * the Passport are badges instead - see `AchievementBadgeGrid`.
 *
 * [showUnlockedCues] exists for the tab, where every row is by definition unearned: the
 * Green dot and "DONE" pill would be dead pixels there, so it hides them.
 */
@Composable
fun AchievementProgressRow(
    achievement: AchievementStatus,
    modifier: Modifier = Modifier,
    showUnlockedCues: Boolean = true
) {
    val animatedProgress by animateFloatAsState(
        targetValue = achievement.progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "achievement_${achievement.id}"
    )

    val barColor = if (achievement.isUnlocked) Green else Amber

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showUnlockedCues) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (achievement.isUnlocked) Green else Slate)
                    )
                }
                Text(
                    text = achievement.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = OffWhite
                )
                if (showUnlockedCues && achievement.isUnlocked) {
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
 * The display name for an achievement's strict category, used both as a section header on the
 * Challenges screen's Achievements tab and as the title of a Passport ladder stack.
 *
 * Shared here rather than duplicated on each surface so the two can never disagree about what a
 * category is called.
 */
fun achievementCategoryLabel(category: AchievementCategory): String = when (category) {
    AchievementCategory.GEOGRAPHIC -> "GEOGRAPHIC"
    AchievementCategory.DISTANCE -> "DISTANCE MILESTONES"
    AchievementCategory.BEHAVIORAL -> "BEHAVIORAL"
}

/**
 * "3 / 7 continents", "12,450 / 40,075 km" for a real numeric target, or "UNLOCKED"/"LOCKED" for
 * a boolean-style achievement ([AchievementStatus.target] <= 1.0, e.g. "First Flight") where a
 * literal "0 / 1" would read oddly.
 */
fun formatAchievementProgressLabel(achievement: AchievementStatus): String {
    if (achievement.target <= 1.0) {
        return if (achievement.isUnlocked) "UNLOCKED" else "LOCKED"
    }
    val currentText = String.format(Locale.US, "%,.0f", achievement.current)
    val targetText = String.format(Locale.US, "%,.0f", achievement.target)
    val unit = achievement.unitLabel.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
    return "$currentText / $targetText$unit"
}
