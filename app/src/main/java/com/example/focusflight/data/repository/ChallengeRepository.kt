package com.example.focusflight.data.repository

import com.example.focusflight.data.model.Challenge
import kotlinx.coroutines.flow.Flow

/**
 * Result of trying to start a new challenge instance - see docs/challenges.md's
 * active-challenge cap (3, curated+custom combined across all three types).
 */
sealed class StartChallengeResult {
    data class Started(val challenge: Challenge) : StartChallengeResult()

    /** Refused: 3 challenges are already active. Starting a challenge never evicts an existing
     *  one - the player must abandon one first (challenges.md's "Abandon, not reset"). */
    object CapReached : StartChallengeResult()

    /** The requested curated catalogId/setId doesn't exist in the seed catalog. */
    object UnknownTemplate : StartChallengeResult()
}

/**
 * Single store for all active/completed challenges (all three types, curated and custom alike) -
 * see docs/challenges.md#persistence--route-scoping. This is the one seam
 * `InFlightViewModel.completeFlight()`'s post-landing pipeline calls into for the challenge half
 * of `checkAchievementsAndChallenges()` (via [processLandingForChallenges]), and the seam the
 * Hub's quest-log UI calls into for start/abandon/list/render-a-progress-bar.
 */
interface ChallengeRepository {
    suspend fun listActiveChallenges(): List<Challenge>
    fun listActiveChallengesFlow(): Flow<List<Challenge>>
    suspend fun getChallenge(id: Int): Challenge?

    /**
     * Every completed challenge (curated or custom, duplicates included for repeat completions of
     * the same one), newest first - the data source for the Achievements screen's "Challenges
     * completed" log (docs/achievements.md). Cross-mode exception per achievements.md's
     * "Scope & isolation": this is the one place a CHALLENGE-tagged flight's effect (completing a
     * Route challenge) or any mode's Distance/Set-completion crediting surfaces in Achievements,
     * display/recognition only - it grants nothing Story-Mode-scoped.
     */
    suspend fun listCompletedChallenges(): List<Challenge>

    suspend fun startCuratedChallenge(catalogId: String): StartChallengeResult
    suspend fun startCustomRouteChallenge(originIata: String, destIata: String, name: String): StartChallengeResult
    suspend fun startCustomDistanceChallenge(targetDistanceKm: Double, name: String): StartChallengeResult

    /** [targetDays] is expected to be small (3-5) - see [ChallengeType.STREAK] for why. */
    suspend fun startCustomStreakChallenge(targetDays: Int, name: String): StartChallengeResult

    /** Removes the challenge entirely and frees its cap slot - no in-place reset
     *  (challenges.md's "Abandon, not reset"). No-op if [id] doesn't exist. */
    suspend fun abandonChallenge(id: Int)

    /**
     * Route-only: moves challenge [challengeId]'s own position pointer to [newPositionIata] and
     * recomputes its progress via [com.example.focusflight.data.model.ChallengeProgress.routeProgress],
     * marking it COMPLETED if [newPositionIata] is the challenge's destination. Never touches
     * `currentAirport` - callers only reach this from a CHALLENGE-tagged session scoped to this
     * specific challenge id (see docs/challenges.md#which-flights-count). No-op (returns
     * the challenge unchanged) if [challengeId] isn't an active Route challenge, or doesn't exist.
     */
    suspend fun advanceRouteChallenge(challengeId: Int, newPositionIata: String): Challenge?

    /**
     * The paused-flight slot for Route challenge [challengeId] - a CHALLENGE-tagged session
     * scoped to it, kept on the challenge's own row (see [Challenge.pausedFlight]) so switching
     * Hub focus (or pausing back to Story Mode) never clobbers it, mirroring
     * [PreferencesRepository.pausedFlightStore] (the mode-dispatching function) for STORY/FREE.
     * Always returns a usable store even
     * if [challengeId] doesn't (currently) exist - its operations are just no-ops in that case.
     */
    fun pausedFlightStore(challengeId: Int): PausedFlightStore

    /**
     * Credits [destIata]/[distanceKm] toward every active Distance and Set-completion challenge
     * (never Route - Route only moves via [advanceRouteChallenge]'s explicit scoping). Meant to
     * be called for every eligible flight (STORY or CHALLENGE, never FREE - see
     * docs/modes.md's isolation matrix), including one flown under a *different*
     * active Route challenge's own session - Distance/Set-completion have no position pointer so
     * they passively credit any eligible flight regardless of its tag's specific scoping. Streak
     * credits the same way, off [completedAt] rather than the route.
     *
     * [completedAt] is the landing's own `FlightLog.completedAt`, not "now". Streak crediting
     * turns it into a local calendar day, so a flight logged just before midnight has to count
     * for the day it actually happened on rather than whenever this code runs.
     */
    suspend fun creditEligibleFlight(destIata: String, distanceKm: Double, completedAt: Long)
}
