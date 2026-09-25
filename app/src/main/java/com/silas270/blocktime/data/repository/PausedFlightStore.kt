package com.silas270.blocktime.data.repository

import com.silas270.blocktime.data.model.PausedFlight

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

/**
 * One flight session's view of a [PausedFlightStore], which stops accepting writes the moment the
 * flight lands.
 *
 * The landing pipeline *clears* this slot, and once it has, no write from that session is ever
 * correct again. Without that rule the two other writers - `InFlightViewModel`'s camera save and
 * its elapsed-time persist, neither of which runs in the landing's own coroutine - can each read
 * the slot before the clear and write it back after, resurrecting a flight that has already
 * landed. The pilot lands, and the Hub still offers to resume the flight they just finished.
 *
 * The clear is what seals, rather than a separate call, because "the landing has taken this slot"
 * is exactly the condition, and coupling the two removes the ordering question instead of moving
 * it: a [save] that arrives *before* the clear is discarded by the clear, and one that arrives
 * *after* is refused here. There is no interleaving left that keeps a stale write.
 *
 * Deliberately per-session rather than per-store: `PreferencesRepository`'s Story and Free slots
 * are process-wide singletons, so sealing one of those directly would refuse the *next* flight's
 * writes too. Wrap at the point where a session picks its store (see `InFlightViewModel`), and the
 * seal dies with the session.
 *
 * Not thread-safe by lock, and does not need to be: [sealed] only ever goes false -> true, and a
 * write racing the transition so exactly that it reads the old value is a write that was already
 * concurrent with the clear, which the clear itself wins.
 */
class SessionPausedFlightStore(private val delegate: PausedFlightStore) : PausedFlightStore {

    @Volatile
    private var sealed = false

    /** Whether the landing has taken this slot. Exposed for tests and for callers that want to
     *  skip assembling a write they know will be refused. */
    val isSealed: Boolean get() = sealed

    override suspend fun get(): PausedFlight? = delegate.get()

    override suspend fun save(flight: PausedFlight) {
        if (sealed) return
        delegate.save(flight)
    }

    override suspend fun clear() {
        sealed = true
        delegate.clear()
    }
}
