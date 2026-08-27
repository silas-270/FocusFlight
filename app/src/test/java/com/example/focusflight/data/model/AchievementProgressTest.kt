package com.example.focusflight.data.model

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage for [AchievementProgress] - no Room/AirportRepository/JNI involved, per
 * docs/design/achievements.md's three progress-bar-shaped categories (Geographic, Distance,
 * Behavioral). Mirrors [ChallengeProgressTest]'s pattern.
 *
 * The isolation tests below (Free Mode / Challenge-mode flights must never count) are the ones
 * called out explicitly in this phase's brief as the most likely thing to get subtly wrong -
 * mirroring how ChallengeLandingTest/LocalChallengeRepositoryTest test the equivalent isolation
 * rule for Challenges.
 */
class AchievementProgressTest {

    private var nextId = 1

    private fun flight(
        distanceKm: Double,
        durationMin: Int = 60,
        mode: FlightMode = FlightMode.STORY,
        completedAt: Long = System.currentTimeMillis()
    ) = FlightLog(
        id = nextId++,
        userId = 1,
        flightNumber = "FF${nextId}",
        originIata = "AAA",
        destIata = "BBB",
        durationMin = durationMin,
        distanceKm = distanceKm,
        completedAt = completedAt,
        mode = mode
    )

    /** A timestamp guaranteed to fall at [hour] o'clock in the JVM's default (local) timezone -
     *  the same basis [AchievementProgress] itself uses via `Calendar.getInstance()`, so this
     *  stays correct regardless of which timezone the test machine happens to be in. */
    private fun epochAtHour(hour: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 30)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun continentStats(
        code: String,
        total: Int,
        visited: Set<String>
    ) = ContinentStats(
        continentCode = code,
        totalCountries = total,
        visitedCountries = visited,
        missingCountries = (1..total).map { "$code$it" }.toSet() - visited,
        isCompleted = visited.size >= total && total > 0
    )

    // ── evaluateGeographic ───────────────────────────────────────────────────────────────

    @Test
    fun `all-continents progress counts continents with at least one visited country`() {
        val geo = VisitedGeography(
            visitedCountries = setOf("EU1", "AF1"),
            countryToContinent = emptyMap(),
            continentStats = listOf(
                continentStats("EU", 5, setOf("EU1")),
                continentStats("AF", 5, setOf("AF1")),
                continentStats("AS", 5, emptySet()),
                continentStats("NA", 5, emptySet()),
                continentStats("SA", 5, emptySet()),
                continentStats("OC", 5, emptySet()),
                continentStats("AN", 5, emptySet())
            ),
            completedContinents = emptySet()
        )

        val result = AchievementProgress.evaluateGeographic(geo)
        val allContinents = result.single { it.id == GeographicAchievementGoal.AllContinents.id }

        assertEquals(2.0, allContinents.current, 0.001)
        assertEquals(7.0, allContinents.target, 0.001)
        assertFalse(allContinents.isUnlocked)
    }

    @Test
    fun `all-continents achievement unlocks once every continent has at least one visited country`() {
        val stats = listOf("EU", "AF", "AS", "NA", "SA", "OC", "AN").map { continentStats(it, 3, setOf("${it}1")) }
        val geo = VisitedGeography(
            visitedCountries = stats.flatMap { it.visitedCountries }.toSet(),
            countryToContinent = emptyMap(),
            continentStats = stats,
            completedContinents = emptySet()
        )

        val allContinents = AchievementProgress.evaluateGeographic(geo)
            .single { it.id == GeographicAchievementGoal.AllContinents.id }
        assertTrue(allContinents.isUnlocked)
        assertEquals(1f, allContinents.progress, 0.001f)
    }

    @Test
    fun `entire-continent achievement mirrors ContinentStats isCompleted`() {
        val geo = VisitedGeography(
            visitedCountries = setOf("EU1", "EU2"),
            countryToContinent = emptyMap(),
            continentStats = listOf(continentStats("EU", 2, setOf("EU1", "EU2"))),
            completedContinents = setOf("EU")
        )

        val entireEurope = AchievementProgress.evaluateGeographic(geo)
            .single { it.id == "geo_entire_EU" }
        assertTrue(entireEurope.isUnlocked)
        assertEquals(2.0, entireEurope.current, 0.001)
        assertEquals(2.0, entireEurope.target, 0.001)
    }

    @Test
    fun `entire-continent achievement not in the catalog reports zero total rather than crashing`() {
        // No Africa entry in continentStats at all (e.g. world-map data not yet loaded).
        val geo = VisitedGeography(
            visitedCountries = emptySet(),
            countryToContinent = emptyMap(),
            continentStats = listOf(continentStats("EU", 2, emptySet())),
            completedContinents = emptySet()
        )

        val entireAfrica = AchievementProgress.evaluateGeographic(geo).single { it.id == "geo_entire_AF" }
        assertEquals(0.0, entireAfrica.current, 0.001)
        assertEquals(0.0, entireAfrica.target, 0.001)
        assertFalse(entireAfrica.isUnlocked)
    }

    @Test
    fun `all-countries progress is total visited over total countries across all continents`() {
        val geo = VisitedGeography(
            visitedCountries = setOf("EU1", "AF1"),
            countryToContinent = emptyMap(),
            continentStats = listOf(
                continentStats("EU", 3, setOf("EU1")),
                continentStats("AF", 3, setOf("AF1"))
            ),
            completedContinents = emptySet()
        )

        val allCountries = AchievementProgress.evaluateGeographic(geo)
            .single { it.id == GeographicAchievementGoal.AllCountries.id }
        assertEquals(2.0, allCountries.current, 0.001)
        assertEquals(6.0, allCountries.target, 0.001)
        assertFalse(allCountries.isUnlocked)
    }

    // ── evaluateDistance ─────────────────────────────────────────────────────────────────

    @Test
    fun `distance milestones sum STORY-mode distance only`() {
        val history = listOf(
            flight(distanceKm = 3000.0, mode = FlightMode.STORY),
            flight(distanceKm = 2500.0, mode = FlightMode.STORY)
        )

        val longHaul = AchievementProgress.evaluateDistance(history)
            .single { it.id == "dist_5000_long_haul" }
        assertEquals(5500.0, longHaul.current, 0.001)
        assertTrue(longHaul.isUnlocked)
    }

    @Test
    fun `FREE and CHALLENGE mode flights never count toward distance milestones`() {
        val history = listOf(
            flight(distanceKm = 3000.0, mode = FlightMode.STORY),
            flight(distanceKm = 50_000.0, mode = FlightMode.FREE),      // would blow past every milestone
            flight(distanceKm = 50_000.0, mode = FlightMode.CHALLENGE)  // ditto
        )

        val results = AchievementProgress.evaluateDistance(history)
        val longHaul = results.single { it.id == "dist_5000_long_haul" }
        val roundTheWorld = results.single { it.id == "dist_40075_round_the_world" }

        // Only the STORY flight's 3,000 km counts - not the 100,000 km of FREE/CHALLENGE flying.
        assertEquals(3000.0, longHaul.current, 0.001)
        assertFalse(longHaul.isUnlocked)
        assertFalse(roundTheWorld.isUnlocked)
    }

    @Test
    fun `distance milestone unlocks exactly at its target`() {
        val history = listOf(flight(distanceKm = 40_075.0, mode = FlightMode.STORY))
        val roundTheWorld = AchievementProgress.evaluateDistance(history)
            .single { it.id == "dist_40075_round_the_world" }
        assertTrue(roundTheWorld.isUnlocked)
        assertEquals(1f, roundTheWorld.progress, 0.001f)
    }

    // ── evaluateBehavioral ───────────────────────────────────────────────────────────────

    @Test
    fun `first-flight achievement is locked with no Story Mode flights and unlocked with one`() {
        assertFalse(AchievementProgress.evaluateBehavioral(emptyList()).single { it.id == AchievementProgress.FIRST_FLIGHT_ID }.isUnlocked)

        val history = listOf(flight(distanceKm = 100.0, mode = FlightMode.STORY))
        assertTrue(AchievementProgress.evaluateBehavioral(history).single { it.id == AchievementProgress.FIRST_FLIGHT_ID }.isUnlocked)
    }

    @Test
    fun `first-flight achievement stays locked when only FREE or CHALLENGE flights exist`() {
        val history = listOf(
            flight(distanceKm = 100.0, mode = FlightMode.FREE),
            flight(distanceKm = 200.0, mode = FlightMode.CHALLENGE)
        )
        val firstFlight = AchievementProgress.evaluateBehavioral(history).single { it.id == AchievementProgress.FIRST_FLIGHT_ID }
        assertFalse(firstFlight.isUnlocked)
    }

    @Test
    fun `marathon-flight achievement uses the longest STORY-mode duration only`() {
        val history = listOf(
            flight(distanceKm = 100.0, durationMin = 600, mode = FlightMode.FREE), // longer, but FREE - excluded
            flight(distanceKm = 100.0, durationMin = 500, mode = FlightMode.STORY)
        )
        val marathon = AchievementProgress.evaluateBehavioral(history).single { it.id == AchievementProgress.MARATHON_ID }
        assertEquals(500.0, marathon.current, 0.001)
        assertTrue(marathon.isUnlocked) // 500 >= MARATHON_TARGET_MIN (480)
    }

    @Test
    fun `grand-voyage achievement uses the longest single STORY-mode flight distance, not cumulative`() {
        val history = listOf(
            flight(distanceKm = 6000.0, mode = FlightMode.STORY),
            flight(distanceKm = 6000.0, mode = FlightMode.STORY)
        )
        // Cumulative (12,000 km) would clear the 10,000 km bar, but the achievement is per-flight.
        val grandVoyage = AchievementProgress.evaluateBehavioral(history).single { it.id == AchievementProgress.GRAND_VOYAGE_ID }
        assertEquals(6000.0, grandVoyage.current, 0.001)
        assertFalse(grandVoyage.isUnlocked)
    }

    @Test
    fun `red-eye-pilot achievement unlocks only for a STORY flight landing between midnight and 5am local`() {
        val daytimeOnly = listOf(flight(distanceKm = 100.0, mode = FlightMode.STORY, completedAt = epochAtHour(14)))
        assertFalse(AchievementProgress.evaluateBehavioral(daytimeOnly).single { it.id == AchievementProgress.RED_EYE_ID }.isUnlocked)

        val withRedEye = daytimeOnly + flight(distanceKm = 100.0, mode = FlightMode.STORY, completedAt = epochAtHour(2))
        assertTrue(AchievementProgress.evaluateBehavioral(withRedEye).single { it.id == AchievementProgress.RED_EYE_ID }.isUnlocked)
    }

    @Test
    fun `red-eye-pilot achievement ignores a FREE-mode flight landing at 2am`() {
        val history = listOf(flight(distanceKm = 100.0, mode = FlightMode.FREE, completedAt = epochAtHour(2)))
        assertFalse(AchievementProgress.evaluateBehavioral(history).single { it.id == AchievementProgress.RED_EYE_ID }.isUnlocked)
    }

    // ── evaluateAll ──────────────────────────────────────────────────────────────────────

    @Test
    fun `evaluateAll groups all three progress-bar categories`() {
        val geo = VisitedGeography(
            visitedCountries = emptySet(),
            countryToContinent = emptyMap(),
            continentStats = emptyList(),
            completedContinents = emptySet()
        )
        val board = AchievementProgress.evaluateAll(geo, emptyList())
        assertEquals(GeographicAchievementCatalog.ALL.size, board.geographic.size)
        assertEquals(DistanceAchievementCatalog.ALL.size, board.distance.size)
        assertEquals(4, board.behavioral.size)
    }
}
