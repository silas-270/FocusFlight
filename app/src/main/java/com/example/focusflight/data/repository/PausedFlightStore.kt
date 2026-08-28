package com.example.focusflight.data.repository

import com.example.focusflight.data.model.PausedFlight

/**
 * One slot that can hold at most one paused flight - the shared shape behind both the Story/Free
 * global slot ([PreferencesRepository.pausedFlightStore]) and a single Route challenge's own row
 * ([ChallengeRepository.pausedFlightStore]). Lets a caller like `InFlightViewModel` pick *which*
 * store once (at construction, based on `mode`/`challengeId`) instead of re-branching on every
 * read/write/clear.
 */
interface PausedFlightStore {
    suspend fun get(): PausedFlight?
    suspend fun save(flight: PausedFlight)
    suspend fun clear()
}
