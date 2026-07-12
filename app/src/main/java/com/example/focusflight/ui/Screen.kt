package com.example.focusflight.ui

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Hub : Screen("hub")
    object FlightSearch : Screen("flight_search")
    object CheckIn : Screen("check_in/{destIata}") {
        fun createRoute(destIata: String) = "check_in/$destIata"
    }
}
