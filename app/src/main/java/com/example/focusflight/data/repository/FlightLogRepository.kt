package com.example.focusflight.data.repository

import androidx.paging.PagingSource
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightHighlights
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.FlightSortOrder
import com.example.focusflight.data.model.FlightStats
import kotlinx.coroutines.flow.Flow

interface FlightLogRepository {
    suspend fun logFlight(
        flightNumber: String,
        originIata: String,
        destIata: String,
        durationMin: Int,
        distanceKm: Double,
        mode: FlightMode = FlightMode.STORY
    ): FlightLog
    fun getFlightHistoryFlow(): Flow<List<FlightLog>>
    suspend fun getFlightHistory(): List<FlightLog>
    suspend fun getRecentFlights(limit: Int = 5): List<FlightLog>
    suspend fun getFlightStats(): FlightStats
    fun getFlightsPagingSource(sortOrder: FlightSortOrder): PagingSource<Int, FlightLog>
    suspend fun getFlightHighlights(): FlightHighlights
}
