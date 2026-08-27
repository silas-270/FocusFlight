package com.example.focusflight.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Insert
    suspend fun insert(challenge: Challenge): Long

    @Update
    suspend fun update(challenge: Challenge)

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
     *  completed" log (docs/design/achievements.md - a flat log, not an x/N tally, since custom
     *  challenges and repeat completions mean there's no fixed denominator). Every completion
     *  leaves its own row (completing sets `status = COMPLETED` and keeps the row; only
     *  *abandoning* deletes it - see `ChallengeRepository.abandonChallenge`), so duplicates from
     *  repeat completions of the same curated/custom challenge show up here as separate rows,
     *  same as the flight logbook. */
    @Query("SELECT * FROM challenges WHERE user_id = :userId AND status = :status ORDER BY completed_at DESC")
    suspend fun getByStatusOrderedByCompletedAt(userId: Int, status: ChallengeStatus): List<Challenge>
}
