package com.example.focusflight.ui

import com.example.focusflight.data.model.FlightMode

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Hub : Screen("hub")

    // `mode` and `challengeId` are query params (not path segments) so `Screen.FlightSearch.route`
    // stays a valid registration pattern with defaults - Story Mode's "Book a flight" button keeps
    // navigating with neither specified, same as before Phase 2/3. `challengeId` (Phase 3) is the
    // same nav-arg-threading mechanism Phase 2 used for `originIata`, extended to carry which
    // Route challenge instance a CHALLENGE-tagged session is scoped to - see
    // docs/design/challenges.md#persistence--route-scoping. Encoded as an Int with a -1 sentinel
    // for "no challenge" since NavType.IntType has no nullable variant; createRoute()'s null
    // default maps to -1, and every reader treats <0 as null.
    object FlightSearch : Screen("flight_search?mode={mode}&challengeId={challengeId}") {
        fun createRoute(mode: FlightMode = FlightMode.STORY, challengeId: Int? = null) =
            "flight_search?mode=${mode.name}&challengeId=${challengeId ?: -1}"
    }

    // `originIata` is threaded alongside destIata from here on (Phase 2) - previously CheckIn/
    // InFlight resolved origin themselves via `PreferencesRepository.getCurrentAirport()`, which
    // only worked because Story Mode's origin is always that value. Free Mode's origin is a user
    // choice made back in FlightSearch, so it has to travel with the booking like everything else.
    // `challengeId` (Phase 3) rides along the same way, purely as a passthrough here - CheckIn
    // itself doesn't read it, InFlight does (see InFlightViewModel.checkAchievementsAndChallenges).
    object CheckIn : Screen("check_in/{originIata}/{flightNo}/{destIata}/{durationMin}/{mode}?challengeId={challengeId}") {
        fun createRoute(originIata: String, flightNo: String, destIata: String, durationMin: Int, mode: FlightMode, challengeId: Int? = null) =
            "check_in/$originIata/$flightNo/$destIata/$durationMin/${mode.name}?challengeId=${challengeId ?: -1}"
    }
    object InFlight : Screen("in_flight/{originIata}/{flightNo}/{destIata}/{durationMin}/{mode}?challengeId={challengeId}") {
        fun createRoute(originIata: String, flightNo: String, destIata: String, durationMin: Int, mode: FlightMode, challengeId: Int? = null) =
            "in_flight/$originIata/$flightNo/$destIata/$durationMin/${mode.name}?challengeId=${challengeId ?: -1}"
    }
    object ArrivalCelebration : Screen("arrival_celebration/{flightNo}/{destIata}/{durationMin}/{rank}/{mode}") {
        fun createRoute(flightNo: String, destIata: String, durationMin: Int, rank: String, mode: FlightMode) =
            "arrival_celebration/$flightNo/$destIata/$durationMin/$rank/${mode.name}"
    }
    object Account : Screen("account")
}
