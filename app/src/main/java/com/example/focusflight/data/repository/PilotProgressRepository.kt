package com.example.focusflight.data.repository

import android.util.Log
import com.example.focusflight.data.local.airport.AirportDataException
import com.example.focusflight.data.model.AchievementBoard
import com.example.focusflight.data.model.FlightHighlights
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightStats
import com.example.focusflight.data.model.Tour
import com.example.focusflight.data.model.TourSegmentation
import com.example.focusflight.data.model.VisitedGeography
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

/**
 * Everything derivable from the pilot's flight history plus their home airport, computed once.
 */
data class PilotProgress(
    val history: List<FlightLog>,
    val homeAirportIata: String?,
    val geography: VisitedGeography,
    val achievements: AchievementBoard,
    val stats: FlightStats,
    val highlights: FlightHighlights,
    /** Newest tour first. Whether the first one is still running is a question for
     *  [com.example.focusflight.data.model.isOpenAt], not a field here - see its doc. */
    val tours: List<Tour>
)

/**
 * One derivation of the pilot's progress, shared by every screen that shows any part of it.
 *
 * Before this, the Passport, the Challenges screen, the Hub and Flight Search each rebuilt their
 * own slice from scratch, on their own ViewModel, on every single visit - and each of those
 * ViewModels is destroyed on `popBackStack`, so reopening the Passport meant re-reading the whole
 * flight log, re-deriving the visited geography (a full-table scan over the airports DB), and
 * re-evaluating every achievement, for an answer identical to the one discarded seconds earlier.
 * That is the loading time this exists to delete.
 *
 * **Why this is a cache and not a second source of truth.** Nothing here is persisted. The whole
 * chain is rooted in two Room `Flow`s - the profile row and the flight log - and Room's own
 * invalidation tracker re-emits them on *any* write to those tables. So the cached value's
 * lifetime is bound to the same mechanism that already guarantees the underlying data is correct:
 * it cannot go stale unless Room itself is wrong, and there is no second writer that could
 * disagree with it. Discarding this object at any moment and rebuilding it would produce exactly
 * the same values, which is the test for whether something is a cache at all (see
 * docs/state.md).
 *
 * Deliberately *not* keyed on the whole profile row: a username edit changes the profile but
 * cannot change any number derived here, so the key is narrowed to "does a profile exist, and what
 * is its home airport" to avoid pointless recomputation.
 *
 * Started eagerly. The point is for this to already be warm when the pilot opens a screen, not to
 * begin working once they have.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PilotProgressRepository(
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val airportRepository: AirportRepository,
    private val achievementsRepository: AchievementsRepository,
    scope: CoroutineScope,
    /** The pilot's own calendar, for tour day boundaries. A parameter so tests can fix it - and
     *  deliberately the *device* zone, not an airport's: `util/FlightClock.kt`'s per-airport
     *  approximation answers "what time is it where I landed", which is a different question. */
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    /** The only inputs that can change a derived value. See the class doc. */
    private data class ProfileKey(val exists: Boolean, val homeIata: String?)

    val progress: StateFlow<PilotProgress?> =
        userRepository.getProfileFlow()
            .map { profile ->
                ProfileKey(
                    exists = profile != null,
                    homeIata = profile?.homeAirportIata?.takeIf { it.isNotBlank() }
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { key -> progressFor(key) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private fun progressFor(key: ProfileKey): Flow<PilotProgress?> {
        // Pre-onboarding there is no profile row, and every repository below resolves the user
        // through requireProfileId(), which throws when it is missing. Emitting null is both the
        // truthful answer ("no progress yet") and what keeps this off a throwing path at startup.
        if (!key.exists) return flowOf(null)

        // mapNotNull, not map: a failed derivation skips the emission entirely so the StateFlow
        // keeps its last good value. Replacing real progress with a blank or partial one would be
        // strictly worse than showing data that is a few seconds old - and the next write to
        // either table retries automatically.
        return flightLogRepository.getFlightHistoryFlow().mapNotNull { history ->
            try {
                compute(key.homeIata, history)
            } catch (e: Exception) {
                Log.e(TAG, "Progress derivation failed; keeping previous snapshot", e)
                null
            }
        }
    }

    private suspend fun compute(homeIata: String?, history: List<FlightLog>): PilotProgress {
        val geography = airportRepository.getVisitedGeography(history, homeIata)
        // Also stamps newly-earned unlock timestamps (see LocalAchievementsRepository's doc on why
        // that is a read-path write). Running it here means it happens once per change rather than
        // once per screen visit, which is strictly fewer writes than before.
        val achievements = achievementsRepository.evaluateBoard(geography, history)
        return PilotProgress(
            history = history,
            homeAirportIata = homeIata,
            geography = geography,
            achievements = achievements,
            stats = flightLogRepository.getFlightStats(homeIata),
            highlights = flightLogRepository.getFlightHighlights(),
            // A fold over `history`, which is already in hand - no query, and nothing persisted.
            tours = TourSegmentation.segment(history, zone)
        )
    }

    private companion object {
        private const val TAG = "PilotProgressRepository"
    }
}
