package com.example.focusflight.data.repository

import com.example.focusflight.data.model.FlightLog
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
}
