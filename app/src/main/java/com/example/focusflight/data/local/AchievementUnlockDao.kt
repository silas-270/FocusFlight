package com.example.focusflight.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.focusflight.data.model.AchievementUnlock

@Dao
interface AchievementUnlockDao {

    /**
     * First write wins. [OnConflictStrategy.IGNORE] against the entity's composite
     * (user_id, achievement_id) primary key is what makes re-stamping impossible: the board is
     * re-evaluated every time an achievements surface opens, and an already-recorded unlock must
     * keep its original timestamp rather than jumping to "now" on each read.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(unlock: AchievementUnlock)

    @Query("SELECT * FROM achievement_unlocks WHERE user_id = :userId")
    suspend fun getAllForUser(userId: Int): List<AchievementUnlock>
}
