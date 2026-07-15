package com.example.focusflight.ui

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Hub : Screen("hub")
    object FlightSearch : Screen("flight_search")
    object CheckIn : Screen("check_in/{flightNo}/{destIata}/{durationMin}") {
        fun createRoute(flightNo: String, destIata: String, durationMin: Int) =
            "check_in/$flightNo/$destIata/$durationMin"
    }
    object InFlight : Screen("in_flight/{flightNo}/{destIata}/{durationMin}") {
        fun createRoute(flightNo: String, destIata: String, durationMin: Int) =
            "in_flight/$flightNo/$destIata/$durationMin"
    }
    object ArrivalCelebration : Screen("arrival_celebration/{flightNo}/{destIata}/{durationMin}/{rank}") {
        fun createRoute(flightNo: String, destIata: String, durationMin: Int, rank: String) =
            "arrival_celebration/$flightNo/$destIata/$durationMin/$rank"
    }
    object Account : Screen("account")
}
