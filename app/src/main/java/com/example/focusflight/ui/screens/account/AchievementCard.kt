package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.ui.components.AchievementProgressRow
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Spacing

/**
 * One category's card of achievements - same DeepNavy container as [ContinentProgressCard], with
 * each row rendered by the shared [AchievementProgressRow].
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
            AchievementProgressRow(achievement)
        }
    }
}
