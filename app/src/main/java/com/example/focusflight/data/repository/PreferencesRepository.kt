package com.example.focusflight.data.repository

import android.content.Context
import com.example.focusflight.data.model.FlightMode

/**
 * The in-progress flight resumable from the Hub's "RESUME FLIGHT" button, persisted so it
 * survives the app being killed and restarted. Carries the full booking context - not just
 * [destIata]/[durationMin] as it did pre-Phase-2 - so a resumed flight reproduces exactly what
 * was booked: the correct origin (which, for a Free Mode flight, is never assumed to equal
 * `currentAirport`) and the correct [mode], so `InFlightViewModel.completeFlight()` branches
 * correctly on landing instead of silently resuming as STORY (the Phase 1 gap flagged in
 * docs/design/codebase-map.md).
 */
data class ActiveFlightContext(
    val flightNumber: String,
    val originIata: String,
    val destIata: String,
    val durationMin: Int,
    val mode: FlightMode
) {
    fun serialize(): String =
        listOf(flightNumber, originIata, destIata, durationMin.toString(), mode.name).joinToString("|")

    companion object {
        /**
         * Parses the persisted string. Accepts both today's 5-field format and the pre-Phase-2
         * 3-field format (`flightNo|destIata|durationMin`, implicitly always STORY with an
         * implicit origin of `currentAirport`) so a context saved by an older build of the app
         * still resumes correctly instead of crashing/dropping silently. [currentAirportIata]
         * supplies the missing origin in that legacy case only.
         */
        fun parse(raw: String, currentAirportIata: String?): ActiveFlightContext? {
            val parts = raw.split("|")
            return when (parts.size) {
                5 -> {
                    val duration = parts[3].toIntOrNull() ?: return null
                    val mode = runCatching { FlightMode.valueOf(parts[4]) }.getOrDefault(FlightMode.STORY)
                    ActiveFlightContext(parts[0], parts[1], parts[2], duration, mode)
                }
                3 -> {
                    val duration = parts[2].toIntOrNull() ?: return null
                    ActiveFlightContext(parts[0], currentAirportIata ?: "STR", parts[1], duration, FlightMode.STORY)
                }
                else -> null
            }
        }
    }
}

/** A saved camera perspective: mode (0=Free/1=Tracking/2=Cockpit) plus position/rotation,
 *  matching CesiumLiveJniBridge.nativeGetCameraPose()'s array layout. */
data class CameraPose(
    val mode: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val qx: Double,
    val qy: Double,
    val qz: Double,
    val qw: Double
)

class PreferencesRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "focus_flight_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_HOME_AIRPORT = "home_airport_iata"
        private const val KEY_CURRENT_AIRPORT = "current_airport_iata"
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



    fun saveActiveFlightProgress(flightNo: String, elapsedMs: Long) {
        prefs.edit().putLong("active_flight_$flightNo", elapsedMs).apply()
    }

    fun getActiveFlightProgress(flightNo: String): Long? {
        val key = "active_flight_$flightNo"
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    fun clearActiveFlightProgress(flightNo: String) {
        prefs.edit().remove("active_flight_$flightNo").apply()
    }

    fun saveActiveFlightContext(flightNo: String, originIata: String, destIata: String, durationMin: Int, mode: FlightMode) {
        prefs.edit().putString(
            "active_flight_context",
            ActiveFlightContext(flightNo, originIata, destIata, durationMin, mode).serialize()
        ).apply()
    }

    fun getActiveFlightContext(): ActiveFlightContext? {
        val str = prefs.getString("active_flight_context", null) ?: return null
        return ActiveFlightContext.parse(str, getCurrentAirport())
    }

    fun clearActiveFlightContext() {
        prefs.edit().remove("active_flight_context").apply()
    }

    fun saveActiveFlightCamera(flightNo: String, pose: CameraPose) {
        val packed = listOf(
            pose.mode, pose.x, pose.y, pose.z, pose.qx, pose.qy, pose.qz, pose.qw
        ).joinToString("|")
        prefs.edit().putString("active_flight_camera_$flightNo", packed).apply()
    }

    fun getActiveFlightCamera(flightNo: String): CameraPose? {
        val str = prefs.getString("active_flight_camera_$flightNo", null) ?: return null
        val parts = str.split("|")
        if (parts.size != 8) return null
        return try {
            CameraPose(
                mode = parts[0].toInt(),
                x = parts[1].toDouble(),
                y = parts[2].toDouble(),
                z = parts[3].toDouble(),
                qx = parts[4].toDouble(),
                qy = parts[5].toDouble(),
                qz = parts[6].toDouble(),
                qw = parts[7].toDouble()
            )
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun clearActiveFlightCamera(flightNo: String) {
        prefs.edit().remove("active_flight_camera_$flightNo").apply()
    }
}
