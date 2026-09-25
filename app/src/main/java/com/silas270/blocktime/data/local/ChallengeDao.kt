package com.silas270.blocktime.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.silas270.blocktime.data.model.Challenge
import com.silas270.blocktime.data.model.ChallengeStatus
import com.silas270.blocktime.data.model.PausedFlight
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Insert
    suspend fun insert(challenge: Challenge): Long

    /**
     * Rewrites **every column** of the row - which is the part that matters, because two of these
     * racing each other is a lost update, not a merge: whichever writer read first and writes last
     * silently reverts the other's columns. Callers that only mean to change one concern should
     * use the scoped updates below instead; this stays for whole-row writes (insert-shaped edits
     * from the challenge lifecycle itself), and `LocalChallengeRepository` serialises every
     * read-modify-write pair that reaches it.
     */
    @Update
    suspend fun update(challenge: Challenge)

    /**
     * The paused-flight slot alone.
     *
     * Scoped rather than a whole-row [update] because the two writers of this column - the
     * in-flight session saving its elapsed time/camera, and the landing pipeline clearing the slot
     * - are not the writers of the route columns next to it, and used to revert them. A landing
     * that credited a leg and then had `position_iata`/`leg_index`/`status` written back to their
     * pre-landing values by a camera save is the bug this exists to make unrepresentable; see
     * ChallengeRowConcurrentWriteTest and `InFlightViewModel.landingStarted`.
     */
    @Query("UPDATE challenges SET paused_flight = :flight WHERE id = :id")
    suspend fun updatePausedFlight(id: Int, flight: PausedFlight?)

    /**
     * The Route-progress columns alone - what [advanceRouteChallenge][com.silas270.blocktime
     * .data.repository.LocalChallengeRepository.advanceRouteChallenge] moves, and nothing else.
     * Same reasoning as [updatePausedFlight] in the other direction: crediting a leg must not
     * write back a stale `paused_flight` (or a stale streak, or a stale visited-member set) that
     * some other writer changed in between.
     */
    @Query(
        """
        UPDATE challenges
        SET position_iata = :positionIata,
            route_progress_fraction = :routeProgressFraction,
            leg_index = :legIndex,
            status = :status,
            completed_at = :completedAt
        WHERE id = :id
        """
    )
    suspend fun updateRouteProgress(
        id: Int,
        positionIata: String?,
        routeProgressFraction: Float,
        legIndex: Int,
        status: ChallengeStatus,
        completedAt: Long?
    )

    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun getById(id: Int): Challenge?

    @Query("SELECT * FROM challenges WHERE user_id = :userId AND status = :status")
    suspend fun getByStatus(userId: Int, status: ChallengeStatus): List<Challenge>

    @Query("SELECT * FROM challenges WHERE user_id = :userId AND status = :status")
    fun getByStatusFlow(userId: Int, status: ChallengeStatus): Flow<List<Challenge>>

    @Query("SELECT COUNT(*) FROM challenges WHERE user_id = :userId AND status = :status")
    suspend fun countByStatus(userId: Int, status: ChallengeStatus): Int

    /** Ordered by completion time, newest first - backs the Achievements screen's "Challenges
     *  completed" log (docs/achievements.md - a flat log, not an x/N tally, since custom
     *  challenges and repeat completions mean there's no fixed denominator). Every completion
     *  leaves its own row (completing sets `status = COMPLETED` and keeps the row; only
     *  *abandoning* deletes it - see `ChallengeRepository.abandonChallenge`), so duplicates from
     *  repeat completions of the same curated/custom challenge show up here as separate rows,
     *  same as the flight logbook. */
    @Query("SELECT * FROM challenges WHERE user_id = :userId AND status = :status ORDER BY completed_at DESC")
    suspend fun getByStatusOrderedByCompletedAt(userId: Int, status: ChallengeStatus): List<Challenge>

    /** ACTIVE, or COMPLETED-but-not-yet-celebrated - everything that should still occupy a slot
     *  on the Challenges screen (docs/challenges.md). Ordered by id so slot position is stable
     *  and matches original creation order, same as the plain ACTIVE-only query it replaces for
     *  slot display. */
    @Query(
        """
        SELECT * FROM challenges
        WHERE user_id = :userId
        AND (status = :active OR (status = :completed AND celebrated = 0))
        ORDER BY id ASC
        """
    )
    fun getSlotDisplayFlow(
        userId: Int,
        active: ChallengeStatus = ChallengeStatus.ACTIVE,
        completed: ChallengeStatus = ChallengeStatus.COMPLETED
    ): Flow<List<Challenge>>

    /** Same "occupying a slot" definition as [getSlotDisplayFlow], as a count - what the
     *  active-challenge cap check (`LocalChallengeRepository.hasCapSlot`) tests against, so an
     *  uncelebrated completion still counts against `MAX_ACTIVE_CHALLENGES` instead of leaving a
     *  fourth challenge with nowhere to render. */
    @Query(
        """
        SELECT COUNT(*) FROM challenges
        WHERE user_id = :userId
        AND (status = :active OR (status = :completed AND celebrated = 0))
        """
    )
    suspend fun countOccupyingSlots(
        userId: Int,
        active: ChallengeStatus = ChallengeStatus.ACTIVE,
        completed: ChallengeStatus = ChallengeStatus.COMPLETED
    ): Int

    /** The completed-challenges log, but only entries the player has actually been shown the
     *  completion-presentation animation for - a completion is not "in the log" until its
     *  celebration has played (docs/challenges.md). Backs `listCompletedChallenges()`; replaces
     *  [getByStatusOrderedByCompletedAt] for that one caller. */
    @Query(
        """
        SELECT * FROM challenges WHERE user_id = :userId AND status = 'COMPLETED' AND celebrated = 1
        ORDER BY completed_at DESC
        """
    )
    suspend fun getCelebratedCompletedOrderedByCompletedAt(userId: Int): List<Challenge>

    /** Flips the one column the completion-presentation overlay owns, once its fly-out animation
     *  finishes for this challenge - scoped rather than a whole-row [update] for the same reason
     *  [updatePausedFlight] is: this writer is not the writer of the progress/status columns next
     *  to it, and must not revert them if it races one of those writes. */
    @Query("UPDATE challenges SET celebrated = 1 WHERE id = :id")
    suspend fun markCelebrated(id: Int)
}
