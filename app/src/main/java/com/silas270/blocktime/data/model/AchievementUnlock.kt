package com.silas270.blocktime.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * The moment an achievement was first observed as unlocked - the *only* persisted achievement
 * state in the app.
 *
 * Achievements themselves stay fully re-derivable on read ([AchievementProgress] is pure, with no
 * stored "unlocked" flag); this table exists solely so earned badges can be ordered newest-first
 * on the Passport, which re-derivation alone can never tell us. Nothing reads it to decide
 * *whether* an achievement is unlocked - only *when* it happened.
 *
 * The composite primary key is load-bearing: it turns the write into an idempotent
 * `INSERT OR IGNORE` (see `AchievementUnlockDao.insertIfAbsent`), so re-evaluating the board on
 * every screen open can never overwrite an earlier, truer timestamp - first write wins, with no
 * read-then-write race.
 */
@Entity(
    tableName = "achievement_unlocks",
    primaryKeys = ["user_id", "achievement_id"],
    foreignKeys = [
        ForeignKey(
            entity = UserProfile::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["user_id"])]
)
data class AchievementUnlock(
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "achievement_id") val achievementId: String,
    @ColumnInfo(name = "unlocked_at") val unlockedAt: Long
)
