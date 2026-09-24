package com.example.focusflight.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.PausedFlight
import com.example.focusflight.data.model.ThemeMode

/**
 * Primary constructor takes [SharedPreferences] directly so JVM unit tests can drive it with
 * `FakeSharedPreferences` instead of needing an Android [Context]. Production uses the [Context]
 * secondary constructor below and is unaffected.
 */
class PreferencesRepository(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    companion object {
        private const val PREFS_NAME = "focus_flight_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_CURRENT_AIRPORT = "current_airport_iata"
        private const val KEY_FOCUSED_ROUTE_CHALLENGE_ID = "focused_route_challenge_id"
        private const val KEY_PAUSED_FLIGHT = "paused_flight"
        private const val KEY_PAUSED_FREE_FLIGHT = "paused_free_flight"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ENGINE_SOUND_ENABLED = "engine_sound_enabled"
        private const val KEY_ROUTE_LINE_MODE = "route_line_mode"
        private const val KEY_MAP_STYLE = "map_style"
        private const val KEY_OFFLINE_DATA_SAVER = "offline_data_saver"

        // docs/modes.md's two distinct home-base cooldowns (see HomeBaseCooldown) -
        // deliberately two separate keys, not one, since the two actions' cooldowns reset
        // independently of each other.
        private const val KEY_LAST_RETURN_HOME_AT = "last_return_home_at"
        private const val KEY_LAST_HOME_BASE_CHANGED_AT = "last_home_base_changed_at"
    }

    /** Defaults to [ThemeMode.SYSTEM] - the app follows the device's light/dark setting until the
     *  pilot explicitly picks a mode via the Account screen's toggle. */
    fun getThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    /** Defaults to false - synthesized jet-engine noise during an active flight is opt-in until
     *  the pilot enables it in Flight Settings. */
    fun getEngineSoundEnabled(): Boolean = prefs.getBoolean(KEY_ENGINE_SOUND_ENABLED, false)

    fun setEngineSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENGINE_SOUND_ENABLED, enabled).apply()
    }

    /** Defaults to 0 (Full) - the complete route line is drawn across the globe until the pilot
     *  chooses Window (1) or Hidden (2) in Flight Settings. */
    fun getRouteLineMode(): Int = prefs.getInt(KEY_ROUTE_LINE_MODE, 0)

    fun setRouteLineMode(mode: Int) {
        prefs.edit().putInt(KEY_ROUTE_LINE_MODE, mode).apply()
    }

    /** Defaults to 0 (Standard) - the dark basemap on the flat globe, until the pilot chooses
     *  Satellite + Terrain (1) or Offline (2) in Flight Settings. */
    fun getMapStyle(): Int = prefs.getInt(KEY_MAP_STYLE, 0)

    fun setMapStyle(style: Int) {
        prefs.edit().putInt(KEY_MAP_STYLE, style).apply()
    }

    /** Defaults to false - the app goes offline on its own only when there is no connection,
     *  until the pilot turns on "Offline maps" in Settings to save data even while connected. */
    fun isOfflineDataSaverEnabled(): Boolean = prefs.getBoolean(KEY_OFFLINE_DATA_SAVER, false)

    fun setOfflineDataSaverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_DATA_SAVER, enabled).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    /**
     * The pilot's current position. No longer falls back to the home airport: home lives in Room
     * now, so the fallback cannot be resolved synchronously here. Callers that want
     * "current, or home if unset" use
     * [com.example.focusflight.domain.resolveCurrentAirportIata], which owns that rule in one
     * place rather than hiding it inside a getter.
     */
    fun getCurrentAirport(): String? {
        return prefs.getString(KEY_CURRENT_AIRPORT, null)
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
     * Story Mode's in-progress (paused) flight - the Hub's "RESUME FLIGHT" button's data source
     * whenever no challenge is focused (see `HubViewModel`). Fully separate from
     * [pausedFreeFlightStore] (so a paused Story flight and a paused Free flight can coexist) and
     * from a Route challenge's own paused flight, which is never stored here - see
     * `Challenge.pausedFlight` and `ChallengeRepository.pausedFlightStore` for that slot's
     * equivalent. Prefer [pausedFlightStore] (the mode-dispatching function below) over reading
     * this directly, so a caller can't accidentally read/write the wrong mode's slot.
     */
    val pausedStoryFlightStore: PausedFlightStore = pausedFlightStoreFor(KEY_PAUSED_FLIGHT)

    /**
     * Free Mode's in-progress (paused) flight - its own slot, independent of
     * [pausedStoryFlightStore], so starting a fresh Story flight can never clobber a paused Free
     * one or vice versa. Surfaced on the Challenges screen's Free Mode row (see
     * `ChallengesViewModel.pausedFreeFlight`), not the Hub - the Hub only ever shows Story Mode.
     */
    val pausedFreeFlightStore: PausedFlightStore = pausedFlightStoreFor(KEY_PAUSED_FREE_FLIGHT)

    private fun pausedFlightStoreFor(key: String): PausedFlightStore = object : PausedFlightStore {
        override suspend fun get(): PausedFlight? {
            val raw = prefs.getString(key, null) ?: return null
            return PausedFlight.parse(raw)
        }

        override suspend fun save(flight: PausedFlight) {
            prefs.edit().putString(key, flight.serialize()).apply()
        }

        override suspend fun clear() {
            prefs.edit().remove(key).apply()
        }
    }

    /** Picks [pausedStoryFlightStore] or [pausedFreeFlightStore] by [mode] - the one slot a
     *  STORY or FREE `InFlightViewModel`/CheckIn session should ever read or write, so the two
     *  modes' paused flights can never collide. CHALLENGE sessions don't use this at all - they
     *  use `ChallengeRepository.pausedFlightStore(challengeId)` instead. */
    fun pausedFlightStore(mode: FlightMode): PausedFlightStore = when (mode) {
        FlightMode.FREE -> pausedFreeFlightStore
        else -> pausedStoryFlightStore
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
