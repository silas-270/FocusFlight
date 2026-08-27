package com.example.focusflight.data.repository

import com.example.focusflight.data.local.ChallengeDao
import com.example.focusflight.data.local.UserProfileDao
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeStatus
import com.example.focusflight.data.model.ChallengeType
import com.example.focusflight.data.model.CuratedChallengeSets
import com.example.focusflight.data.model.Runway
import com.example.focusflight.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises [LocalChallengeRepository] - the active-challenge cap, abandon-not-reset, Route's own
 * position-pointer scoping (including the accepted decreasing-progress case), Distance
 * accumulation, and Set-completion crediting (including its "starts fresh per instance"
 * isolation guarantee) - all via hand-written fakes for [ChallengeDao]/[UserProfileDao]/
 * [AirportRepository], the same pattern LegacyFlightLogMigratorTest uses for the DAO-adjacent
 * repository interfaces.
 */
class LocalChallengeRepositoryTest {

    private class FakeChallengeDao : ChallengeDao {
        private val rows = mutableMapOf<Int, Challenge>()
        private var nextId = 1

        override suspend fun insert(challenge: Challenge): Long {
            val id = nextId++
            rows[id] = challenge.copy(id = id)
            return id.toLong()
        }

        override suspend fun update(challenge: Challenge) {
            rows[challenge.id] = challenge
        }

        override suspend fun deleteById(id: Int) {
            rows.remove(id)
        }

        override suspend fun getById(id: Int): Challenge? = rows[id]

        override suspend fun getByStatus(userId: Int, status: ChallengeStatus): List<Challenge> =
            rows.values.filter { it.userId == userId && it.status == status }

        override fun getByStatusFlow(userId: Int, status: ChallengeStatus): Flow<List<Challenge>> =
            MutableStateFlow(getByStatusBlocking(userId, status))

        private fun getByStatusBlocking(userId: Int, status: ChallengeStatus): List<Challenge> =
            rows.values.filter { it.userId == userId && it.status == status }

        override suspend fun countByStatus(userId: Int, status: ChallengeStatus): Int =
            rows.values.count { it.userId == userId && it.status == status }

        override suspend fun getByStatusOrderedByCompletedAt(userId: Int, status: ChallengeStatus): List<Challenge> =
            rows.values
                .filter { it.userId == userId && it.status == status }
                .sortedByDescending { it.completedAt ?: 0L }
    }

    private class FakeUserProfileDao(private val profile: UserProfile) : UserProfileDao {
        override fun getProfileFlow(): Flow<UserProfile?> = MutableStateFlow(profile)
        override suspend fun getProfile(): UserProfile? = profile
        override suspend fun insertProfile(profile: UserProfile): Long = profile.id.toLong()
        override suspend fun updateProfile(profile: UserProfile) = Unit
        override suspend fun updateUsername(id: Int, username: String, updatedAt: Long) = Unit
        override suspend fun updateHomeAirport(id: Int, iata: String, updatedAt: Long) = Unit
    }

    private class FakeAirportRepository(private val airports: Map<String, Airport>) : AirportRepository {
        override fun ensureDatabaseCopied() = Unit
        override fun searchAirports(query: String): List<Airport> = emptyList()
        override fun getAirportByIata(iataCode: String): Airport? = airports[iataCode]
        override fun getRunwaysForAirport(airportId: Int): List<Runway> = emptyList()
        override fun getOutboundRoutes(originIata: String, searchQuery: String, sortBy: String) = emptyList<com.example.focusflight.data.model.FlightRoute>()
        override fun getContinentCountryMap(): Map<String, Set<String>> = emptyMap()
        override fun getCountriesForAirports(iatas: List<String>): Set<String> = emptySet()
    }

    private fun airport(iata: String, lat: Double, lon: Double, continent: String, country: String) = Airport(
        id = iata.hashCode(),
        ident = iata,
        iataCode = iata,
        name = iata,
        lat = lat,
        lon = lon,
        elevationFt = 0.0,
        continent = continent,
        isoCountry = country,
        isoRegion = "",
        municipality = iata,
        type = "large_airport"
    )

    // Synthetic geography, chosen for clean arithmetic rather than real-world coordinates -
    // origin/dest 100 degrees apart along the equator, "closer"/"farther" at 50/30 degrees in.
    private val origin = airport("ORI", 0.0, 0.0, "EU", "FR")
    private val dest = airport("DST", 0.0, 100.0, "OC", "AU")
    private val closer = airport("MID", 0.0, 50.0, "AS", "IN")
    private val fartherAgain = airport("BAK", 0.0, 30.0, "AS", "CN")

    private lateinit var dao: FakeChallengeDao
    private lateinit var userProfileDao: FakeUserProfileDao
    private lateinit var airportRepository: FakeAirportRepository
    private lateinit var repository: LocalChallengeRepository

    @Before
    fun setUp() {
        dao = FakeChallengeDao()
        userProfileDao = FakeUserProfileDao(UserProfile(id = 1, username = "Cap", userCode = "ABC123", homeAirportIata = "ORI"))
        airportRepository = FakeAirportRepository(
            mapOf(
                origin.iataCode to origin,
                dest.iataCode to dest,
                closer.iataCode to closer,
                fartherAgain.iataCode to fartherAgain,
                "LHR" to airport("LHR", 51.47, -0.45, "EU", "GB"),
                "SYD" to airport("SYD", -33.94, 151.18, "OC", "AU")
            )
        )
        repository = LocalChallengeRepository(dao, userProfileDao, airportRepository)
    }

    // ── start / cap / abandon ────────────────────────────────────────────────────────────

    @Test
    fun `starting a curated route challenge seeds the position pointer at the origin`() = runTest {
        val result = repository.startCuratedChallenge("route_lhr_syd")

        assertTrue(result is StartChallengeResult.Started)
        val challenge = (result as StartChallengeResult.Started).challenge
        assertEquals("LHR", challenge.positionIata)
        assertEquals("LHR", challenge.originIata)
        assertEquals("SYD", challenge.destIata)
        assertEquals(0f, challenge.routeProgressFraction, 0.001f)
        assertEquals(ChallengeStatus.ACTIVE, challenge.status)
    }

    @Test
    fun `starting a curated set-completion challenge starts with zero visited members`() = runTest {
        val result = repository.startCuratedChallenge("set_all_continents")

        val challenge = (result as StartChallengeResult.Started).challenge
        assertEquals(CuratedChallengeSets.ALL_CONTINENTS.members.size, challenge.setTotalMembers)
        assertTrue(challenge.visitedSetMembers.isEmpty())
    }

    @Test
    fun `unknown catalog id is refused`() = runTest {
        val result = repository.startCuratedChallenge("not_a_real_template")
        assertEquals(StartChallengeResult.UnknownTemplate, result)
    }

    @Test
    fun `a fourth active challenge is refused once the cap of three is reached`() = runTest {
        repository.startCustomDistanceChallenge(1000.0, "A")
        repository.startCustomDistanceChallenge(2000.0, "B")
        repository.startCustomDistanceChallenge(3000.0, "C")

        val fourth = repository.startCustomDistanceChallenge(4000.0, "D")

        assertEquals(StartChallengeResult.CapReached, fourth)
        assertEquals(3, repository.listActiveChallenges().size)
    }

    @Test
    fun `abandoning a challenge removes it entirely and frees the cap slot`() = runTest {
        val a = (repository.startCustomDistanceChallenge(1000.0, "A") as StartChallengeResult.Started).challenge
        repository.startCustomDistanceChallenge(2000.0, "B")
        repository.startCustomDistanceChallenge(3000.0, "C")

        repository.abandonChallenge(a.id)

        assertNull(repository.getChallenge(a.id))
        assertEquals(2, repository.listActiveChallenges().size)

        // The freed slot can be used immediately - starting again is a fresh instance, not a
        // reset of the abandoned one (challenges.md's "Abandon, not reset").
        val fourth = repository.startCustomDistanceChallenge(4000.0, "D")
        assertTrue(fourth is StartChallengeResult.Started)
        assertEquals(3, repository.listActiveChallenges().size)
    }

    // ── Route scoping / advanceRouteChallenge ───────────────────────────────────────────

    @Test
    fun `advancing a route challenge moves the pointer and increases progress when getting closer`() = runTest {
        val started = (repository.startCustomRouteChallenge("ORI", "DST", "Test Route") as StartChallengeResult.Started).challenge

        val updated = repository.advanceRouteChallenge(started.id, "MID")

        assertNotNull(updated)
        assertEquals("MID", updated!!.positionIata)
        assertEquals(0.5f, updated.routeProgressFraction, 0.02f)
        assertEquals(ChallengeStatus.ACTIVE, updated.status)
    }

    @Test
    fun `a leg that points away from the destination legitimately decreases progress`() = runTest {
        val started = (repository.startCustomRouteChallenge("ORI", "DST", "Test Route") as StartChallengeResult.Started).challenge
        val afterCloser = repository.advanceRouteChallenge(started.id, "MID")!!

        val afterFartherAgain = repository.advanceRouteChallenge(started.id, "BAK")!!

        assertTrue(afterFartherAgain.routeProgressFraction < afterCloser.routeProgressFraction)
        assertEquals(ChallengeStatus.ACTIVE, afterFartherAgain.status) // still active, not stuck/broken
    }

    @Test
    fun `reaching the destination completes the route challenge`() = runTest {
        val started = (repository.startCustomRouteChallenge("ORI", "DST", "Test Route") as StartChallengeResult.Started).challenge

        val updated = repository.advanceRouteChallenge(started.id, "DST")!!

        assertEquals(ChallengeStatus.COMPLETED, updated.status)
        assertEquals(1f, updated.routeProgressFraction, 0.001f)
        assertNotNull(updated.completedAt)
    }

    @Test
    fun `advancing does not touch other active challenges`() = runTest {
        val route = (repository.startCustomRouteChallenge("ORI", "DST", "Route") as StartChallengeResult.Started).challenge
        val distance = (repository.startCustomDistanceChallenge(5000.0, "Distance") as StartChallengeResult.Started).challenge

        repository.advanceRouteChallenge(route.id, "MID")

        val untouchedDistance = repository.getChallenge(distance.id)!!
        assertEquals(0.0, untouchedDistance.cumulativeDistanceKm, 0.0)
    }

    // ── creditEligibleFlight: Distance ──────────────────────────────────────────────────

    @Test
    fun `distance challenges accumulate across multiple eligible flights`() = runTest {
        val challenge = (repository.startCustomDistanceChallenge(1000.0, "Test") as StartChallengeResult.Started).challenge

        repository.creditEligibleFlight("DST", 400.0)
        repository.creditEligibleFlight("DST", 300.0)

        val updated = repository.getChallenge(challenge.id)!!
        assertEquals(700.0, updated.cumulativeDistanceKm, 0.001)
        assertEquals(ChallengeStatus.ACTIVE, updated.status)
    }

    @Test
    fun `a distance challenge completes once the cumulative total reaches its target`() = runTest {
        val challenge = (repository.startCustomDistanceChallenge(1000.0, "Test") as StartChallengeResult.Started).challenge

        repository.creditEligibleFlight("DST", 1200.0)

        val updated = repository.getChallenge(challenge.id)!!
        assertEquals(ChallengeStatus.COMPLETED, updated.status)
        assertNotNull(updated.completedAt)
    }

    // ── creditEligibleFlight: Set-completion ────────────────────────────────────────────

    @Test
    fun `landing on a new continent credits a set-completion challenge`() = runTest {
        val challenge = (repository.startCuratedChallenge("set_all_continents") as StartChallengeResult.Started).challenge

        // "DST" is continent "OC" per the fake airport data above.
        repository.creditEligibleFlight("DST", 500.0)

        val updated = repository.getChallenge(challenge.id)!!
        assertEquals(setOf("OC"), updated.visitedSetMembers)
    }

    @Test
    fun `landing on an already-credited continent again does not double count it`() = runTest {
        val challenge = (repository.startCuratedChallenge("set_all_continents") as StartChallengeResult.Started).challenge

        repository.creditEligibleFlight("DST", 500.0) // OC
        repository.creditEligibleFlight("DST", 500.0) // OC again

        val updated = repository.getChallenge(challenge.id)!!
        assertEquals(1, updated.visitedSetMembers.size)
    }

    @Test
    fun `set-completion challenge completes once every member has been credited`() = runTest {
        val challenge = (repository.startCuratedChallenge("set_all_continents") as StartChallengeResult.Started).challenge
        val allContinents = CuratedChallengeSets.ALL_CONTINENTS.members
        // Feed one flight per continent using synthetic airports whose `continent` cycles through
        // every member of the curated set.
        allContinents.forEachIndexed { index, continentCode ->
            val iata = "C$index"
            val perContinentAirport = airport(iata, 0.0, index.toDouble(), continentCode, "XX")
            val repo = LocalChallengeRepository(
                dao, userProfileDao,
                FakeAirportRepository(mapOf(iata to perContinentAirport))
            )
            repo.creditEligibleFlight(iata, 100.0)
        }

        val updated = repository.getChallenge(challenge.id)!!
        assertEquals(allContinents.size, updated.visitedSetMembers.size)
        assertEquals(ChallengeStatus.COMPLETED, updated.status)
    }

    @Test
    fun `starting the same curated set-completion challenge again starts fresh at zero`() = runTest {
        val first = (repository.startCuratedChallenge("set_all_continents") as StartChallengeResult.Started).challenge
        repository.creditEligibleFlight("DST", 500.0) // credits OC on the first instance
        repository.abandonChallenge(first.id)

        val second = (repository.startCuratedChallenge("set_all_continents") as StartChallengeResult.Started).challenge

        // A brand-new instance never inherits progress from a previous (even abandoned) one -
        // this is the concrete mechanism behind challenges.md's "isolation" / repeatability
        // guarantee: each instance tracks its own visited-members, starting at zero.
        assertTrue(second.visitedSetMembers.isEmpty())
    }

    @Test
    fun `landing somewhere that is not a relevant set member credits nothing`() = runTest {
        val challenge = (repository.startCuratedChallenge("set_g7_capitals") as StartChallengeResult.Started).challenge

        // "DST"'s IATA code isn't one of the G7 capitals in CuratedChallengeSets.G7_CAPITALS.
        repository.creditEligibleFlight("DST", 500.0)

        val updated = repository.getChallenge(challenge.id)!!
        assertTrue(updated.visitedSetMembers.isEmpty())
    }

    @Test
    fun `route challenges are never touched by creditEligibleFlight`() = runTest {
        val route = (repository.startCustomRouteChallenge("ORI", "DST", "Route") as StartChallengeResult.Started).challenge

        repository.creditEligibleFlight("DST", 999.0)

        val untouched = repository.getChallenge(route.id)!!
        assertEquals("ORI", untouched.positionIata) // unchanged - only advanceRouteChallenge moves it
        assertEquals(0f, untouched.routeProgressFraction, 0.001f)
    }
}
