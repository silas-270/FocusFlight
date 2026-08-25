package com.example.focusflight.data.repository

import androidx.paging.PagingSource
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightHighlights
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.model.FlightSortOrder
import com.example.focusflight.data.model.FlightStats
import com.example.focusflight.data.model.Runway
import com.example.focusflight.data.model.UserProfile
import com.example.focusflight.testutil.FakeSharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFlightLogMigratorTest {

    private class FakeUserRepository(private var profile: UserProfile? = null) : UserRepository {
        var created: Pair<String, String>? = null
        override fun getProfileFlow(): Flow<UserProfile?> = MutableStateFlow(profile)
        override suspend fun getProfile(): UserProfile? = profile
        override suspend fun createProfile(username: String, homeAirportIata: String): UserProfile {
            created = username to homeAirportIata
            val newProfile = UserProfile(
                id = 1,
                username = username,
                userCode = "TEST01",
                homeAirportIata = homeAirportIata
            )
            profile = newProfile
            return newProfile
        }
        override suspend fun updateUsername(username: String) = Unit
        override suspend fun updateHomeAirport(iata: String) = Unit
    }

    private class FakeFlightLogRepository : FlightLogRepository {
        val loggedFlights = mutableListOf<FlightLog>()
        override suspend fun logFlight(
            flightNumber: String,
            originIata: String,
            destIata: String,
            durationMin: Int,
            distanceKm: Double
        ): FlightLog {
            val log = FlightLog(
                id = loggedFlights.size,
                userId = 1,
                flightNumber = flightNumber,
                originIata = originIata,
                destIata = destIata,
                durationMin = durationMin,
                distanceKm = distanceKm,
                completedAt = 0L
            )
            loggedFlights.add(log)
            return log
        }
        override fun getFlightHistoryFlow(): Flow<List<FlightLog>> = MutableStateFlow(emptyList())
        override suspend fun getFlightHistory(): List<FlightLog> = emptyList()
        override suspend fun getRecentFlights(limit: Int): List<FlightLog> = emptyList()
        override suspend fun getFlightStats(): FlightStats = FlightStats()
        override fun getFlightsPagingSource(sortOrder: FlightSortOrder): PagingSource<Int, FlightLog> =
            throw NotImplementedError("unused in this test")
        override suspend fun getFlightHighlights(): FlightHighlights = FlightHighlights()
    }

    private class FakeAirportRepository(private val routesByOrigin: Map<String, List<FlightRoute>>) : AirportRepository {
        override fun ensureDatabaseCopied() = Unit
        override fun searchAirports(query: String): List<Airport> = emptyList()
        override fun getAirportByIata(iataCode: String): Airport? = null
        override fun getRunwaysForAirport(airportId: Int): List<Runway> = emptyList()
        override fun getOutboundRoutes(originIata: String, searchQuery: String, sortBy: String): List<FlightRoute> =
            routesByOrigin[originIata] ?: emptyList()
        override fun getContinentCountryMap(): Map<String, Set<String>> = emptyMap()
        override fun getCountriesForAirports(iatas: List<String>): Set<String> = emptySet()
    }

    private fun route(destIata: String, distanceKm: Double) = FlightRoute(
        id = 1,
        originIata = "STR",
        destIata = destIata,
        distanceKm = distanceKm,
        flightTimeMin = 120,
        carriers = "",
        destName = "",
        destMunicipality = "",
        destCountry = "",
        destLat = 0.0,
        destLon = 0.0
    )

    @Test
    fun `no legacy key means no migration and no profile creation`() = runTest {
        val prefs = FakeSharedPreferences()
        val userRepo = FakeUserRepository()
        val flightLogRepo = FakeFlightLogRepository()
        val migrator = LegacyFlightLogMigrator(prefs, userRepo, flightLogRepo, FakeAirportRepository(emptyMap()))

        migrator.migrateIfNeeded()

        assertTrue(flightLogRepo.loggedFlights.isEmpty())
        assertNull(userRepo.created)
    }

    @Test
    fun `migrates legacy entries, looks up distance, and clears the legacy key`() = runTest {
        val prefs = FakeSharedPreferences()
        prefs.putStringSetForTest("flight_logs_set", setOf("STR|JFK|480|FF-123"))
        val userRepo = FakeUserRepository(profile = UserProfile(id = 1, username = "Cap", userCode = "ABC123", homeAirportIata = "STR"))
        val flightLogRepo = FakeFlightLogRepository()
        val airportRepo = FakeAirportRepository(mapOf("STR" to listOf(route("JFK", 6700.0), route("FRA", 100.0))))
        val migrator = LegacyFlightLogMigrator(prefs, userRepo, flightLogRepo, airportRepo)

        migrator.migrateIfNeeded()

        assertEquals(1, flightLogRepo.loggedFlights.size)
        val logged = flightLogRepo.loggedFlights.first()
        assertEquals("STR", logged.originIata)
        assertEquals("JFK", logged.destIata)
        assertEquals(480, logged.durationMin)
        assertEquals("FF-123", logged.flightNumber)
        assertEquals(6700.0, logged.distanceKm, 0.0)
        assertTrue(prefs.getStringSet("flight_logs_set", null).isNullOrEmpty())
    }

    @Test
    fun `defaults distance to zero when no matching route is found`() = runTest {
        val prefs = FakeSharedPreferences()
        prefs.putStringSetForTest("flight_logs_set", setOf("STR|XXX|90|FF-999"))
        val userRepo = FakeUserRepository(profile = UserProfile(id = 1, username = "Cap", userCode = "ABC123", homeAirportIata = "STR"))
        val flightLogRepo = FakeFlightLogRepository()
        val airportRepo = FakeAirportRepository(mapOf("STR" to listOf(route("JFK", 6700.0))))
        val migrator = LegacyFlightLogMigrator(prefs, userRepo, flightLogRepo, airportRepo)

        migrator.migrateIfNeeded()

        assertEquals(0.0, flightLogRepo.loggedFlights.single().distanceKm, 0.0)
    }

    @Test
    fun `creates a profile using the fallback home airport when none exists`() = runTest {
        val prefs = FakeSharedPreferences()
        prefs.putStringSetForTest("flight_logs_set", setOf("STR|JFK|480|FF-123"))
        val userRepo = FakeUserRepository(profile = null)
        val flightLogRepo = FakeFlightLogRepository()
        val airportRepo = FakeAirportRepository(emptyMap())
        val migrator = LegacyFlightLogMigrator(
            prefs, userRepo, flightLogRepo, airportRepo,
            fallbackHomeAirportIata = { "FRA" }
        )

        migrator.migrateIfNeeded()

        assertEquals("FRA", userRepo.created?.second)
    }

    @Test
    fun `malformed entries are skipped without aborting the whole migration`() = runTest {
        val prefs = FakeSharedPreferences()
        prefs.putStringSetForTest("flight_logs_set", setOf("not-enough-fields", "STR|JFK|480|FF-123"))
        val userRepo = FakeUserRepository(profile = UserProfile(id = 1, username = "Cap", userCode = "ABC123", homeAirportIata = "STR"))
        val flightLogRepo = FakeFlightLogRepository()
        val airportRepo = FakeAirportRepository(mapOf("STR" to listOf(route("JFK", 6700.0))))
        val migrator = LegacyFlightLogMigrator(prefs, userRepo, flightLogRepo, airportRepo)

        migrator.migrateIfNeeded()

        assertEquals(1, flightLogRepo.loggedFlights.size)
    }
}
