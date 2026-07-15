package com.example.focusflight.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.focusflight.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile): Long

    @Update
    suspend fun updateProfile(profile: UserProfile)

    @Query("UPDATE user_profile SET username = :username, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateUsername(id: Int, username: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET home_airport_iata = :iata, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateHomeAirport(id: Int, iata: String, updatedAt: Long = System.currentTimeMillis())
}
