package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.SoftAmber
import com.example.focusflight.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val unlockedDateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)

/**
 * Earned achievements, as compact badges that wrap several to a row.
 *
 * This replaced three full-width stacked lists that showed every achievement, earned or not, and
 * ran most of the length of the Passport. Unearned goals now live on the Challenges screen, which
 * leaves this purely as a trophy case - so it can be dense, and the description moves behind a tap
 * rather than being printed under every entry.
 *
 * Newest-first, using the persisted unlock timestamps (see `AchievementUnlock`). Badges with no
 * recorded time sort last: that only happens for achievements earned before unlock-time
 * persistence existed and not yet re-stamped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AchievementBadgeGrid(achievements: List<AchievementStatus>) {
    if (achievements.isEmpty()) {
        Text(
            text = "No achievements earned yet - see the Challenges screen for what's in reach.",
            style = MaterialTheme.typography.bodySmall,
            color = Haze
        )
        return
    }

    var selected by remember { mutableStateOf<AchievementStatus?>(null) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        achievements.forEach { achievement ->
            AchievementBadge(achievement = achievement, onClick = { selected = achievement })
        }
    }

    selected?.let { achievement ->
        AchievementBadgeModal(achievement = achievement, onDismiss = { selected = null })
    }
}

@Composable
private fun AchievementBadge(achievement: AchievementStatus, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DeepNavy)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.Small, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SoftAmber)
                .border(1.dp, Amber.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = achievementIcon(achievement.id, achievement.category),
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = achievement.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = OffWhite,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun AchievementBadgeModal(achievement: AchievementStatus, onDismiss: () -> Unit) {
    ScrimCardModal(onScrimTap = onDismiss) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SoftAmber)
                .border(1.dp, Amber.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = achievementIcon(achievement.id, achievement.category),
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(Spacing.Medium))
        Text(
            text = achievement.displayName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = OffWhite,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = achievement.description,
            style = MaterialTheme.typography.bodySmall,
            color = Haze,
            textAlign = TextAlign.Center
        )
        achievement.unlockedAt?.let { millis ->
            Spacer(Modifier.height(Spacing.Medium))
            Text(
                text = "Earned ${unlockedDateFormat.format(Date(millis))}",
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
                letterSpacing = 1.sp
            )
        }
    }
}
