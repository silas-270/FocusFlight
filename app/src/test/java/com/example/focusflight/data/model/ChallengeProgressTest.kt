package com.example.focusflight.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage for [ChallengeProgress] and [progressFraction] - no Room/repository
 * involved, per docs/design/challenges.md's Route progress formula and the per-type metrics for
 * Set-completion/Distance.
 */
class ChallengeProgressTest {

    private fun challenge(
        type: ChallengeType,
        routeProgressFraction: Float = 0f,
        setTotalMembers: Int = 0,
        visitedSetMembers: Set<String> = emptySet(),
        targetDistanceKm: Double? = null,
        cumulativeDistanceKm: Double = 0.0
    ) = Challenge(
        userId = 1,
        type = type,
        source = ChallengeSource.CURATED,
        name = "Test",
        routeProgressFraction = routeProgressFraction,
        setTotalMembers = setTotalMembers,
        visitedSetMembers = visitedSetMembers,
        targetDistanceKm = targetDistanceKm,
        cumulativeDistanceKm = cumulativeDistanceKm
    )

    // ── haversineKm ──────────────────────────────────────────────────────────────────────

    @Test
    fun `haversine distance between a point and itself is zero`() {
        assertEquals(0.0, ChallengeProgress.haversineKm(51.47, -0.45, 51.47, -0.45), 0.0001)
    }

    @Test
    fun `haversine distance is symmetric`() {
        val a = ChallengeProgress.haversineKm(0.0, 0.0, 0.0, 100.0)
        val b = ChallengeProgress.haversineKm(0.0, 100.0, 0.0, 0.0)
        assertEquals(a, b, 0.0001)
    }

    // ── routeProgress ────────────────────────────────────────────────────────────────────

    @Test
    fun `progress is zero when current position equals origin`() {
        val progress = ChallengeProgress.routeProgress(
            originLat = 0.0, originLon = 0.0,
            destLat = 0.0, destLon = 100.0,
            currentLat = 0.0, currentLon = 0.0
        )
        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun `progress is one when current position equals destination`() {
        val progress = ChallengeProgress.routeProgress(
            originLat = 0.0, originLon = 0.0,
            destLat = 0.0, destLon = 100.0,
            currentLat = 0.0, currentLon = 100.0
        )
        assertEquals(1f, progress, 0.001f)
    }

    @Test
    fun `progress is roughly one half at the midpoint`() {
        val progress = ChallengeProgress.routeProgress(
            originLat = 0.0, originLon = 0.0,
            destLat = 0.0, destLon = 100.0,
            currentLat = 0.0, currentLon = 50.0
        )
        assertEquals(0.5f, progress, 0.01f)
    }

    @Test
    fun `progress can legitimately decrease when a leg points away from the destination`() {
        // docs/design/challenges.md's accepted v1 limitation: a straight-line proxy, not real
        // routing, so a real onward flight that happens to point away from the destination makes
        // the percentage go down, not up.
        val closer = ChallengeProgress.routeProgress(
            originLat = 0.0, originLon = 0.0,
            destLat = 0.0, destLon = 100.0,
            currentLat = 0.0, currentLon = 50.0
        )
        val fartherAgain = ChallengeProgress.routeProgress(
            originLat = 0.0, originLon = 0.0,
            destLat = 0.0, destLon = 100.0,
            currentLat = 0.0, currentLon = 30.0
        )
        assertTrue("expected progress to decrease from $closer to $fartherAgain", fartherAgain < closer)
    }

    @Test
    fun `progress is clamped to zero when current is farther from destination than origin`() {
        val progress = ChallengeProgress.routeProgress(
            originLat = 0.0, originLon = 0.0,
            destLat = 0.0, destLon = 100.0,
            currentLat = 0.0, currentLon = -50.0 // behind the origin, away from the destination
        )
        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun `origin equal to destination is treated as already complete`() {
        val progress = ChallengeProgress.routeProgress(
            originLat = 10.0, originLon = 10.0,
            destLat = 10.0, destLon = 10.0,
            currentLat = 5.0, currentLon = 5.0
        )
        assertEquals(1f, progress, 0.001f)
    }

    // ── progressFraction() per type ──────────────────────────────────────────────────────

    @Test
    fun `route progress fraction reads the cached routeProgressFraction field`() {
        val c = challenge(ChallengeType.ROUTE, routeProgressFraction = 0.42f)
        assertEquals(0.42f, c.progressFraction(), 0.001f)
    }

    @Test
    fun `set-completion progress fraction is visited over total`() {
        val c = challenge(ChallengeType.SET_COMPLETION, setTotalMembers = 4, visitedSetMembers = setOf("EU", "AS"))
        assertEquals(0.5f, c.progressFraction(), 0.001f)
    }

    @Test
    fun `set-completion progress fraction is zero when total is zero (avoids div by zero)`() {
        val c = challenge(ChallengeType.SET_COMPLETION, setTotalMembers = 0)
        assertEquals(0f, c.progressFraction(), 0.001f)
    }

    @Test
    fun `distance progress fraction is cumulative over target`() {
        val c = challenge(ChallengeType.DISTANCE, targetDistanceKm = 10000.0, cumulativeDistanceKm = 2500.0)
        assertEquals(0.25f, c.progressFraction(), 0.001f)
    }

    @Test
    fun `distance progress fraction clamps at one past the target`() {
        val c = challenge(ChallengeType.DISTANCE, targetDistanceKm = 10000.0, cumulativeDistanceKm = 15000.0)
        assertEquals(1f, c.progressFraction(), 0.001f)
    }
}
