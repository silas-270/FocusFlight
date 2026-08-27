package com.example.focusflight.data.repository

import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.FlightMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers [processLandingForChallenges] - the exact call sequence
 * `InFlightViewModel.completeFlight()`'s `checkAchievementsAndChallenges()` delegates to on every
 * landed flight (docs/design/mechanics.md's post-landing pipeline step 4, challenge half).
 *
 * This is the closest thing to an end-to-end test of that path achievable in this project's
 * plain-JVM unit test setup: `InFlightViewModel` itself can't be instantiated here at all, since
 * `CesiumLiveJniBridge` (which `completeFlight()` calls into) eagerly runs
 * `System.loadLibrary("cesium_rs")` in an `init` block the moment the object is touched, and no
 * native library is available in a JVM-only test run - a pre-existing condition, not introduced
 * by this phase. `processLandingForChallenges` was pulled out specifically so the *logic*
 * `checkAchievementsAndChallenges()` adds is still directly testable despite that.
 */
class ChallengeLandingTest {

    private class FakeChallengeRepository : ChallengeRepository {
        val advancedRouteCalls = mutableListOf<Pair<Int, String>>()
        val creditedFlights = mutableListOf<Pair<String, Double>>()

        override suspend fun listActiveChallenges(): List<Challenge> = emptyList()
        override fun listActiveChallengesFlow(): Flow<List<Challenge>> = MutableStateFlow(emptyList())
        override suspend fun getChallenge(id: Int): Challenge? = null
        override suspend fun listCompletedChallenges(): List<Challenge> = emptyList()
        override suspend fun startCuratedChallenge(catalogId: String): StartChallengeResult =
            StartChallengeResult.UnknownTemplate
        override suspend fun startCustomRouteChallenge(originIata: String, destIata: String, name: String): StartChallengeResult =
            throw NotImplementedError("unused in this test")
        override suspend fun startCustomDistanceChallenge(targetDistanceKm: Double, name: String): StartChallengeResult =
            throw NotImplementedError("unused in this test")
        override suspend fun abandonChallenge(id: Int) = Unit

        override suspend fun advanceRouteChallenge(challengeId: Int, newPositionIata: String): Challenge? {
            advancedRouteCalls.add(challengeId to newPositionIata)
            return null
        }

        override suspend fun creditEligibleFlight(destIata: String, distanceKm: Double) {
            creditedFlights.add(destIata to distanceKm)
        }
    }

    @Test
    fun `a FREE flight never touches the challenge repository at all`() = runTest {
        val repo = FakeChallengeRepository()

        processLandingForChallenges(repo, FlightMode.FREE, challengeId = 42, destIata = "JFK", distanceKm = 5000.0)

        assertEquals(emptyList<Pair<Int, String>>(), repo.advancedRouteCalls)
        assertEquals(emptyList<Pair<String, Double>>(), repo.creditedFlights)
    }

    @Test
    fun `a STORY flight credits Distance and Set-completion challenges but advances no route`() = runTest {
        val repo = FakeChallengeRepository()

        processLandingForChallenges(repo, FlightMode.STORY, challengeId = null, destIata = "JFK", distanceKm = 5000.0)

        assertEquals(emptyList<Pair<Int, String>>(), repo.advancedRouteCalls)
        assertEquals(listOf("JFK" to 5000.0), repo.creditedFlights)
    }

    @Test
    fun `a CHALLENGE flight both advances its own scoped route and credits other active challenges`() = runTest {
        val repo = FakeChallengeRepository()

        processLandingForChallenges(repo, FlightMode.CHALLENGE, challengeId = 7, destIata = "SYD", distanceKm = 3000.0)

        // Advances the scoped Route challenge...
        assertEquals(listOf(7 to "SYD"), repo.advancedRouteCalls)
        // ...and still credits every other active Distance/Set-completion challenge, per
        // docs/design/challenges.md's "which flights count" (they have no position pointer, so
        // they passively credit any eligible flight regardless of its own Route scoping).
        assertEquals(listOf("SYD" to 3000.0), repo.creditedFlights)
    }

    @Test
    fun `a CHALLENGE flight with no scoped challenge id still credits Distance and Set-completion`() = runTest {
        val repo = FakeChallengeRepository()

        // Defensive case - shouldn't happen in practice (CHALLENGE mode is only ever produced
        // with a challengeId), but the function must not silently drop the passive crediting.
        processLandingForChallenges(repo, FlightMode.CHALLENGE, challengeId = null, destIata = "SYD", distanceKm = 3000.0)

        assertEquals(emptyList<Pair<Int, String>>(), repo.advancedRouteCalls)
        assertEquals(listOf("SYD" to 3000.0), repo.creditedFlights)
    }

    @Test
    fun `challengeId is ignored (no crash) for a STORY flight even if one is somehow present`() = runTest {
        val repo = FakeChallengeRepository()

        processLandingForChallenges(repo, FlightMode.STORY, challengeId = 99, destIata = "JFK", distanceKm = 100.0)

        assertEquals(emptyList<Pair<Int, String>>(), repo.advancedRouteCalls)
        assertNull(repo.advancedRouteCalls.firstOrNull())
    }
}
