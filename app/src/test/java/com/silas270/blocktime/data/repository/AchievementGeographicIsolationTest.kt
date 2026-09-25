package com.silas270.blocktime.data.repository

import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.data.model.FlightLog
import com.silas270.blocktime.data.model.FlightMode
import com.silas270.blocktime.data.model.FlightRoute
import com.silas270.blocktime.data.model.Runway
import com.silas270.blocktime.data.model.AchievementProgress
import com.silas270.blocktime.data.model.GeographicAchievementGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * End-to-end (still plain-JVM, no Room) proof that a Free Mode or Challenge-mode flight can never
 * move a Geographic achievement's progress - the isolation rule this phase's brief calls out as
 * the one most likely to get subtly wrong, mirroring how `LocalChallengeRepositoryTest` covers
 * Set-completion challenges' equivalent "starts fresh" isolation.
 *
 * Exercises the real, unmodified [AirportRepository.getVisitedGeography] default method (only
 * [FakeAirportRepository.getCountriesForAirports]/[FakeAirportRepository.getContinentCountryMap]
 * need a real implementation for this path) feeding straight into
 * [AchievementProgress.evaluateGeographic] - the same two calls `AccountViewModel` makes.
 */
class AchievementGeographicIsolationTest {

    /** Two continents, two countries each - just enough to exercise real set-membership math,
     *  not a full world map. */
    private val countryByIata = mapOf(
        "LHR" to "GB", "CDG" to "FR", // EU
        "JFK" to "US", "YYZ" to "CA"  // NA
    )
    private val continentMap = mapOf(
        "EU" to setOf("GB", "FR"),
        "NA" to setOf("US", "CA")
    )

    private inner class FakeAirportRepository : AirportRepository {
        override fun ensureDatabaseCopied() = Unit
        override fun searchAirports(query: String): List<Airport> = emptyList()
        override fun getAirportByIata(iataCode: String): Airport? = null
        override fun getRunwaysForAirport(airportId: Int): List<Runway> = emptyList()
        override fun getOutboundRoutes(originIata: String, searchQuery: String, sortBy: String): List<FlightRoute> = emptyList()
        override fun getContinentCountryMap(): Map<String, Set<String>> = continentMap
        override fun getCountriesForAirports(iatas: List<String>): Set<String> =
            iatas.mapNotNull { countryByIata[it] }.toSet()
    }

    private var nextId = 1
    private fun flight(destIata: String, mode: FlightMode) = FlightLog(
        id = nextId++,
        userId = 1,
        flightNumber = "FF$nextId",
        originIata = "LHR",
        destIata = destIata,
        durationMin = 60,
        distanceKm = 500.0,
        completedAt = System.currentTimeMillis(),
        mode = mode
    )

    @Test
    fun `a FREE-mode landing in a brand-new country never advances a Geographic achievement`() {
        val repo = FakeAirportRepository()
        // Only ever visited GB (STORY) - the FREE flight to the US should count for nothing.
        val history = listOf(
            flight("LHR", FlightMode.STORY),
            flight("JFK", FlightMode.FREE)
        )

        val geo = repo.getVisitedGeography(history, homeAirportIata = null)
        val achievements = AchievementProgress.evaluateGeographic(geo)
        val allContinents = achievements.single { it.id == GeographicAchievementGoal.AllContinents.id }

        // Only EU (from GB) reached - NA (from the FREE-mode JFK landing) must not count.
        assertEquals(1.0, allContinents.current, 0.001)
        assertFalse(allContinents.isUnlocked)
        assertFalse("US" in geo.visitedCountries)
    }

    @Test
    fun `a CHALLENGE-mode landing in a brand-new country never advances a Geographic achievement`() {
        val repo = FakeAirportRepository()
        val history = listOf(
            flight("LHR", FlightMode.STORY),
            flight("YYZ", FlightMode.CHALLENGE)
        )

        val geo = repo.getVisitedGeography(history, homeAirportIata = null)
        val achievements = AchievementProgress.evaluateGeographic(geo)
        val allContinents = achievements.single { it.id == GeographicAchievementGoal.AllContinents.id }

        assertEquals(1.0, allContinents.current, 0.001)
        assertFalse(allContinents.isUnlocked)
        assertFalse("CA" in geo.visitedCountries)
    }

    @Test
    fun `mixing STORY flights across both continents does unlock the all-continents achievement`() {
        val repo = FakeAirportRepository()
        val history = listOf(
            flight("LHR", FlightMode.STORY), // EU
            flight("JFK", FlightMode.STORY)  // NA
        )

        val geo = repo.getVisitedGeography(history, homeAirportIata = null)
        val allContinents = AchievementProgress.evaluateGeographic(geo)
            .single { it.id == GeographicAchievementGoal.AllContinents.id }

        assertEquals(2.0, allContinents.current, 0.001)
        assertEquals(2.0, allContinents.target, 0.001)
        assert(allContinents.isUnlocked)
    }

    @Test
    fun `a STORY flight's origin counts as visited, so an old home base is never un-visited`() {
        val repo = FakeAirportRepository()
        // Departed from JFK (the old home) in Story Mode; home has since moved to CDG.
        val history = listOf(flight("LHR", FlightMode.STORY).copy(originIata = "JFK"))

        val geo = repo.getVisitedGeography(history, homeAirportIata = "CDG")

        assertEquals(setOf("US", "GB", "FR"), geo.visitedCountries)
    }

    @Test
    fun `a FREE flight's origin does not count as visited`() {
        val repo = FakeAirportRepository()
        val history = listOf(flight("LHR", FlightMode.FREE).copy(originIata = "JFK"))

        val geo = repo.getVisitedGeography(history, homeAirportIata = null)

        assertFalse("US" in geo.visitedCountries)
    }
}
