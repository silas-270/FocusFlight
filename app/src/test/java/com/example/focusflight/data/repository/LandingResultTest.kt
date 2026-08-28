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
 * bridges that result across the InFlight -> ArrivalCelebration -> outcome navigation hop). See
 * ChallengeLandingTest for the sibling coverage of processLandingForChallenges itself.
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

    private fun setChallenge(id: Int, name: String, status: ChallengeStatus = ChallengeStatus.ACTIVE) = Challenge(
        id = id,
        userId = 1,
        type = ChallengeType.SET_COMPLETION,
        source = ChallengeSource.CURATED,
        status = status,
        name = name,
        setCatalogId = "all_continents",
        setTotalMembers = 7,
        visitedSetMembers = if (status == ChallengeStatus.COMPLETED) setOf("EU", "AF", "AS", "NA", "SA", "OC", "AN") else setOf("EU"),
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
    fun `a challenge that advanced but is still active resolves to a single Advanced outcome with old and new progress`() {
        val before = listOf(routeChallenge(1, 0.2f))
        val after = listOf(routeChallenge(1, 0.55f))

        val result = resolveLandingOutcome(before, after)
        assertTrue(result is LandingResult.ChallengesAffected)
        val outcomes = (result as LandingResult.ChallengesAffected).outcomes
        assertEquals(1, outcomes.size)
        val outcome = outcomes.single() as ChallengeOutcome.Advanced
        assertEquals(1, outcome.challengeId)
        assertEquals(0.2f, outcome.oldProgress)
        assertEquals(0.55f, outcome.newProgress)
        assertEquals(ChallengeType.ROUTE, outcome.type)
    }

    @Test
    fun `a challenge that flips from ACTIVE to COMPLETED resolves to a single Completed outcome`() {
        val before = listOf(distanceChallenge(1, 9000.0))
        val after = listOf(distanceChallenge(1, cumulativeKm = 10_500.0, status = ChallengeStatus.COMPLETED))

        val result = resolveLandingOutcome(before, after)
        assertTrue(result is LandingResult.ChallengesAffected)
        val outcomes = (result as LandingResult.ChallengesAffected).outcomes
        assertEquals(1, outcomes.size)
        val outcome = outcomes.single() as ChallengeOutcome.Completed
        assertEquals(1, outcome.challengeId)
        assertEquals(ChallengeType.DISTANCE, outcome.type)
        assertEquals(0.9f, outcome.oldProgress)
    }

    @Test
    fun `a completion and a mere advance in the same landing both appear, in before's order`() {
        val before = listOf(routeChallenge(1, 0.3f), distanceChallenge(2, 9000.0))
        val after = listOf(
            routeChallenge(1, 0.6f), // this landing's own scoped Route challenge also advanced
            distanceChallenge(2, cumulativeKm = 10_500.0, status = ChallengeStatus.COMPLETED)
        )

        val result = resolveLandingOutcome(before, after)
        assertTrue(result is LandingResult.ChallengesAffected)
        val outcomes = (result as LandingResult.ChallengesAffected).outcomes
        assertEquals(2, outcomes.size)
        assertTrue(outcomes[0] is ChallengeOutcome.Advanced)
        assertEquals(1, outcomes[0].challengeId)
        assertTrue(outcomes[1] is ChallengeOutcome.Completed)
        assertEquals(2, outcomes[1].challengeId)
    }

    @Test
    fun `two challenges both advance - both appear, in before's order`() {
        val before = listOf(routeChallenge(1, 0.1f), distanceChallenge(2, 1000.0))
        val after = listOf(routeChallenge(1, 0.3f), distanceChallenge(2, 4000.0))

        val result = resolveLandingOutcome(before, after)
        assertTrue(result is LandingResult.ChallengesAffected)
        val outcomes = (result as LandingResult.ChallengesAffected).outcomes
        assertEquals(2, outcomes.size)
        assertEquals(1, outcomes[0].challengeId)
        assertEquals(2, outcomes[1].challengeId)
    }

    @Test
    fun `three simultaneous outcomes (the MAX_ACTIVE_CHALLENGES=3 case) all appear, in before's order`() {
        val before = listOf(
            distanceChallenge(1, 1000.0),
            distanceChallenge(2, 9000.0),
            setChallenge(3, "All Continents")
        )
        val after = listOf(
            distanceChallenge(1, 3000.0), // advances
            distanceChallenge(2, cumulativeKm = 10_500.0, status = ChallengeStatus.COMPLETED), // completes
            setChallenge(3, "All Continents", status = ChallengeStatus.COMPLETED) // completes
        )

        val result = resolveLandingOutcome(before, after)
        assertTrue(result is LandingResult.ChallengesAffected)
        val outcomes = (result as LandingResult.ChallengesAffected).outcomes
        assertEquals(3, outcomes.size)
        assertEquals(listOf(1, 2, 3), outcomes.map { it.challengeId })
        assertTrue(outcomes[0] is ChallengeOutcome.Advanced)
        assertTrue(outcomes[1] is ChallengeOutcome.Completed)
        assertTrue(outcomes[2] is ChallengeOutcome.Completed)
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

        val outcome = LandingResult.ChallengesAffected(
            listOf(ChallengeOutcome.Completed(1, "10,000 km Club", ChallengeType.DISTANCE, 0.9f))
        )
        channel.publish(outcome)
        assertEquals(outcome, channel.result.value)
    }
}
