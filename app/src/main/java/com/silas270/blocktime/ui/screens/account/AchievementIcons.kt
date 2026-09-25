package com.silas270.blocktime.ui.screens.account

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.silas270.blocktime.data.model.AchievementCategory
import com.silas270.blocktime.data.model.AchievementProgress
import com.silas270.blocktime.data.model.AchievementStatus

/**
 * Per-achievement badge iconography, keyed off the stable ids the catalogs already assign.
 *
 * Kept in the UI layer rather than as a field on [AchievementStatus]: an icon is a rendering
 * choice, and the data model deliberately carries no presentation. The `geo_`/`dist_`/`behav_`
 * prefixes give a safe fallback, so a new catalog entry gets a sensible icon on day one instead
 * of a crash or a blank square.
 */
internal fun achievementIcon(id: String, category: AchievementCategory): ImageVector = when (id) {
    AchievementProgress.RED_EYE_ID -> Icons.Outlined.DarkMode
    AchievementProgress.MARATHON_ID -> Icons.Outlined.Schedule
    AchievementProgress.HIGH_ALTITUDE_ID -> Icons.Outlined.FlightTakeoff
    AchievementProgress.EQUATOR_CROSSING_ID -> Icons.Outlined.Public
    else -> when {
        id.startsWith("geo_") && id.contains("countries") -> Icons.Outlined.Flag
        id.startsWith("geo_") && id.contains("continents") -> Icons.Outlined.Public
        id.startsWith("geo_") -> Icons.Outlined.Map
        id.startsWith("dist_") -> Icons.Outlined.Straighten
        else -> categoryFallbackIcon(category)
    }
}

private fun categoryFallbackIcon(category: AchievementCategory): ImageVector = when (category) {
    AchievementCategory.GEOGRAPHIC -> Icons.Outlined.Map
    AchievementCategory.DISTANCE -> Icons.Outlined.Straighten
    AchievementCategory.BEHAVIORAL -> Icons.Outlined.Timer
}
