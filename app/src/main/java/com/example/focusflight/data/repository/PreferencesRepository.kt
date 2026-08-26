package com.example.focusflight.data.repository

import android.content.Context

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

    fun saveActiveFlightContext(flightNo: String, destIata: String, durationMin: Int) {
        prefs.edit().putString("active_flight_context", "$flightNo|$destIata|$durationMin").apply()
    }

    fun getActiveFlightContext(): Triple<String, String, Int>? {
        val str = prefs.getString("active_flight_context", null) ?: return null
        val parts = str.split("|")
        if (parts.size == 3) {
            val duration = parts[2].toIntOrNull() ?: return null
            return Triple(parts[0], parts[1], duration)
        }
        return null
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
