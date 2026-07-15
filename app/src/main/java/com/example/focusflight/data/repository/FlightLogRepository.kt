package com.example.focusflight.data.repository

import androidx.paging.PagingSource
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.ui.viewmodel.FlightHighlights
import com.example.focusflight.ui.viewmodel.FlightSortOrder
import com.example.focusflight.ui.viewmodel.FlightStats
import kotlinx.coroutines.flow.Flow

interface FlightLogRepository {
    suspend fun logFlight(
        flightNumber: String,
        originIata: String,
        destIata: String,
        durationMin: Int,
        distanceKm: Double
    ): FlightLog
    fun getFlightHistoryFlow(): Flow<List<FlightLog>>
    suspend fun getFlightHistory(): List<FlightLog>
    suspend fun getRecentFlights(limit: Int = 5): List<FlightLog>
    suspend fun getFlightStats(): FlightStats
    fun getFlightsPagingSource(sortOrder: FlightSortOrder): PagingSource<Int, FlightLog>
    suspend fun getFlightHighlights(): FlightHighlights
}
