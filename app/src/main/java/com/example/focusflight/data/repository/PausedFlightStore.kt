package com.example.focusflight.data.repository

import com.example.focusflight.data.model.PausedFlight

/**
 * One slot that can hold at most one paused flight - the shared shape behind the Story slot
 * ([PreferencesRepository.pausedStoryFlightStore]), the Free slot
 * ([PreferencesRepository.pausedFreeFlightStore]), and a single Route challenge's own row
 * ([ChallengeRepository.pausedFlightStore]). Lets a caller like `InFlightViewModel` pick *which*
 * store once (at construction, based on `mode`/`challengeId`) instead of re-branching on every
 * read/write/clear.
 */
interface PausedFlightStore {
    suspend fun get(): PausedFlight?
    suspend fun save(flight: PausedFlight)
    suspend fun clear()
}
