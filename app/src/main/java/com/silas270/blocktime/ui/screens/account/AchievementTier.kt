package com.silas270.blocktime.ui.screens.account

import androidx.compose.ui.graphics.Color
import com.silas270.blocktime.data.model.AchievementCategory
import com.silas270.blocktime.data.model.AchievementStatus
import com.silas270.blocktime.data.model.GeographicAchievementGoal
import com.silas270.blocktime.ui.theme.Bronze
import com.silas270.blocktime.ui.theme.BronzeDeep
import com.silas270.blocktime.ui.theme.BronzeIcon
import com.silas270.blocktime.ui.theme.Gold
import com.silas270.blocktime.ui.theme.Ruby
import com.silas270.blocktime.ui.theme.RubyDeep
import com.silas270.blocktime.ui.theme.RubyIcon
import com.silas270.blocktime.ui.theme.GoldDeep
import com.silas270.blocktime.ui.theme.GoldIcon
import com.silas270.blocktime.ui.theme.Silver
import com.silas270.blocktime.ui.theme.SilverDeep
import com.silas270.blocktime.ui.theme.SilverIcon

/**
 * How hard an achievement is to earn - the badge's whole visual identity.
 *
 * [BRONZE]/[SILVER]/[GOLD] are a ladder: each is strictly harder than the last, and the metal is
 * how that reads from across the grid. [RUBY] is deliberately *not* on that ladder - it marks an
 * achievement that has no difficulty rank at all, so a ruby badge answers "what kind of thing is
 * this" rather than "how hard was it". Colouring a rank-less achievement bronze implied it was the
 * easy end of a scale it was never on.
 */
internal enum class AchievementTier { BRONZE, SILVER, GOLD, DIAMOND, RUBY }

/** The metal the badge is cast from - the plaque's own background. */
internal val AchievementTier.base: Color
    get() = when (this) {
        AchievementTier.BRONZE -> BronzeDeep
        AchievementTier.SILVER -> SilverDeep
        AchievementTier.GOLD -> GoldDeep
        AchievementTier.DIAMOND -> Color(0xFF00363A)
        AchievementTier.RUBY -> RubyDeep
    }

/** The metallic accent for stripes and subtle highlights. */
internal val AchievementTier.shine: Color
    get() = when (this) {
        AchievementTier.BRONZE -> Bronze
        AchievementTier.SILVER -> Silver
        AchievementTier.GOLD -> Gold
        AchievementTier.DIAMOND -> Color(0xFF80DEEA)
        AchievementTier.RUBY -> Ruby
    }

/** The radiant, high-contrast icon tint that pops sharply against the striped background. */
internal val AchievementTier.iconTint: Color
    get() = when (this) {
        AchievementTier.BRONZE -> BronzeIcon
        AchievementTier.SILVER -> SilverIcon
        AchievementTier.GOLD -> GoldIcon
        AchievementTier.DIAMOND -> Color(0xFFE0F7FA)
        AchievementTier.RUBY -> RubyIcon
    }

/** Display order on the Passport: the ranked metals hardest-first, then the unranked band. Ruby
 *  sorts last because it is not a rank - interleaving it with the metals would imply one. */
internal val AchievementTier.sortOrder: Int
    get() = when (this) {
        AchievementTier.DIAMOND -> 0
        AchievementTier.GOLD -> 1
        AchievementTier.SILVER -> 2
        AchievementTier.BRONZE -> 3
        AchievementTier.RUBY -> 4
    }

/**
 * Difficulty band for a badge, derived rather than stored.
 *
 * Lives in the UI layer next to [achievementIcon] and for the same reason: the catalogs describe
 * *what* an achievement is, and the data model deliberately carries no presentation. Tier only
 * exists to colour a badge, so putting it here costs no Room migration and no catalog churn.
 *
 * Distance milestones are banded off [AchievementStatus.target] rather than off their ids, so a
 * new entry in [com.silas270.blocktime.data.model.DistanceAchievementCatalog] lands in the right
 * band on day one. Geographic goals have no comparable scalar - "every country in Africa" and
 * "visit every continent" aren't on one axis - so those are named explicitly, with a fallback so an
 * unrecognised id renders as silver instead of crashing.
 *
 * Behavioral achievements are all [AchievementTier.RUBY], with no per-id banding at all. That is
 * the category's defining property rather than a shortcut: "land between midnight and 5am" and
 * "fly 10,000 km in one go" are not harder or easier versions of one another, they are simply
 * different things you can have done. Ranking them against each other - or against a distance
 * ladder - would be inventing an axis that does not exist.
 */
internal fun achievementTier(achievement: AchievementStatus): AchievementTier =
    when (achievement.category) {
        AchievementCategory.DISTANCE -> distanceTier(achievement.target)
        AchievementCategory.GEOGRAPHIC -> geographicTier(achievement.id)
        AchievementCategory.BEHAVIORAL -> AchievementTier.RUBY
    }

/** Cumulative km. The catalog's own ladder - 5k / 40k / 384k / 1M - straddles these two cuts. */
private fun distanceTier(targetKm: Double): AchievementTier = when {
    targetKm >= 10_000_000.0 -> AchievementTier.DIAMOND
    targetKm >= 100_000.0 -> AchievementTier.GOLD
    targetKm >= 20_000.0 -> AchievementTier.SILVER
    else -> AchievementTier.BRONZE
}

private fun geographicTier(id: String): AchievementTier = when {
    // Every country on Earth, and full coverage of one continent (40-odd countries each): the two
    // longest grinds in the app.
    id == GeographicAchievementGoal.AllCountries.id -> AchievementTier.DIAMOND
    id.startsWith("geo_entire_") -> AchievementTier.GOLD
    id == "geo_100_world_citizen" -> AchievementTier.GOLD
    id == "geo_50_diplomat" -> AchievementTier.SILVER
    id == "geo_10_border_crosser" -> AchievementTier.BRONZE
    // "Touched every continent" - long, but one airport per continent clears it.
    id == GeographicAchievementGoal.AllContinents.id -> AchievementTier.SILVER
    else -> AchievementTier.SILVER
}

