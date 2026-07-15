package com.example.focusflight.data.local

import androidx.paging.PagingSource
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

    // ── Paged queries (one per sort order — Room requires static SQL) ──────

    @Query("SELECT * FROM flight_log WHERE user_id = :userId ORDER BY completed_at DESC")
    fun getFlightsPagedDateDesc(userId: Int): PagingSource<Int, FlightLog>

    @Query("SELECT * FROM flight_log WHERE user_id = :userId ORDER BY completed_at ASC")
    fun getFlightsPagedDateAsc(userId: Int): PagingSource<Int, FlightLog>

    @Query("SELECT * FROM flight_log WHERE user_id = :userId ORDER BY distance_km DESC")
    fun getFlightsPagedDistanceDesc(userId: Int): PagingSource<Int, FlightLog>

    @Query("SELECT * FROM flight_log WHERE user_id = :userId ORDER BY distance_km ASC")
    fun getFlightsPagedDistanceAsc(userId: Int): PagingSource<Int, FlightLog>

    @Query("SELECT * FROM flight_log WHERE user_id = :userId ORDER BY duration_min DESC")
    fun getFlightsPagedDurationDesc(userId: Int): PagingSource<Int, FlightLog>

    // ── Highlight aggregate queries ────────────────────────────────────────

    @Query("SELECT * FROM flight_log WHERE user_id = :userId ORDER BY distance_km DESC LIMIT 1")
    suspend fun getLongestFlight(userId: Int): FlightLog?

    @Query("""
        SELECT dest_iata FROM flight_log
        WHERE user_id = :userId
        GROUP BY dest_iata
        ORDER BY COUNT(*) DESC
        LIMIT 1
    """)
    suspend fun getMostVisitedIata(userId: Int): String?

    @Query("SELECT COUNT(*) FROM flight_log WHERE user_id = :userId AND dest_iata = :iata")
    suspend fun getVisitCount(userId: Int, iata: String): Int

    @Query("SELECT COALESCE(SUM(distance_km), 0.0) FROM flight_log WHERE user_id = :userId")
    suspend fun getTotalDistanceKm(userId: Int): Double
}
