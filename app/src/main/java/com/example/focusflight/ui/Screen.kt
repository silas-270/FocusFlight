package com.example.focusflight.ui

import com.example.focusflight.data.model.FlightMode

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Hub : Screen("hub")
    object FlightSearch : Screen("flight_search")
    object CheckIn : Screen("check_in/{flightNo}/{destIata}/{durationMin}/{mode}") {
        fun createRoute(flightNo: String, destIata: String, durationMin: Int, mode: FlightMode) =
            "check_in/$flightNo/$destIata/$durationMin/${mode.name}"
    }
    object InFlight : Screen("in_flight/{flightNo}/{destIata}/{durationMin}/{mode}") {
        fun createRoute(flightNo: String, destIata: String, durationMin: Int, mode: FlightMode) =
            "in_flight/$flightNo/$destIata/$durationMin/${mode.name}"
    }
    object ArrivalCelebration : Screen("arrival_celebration/{flightNo}/{destIata}/{durationMin}/{rank}/{mode}") {
        fun createRoute(flightNo: String, destIata: String, durationMin: Int, rank: String, mode: FlightMode) =
            "arrival_celebration/$flightNo/$destIata/$durationMin/$rank/${mode.name}"
    }
    object Account : Screen("account")
}
