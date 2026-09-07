package com.example.focusflight.data.repository

import com.example.focusflight.data.local.airport.AirportDataException
import com.example.focusflight.data.model.AchievementBoard
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightHighlights
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.model.FlightSortOrder
import com.example.focusflight.data.model.FlightStats
import com.example.focusflight.data.model.Runway
import com.example.focusflight.data.model.UserProfile
import com.example.focusflight.data.model.VisitedGeography
import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 3's shared derivation. The behaviours worth pinning are the ones that are invisible when
 * they break: that a failed derivation leaves the previous good snapshot standing rather than
 * blanking every screen at once, and that the recompute key is narrow enough that editing a
 * username does not re-scan the airports database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PilotProgressRepositoryTest {

    private val profiles = MutableStateFlow<UserProfile?>(null)
    private val histories = MutableStateFlow<List<FlightLog>>(emptyList())

    /** Number of full derivations performed - the thing `distinctUntilChanged` is there to keep down. */
    private var derivations = 0

    /** When set, the next geography derivation throws, simulating a transient airport-DB failure. */
    private var failNextGeography = false

    private val users = object : UserRepository {
        override fun getProfileFlow(): Flow<UserProfile?> = profiles
        override suspend fun getProfile(): UserProfile? = profiles.value
        override suspend fun createProfile(username: String, homeAirportIata: String) =
            UserProfile(username = username, userCode = "#T", homeAirportIata = homeAirportIata)
        override suspend fun updateUsername(username: String) = Unit
        override suspend fun updateHomeAirport(iata: String) = Unit
    }

    private val flightLogs = object : FlightLogRepository {
        override suspend fun logFlight(
            flightNumber: String, originIata: String, destIata: String,
            durationMin: Int, distanceKm: Double, mode: FlightMode
        ): FlightLog = throw UnsupportedOperationException()

        override fun getFlightHistoryFlow(): Flow<List<FlightLog>> = histories
        override suspend fun getFlightHistory(): List<FlightLog> = histories.value
        override suspend fun getRecentFlights(limit: Int): List<FlightLog> = histories.value
        override suspend fun getFlightStats(homeAirportIata: String?) =
            FlightStats(totalFlights = histories.value.size, totalMinutes = 0, airportsVisited = 1)
        override suspend fun getFlightHighlights() = FlightHighlights()
    }

    private val airports = object : AirportRepository {
        override fun ensureDatabaseCopied() = Unit
        override fun searchAirports(query: String): List<Airport> = emptyList()
        override fun getAirportByIata(iataCode: String): Airport? = null
        override fun getRunwaysForAirport(airportId: Int): List<Runway> = emptyList()
        override fun getOutboundRoutes(originIata: String, searchQuery: String, sortBy: String): List<FlightRoute> = emptyList()
        override fun getContinentCountryMap(): Map<String, Set<String>> = emptyMap()
        override fun getCountriesForAirports(iatas: List<String>): Set<String> = emptySet()

        // Overriding the default method directly: this test is about the repository's flow
        // behaviour, not about geography maths, which AchievementGeographicIsolationTest covers.
        override fun getVisitedGeography(flightHistory: List<FlightLog>, homeAirportIata: String?): VisitedGeography {
            if (failNextGeography) {
                failNextGeography = false
                throw AirportDataException("simulated airport DB failure")
            }
            derivations++
            return VisitedGeography(
                visitedCountries = setOfNotNull(homeAirportIata),
                countryToContinent = emptyMap(),
                continentStats = emptyList(),
                completedContinents = emptySet()
            )
        }
    }

    private val achievements = object : AchievementsRepository {
        override suspend fun evaluateBoard(geo: VisitedGeography, history: List<FlightLog>) =
            AchievementBoard(emptyList(), emptyList(), emptyList())
        override suspend fun loadBoard() = AchievementBoard(emptyList(), emptyList(), emptyList())
    }

    private fun profile(home: String, name: String = "Pilot") =
        UserProfile(id = 1, username = name, userCode = "#T", homeAirportIata = home)

    /**
     * `progress` is a `stateIn(..., SharingStarted.Eagerly)`, and on `StandardTestDispatcher` the
     * sharing coroutine and the inner `flatMapLatest` collection sit queued rather than running -
     * so the flow produces nothing no matter how far the scheduler is advanced. An unconfined
     * dispatcher runs each emission eagerly at its suspension point, which is what makes the
     * derived value observable synchronously here. This is a test-harness concern only; production
     * collects on Dispatchers.IO as normal.
     */
    private fun TestScope.repository(): PilotProgressRepository =
        PilotProgressRepository(
            users, flightLogs, airports, achievements,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )

    @Test
    fun `no profile yields null rather than attempting a derivation`() = runTest {
        val repo = repository()
        advanceUntilIdle()

        assertNull(repo.progress.value)
        assertEquals("nothing should be derived before onboarding", 0, derivations)
    }

    @Test
    fun `a profile produces a snapshot`() = runTest {
        val repo = repository()
        profiles.value = profile("STR")
        advanceUntilIdle()

        val snapshot = repo.progress.value
        assertNotNull(snapshot)
        assertEquals("STR", snapshot!!.homeAirportIata)
        assertEquals(setOf("STR"), snapshot.geography.visitedCountries)
    }

    @Test
    fun `a failed derivation keeps the previous snapshot instead of blanking it`() = runTest {
        val repo = repository()
        profiles.value = profile("STR")
        advanceUntilIdle()
        val good = repo.progress.value
        assertNotNull(good)

        // A new flight lands while the airport DB is briefly unreadable.
        failNextGeography = true
        histories.value = listOf(
            FlightLog(
                userId = 1, flightNumber = "FF1", originIata = "STR", destIata = "LHR",
                durationMin = 90, distanceKm = 800.0, completedAt = 1L, mode = FlightMode.STORY
            )
        )
        advanceUntilIdle()

        assertEquals(
            "a transient failure must not replace real progress with an empty snapshot",
            good,
            repo.progress.value
        )
    }

    @Test
    fun `the flow recovers on the next emission after a failure`() = runTest {
        val repo = repository()
        profiles.value = profile("STR")
        advanceUntilIdle()

        failNextGeography = true
        histories.value = listOf(
            FlightLog(
                userId = 1, flightNumber = "FF1", originIata = "STR", destIata = "LHR",
                durationMin = 90, distanceKm = 800.0, completedAt = 1L, mode = FlightMode.STORY
            )
        )
        advanceUntilIdle()

        // A subsequent write must not find the subscription dead - the earlier throw has to have
        // been swallowed inside the map, not propagated out of the flow.
        histories.value = histories.value + FlightLog(
            userId = 1, flightNumber = "FF2", originIata = "LHR", destIata = "JFK",
            durationMin = 400, distanceKm = 5500.0, completedAt = 2L, mode = FlightMode.STORY
        )
        advanceUntilIdle()

        assertEquals(2, repo.progress.value?.history?.size)
    }

    @Test
    fun `editing the username does not trigger a recompute`() = runTest {
        val repo = repository()
        profiles.value = profile("STR")
        advanceUntilIdle()
        val before = derivations

        profiles.value = profile("STR", name = "Renamed")
        advanceUntilIdle()

        assertEquals(
            "a username edit cannot change any derived number, so it must not re-scan the airports DB",
            before,
            derivations
        )
    }

    @Test
    fun `changing the home base does trigger a recompute`() = runTest {
        val repo = repository()
        profiles.value = profile("STR")
        advanceUntilIdle()
        val before = derivations

        profiles.value = profile("MUC")
        advanceUntilIdle()

        assertEquals(before + 1, derivations)
        assertEquals("MUC", repo.progress.value?.homeAirportIata)
    }

    @Test
    fun `a blank home airport is treated as no home base`() = runTest {
        val repo = repository()
        profiles.value = profile("")
        advanceUntilIdle()

        assertNotNull(repo.progress.value)
        assertNull(repo.progress.value?.homeAirportIata)
    }
}
