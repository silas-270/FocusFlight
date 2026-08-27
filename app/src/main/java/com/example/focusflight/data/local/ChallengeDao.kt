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
}
