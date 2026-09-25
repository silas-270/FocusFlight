package com.silas270.blocktime.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.silas270.blocktime.data.model.FlightLog
import com.silas270.blocktime.data.model.FlightMode
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
    suspend fun getRecentFlights(userId: Int, limit: Int): List<FlightLog>

    @Query("SELECT COUNT(*) FROM flight_log WHERE user_id = :userId")
    suspend fun getTotalFlights(userId: Int): Int

    @Query("SELECT COALESCE(SUM(duration_min), 0) FROM flight_log WHERE user_id = :userId")
    suspend fun getTotalMinutes(userId: Int): Int

    /**
     * How many distinct airports the pilot has *visited* - the origins and destinations of STORY
     * flights, matching `AirportRepository.getVisitedGeography` (a Story origin is always somewhere
     * the pilot really was, under the origin lock). Counts only STORY flights and never
     * counting [homeIata] - which the caller adds back itself, exactly once, whether or not it
     * ever appears as a destination here.
     *
     * The mode filter is the whole point: the visited-set is STORY-only (the isolation matrix in
     * docs/modes.md), and the Passport's visited-country map and Geographic
     * achievements both come from `AirportRepository.getVisitedGeography`, which filters the same
     * way. A mode-blind count let a Free Mode or Challenge flight raise the "places visited"
     * number sitting right next to a map that had not changed at all.
     *
     * [mode] is deliberately a [FlightMode] rather than a hardcoded `'STORY'` string so Room's
     * [Converters][com.silas270.blocktime.data.local.Converters] owns the enum's on-disk
     * spelling here as everywhere else; a literal would silently stop matching the day that
     * encoding changes. A null [homeIata] (no home base yet) excludes nothing.
     */
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT dest_iata AS iata FROM flight_log WHERE user_id = :userId AND mode = :mode
            UNION
            SELECT origin_iata AS iata FROM flight_log WHERE user_id = :userId AND mode = :mode
        )
        WHERE iata <> '' AND (:homeIata IS NULL OR iata <> :homeIata)
    """)
    suspend fun getDistinctAirportsInMode(userId: Int, mode: FlightMode, homeIata: String?): Int

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
