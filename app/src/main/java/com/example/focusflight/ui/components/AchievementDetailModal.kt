package com.example.focusflight.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Green
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Spacing

/**
 * One achievement, opened from a tap.
 *
 * Exists because a set-shaped achievement's progress bar is a summary, not the answer: "Master of
 * Africa - 12 / 54 countries" prompts exactly one question, and until now there was nowhere to ask
 * it. [AchievementStatus.members] carries the checklist for those, and this is where it renders -
 * the same [SetMemberChecklist] a Set-completion challenge already uses, so the two read
 * identically.
 *
 * A scalar achievement (a distance milestone, a behavioral one) has no members and simply gets the
 * modal without the checklist, rather than being made unclickable: a grid where some cards open and
 * others silently do nothing is worse than one where every card opens.
 */
@Composable
fun AchievementDetailModal(achievement: AchievementStatus, onDismiss: () -> Unit) {
    ScrimCardModal(onScrimTap = onDismiss) {
        ModalTitle(text = achievement.displayName)
        Spacer(Modifier.height(6.dp))
        Text(
            text = achievement.description,
            style = MaterialTheme.typography.bodySmall,
            color = Haze,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.Medium))
        ChallengeProgressBar(
            progress = achievement.progress,
            fillColor = if (achievement.isUnlocked) Green else Amber,
            height = 10.dp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatAchievementProgressLabel(achievement),
            style = MaterialTheme.typography.labelMedium,
            color = Haze
        )

        // Same rule as the Passport card: the checklist is there to answer "what am I still
        // missing", so a completed set has nothing left for it to say.
        if (!achievement.isUnlocked) {
            achievement.members?.takeIf { it.isNotEmpty() }?.let { members ->
                Spacer(Modifier.height(Spacing.Medium))
                SetMemberChecklist(members = members)
            }
        }
    }
}
