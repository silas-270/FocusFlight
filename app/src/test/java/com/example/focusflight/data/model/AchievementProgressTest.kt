package com.example.focusflight.data.model

import com.example.focusflight.ui.screens.account.achievementTier
import com.example.focusflight.ui.screens.account.sortOrder
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage for [AchievementProgress] - no Room/AirportRepository/JNI involved, per
 * docs/achievements.md's three progress-bar-shaped categories (Geographic, Distance,
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
            countryToContinent = listOf("EU1", "EU2", "EU3").associateWith { "EU" } +
                listOf("AF1", "AF2", "AF3").associateWith { "AF" },
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
            countryToContinent = listOf("EU1", "EU2", "EU3").associateWith { "EU" } +
                listOf("AF1", "AF2", "AF3").associateWith { "AF" },
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
            flight(distanceKm = 5000.0, mode = FlightMode.STORY),
            flight(distanceKm = 4000.0, mode = FlightMode.STORY)
        )

        val longHaul = AchievementProgress.evaluateDistance(history)
            .single { it.id == "dist_5000_long_haul" }
        assertEquals(9000.0 * 0.621371, longHaul.current, 0.01) // reported in miles
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
        val roundTheWorld = results.single { it.id == "dist_24901_around_the_earth" }

        // Only the STORY flight's 3,000 km counts - not the 100,000 km of FREE/CHALLENGE flying.
        // Reported in miles.
        assertEquals(3000.0 * 0.621371, longHaul.current, 0.01)
        assertFalse(longHaul.isUnlocked)
        assertFalse(roundTheWorld.isUnlocked)
    }

    @Test
    fun `distance milestone unlocks exactly at its target`() {
        val history = listOf(flight(distanceKm = 40_075.0, mode = FlightMode.STORY))
        val roundTheWorld = AchievementProgress.evaluateDistance(history)
            .single { it.id == "dist_24901_around_the_earth" }
        assertTrue(roundTheWorld.isUnlocked)
        assertEquals(1f, roundTheWorld.progress, 0.001f)
    }

    // ── evaluateBehavioral ───────────────────────────────────────────────────────────────

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

    @Test
    fun `high-altitude achievement unlocks when landing at a high elevation airport`() {
        val normalFlight = listOf(flight(distanceKm = 100.0, mode = FlightMode.STORY).copy(originIata = "LHR", destIata = "CDG"))
        assertFalse(AchievementProgress.evaluateBehavioral(normalFlight).single { it.id == AchievementProgress.HIGH_ALTITUDE_ID }.isUnlocked)

        val highFlight = listOf(flight(distanceKm = 100.0, mode = FlightMode.STORY).copy(originIata = "DEL", destIata = "IXL"))
        assertTrue(AchievementProgress.evaluateBehavioral(highFlight).single { it.id == AchievementProgress.HIGH_ALTITUDE_ID }.isUnlocked)
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

    // ── Achievement Tier Sorting ─────────────────────────────────────────────────────────

    @Test
    fun `achievement tier sorting orders diamond then gold then silver then ruby`() {
        val diamondItem = AchievementStatus(
            id = "geo_all_countries",
            displayName = "World Traveler",
            description = "Every country",
            category = AchievementCategory.GEOGRAPHIC,
            current = 1.0,
            target = 1.0,
            unitLabel = "countries",
            isUnlocked = true,
            unlockedAt = 1000L
        )
        val goldItem = AchievementStatus(
            id = "geo_entire_EU",
            displayName = "Master of Europe",
            description = "Every European country",
            category = AchievementCategory.GEOGRAPHIC,
            current = 1.0,
            target = 1.0,
            unitLabel = "countries",
            isUnlocked = true,
            unlockedAt = 1500L
        )
        val silverItem = AchievementStatus(
            id = "dist_24901_around_the_earth",
            displayName = "Around the Earth",
            description = "Fly 24,901 mi",
            category = AchievementCategory.DISTANCE,
            current = 40075.0,
            target = 40075.0,
            unitLabel = "km",
            isUnlocked = true,
            unlockedAt = 2000L
        )
        val rubyItem = AchievementStatus(
            id = AchievementProgress.RED_EYE_ID,
            displayName = "Red-Eye Pilot",
            description = "Land at night",
            category = AchievementCategory.BEHAVIORAL,
            current = 1.0,
            target = 1.0,
            unitLabel = "",
            isUnlocked = true,
            unlockedAt = 3000L
        )

        val list = listOf(rubyItem, goldItem, diamondItem, silverItem)
        val sorted = list.sortedWith(
            compareBy<AchievementStatus> {
                achievementTier(it).sortOrder
            }.thenByDescending { it.unlockedAt ?: Long.MIN_VALUE }
        )

        assertEquals(listOf(diamondItem, goldItem, silverItem, rubyItem), sorted)
    }

    // ── Set membership (GeographicAchievementGoal.memberProgress) ────────────────────────

    /** A geography where Africa has 3 of 4 countries visited. `continentStats` derives
     *  `missingCountries` as "AF1".."AF4" minus what's visited, so the missing one is "AF4". */
    private fun africaGeo() = VisitedGeography(
        visitedCountries = setOf("AF1", "AF2", "AF3"),
        countryToContinent = mapOf(
            "AF1" to "AF", "AF2" to "AF", "AF3" to "AF", "AF4" to "AF"
        ),
        continentStats = listOf(continentStats("AF", 4, setOf("AF1", "AF2", "AF3"))),
        completedContinents = emptySet()
    )

    @Test
    fun `entire-continent members split visited from missing`() {
        val members = GeographicAchievementGoal.EntireContinent("AF", "Africa")
            .memberProgress(africaGeo())

        assertEquals(4, members.size)
        assertEquals(setOf("AF1", "AF2", "AF3"), members.filter { it.isVisited }.map { it.id }.toSet())
        assertEquals(setOf("AF4"), members.filterNot { it.isVisited }.map { it.id }.toSet())
    }

    @Test
    fun `entire-continent members are ordered visited-first`() {
        val members = GeographicAchievementGoal.EntireContinent("AF", "Africa")
            .memberProgress(africaGeo())

        // Every visited member precedes every unvisited one, so a long checklist opens on what has
        // been earned rather than on a wall of blanks.
        assertEquals(
            members.sortedByDescending { it.isVisited }.map { it.id },
            members.map { it.id }
        )
    }

    @Test
    fun `entire-continent members are empty for a continent the geography knows nothing about`() {
        // Mirrors evaluateGeographic's existing 0/0 posture: an unknown code is not a crash.
        val members = GeographicAchievementGoal.EntireContinent("ZZ", "Nowhere")
            .memberProgress(africaGeo())

        assertTrue(members.isEmpty())
    }

    @Test
    fun `evaluateGeographic attaches a member checklist to every geographic achievement`() {
        // The whole point of making memberProgress abstract: a geographic goal can never ship as a
        // bare progress fraction with no way to see which members are missing.
        val statuses = AchievementProgress.evaluateGeographic(africaGeo())

        assertTrue(statuses.isNotEmpty())
        assertTrue(statuses.all { it.members != null })
    }

    @Test
    fun `all-continents members are exactly the continents the geography contains`() {
        val geo = africaGeo()
        val members = GeographicAchievementGoal.AllContinents.memberProgress(geo)

        // Only continents the route network reaches, so the checklist matches the progress target.
        assertEquals(
            geo.continentStats.map { it.continentCode }.toSet(),
            members.map { it.id }.toSet()
        )
        assertTrue(members.all { it.id in CuratedChallengeSets.ALL_CONTINENTS.members })
        // Africa has visits; nothing else in this geography does.
        assertEquals(listOf("AF"), members.filter { it.isVisited }.map { it.id })
    }

    // ── Distance ladder ─────────────────────────────────────────────────────────────────

    @Test
    fun `distance milestones share one family id and rank ascending by target`() {
        val statuses = AchievementProgress.evaluateDistance(listOf(flight(distanceKm = 1.0)))

        assertTrue(statuses.all { it.familyId == DistanceAchievementCatalog.FAMILY_ID })
        assertEquals(statuses.indices.toList(), statuses.map { it.familyRank })
        // Rank has to track difficulty, or the Passport's stack would show the wrong card on top.
        assertEquals(
            statuses.sortedBy { it.target }.map { it.id },
            statuses.sortedBy { it.familyRank }.map { it.id }
        )
    }

    @Test
    fun `standalone non-distance achievements belong to no ladder`() {
        val standaloneGeographic = AchievementProgress.evaluateGeographic(africaGeo())
            .filter { it.familyId == null }
        val behavioral = AchievementProgress.evaluateBehavioral(listOf(flight(distanceKm = 1.0)))

        assertTrue((standaloneGeographic + behavioral).all { it.familyId == null })
    }
}
