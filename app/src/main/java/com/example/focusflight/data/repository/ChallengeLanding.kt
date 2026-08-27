package com.example.focusflight.data.repository

import com.example.focusflight.data.model.FlightMode

/**
 * The challenge half of docs/design/mechanics.md's post-landing pipeline step 4 - what
 * `InFlightViewModel.completeFlight()`'s `checkAchievementsAndChallenges()` delegates to for
 * every landed flight. Pulled out as a standalone, JNI-free suspend function (rather than inlined
 * directly in the ViewModel) specifically so it's unit-testable without instantiating
 * `InFlightViewModel` itself, which eagerly loads the native Cesium engine via
 * `CesiumLiveJniBridge`'s `init` block on first touch and can't run in a plain JVM unit test -
 * see ChallengeLandingTest.
 *
 * - FREE flights never reach here (the caller already gates on mode, this re-checks defensively
 *   since it's a public seam).
 * - A CHALLENGE-tagged session with a non-null [challengeId] advances *that* Route challenge's
 *   own position pointer - docs/design/challenges.md's "which flights count": only a flight
 *   tagged CHALLENGE and scoped to this specific instance moves it.
 * - Every eligible flight (STORY or CHALLENGE) also passively credits every active Distance and
 *   Set-completion challenge, regardless of whether it was itself scoped to a Route challenge.
 */
suspend fun processLandingForChallenges(
    challengeRepository: ChallengeRepository,
    mode: FlightMode,
    challengeId: Int?,
    destIata: String,
    distanceKm: Double
) {
    if (mode == FlightMode.FREE) return

    if (mode == FlightMode.CHALLENGE && challengeId != null) {
        challengeRepository.advanceRouteChallenge(challengeId, destIata)
    }

    challengeRepository.creditEligibleFlight(destIata, distanceKm)
}
