package com.example.focusflight.data.repository

import com.example.focusflight.data.local.FlightLogDao
import com.example.focusflight.data.local.UserProfileDao
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.ui.viewmodel.FlightStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class LocalFlightLogRepository(
    private val flightLogDao: FlightLogDao,
    private val userProfileDao: UserProfileDao
) : FlightLogRepository {

    private suspend fun getUserId(): Int {
        return userProfileDao.getProfile()?.id
            ?: throw IllegalStateException("No user profile found. Create a profile first.")
    }

    override suspend fun logFlight(
        flightNumber: String,
        originIata: String,
        destIata: String,
        durationMin: Int,
        distanceKm: Double
    ): FlightLog {
        val userId = getUserId()
        val log = FlightLog(
            userId = userId,
            flightNumber = flightNumber,
            originIata = originIata,
            destIata = destIata,
            durationMin = durationMin,
            distanceKm = distanceKm,
            completedAt = System.currentTimeMillis()
        )
        val id = flightLogDao.insertFlightLog(log)
        return log.copy(id = id.toInt())
    }

    override fun getFlightHistoryFlow(): Flow<List<FlightLog>> {
        val profile = kotlinx.coroutines.runBlocking { userProfileDao.getProfile() }
            ?: return emptyFlow()
        return flightLogDao.getFlightHistoryFlow(profile.id)
    }

    override suspend fun getFlightHistory(): List<FlightLog> {
        val userId = getUserId()
        return flightLogDao.getFlightHistory(userId)
    }

    override suspend fun getRecentFlights(limit: Int): List<FlightLog> {
        val userId = getUserId()
        return flightLogDao.getRecentFlights(userId, limit)
    }

    override suspend fun getFlightStats(): FlightStats {
        val userId = getUserId()
        val totalFlights = flightLogDao.getTotalFlights(userId)
        val totalMinutes = flightLogDao.getTotalMinutes(userId)
        val distinctDest = flightLogDao.getDistinctDestinations(userId)
        return FlightStats(
            totalFlights = totalFlights,
            totalMinutes = totalMinutes,
            airportsVisited = distinctDest + 1 // +1 for home airport
        )
    }
}
