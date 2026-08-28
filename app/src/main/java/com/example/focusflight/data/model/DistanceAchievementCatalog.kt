package com.example.focusflight.data.model

/**
 * A thematic (not linear) distance milestone (docs/design/achievements.md's "Distance milestones"
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
    val ALL: List<DistanceAchievementMilestone> = listOf(
        DistanceAchievementMilestone(
            id = "dist_5000_long_haul",
            displayName = "Cleared for Long-Haul",
            description = "Fly a cumulative 5,000 km in Story Mode.",
            targetKm = 5_000.0
        ),
        DistanceAchievementMilestone(
            id = "dist_40075_round_the_world",
            displayName = "Around the World",
            description = "Fly a cumulative 40,075 km in Story Mode - Earth's circumference.",
            targetKm = 40_075.0
        ),
        DistanceAchievementMilestone(
            id = "dist_384400_to_the_moon",
            displayName = "To the Moon",
            description = "Fly a cumulative 384,400 km in Story Mode - the distance to the Moon.",
            targetKm = 384_400.0
        ),
        DistanceAchievementMilestone(
            id = "dist_1000000_voyagers_reach",
            displayName = "Voyager's Reach",
            description = "Fly a cumulative 1,000,000 km in Story Mode.",
            targetKm = 1_000_000.0
        )
    )
}
