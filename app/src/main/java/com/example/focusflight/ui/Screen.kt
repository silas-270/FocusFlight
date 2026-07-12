package com.example.focusflight.ui

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Hub : Screen("hub")
    object FlightSearch : Screen("flight_search")
    object CheckIn : Screen("check_in/{destIata}") {
        fun createRoute(destIata: String) = "check_in/$destIata"
    }
    object InFlight : Screen("in_flight/{flightNo}/{destIata}/{durationMin}") {
        fun createRoute(flightNo: String, destIata: String, durationMin: Int) =
            "in_flight/$flightNo/$destIata/$durationMin"
    }
}
