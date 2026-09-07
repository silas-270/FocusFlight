package com.example.focusflight.data.repository

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
    /**
     * Hub/Passport headline numbers. [homeAirportIata] is only used by `airportsVisited`, which is
     * a *geography* stat and therefore STORY-scoped with home counted once; `totalFlights` and
     * `totalMinutes` stay mode-blind, because a Free Mode flight really was flown. Callers should
     * resolve the parameter through
     * [resolveHomeAirportIata][com.example.focusflight.domain.resolveHomeAirportIata] rather than
     * reading a profile field directly - null means "no home base yet", not "unknown".
     */
    suspend fun getFlightStats(homeAirportIata: String?): FlightStats
    suspend fun getFlightHighlights(): FlightHighlights
}
