package com.example.focusflight.data.model

import java.util.Calendar

/** The three progress-bar-shaped achievement categories, grouped for display - see
 *  [AchievementProgress.evaluateAll]. "Challenges completed" (achievements.md's fourth category)
 *  isn't part of this board - it's a flat log read straight from
 *  `ChallengeRepository.listCompletedChallenges`, not a computed [AchievementStatus] list. */
data class AchievementBoard(
    val geographic: List<AchievementStatus>,
    val distance: List<AchievementStatus>,
    val behavioral: List<AchievementStatus>
)

/**
 * Pure progress/unlock math for docs/design/achievements.md's three progress-bar-shaped
 * categories (Geographic sets, Distance milestones, Behavioral) - no Room/AirportRepository/JNI
 * dependency, mirroring [ChallengeProgress]'s pattern so this is directly unit-testable (see
 * AchievementProgressTest) independent of the repository/ViewModel layer that calls into it.
 *
 * Scope is Story Mode only (achievements.md's "Scope & isolation"):
 * - [evaluateGeographic] takes an already STORY-filtered [VisitedGeography] (see
 *   `AirportRepository.getVisitedGeography`, which filters internally) - no re-filtering needed
 *   here.
 * - [evaluateDistance]/[evaluateBehavioral] take the *full*, unfiltered flight history and filter
 *   to STORY internally themselves, the same way `getVisitedGeography` does - so a Free Mode
 *   quick-hop or a Route-challenge session can never move these numbers even if a caller passes
 *   in the raw, all-modes history (which `AccountViewModel` does, since that's the same list
 *   already loaded for the logbook/highlights).
 *
 * Computed reactively/on-demand whenever the Account screen reads it (no persisted "unlocked"
 * flag, no new Room table/migration) - see docs/design/mechanics.md's post-landing pipeline note
 * and `InFlightViewModel.checkAchievementsAndChallenges`'s doc comment for why.
 */
object AchievementProgress {

    fun evaluateAll(geo: VisitedGeography, flightHistory: List<FlightLog>): AchievementBoard =
        AchievementBoard(
            geographic = evaluateGeographic(geo),
            distance = evaluateDistance(flightHistory),
            behavioral = evaluateBehavioral(flightHistory)
        )

    fun evaluateGeographic(geo: VisitedGeography): List<AchievementStatus> =
        GeographicAchievementCatalog.ALL.map { goal ->
            when (goal) {
                is GeographicAchievementGoal.AllContinents -> {
                    val total = geo.continentStats.size
                    val current = geo.continentStats.count { it.visitedCountries.isNotEmpty() }
                    AchievementStatus(
                        id = goal.id,
                        category = AchievementCategory.GEOGRAPHIC,
                        displayName = goal.displayName,
                        description = goal.description,
                        current = current.toDouble(),
                        target = total.toDouble(),
                        unitLabel = "continents",
                        isUnlocked = total > 0 && current >= total
                    )
                }
                is GeographicAchievementGoal.AllCountries -> {
                    val total = geo.continentStats.sumOf { it.totalCountries }
                    val current = geo.visitedCountries.size
                    AchievementStatus(
                        id = goal.id,
                        category = AchievementCategory.GEOGRAPHIC,
                        displayName = goal.displayName,
                        description = goal.description,
                        current = current.toDouble(),
                        target = total.toDouble(),
                        unitLabel = "countries",
                        isUnlocked = total > 0 && current >= total
                    )
                }
                is GeographicAchievementGoal.EntireContinent -> {
                    val stat = geo.continentStats.find { it.continentCode == goal.continentCode }
                    val total = stat?.totalCountries ?: 0
                    val current = stat?.visitedCountries?.size ?: 0
                    AchievementStatus(
                        id = goal.id,
                        category = AchievementCategory.GEOGRAPHIC,
                        displayName = goal.displayName,
                        description = goal.description,
                        current = current.toDouble(),
                        target = total.toDouble(),
                        unitLabel = "countries",
                        isUnlocked = stat?.isCompleted ?: false
                    )
                }
            }
        }

    fun evaluateDistance(flightHistory: List<FlightLog>): List<AchievementStatus> {
        val storyDistanceKm = flightHistory.filter { it.mode == FlightMode.STORY }.sumOf { it.distanceKm }
        return DistanceAchievementCatalog.ALL.map { milestone ->
            AchievementStatus(
                id = milestone.id,
                category = AchievementCategory.DISTANCE,
                displayName = milestone.displayName,
                description = milestone.description,
                current = storyDistanceKm,
                target = milestone.targetKm,
                unitLabel = "km",
                isUnlocked = storyDistanceKm >= milestone.targetKm
            )
        }
    }

    // ── Behavioral ids/thresholds (docs/design/achievements.md's "Behavioral / session-based") ──
    const val FIRST_FLIGHT_ID = "behav_first_flight"
    const val RED_EYE_ID = "behav_red_eye_pilot"
    const val MARATHON_ID = "behav_marathon_flight"
    const val GRAND_VOYAGE_ID = "behav_grand_voyage"

    /** 8 hours, in minutes - matches [FlightLog.durationMin]'s unit directly. */
    const val MARATHON_TARGET_MIN = 480.0

    /** A single flight, not cumulative - distinct from the Distance category's cumulative
     *  milestones. */
    const val GRAND_VOYAGE_TARGET_KM = 10_000.0
    private const val RED_EYE_HOUR_START = 0
    private const val RED_EYE_HOUR_END_EXCLUSIVE = 5 // local midnight..4:59am

    /**
     * Four small, real behavioral achievements - a first-flight milestone, a duration extreme, a
     * distance extreme (per-flight, not cumulative - complements Distance's cumulative
     * milestones), and a time-of-day quirk. Same "a handful, not exhaustive" content posture as
     * the other categories.
     */
    fun evaluateBehavioral(flightHistory: List<FlightLog>): List<AchievementStatus> {
        val story = flightHistory.filter { it.mode == FlightMode.STORY }

        val hasAnyFlight = story.isNotEmpty()
        val firstFlight = AchievementStatus(
            id = FIRST_FLIGHT_ID,
            category = AchievementCategory.BEHAVIORAL,
            displayName = "First Flight",
            description = "Complete your first Story Mode flight.",
            current = if (hasAnyFlight) 1.0 else 0.0,
            target = 1.0,
            unitLabel = "",
            isUnlocked = hasAnyFlight
        )

        val longestDurationMin = story.maxOfOrNull { it.durationMin } ?: 0
        val marathon = AchievementStatus(
            id = MARATHON_ID,
            category = AchievementCategory.BEHAVIORAL,
            displayName = "Marathon Flight",
            description = "Complete a single Story Mode flight of at least 8 hours.",
            current = longestDurationMin.toDouble(),
            target = MARATHON_TARGET_MIN,
            unitLabel = "min",
            isUnlocked = longestDurationMin >= MARATHON_TARGET_MIN
        )

        val longestSingleDistanceKm = story.maxOfOrNull { it.distanceKm } ?: 0.0
        val grandVoyage = AchievementStatus(
            id = GRAND_VOYAGE_ID,
            category = AchievementCategory.BEHAVIORAL,
            displayName = "Grand Voyage",
            description = "Complete a single Story Mode flight of at least 10,000 km.",
            current = longestSingleDistanceKm,
            target = GRAND_VOYAGE_TARGET_KM,
            unitLabel = "km",
            isUnlocked = longestSingleDistanceKm >= GRAND_VOYAGE_TARGET_KM
        )

        val hasRedEye = story.any { isRedEyeLanding(it.completedAt) }
        val redEye = AchievementStatus(
            id = RED_EYE_ID,
            category = AchievementCategory.BEHAVIORAL,
            displayName = "Red-Eye Pilot",
            description = "Land a Story Mode flight between midnight and 5am.",
            current = if (hasRedEye) 1.0 else 0.0,
            target = 1.0,
            unitLabel = "",
            isUnlocked = hasRedEye
        )

        return listOf(firstFlight, marathon, grandVoyage, redEye)
    }

    /** Local-time (device default timezone) hour check - same basis as every other
     *  `completedAt`-derived display already in this app (e.g. `LogbookEntry`'s date formatting),
     *  not UTC. */
    private fun isRedEyeLanding(completedAtMillis: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = completedAtMillis
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in RED_EYE_HOUR_START until RED_EYE_HOUR_END_EXCLUSIVE
    }
}
