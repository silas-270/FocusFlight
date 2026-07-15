package com.example.focusflight.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.focusflight.data.model.FlightLog
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightLogDao {
    @Insert
    suspend fun insertFlightLog(log: FlightLog): Long

    @Query("SELECT * FROM flight_log WHERE user_id = :userId ORDER BY completed_at DESC")
    fun getFlightHistoryFlow(userId: Int): Flow<List<FlightLog>>

    @Query("SELECT * FROM flight_log WHERE user_id = :userId ORDER BY completed_at DESC")
    suspend fun getFlightHistory(userId: Int): List<FlightLog>

    @Query("SELECT * FROM flight_log WHERE user_id = :userId ORDER BY completed_at DESC LIMIT :limit")
    suspend fun getRecentFlights(userId: Int, limit: Int = 5): List<FlightLog>

    @Query("SELECT COUNT(*) FROM flight_log WHERE user_id = :userId")
    suspend fun getTotalFlights(userId: Int): Int

    @Query("SELECT COALESCE(SUM(duration_min), 0) FROM flight_log WHERE user_id = :userId")
    suspend fun getTotalMinutes(userId: Int): Int

    @Query("SELECT COUNT(DISTINCT dest_iata) FROM flight_log WHERE user_id = :userId")
    suspend fun getDistinctDestinations(userId: Int): Int
}
