package com.silas270.blocktime.ui

import com.silas270.blocktime.data.model.FlightMode

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Hub : Screen("hub")

    // `mode` and `challengeId` are query params (not path segments) so `Screen.FlightSearch.route`
    // stays a valid registration pattern with defaults - Story Mode's "Book a flight" button keeps
    // navigating with neither specified, same as before Phase 2/3. `challengeId` (Phase 3) is the
    // same nav-arg-threading mechanism Phase 2 used for `originIata`, extended to carry which
    // Route challenge instance a CHALLENGE-tagged session is scoped to - see
    // docs/challenges.md#persistence--route-scoping. Encoded as an Int with a -1 sentinel
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

    // The second beat of docs/core-loop.md's post-landing pipeline step 5 - reached from
    // ArrivalCelebration's "continue" only when `LandingResultChannel` resolved to a
    // ChallengesAffected outcome for this landing (never on Story/Free Mode landings with no
    // active challenge progress, which go straight to Hub as before). Argument-less: the screen
    // reads the resolved outcome list directly off the shared, Activity-scoped
    // `LandingResultChannel` (see `CesiumGameActivity`) rather than round-tripping its fields
    // through nav args - the result only ever needs to reach the very next screen in the same
    // process, the same reasoning `PausedFlight` already bridges other per-session state on.
    object ChallengeOutcome : Screen("challenge_outcome")

    object Account : Screen("account")

    // Preferences (theme) and the Story Mode home-base actions (return home / change home base) -
    // split out of the Passport so that screen can stay a read-only trophy case/logbook and this
    // one owns anything that changes app state or app-wide settings.
    object Settings : Screen("settings")

    // The modes/goals surface: Free Mode entry, the three challenge slots, the completed-challenge
    // log, and the still-unearned achievements tab. A full destination rather than the Hub bottom
    // sheet this used to be - that sheet needed its own inner scroll and swapped five view states
    // inside a card. Argument-less; everything it shows comes from ChallengesViewModel.
    object Challenges : Screen("challenges")
}
