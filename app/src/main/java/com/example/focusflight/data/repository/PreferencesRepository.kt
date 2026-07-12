package com.example.focusflight.data.repository

import android.content.Context

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

    fun saveFlightLog(origin: String, dest: String, durationMin: Int, flightNo: String, rank: String, date: String) {
        val currentLogs = prefs.getStringSet("flight_logs_set", emptySet()) ?: emptySet()
        val entry = "$origin|$dest|$durationMin|$flightNo|$rank|$date"
        val newLogs = currentLogs.toMutableSet()
        newLogs.add(entry)
        prefs.edit().putStringSet("flight_logs_set", newLogs).apply()
    }

    fun getFlightLogs(): List<String> {
        return prefs.getStringSet("flight_logs_set", emptySet())?.toList() ?: emptyList()
    }
}
