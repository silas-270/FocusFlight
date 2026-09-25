package com.silas270.blocktime.data.repository

import com.silas270.blocktime.data.model.AchievementBoard
import com.silas270.blocktime.data.model.FlightLog
import com.silas270.blocktime.data.model.VisitedGeography

/**
 * The one seam that turns [com.silas270.blocktime.data.model.AchievementProgress]'s pure,
 * always-recomputed board into one carrying real unlock timestamps - see
 * [com.silas270.blocktime.data.model.AchievementUnlock] for why only the *timestamp* is stored.
 *
 * Two entry points on purpose, one stamping implementation behind both:
 * - [evaluateBoard] for callers that already hold the inputs (the Passport screen loads the same
 *   geography + flight history for its map, stats and logbook anyway - re-fetching them here
 *   would double every query on that screen).
 * - [loadBoard] for callers that hold no flight data of their own (the Challenges screen).
 */
interface AchievementsRepository {

    /** Evaluate from already-loaded inputs, then stamp/merge unlock times. */
    suspend fun evaluateBoard(geo: VisitedGeography, history: List<FlightLog>): AchievementBoard

    /** Fetch the inputs, then delegate to [evaluateBoard]. */
    suspend fun loadBoard(): AchievementBoard
}
