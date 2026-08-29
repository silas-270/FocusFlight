package com.example.focusflight.data.repository

import androidx.paging.PagingSource
import com.example.focusflight.data.local.FlightLogDao
import com.example.focusflight.data.local.UserProfileDao
import com.example.focusflight.data.local.requireProfileId
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightHighlights
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.FlightSortOrder
import com.example.focusflight.data.model.FlightStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class LocalFlightLogRepository(
    private val flightLogDao: FlightLogDao,
    private val userProfileDao: UserProfileDao
) : FlightLogRepository {

    override suspend fun logFlight(
        flightNumber: String,
        originIata: String,
        destIata: String,
        durationMin: Int,
        distanceKm: Double,
        mode: FlightMode
    ): FlightLog {
        val userId = userProfileDao.requireProfileId()
        val log = FlightLog(
            userId = userId,
            flightNumber = flightNumber,
            originIata = originIata,
            destIata = destIata,
            durationMin = durationMin,
            distanceKm = distanceKm,
            completedAt = System.currentTimeMillis(),
            mode = mode
        )
        val id = flightLogDao.insertFlightLog(log)
        return log.copy(id = id.toInt())
    }

    /**
     * Resolves the profile at collection time rather than construction time, so the Room lookup
     * lands on the collector's dispatcher instead of blocking whichever thread happened to build
     * the flow. Same change, same reason, as `LocalChallengeRepository.listActiveChallengesFlow`.
     *
     * A missing profile still yields an empty flow rather than throwing - this is read by the
     * Passport and Flight Search, both of which are only reachable post-onboarding, so a null
     * profile here means something is already wrong and emitting nothing is the quiet option.
     */
    override fun getFlightHistoryFlow(): Flow<List<FlightLog>> = flow {
        val profile = userProfileDao.getProfile() ?: return@flow
        emitAll(flightLogDao.getFlightHistoryFlow(profile.id))
    }

    override suspend fun getFlightHistory(): List<FlightLog> {
        val userId = userProfileDao.requireProfileId()
        return flightLogDao.getFlightHistory(userId)
    }

    override suspend fun getRecentFlights(limit: Int): List<FlightLog> {
        val userId = userProfileDao.requireProfileId()
        return flightLogDao.getRecentFlights(userId, limit)
    }

    /**
     * `airportsVisited` is deliberately the size of exactly the set
     * [AirportRepository.getVisitedGeography][com.example.focusflight.data.repository.AirportRepository.getVisitedGeography]
     * builds - `distinct(STORY destinations + home)` - because the two are drawn side by side on
     * the Passport and any other definition makes the number contradict its own map.
     *
     * It used to be `COUNT(DISTINCT dest_iata) over every mode, + 1 for home`, which was wrong
     * twice over. The missing mode filter meant a Free Mode or Challenge flight raised "places
     * visited" while the visited-country map (STORY-only, per docs/design/mechanics.md's
     * isolation matrix) stayed exactly as it was. And the unconditional `+ 1` double-counted home
     * for any pilot who had ever flown *to* their own home base in Story Mode, since home was
     * already in the distinct count. The DAO now excludes home from the STORY count and we add it
     * back here once, only when there is a home base at all - so home contributes 1 or 0, never 2.
     *
     * `totalFlights`/`totalMinutes` stay deliberately mode-blind: they are activity, not
     * geography, and a Free Mode flight really was flown.
     */
    override suspend fun getFlightStats(homeAirportIata: String?): FlightStats {
        val userId = userProfileDao.requireProfileId()
        val totalFlights = flightLogDao.getTotalFlights(userId)
        val totalMinutes = flightLogDao.getTotalMinutes(userId)
        val homeIata = homeAirportIata?.takeIf { it.isNotBlank() }
        val distinctStoryDest =
            flightLogDao.getDistinctDestinationsInMode(userId, FlightMode.STORY, homeIata)
        return FlightStats(
            totalFlights = totalFlights,
            totalMinutes = totalMinutes,
            airportsVisited = distinctStoryDest + if (homeIata != null) 1 else 0
        )
    }

    override fun getFlightsPagingSource(sortOrder: FlightSortOrder): PagingSource<Int, FlightLog> {
        val userId = kotlinx.coroutines.runBlocking { userProfileDao.requireProfileId() }
        return when (sortOrder) {
            FlightSortOrder.DATE_DESC -> flightLogDao.getFlightsPagedDateDesc(userId)
            FlightSortOrder.DATE_ASC -> flightLogDao.getFlightsPagedDateAsc(userId)
            FlightSortOrder.DISTANCE_DESC -> flightLogDao.getFlightsPagedDistanceDesc(userId)
            FlightSortOrder.DISTANCE_ASC -> flightLogDao.getFlightsPagedDistanceAsc(userId)
            FlightSortOrder.DURATION_DESC -> flightLogDao.getFlightsPagedDurationDesc(userId)
        }
    }

    override suspend fun getFlightHighlights(): FlightHighlights {
        val userId = userProfileDao.requireProfileId()
        val longest = flightLogDao.getLongestFlight(userId)
        val mostVisitedIata = flightLogDao.getMostVisitedIata(userId)
        val mostVisitedCount = mostVisitedIata?.let { flightLogDao.getVisitCount(userId, it) } ?: 0
        val totalDist = flightLogDao.getTotalDistanceKm(userId)
        
        // Length of equator is ~40,075 km
        val equatorCircumference = 40075.0
        val equatorRatio = totalDist / equatorCircumference

        return FlightHighlights(
            longestFlight = longest,
            mostVisitedIata = mostVisitedIata,
            mostVisitedCount = mostVisitedCount,
            equatorRatio = equatorRatio
        )
    }
}
