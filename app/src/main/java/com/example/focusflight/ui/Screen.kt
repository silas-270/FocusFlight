package com.example.focusflight.ui

import com.example.focusflight.data.model.FlightMode

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Hub : Screen("hub")

    // `mode` is a query param (not a path segment) so `Screen.FlightSearch.route` stays a valid
    // registration pattern with a default - Story Mode's "Book a flight" button keeps navigating
    // with no mode specified at all, same as before Phase 2.
    object FlightSearch : Screen("flight_search?mode={mode}") {
        fun createRoute(mode: FlightMode = FlightMode.STORY) = "flight_search?mode=${mode.name}"
    }

    // `originIata` is threaded alongside destIata from here on (Phase 2) - previously CheckIn/
    // InFlight resolved origin themselves via `PreferencesRepository.getCurrentAirport()`, which
    // only worked because Story Mode's origin is always that value. Free Mode's origin is a user
    // choice made back in FlightSearch, so it has to travel with the booking like everything else.
    object CheckIn : Screen("check_in/{originIata}/{flightNo}/{destIata}/{durationMin}/{mode}") {
        fun createRoute(originIata: String, flightNo: String, destIata: String, durationMin: Int, mode: FlightMode) =
            "check_in/$originIata/$flightNo/$destIata/$durationMin/${mode.name}"
    }
    object InFlight : Screen("in_flight/{originIata}/{flightNo}/{destIata}/{durationMin}/{mode}") {
        fun createRoute(originIata: String, flightNo: String, destIata: String, durationMin: Int, mode: FlightMode) =
            "in_flight/$originIata/$flightNo/$destIata/$durationMin/${mode.name}"
    }
    object ArrivalCelebration : Screen("arrival_celebration/{flightNo}/{destIata}/{durationMin}/{rank}/{mode}") {
        fun createRoute(flightNo: String, destIata: String, durationMin: Int, rank: String, mode: FlightMode) =
            "arrival_celebration/$flightNo/$destIata/$durationMin/$rank/${mode.name}"
    }
    object Account : Screen("account")
}
