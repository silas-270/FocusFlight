package com.example.focusflight.ui.screens.account

import androidx.compose.ui.graphics.Color
import com.example.focusflight.data.model.AchievementCategory
import com.example.focusflight.data.model.AchievementProgress
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.data.model.GeographicAchievementGoal
import com.example.focusflight.ui.theme.Bronze
import com.example.focusflight.ui.theme.BronzeDeep
import com.example.focusflight.ui.theme.BronzeIcon
import com.example.focusflight.ui.theme.Gold
import com.example.focusflight.ui.theme.GoldDeep
import com.example.focusflight.ui.theme.GoldIcon
import com.example.focusflight.ui.theme.Silver
import com.example.focusflight.ui.theme.SilverDeep
import com.example.focusflight.ui.theme.SilverIcon

/** How hard an achievement is to earn, in three bands - the badge's whole visual identity. */
internal enum class AchievementTier { BRONZE, SILVER, GOLD }

/** The metal the badge is cast from - the plaque's own background. */
internal val AchievementTier.base: Color
    get() = when (this) {
        AchievementTier.BRONZE -> BronzeDeep
        AchievementTier.SILVER -> SilverDeep
        AchievementTier.GOLD -> GoldDeep
    }

/** The metallic accent for stripes and subtle highlights. */
internal val AchievementTier.shine: Color
    get() = when (this) {
        AchievementTier.BRONZE -> Bronze
        AchievementTier.SILVER -> Silver
        AchievementTier.GOLD -> Gold
    }

/** The radiant, high-contrast icon tint that pops sharply against the striped background. */
internal val AchievementTier.iconTint: Color
    get() = when (this) {
        AchievementTier.BRONZE -> BronzeIcon
        AchievementTier.SILVER -> SilverIcon
        AchievementTier.GOLD -> GoldIcon
    }

/**
 * Difficulty band for a badge, derived rather than stored.
 *
 * Lives in the UI layer next to [achievementIcon] and for the same reason: the catalogs describe
 * *what* an achievement is, and the data model deliberately carries no presentation. Tier only
 * exists to colour a badge, so putting it here costs no Room migration and no catalog churn.
 *
 * Distance milestones are banded off [AchievementStatus.target] rather than off their ids, so a
 * new entry in [com.example.focusflight.data.model.DistanceAchievementCatalog] lands in the right
 * band on day one. Geographic and behavioral goals have no comparable scalar - "every country in
 * Africa" and "land between midnight and 5am" aren't on one axis - so those are named explicitly,
 * with a per-category fallback so an unrecognised id renders as bronze instead of crashing.
 */
internal fun achievementTier(achievement: AchievementStatus): AchievementTier =
    when (achievement.category) {
        AchievementCategory.DISTANCE -> distanceTier(achievement.target)
        AchievementCategory.GEOGRAPHIC -> geographicTier(achievement.id)
        AchievementCategory.BEHAVIORAL -> behavioralTier(achievement.id)
    }

/** Cumulative km. The catalog's own ladder - 5k / 40k / 384k / 1M - straddles these two cuts. */
private fun distanceTier(targetKm: Double): AchievementTier = when {
    targetKm >= 100_000.0 -> AchievementTier.GOLD
    targetKm >= 20_000.0 -> AchievementTier.SILVER
    else -> AchievementTier.BRONZE
}

private fun geographicTier(id: String): AchievementTier = when {
    // Every country on Earth, and full coverage of one continent (40-odd countries each): the two
    // longest grinds in the app.
    id == GeographicAchievementGoal.AllCountries.id -> AchievementTier.GOLD
    id.startsWith("geo_entire_") -> AchievementTier.GOLD
    // "Touched every continent" - long, but one airport per continent clears it.
    id == GeographicAchievementGoal.AllContinents.id -> AchievementTier.SILVER
    else -> AchievementTier.SILVER
}

private fun behavioralTier(id: String): AchievementTier = when (id) {
    // Both are single-flight extremes you have to deliberately set up.
    AchievementProgress.MARATHON_ID, AchievementProgress.GRAND_VOYAGE_ID -> AchievementTier.SILVER
    // First flight is automatic; the red-eye needs nothing but the right hour.
    else -> AchievementTier.BRONZE
}
