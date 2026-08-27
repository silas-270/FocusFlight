package com.example.focusflight.data.repository

import com.example.focusflight.data.local.AchievementUnlockDao
import com.example.focusflight.data.local.UserProfileDao
import com.example.focusflight.data.model.AchievementBoard
import com.example.focusflight.data.model.AchievementProgress
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.data.model.AchievementUnlock
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.VisitedGeography

/**
 * Stamps unlock timestamps onto an otherwise purely-computed [AchievementBoard].
 *
 * Deliberately a *read-path* write ("lazy upsert"), not a landing-pipeline one: achievements stay
 * fully re-derivable from flight history, so there is exactly one place that can record an unlock
 * rather than two that must agree. This also makes it self-healing across upgrades - a user who
 * earned achievements before the `achievement_unlocks` table existed gets them stamped the first
 * time they open an achievements surface, with no separate backfill migration.
 *
 * The trade-off, accepted knowingly: those pre-existing unlocks carry a stamp of "first opened
 * after updating" rather than the true historical moment. Nothing in the flight log can recover
 * the real one, and no UI claims otherwise - the timestamp is only ever used to order badges.
 */
class LocalAchievementsRepository(
    private val achievementUnlockDao: AchievementUnlockDao,
    private val userProfileDao: UserProfileDao,
    private val airportRepository: AirportRepository,
    private val flightLogRepository: FlightLogRepository,
    /** Injectable for tests; production always reads the wall clock. */
    private val now: () -> Long = System::currentTimeMillis
) : AchievementsRepository {

    private suspend fun getUserId(): Int =
        userProfileDao.getProfile()?.id
            ?: throw IllegalStateException("No user profile found. Create a profile first.")

    override suspend fun evaluateBoard(
        geo: VisitedGeography,
        history: List<FlightLog>
    ): AchievementBoard {
        val board = AchievementProgress.evaluateAll(geo, history)
        val userId = getUserId()
        val stampedAt = now()

        // Record anything newly unlocked. IGNORE-on-conflict means an already-stamped achievement
        // keeps its original time, so this is safe to run on every single board read.
        (board.geographic + board.distance + board.behavioral)
            .filter { it.isUnlocked }
            .forEach { achievementUnlockDao.insertIfAbsent(AchievementUnlock(userId, it.id, stampedAt)) }

        val unlockTimes = achievementUnlockDao.getAllForUser(userId)
            .associate { it.achievementId to it.unlockedAt }

        fun merge(list: List<AchievementStatus>): List<AchievementStatus> =
            list.map { it.copy(unlockedAt = unlockTimes[it.id]) }

        return AchievementBoard(
            geographic = merge(board.geographic),
            distance = merge(board.distance),
            behavioral = merge(board.behavioral)
        )
    }

    override suspend fun loadBoard(): AchievementBoard {
        val history = flightLogRepository.getFlightHistory()
        val homeIata = userProfileDao.getProfile()?.homeAirportIata
        // getVisitedGeography filters to STORY internally, as do evaluateDistance/evaluateBehavioral
        // - so passing the raw all-modes history here is correct, matching AccountViewModel.
        val geo = airportRepository.getVisitedGeography(history, homeIata)
        return evaluateBoard(geo, history)
    }
}
