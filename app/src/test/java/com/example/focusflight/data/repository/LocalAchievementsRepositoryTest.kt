package com.example.focusflight.data.repository

import com.example.focusflight.data.local.AchievementUnlockDao
import com.example.focusflight.data.local.UserProfileDao
import com.example.focusflight.data.model.AchievementBoard
import com.example.focusflight.data.model.AchievementProgress
import com.example.focusflight.data.model.AchievementUnlock
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.UserProfile
import com.example.focusflight.data.model.VisitedGeography
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Exercises [LocalAchievementsRepository]'s one job: stamping unlock timestamps onto a board that
 * [AchievementProgress] itself computes purely. The behaviour that actually matters here is
 * *first write wins* - the board is re-evaluated on every screen open, so a second pass must
 * never move an already-recorded unlock time forward.
 *
 * Hand-written DAO fakes, matching LocalChallengeRepositoryTest's pattern. The fake DAO
 * reproduces the real composite-primary-key + IGNORE-on-conflict semantics, since that
 * constraint is precisely what the production code relies on.
 */
class LocalAchievementsRepositoryTest {

    /** Keyed by (userId, achievementId) so `insertIfAbsent` really is a no-op on conflict,
     *  mirroring the entity's composite PK + [androidx.room.OnConflictStrategy.IGNORE]. */
    private class FakeAchievementUnlockDao : AchievementUnlockDao {
        val rows = mutableMapOf<Pair<Int, String>, AchievementUnlock>()

        override suspend fun insertIfAbsent(unlock: AchievementUnlock) {
            val key = unlock.userId to unlock.achievementId
            if (!rows.containsKey(key)) rows[key] = unlock
        }

        override suspend fun getAllForUser(userId: Int): List<AchievementUnlock> =
            rows.values.filter { it.userId == userId }
    }

    private class FakeUserProfileDao(private val profile: UserProfile) : UserProfileDao {
        override fun getProfileFlow(): Flow<UserProfile?> = MutableStateFlow(profile)
        override suspend fun getProfile(): UserProfile? = profile
        override suspend fun insertProfile(profile: UserProfile): Long = profile.id.toLong()
        override suspend fun updateProfile(profile: UserProfile) = Unit
        override suspend fun updateUsername(id: Int, username: String, updatedAt: Long) = Unit
        override suspend fun updateHomeAirport(id: Int, iata: String, updatedAt: Long) = Unit
    }

    private val emptyGeography = VisitedGeography(
        visitedCountries = emptySet(),
        countryToContinent = emptyMap(),
        continentStats = emptyList(),
        completedContinents = emptySet()
    )

    private fun storyFlight(distanceKm: Double = 500.0, durationMin: Int = 60) = FlightLog(
        userId = 1,
        flightNumber = "FF1",
        originIata = "AAA",
        destIata = "BBB",
        durationMin = durationMin,
        distanceKm = distanceKm,
        // Midday, so the Red-Eye achievement stays locked and can serve as the "still locked"
        // control case below.
        completedAt = 1_700_000_000_000L,
        mode = FlightMode.STORY
    )

    private lateinit var dao: FakeAchievementUnlockDao
    private lateinit var userProfileDao: FakeUserProfileDao

    @Before
    fun setUp() {
        dao = FakeAchievementUnlockDao()
        userProfileDao = FakeUserProfileDao(
            UserProfile(id = 1, username = "Cap", userCode = "ABC123", homeAirportIata = "AAA")
        )
    }

    private fun repositoryAt(now: Long) = LocalAchievementsRepository(
        achievementUnlockDao = dao,
        userProfileDao = userProfileDao,
        airportRepository = throwingAirportRepository(),
        flightLogRepository = throwingFlightLogRepository(),
        now = { now }
    )

    private fun AchievementBoard.byId(id: String) =
        (geographic + distance + behavioral).first { it.id == id }

    @Test
    fun `first evaluation stamps newly unlocked achievements`() = runTest {
        val board = repositoryAt(1_000L)
            .evaluateBoard(emptyGeography, listOf(storyFlight()))

        val firstFlight = board.byId(AchievementProgress.FIRST_FLIGHT_ID)
        assertEquals(true, firstFlight.isUnlocked)
        assertEquals(1_000L, firstFlight.unlockedAt)
    }

    @Test
    fun `re-evaluation does not overwrite an existing unlock timestamp`() = runTest {
        val history = listOf(storyFlight())
        repositoryAt(1_000L).evaluateBoard(emptyGeography, history)

        // Same achievement, later clock - the original stamp must survive.
        val second = repositoryAt(9_999L).evaluateBoard(emptyGeography, history)

        assertEquals(1_000L, second.byId(AchievementProgress.FIRST_FLIGHT_ID).unlockedAt)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `locked achievements are never stamped`() = runTest {
        val board = repositoryAt(1_000L)
            .evaluateBoard(emptyGeography, listOf(storyFlight()))

        // A midday, short, close flight leaves Red-Eye locked.
        val redEye = board.byId(AchievementProgress.RED_EYE_ID)
        assertEquals(false, redEye.isUnlocked)
        assertNull(redEye.unlockedAt)
        assertNull(dao.rows[1 to AchievementProgress.RED_EYE_ID])
    }

    @Test
    fun `an achievement unlocked later gets its own later timestamp`() = runTest {
        repositoryAt(1_000L).evaluateBoard(emptyGeography, listOf(storyFlight()))

        // A 10,000km flight additionally unlocks Grand Voyage, at the later clock.
        val board = repositoryAt(5_000L)
            .evaluateBoard(emptyGeography, listOf(storyFlight(), storyFlight(distanceKm = 12_000.0)))

        assertEquals(1_000L, board.byId(AchievementProgress.FIRST_FLIGHT_ID).unlockedAt)
        val grandVoyage = board.byId(AchievementProgress.GRAND_VOYAGE_ID)
        assertEquals(true, grandVoyage.isUnlocked)
        assertNotNull(grandVoyage.unlockedAt)
        assertEquals(5_000L, grandVoyage.unlockedAt)
    }

    // evaluateBoard() never touches these two - only loadBoard() does, and it isn't under test
    // here. Throwing fakes make an accidental dependency on them fail loudly rather than silently.
    private fun throwingAirportRepository(): AirportRepository = object : AirportRepository {
        override fun ensureDatabaseCopied() = throw NotImplementedError()
        override fun searchAirports(query: String) = throw NotImplementedError()
        override fun getAirportByIata(iataCode: String) = throw NotImplementedError()
        override fun getRunwaysForAirport(airportId: Int) = throw NotImplementedError()
        override fun getOutboundRoutes(originIata: String, searchQuery: String, sortBy: String) = throw NotImplementedError()
        override fun getContinentCountryMap() = throw NotImplementedError()
        override fun getCountriesForAirports(iatas: List<String>) = throw NotImplementedError()
    }

    private fun throwingFlightLogRepository(): FlightLogRepository = object : FlightLogRepository {
        override suspend fun logFlight(
            flightNumber: String,
            originIata: String,
            destIata: String,
            durationMin: Int,
            distanceKm: Double,
            mode: FlightMode
        ) = throw NotImplementedError()
        override fun getFlightHistoryFlow() = throw NotImplementedError()
        override suspend fun getFlightHistory() = throw NotImplementedError()
        override suspend fun getRecentFlights(limit: Int) = throw NotImplementedError()
        override suspend fun getFlightStats() = throw NotImplementedError()
        override fun getFlightsPagingSource(sortOrder: com.example.focusflight.data.model.FlightSortOrder) = throw NotImplementedError()
        override suspend fun getFlightHighlights() = throw NotImplementedError()
    }
}
