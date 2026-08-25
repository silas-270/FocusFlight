package com.example.focusflight.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.example.focusflight.data.model.UserProfile

/**
 * One-time migration of flight logs written to SharedPreferences by older
 * app versions into the Room-backed FlightLogRepository. Safe to call on
 * every app start - it's a no-op once the legacy preference key is empty.
 */
class LegacyFlightLogMigrator(
    private val prefs: SharedPreferences,
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val airportRepository: AirportRepository,
    private val fallbackHomeAirportIata: () -> String? = { null }
) {
    companion object {
        private const val TAG = "LegacyFlightLogMigrator"
    }

    suspend fun migrateIfNeeded() {
        val legacyLogs = prefs.getStringSet("flight_logs_set", null) ?: return
        if (legacyLogs.isEmpty()) return

        Log.d(TAG, "Migrating ${legacyLogs.size} flight logs from SharedPreferences to Room...")

        var profile = userRepository.getProfile()
        if (profile == null) {
            val homeIata = fallbackHomeAirportIata() ?: "STR"
            profile = userRepository.createProfile(UserProfile.generateRandomName(), homeIata)
        }

        for (entry in legacyLogs) {
            try {
                val parts = entry.split("|")
                if (parts.size >= 4) {
                    val origin = parts[0]
                    val dest = parts[1]
                    val duration = parts[2].toIntOrNull() ?: continue
                    val flightNo = parts[3]

                    // Look up distance from routes table, default to 0.0
                    val routes = airportRepository.getOutboundRoutes(origin)
                    val matchingRoute = routes.find { it.destIata == dest }
                    val distanceKm = matchingRoute?.distanceKm ?: 0.0

                    flightLogRepository.logFlight(
                        flightNumber = flightNo,
                        originIata = origin,
                        destIata = dest,
                        durationMin = duration,
                        distanceKm = distanceKm
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate flight log entry: $entry", e)
            }
        }

        prefs.edit().remove("flight_logs_set").apply()
        Log.d(TAG, "Migration complete. Cleared legacy flight_logs_set.")
    }
}
