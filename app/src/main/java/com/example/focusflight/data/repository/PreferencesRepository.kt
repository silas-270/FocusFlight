package com.example.focusflight.data.repository

import android.content.Context
import com.example.focusflight.data.model.PausedFlight

class PreferencesRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "focus_flight_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_HOME_AIRPORT = "home_airport_iata"
        private const val KEY_CURRENT_AIRPORT = "current_airport_iata"
        private const val KEY_FOCUSED_ROUTE_CHALLENGE_ID = "focused_route_challenge_id"
        private const val KEY_PAUSED_FLIGHT = "paused_flight"

        // docs/design/story-mode.md's two distinct home-base cooldowns (see HomeBaseCooldown) -
        // deliberately two separate keys, not one, since the two actions' cooldowns reset
        // independently of each other.
        private const val KEY_LAST_RETURN_HOME_AT = "last_return_home_at"
        private const val KEY_LAST_HOME_BASE_CHANGED_AT = "last_home_base_changed_at"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun getHomeAirport(): String? {
        return prefs.getString(KEY_HOME_AIRPORT, null)
    }

    fun setHomeAirport(iata: String) {
        prefs.edit().putString(KEY_HOME_AIRPORT, iata).apply()
    }

    fun getCurrentAirport(): String? {
        return prefs.getString(KEY_CURRENT_AIRPORT, getHomeAirport())
    }

    fun setCurrentAirport(iata: String) {
        prefs.edit().putString(KEY_CURRENT_AIRPORT, iata).apply()
    }

    /** Epoch millis of the last return-home teleport, or null if it's never been used - see
     *  [com.example.focusflight.data.model.HomeBaseCooldown]'s 7-day cooldown check. */
    fun getLastReturnHomeAt(): Long? =
        if (prefs.contains(KEY_LAST_RETURN_HOME_AT)) prefs.getLong(KEY_LAST_RETURN_HOME_AT, 0L) else null

    fun setLastReturnHomeAt(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_RETURN_HOME_AT, timestampMs).apply()
    }

    /** Epoch millis of the last home-base change - seeded to 31 days before onboarding at
     *  onboarding time (see `OnboardingViewModel.saveHomeAirport()`), so it's never actually null
     *  in practice, but callers should still treat a genuinely missing value as "always eligible"
     *  like [HomeBaseCooldown.isEligible] does, rather than assuming it's always present. Gates
     *  the separate 30-day change-home-base cooldown - never conflated with
     *  [getLastReturnHomeAt]'s 7-day one. */
    fun getLastHomeBaseChangedAt(): Long? =
        if (prefs.contains(KEY_LAST_HOME_BASE_CHANGED_AT)) prefs.getLong(KEY_LAST_HOME_BASE_CHANGED_AT, 0L) else null

    fun setLastHomeBaseChangedAt(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_HOME_BASE_CHANGED_AT, timestampMs).apply()
    }

    /**
     * The Story/Free slot's in-progress (paused) flight - the Hub's "RESUME FLIGHT" button's
     * data source whenever no challenge is focused (see `HubViewModel`). A Route challenge's own
     * paused flight is never stored here - see `Challenge.pausedFlight` and
     * `ChallengeRepository.pausedFlightStore` for that slot's equivalent - so the two can never
     * clobber each other.
     */
    val pausedFlightStore: PausedFlightStore = object : PausedFlightStore {
        override suspend fun get(): PausedFlight? {
            val raw = prefs.getString(KEY_PAUSED_FLIGHT, null) ?: return null
            return PausedFlight.parse(raw)
        }

        override suspend fun save(flight: PausedFlight) {
            prefs.edit().putString(KEY_PAUSED_FLIGHT, flight.serialize()).apply()
        }

        override suspend fun clear() {
            prefs.edit().remove(KEY_PAUSED_FLIGHT).apply()
        }
    }

    /**
     * The Route challenge currently "focused" on the Hub - i.e. whose airport/progress the Hub
     * shows in place of [getCurrentAirport]. Purely a display concern: it never touches the
     * challenge's own row (see [ChallengeRepository.abandonChallenge] for actual deletion), so
     * clearing this is a pause, not a reset - the challenge keeps its saved position and can be
     * refocused later.
     */
    fun getFocusedRouteChallengeId(): Int? {
        val id = prefs.getInt(KEY_FOCUSED_ROUTE_CHALLENGE_ID, -1)
        return if (id >= 0) id else null
    }

    fun setFocusedRouteChallengeId(id: Int) {
        prefs.edit().putInt(KEY_FOCUSED_ROUTE_CHALLENGE_ID, id).apply()
    }

    fun clearFocusedRouteChallengeId() {
        prefs.edit().remove(KEY_FOCUSED_ROUTE_CHALLENGE_ID).apply()
    }
}
