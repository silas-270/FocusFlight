package com.example.focusflight.data.model

/**
 * A thematic (not linear) distance milestone (docs/achievements.md's "Distance milestones"
 * category - "a few big, flavorful ones rather than boring 'fly 1/5/10 flights' counters").
 * Metadata only; [AchievementProgress.evaluateDistance] computes live cumulative STORY-tagged
 * distance and compares it against [targetKm]. A small, real seed list - see
 * [DistanceAchievementCatalog.ALL] - not an exhaustive ladder.
 */
data class DistanceAchievementMilestone(
    override val id: String,
    override val displayName: String,
    override val description: String,
    val targetKm: Double
) : AchievementCatalogEntry

object DistanceAchievementCatalog {
    /**
     * These five are one ladder, not five independent goals: they all read the same cumulative
     * STORY distance, so passing "To the Moon" necessarily means "Around the World" and "Cleared
     * for Long-Haul" are already earned. Sharing a family id lets the Passport collapse them into a
     * single stacked tile - see `AchievementStatus.familyId`.
     */
    const val FAMILY_ID = "dist_cumulative"

    val ALL: List<DistanceAchievementMilestone> = listOf(
        DistanceAchievementMilestone(
            id = "dist_5000_long_haul",
            displayName = "Cleared for Long-Haul",
            description = "Fly a cumulative 5,000 miles in Story Mode.",
            targetKm = 8_046.0
        ),
        DistanceAchievementMilestone(
            id = "dist_24901_around_the_earth",
            displayName = "Around the Earth",
            description = "Fly a cumulative 24,901 miles in Story Mode - the Earth's circumference.",
            targetKm = 40_075.0
        ),
        DistanceAchievementMilestone(
            id = "dist_477710_to_the_moon_and_back",
            displayName = "To the Moon and Back",
            description = "Fly a cumulative 477,710 miles in Story Mode - round-trip lunar voyage.",
            targetKm = 768_800.0
        ),
        DistanceAchievementMilestone(
            id = "dist_1000000_million_miler",
            displayName = "Million Miler",
            description = "Fly a cumulative 1,000,000 miles in Story Mode - lifetime airline status.",
            targetKm = 1_609_344.0
        ),
        DistanceAchievementMilestone(
            id = "dist_23000000_stuker_record",
            displayName = "The Stuker Record",
            description = "Fly a cumulative 23,000,000 miles in Story Mode - world record frequent flyer.",
            targetKm = 37_014_912.0
        )
    )
}
