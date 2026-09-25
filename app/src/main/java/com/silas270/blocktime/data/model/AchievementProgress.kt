package com.silas270.blocktime.data.model

import com.silas270.blocktime.util.kmToMiles
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
 * Pure progress/unlock math for docs/achievements.md's three progress-bar-shaped
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
 * flag, no new Room table/migration) - see docs/core-loop.md's post-landing pipeline note
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
                is GeographicAchievementGoal.CountryCountMilestone -> {
                    val current = geo.visitedCountries.size
                    goal.toStatus(
                        category = AchievementCategory.GEOGRAPHIC,
                        current = current.toDouble(),
                        target = goal.targetCount.toDouble(),
                        unitLabel = "countries",
                        isUnlocked = current >= goal.targetCount,
                        members = goal.memberProgress(geo),
                        familyId = GeographicAchievementCatalog.COUNTRY_FAMILY_ID,
                        familyRank = goal.familyRank
                    )
                }
                is GeographicAchievementGoal.AllContinents -> {
                    // Only continents the route network actually reaches (the map has no country
                    // for a continent with no reachable airport), so the target and the checklist
                    // are the same list.
                    val total = geo.continentStats.size
                    val current = geo.continentStats.count { it.continentCode in geo.reachedContinents }
                    goal.toStatus(
                        category = AchievementCategory.GEOGRAPHIC,
                        current = current.toDouble(),
                        target = total.toDouble(),
                        unitLabel = "continents",
                        isUnlocked = total > 0 && current >= total,
                        members = goal.memberProgress(geo)
                    )
                }
                is GeographicAchievementGoal.AllCountries -> {
                    // Distinct reachable countries. A sum over continents used to count the six
                    // countries listed under two continents twice, so the target (239) was larger
                    // than the number of countries that exist and this could never unlock.
                    val world = geo.countryToContinent.keys
                    val total = world.size
                    val current = geo.visitedCountries.count { it in world }
                    goal.toStatus(
                        category = AchievementCategory.GEOGRAPHIC,
                        current = current.toDouble(),
                        target = total.toDouble(),
                        unitLabel = "countries",
                        isUnlocked = total > 0 && current >= total,
                        members = goal.memberProgress(geo),
                        familyId = GeographicAchievementCatalog.COUNTRY_FAMILY_ID,
                        familyRank = 3
                    )
                }
                is GeographicAchievementGoal.EntireContinent -> {
                    val stat = geo.continentStats.find { it.continentCode == goal.continentCode }
                    val total = stat?.totalCountries ?: 0
                    val current = stat?.visitedCountries?.size ?: 0
                    goal.toStatus(
                        category = AchievementCategory.GEOGRAPHIC,
                        current = current.toDouble(),
                        target = total.toDouble(),
                        unitLabel = "countries",
                        isUnlocked = stat?.isCompleted ?: false,
                        members = goal.memberProgress(geo)
                    )
                }
            }
        }

    fun evaluateDistance(flightHistory: List<FlightLog>): List<AchievementStatus> {
        val storyDistanceKm = flightHistory.filter { it.mode == FlightMode.STORY }.sumOf { it.distanceKm }
        // The catalog is ordered ascending by target, so the index *is* the ladder rank - see
        // DistanceAchievementCatalog.ALL's doc.
        return DistanceAchievementCatalog.ALL.mapIndexed { index, milestone ->
            // Stored and compared in km (what the flight log holds); reported in miles, the unit
            // every distance in the UI uses.
            milestone.toStatus(
                category = AchievementCategory.DISTANCE,
                current = kmToMiles(storyDistanceKm),
                target = kmToMiles(milestone.targetKm),
                unitLabel = "mi",
                isUnlocked = storyDistanceKm >= milestone.targetKm,
                familyId = DistanceAchievementCatalog.FAMILY_ID,
                familyRank = index
            )
        }
    }

    // ── Behavioral ids/thresholds ──
    const val MARATHON_ID = "behav_marathon_flight"
    const val RED_EYE_ID = "behav_red_eye_pilot"
    const val HIGH_ALTITUDE_ID = "behav_high_altitude_club"
    const val EQUATOR_CROSSING_ID = "behav_equator_crossing"

    /** 8 hours, in minutes - matches [FlightLog.durationMin]'s unit directly. */
    const val MARATHON_TARGET_MIN = 480.0
    private const val RED_EYE_HOUR_START = 0
    private const val RED_EYE_HOUR_END_EXCLUSIVE = 5 // local midnight..4:59am

    private val HIGH_ALTITUDE_IATAS = setOf(
        "ANS", "JAU", "JUL", "CUZ", "IXL", "BPX", "DIG", "LXA", "UYU", "JZH",
        "YUS", "NGQ", "GXH", "NLH", "GMQ", "DCY", "KGT", "HBQ", "SRE", "GZG",
        "DDR", "HQL", "LGZ", "LPB"
    )

    private val SOUTHERN_CONTINENTS = setOf("SA", "OC", "AN")
    private val NORTHERN_CONTINENTS = setOf("EU", "NA", "AS")

    fun evaluateBehavioral(flightHistory: List<FlightLog>): List<AchievementStatus> {
        val story = flightHistory.filter { it.mode == FlightMode.STORY }

        val longestDurationMin = story.maxOfOrNull { it.durationMin } ?: 0
        val marathon = AchievementStatus(
            id = MARATHON_ID,
            category = AchievementCategory.BEHAVIORAL,
            displayName = "Marathon Flight",
            description = "Complete a single Story Mode flight lasting at least 8 hours (480 minutes).",
            current = longestDurationMin.toDouble(),
            target = MARATHON_TARGET_MIN,
            unitLabel = "min",
            isUnlocked = longestDurationMin >= MARATHON_TARGET_MIN
        )

        val hasRedEye = story.any { isRedEyeLanding(it.completedAt) }
        val redEye = AchievementStatus(
            id = RED_EYE_ID,
            category = AchievementCategory.BEHAVIORAL,
            displayName = "Red-Eye Pilot",
            description = "Land a Story Mode flight between midnight and 5:00 AM local time.",
            current = if (hasRedEye) 1.0 else 0.0,
            target = 1.0,
            unitLabel = "",
            isUnlocked = hasRedEye
        )

        val hasHighAltitude = story.any { it.destIata in HIGH_ALTITUDE_IATAS || it.originIata in HIGH_ALTITUDE_IATAS }
        val highAltitude = AchievementStatus(
            id = HIGH_ALTITUDE_ID,
            category = AchievementCategory.BEHAVIORAL,
            displayName = "High Altitude Club",
            description = "Land at an airport above 10,000 feet elevation in Story Mode.",
            current = if (hasHighAltitude) 1.0 else 0.0,
            target = 1.0,
            unitLabel = "",
            isUnlocked = hasHighAltitude
        )

        val hasEquatorCross = story.any {
            // Distance over 5000 km between long-haul flights or known trans-hemisphere routes
            it.distanceKm >= 5000.0 && (it.originIata in setOf("LHR", "CDG", "FRA", "JFK", "DEL", "PEK", "HND") && it.destIata in setOf("JNB", "CPT", "SYD", "MEL", "EZE", "GRU", "SCL")) ||
            (it.originIata in setOf("JNB", "CPT", "SYD", "MEL", "EZE", "GRU", "SCL") && it.destIata in setOf("LHR", "CDG", "FRA", "JFK", "DEL", "PEK", "HND"))
        }
        val equator = AchievementStatus(
            id = EQUATOR_CROSSING_ID,
            category = AchievementCategory.BEHAVIORAL,
            displayName = "Equator Crossing",
            description = "Complete a Story Mode flight that crosses the Equator.",
            current = if (hasEquatorCross) 1.0 else 0.0,
            target = 1.0,
            unitLabel = "",
            isUnlocked = hasEquatorCross
        )

        return listOf(marathon, redEye, highAltitude, equator)
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
