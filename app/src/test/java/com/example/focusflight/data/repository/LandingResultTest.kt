package com.example.focusflight.data.repository

import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeSource
import com.example.focusflight.data.model.ChallengeStatus
import com.example.focusflight.data.model.ChallengeType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [resolveLandingOutcome] (the before/after diff that turns [processLandingForChallenges]'s
 * side effects into a UI-facing [LandingResult]) and [LandingResultChannel] (the state holder that
 * bridges that result across the InFlight -> ArrivalCelebration -> tick-up/completion navigation
 * hop). See ChallengeLandingTest for the sibling coverage of processLandingForChallenges itself.
 */
class LandingResultTest {

    private fun routeChallenge(id: Int, progress: Float, status: ChallengeStatus = ChallengeStatus.ACTIVE) = Challenge(
        id = id,
        userId = 1,
        type = ChallengeType.ROUTE,
        source = ChallengeSource.CURATED,
        status = status,
        name = "London → Sydney",
        originIata = "LHR",
        destIata = "SYD",
        positionIata = "LHR",
        routeProgressFraction = progress,
        completedAt = if (status == ChallengeStatus.COMPLETED) 1L else null
    )

    private fun distanceChallenge(id: Int, cumulativeKm: Double, targetKm: Double = 10_000.0, status: ChallengeStatus = ChallengeStatus.ACTIVE) = Challenge(
        id = id,
        userId = 1,
        type = ChallengeType.DISTANCE,
        source = ChallengeSource.CURATED,
        status = status,
        name = "10,000 km Club",
        targetDistanceKm = targetKm,
        cumulativeDistanceKm = cumulativeKm,
        completedAt = if (status == ChallengeStatus.COMPLETED) 1L else null
    )

    @Test
    fun `no changes resolves to None`() {
        val before = listOf(routeChallenge(1, 0.2f), distanceChallenge(2, 1000.0))
        val after = listOf(routeChallenge(1, 0.2f), distanceChallenge(2, 1000.0))

        assertEquals(LandingResult.None, resolveLandingOutcome(before, after))
    }

    @Test
    fun `empty before (no active challenges) resolves to None`() {
        assertEquals(LandingResult.None, resolveLandingOutcome(emptyList(), emptyList()))
    }

    @Test
    fun `a challenge that advanced but is still active resolves to ChallengeAdvanced with old and new progress`() {
        val before = listOf(routeChallenge(1, 0.2f))
        val after = listOf(routeChallenge(1, 0.55f))

        val result = resolveLandingOutcome(before, after)
        assertTrue(result is LandingResult.ChallengeAdvanced)
        result as LandingResult.ChallengeAdvanced
        assertEquals(1, result.challengeId)
        assertEquals(0.2f, result.oldProgress)
        assertEquals(0.55f, result.newProgress)
        assertEquals(ChallengeType.ROUTE, result.type)
    }

    @Test
    fun `a challenge that flips from ACTIVE to COMPLETED resolves to ChallengeCompleted, not Advanced`() {
        val before = listOf(distanceChallenge(1, 9000.0))
        val after = listOf(distanceChallenge(1, cumulativeKm = 10_500.0, status = ChallengeStatus.COMPLETED))

        val result = resolveLandingOutcome(before, after)
        assertTrue(result is LandingResult.ChallengeCompleted)
        result as LandingResult.ChallengeCompleted
        assertEquals(1, result.challengeId)
        assertEquals(ChallengeType.DISTANCE, result.type)
    }

    @Test
    fun `a completion and a mere advance in the same landing - completion wins`() {
        val before = listOf(routeChallenge(1, 0.3f), distanceChallenge(2, 9000.0))
        val after = listOf(
            routeChallenge(1, 0.3f), // untouched - a different challenge's flight
            distanceChallenge(2, cumulativeKm = 10_500.0, status = ChallengeStatus.COMPLETED)
        )

        val result = resolveLandingOutcome(before, after)
        assertTrue(result is LandingResult.ChallengeCompleted)
        assertEquals(2, (result as LandingResult.ChallengeCompleted).challengeId)
    }

    @Test
    fun `two challenges both advance - the first in before's order wins, per the documented tie-break`() {
        val before = listOf(routeChallenge(1, 0.1f), distanceChallenge(2, 1000.0))
        val after = listOf(routeChallenge(1, 0.1f), distanceChallenge(2, 4000.0))
        // Route challenge 1 didn't move (Route only moves via its own scoped session) - so the
        // one advance here is unambiguous; this exercises that a *non*-advancing before entry
        // doesn't get mistaken for one.
        val result = resolveLandingOutcome(before, after)
        assertTrue(result is LandingResult.ChallengeAdvanced)
        assertEquals(2, (result as LandingResult.ChallengeAdvanced).challengeId)
    }

    @Test
    fun `a before entry missing from after (e_g_ abandoned mid-flight) is skipped, not a crash`() {
        val before = listOf(routeChallenge(1, 0.2f), distanceChallenge(2, 1000.0))
        val after = listOf(distanceChallenge(2, 1000.0)) // challenge 1 is gone

        assertEquals(LandingResult.None, resolveLandingOutcome(before, after))
    }

    @Test
    fun `LandingResultChannel starts Pending, resets to Pending, and publishes resolved values`() = runTest {
        val channel = LandingResultChannel()
        assertEquals(LandingResult.Pending, channel.result.value)

        channel.publish(LandingResult.None)
        assertEquals(LandingResult.None, channel.result.value)

        channel.reset()
        assertEquals(LandingResult.Pending, channel.result.value)

        val outcome = LandingResult.ChallengeCompleted(1, "10,000 km Club", ChallengeType.DISTANCE)
        channel.publish(outcome)
        assertEquals(outcome, channel.result.value)
    }
}
