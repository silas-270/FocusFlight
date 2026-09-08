# Navigation

One `NavHost`, defined inline in `CesiumGameActivity`, over ten destinations declared in
`ui/Screen.kt`. There is no nested graph and no bottom navigation bar — the Hub is the
single point everything returns to.

## Start destination

Chosen once at composition:

```
onboarding_completed == true AND a user_profile row exists  →  Hub
otherwise                                                    →  Onboarding
```

Both conditions, not just the flag. Before `onCreate` builds the tree it also runs a
self-heal: if the flag is set but no profile exists, the flag is reset to `false`. That
combination used to be permanently fatal — the Challenges screen calls
`requireProfileId()` on open and crashed forever. The write ordering that prevents the
state arising in the first place is in [state.md](state.md).

## Destinations

| Screen | Route pattern | Arguments |
|---|---|---|
| Onboarding | `onboarding` | — |
| Hub | `hub` | — |
| Challenges | `challenges` | — |
| Account | `account` | — |
| Flight Search | `flight_search?mode={mode}&challengeId={challengeId}` | both optional |
| Check-In | `check_in/{originIata}/{flightNo}/{destIata}/{durationMin}/{mode}?challengeId=` | five path, one query |
| In-Flight | `in_flight/{originIata}/{flightNo}/{destIata}/{durationMin}/{mode}?challengeId=` | same as Check-In |
| Arrival Celebration | `arrival_celebration/{flightNo}/{destIata}/{durationMin}/{rank}/{mode}` | five path |
| Challenge Outcome | `challenge_outcome` | — (reads a channel, see below) |

Never build a route string by hand. Every `Screen` object has a `createRoute(...)` that
encodes the arguments correctly; the route constant is only for registration.

## The graph

```
Onboarding ──▶ Hub                     (popUpTo Onboarding, inclusive)

Hub ──▶ Flight Search                  (Book a flight — STORY, no challengeId)
    ──▶ Challenges
    ──▶ Account
    ──▶ In-Flight                      (Resume flight — via resumeFlight())
    ──▶ Hub                            (self-replacing refresh)

Challenges ──▶ Flight Search           (Free Mode entry — FREE)
           ──▶ Flight Search           (free-form Route challenge — CHALLENGE + id)
           ──▶ Check-In                (predefined-itinerary challenge — next leg booked)
           ──▶ In-Flight               (resume this challenge's leg)

Flight Search ──▶ Check-In             (route confirmed)

Check-In ──▶ In-Flight                 (popUpTo CheckIn, inclusive)

In-Flight ──▶ Arrival Celebration      (landed — popUpTo InFlight, inclusive)
          ──▶ Hub                      (exit — popUpTo Hub, inclusive)

Arrival Celebration ──▶ Challenge Outcome   (only if the landing affected a challenge)
                    ──▶ Hub                 (otherwise)

Challenge Outcome ──▶ Challenges           (any outcome completed a challenge — popUpTo Hub, exclusive)
                  ──▶ Hub                 (otherwise — popUpTo Hub, inclusive)
Account ──▶ Hub                        (return home)
```

## Rules that hold across the graph

**Forward steps in the flight sequence pop what they replace.** Check-In → In-Flight and
In-Flight → Arrival each use `popUpTo(..., inclusive = true)`. A flight is not a stack
you can back out of halfway; going back from In-Flight to Check-In would leave a running
timer behind an unloaded screen.

**Everything ends at a single Hub entry.** Terminal navigations use
`popUpTo(Screen.Hub.route) { inclusive = true }`, so the back stack never accumulates
Hubs across sessions.

**The nav destination drives the 3D engine.** `CesiumGameActivity` enables native
rendering only while the current route starts with `check_in/` or `in_flight/`, and
allows free rotation only on `in_flight/` (everything else is locked portrait). A new
screen that needs the globe must be added to that prefix check — see
[engine.md](engine.md).

## Argument threading

**Origin travels with the booking.** Check-In and In-Flight take `originIata` explicitly
rather than reading the pilot's current airport. That shortcut only ever worked because
Story Mode's origin *is* the current airport; Free Mode's origin is a choice the pilot
made back in Flight Search, so it has to ride along like every other field.

**`challengeId` is an `Int` with a `-1` sentinel.** `NavType.IntType` has no nullable
variant, so `createRoute()`'s `null` default encodes as `-1` and every reader treats a
negative value as absent. Check-In carries it purely as a passthrough — it never reads
it; In-Flight does, in `checkAchievementsAndChallenges`.

**Optional arguments are query params, not path segments.** That is what keeps
`Screen.FlightSearch.route` a valid registration pattern while still letting the Hub's
"Book a flight" navigate with neither specified.

## What deliberately does not travel as a nav argument

Two pieces of per-landing state cross the In-Flight → Arrival → Outcome hop through
Activity-scoped channels rather than the route string:

- `LandingResultChannel` — the resolved post-landing challenge outcome
- `DestinationPhotoChannel` — the arrival screen's prefetched destination photo

Both only ever need to reach the very next screen within the same process, and neither
is URL-shaped. `Screen.ChallengeOutcome` therefore takes no arguments at all: it reads
the outcome list straight off the channel.

The arrival screen waits up to `LANDING_RESULT_TIMEOUT_MS` (5s) for the channel to
resolve before continuing to the Hub anyway. That bound exists to stop a pathological
case hanging the screen, not to race a healthy check — which resolves well inside it on
any device.

`Screen.Challenges` is the one exception to "reached via a channel": which challenges to
run the completion-presentation animation for (see
[challenges.md](challenges.md#completion-presentation)) is **not** threaded through this
hop at all, from the channel or otherwise — `ChallengesViewModel` derives its own queue
straight from the database (`COMPLETED, celebrated = false`) when it's constructed. That
is deliberate: it makes the animation work identically whether the pilot arrived via this
nav edge or just opened Challenges normally, and survives the app being killed anywhere
in between.

## Shared entry points

Two navigation flows are complex enough to live as functions in `CesiumGameActivity`
rather than being duplicated at each call site:

**`resumeFlight(...)`** — loads the paused flight into the engine and navigates into
In-Flight. Shared by the Hub's "Resume flight" and the Challenges screen's per-challenge
resume, because `PausedFlight` already carries everything either needs.

**`continueRouteChallenge(...)`** — one button, two journeys. A free-form Route challenge
still has a real decision left (which onward flight gets closer), so it goes to Flight
Search scoped to that challenge. A predefined-itinerary challenge does not — its next hop
is authored, so `resolveNextLeg` books it and this lands straight on the boarding card.
The `null` return from `resolveNextLeg` is what distinguishes the two; the free-form path
is reached by falling through, not by re-deriving which kind of challenge it is.

## ViewModel lifetime, and one consequence

Each screen owns one ViewModel, constructed through a manual factory with the
repositories `CesiumGameActivity` built. They are scoped to the **back stack entry**, so
`popBackStack` destroys them.

That is worth knowing for a reason beyond memory: inside a `NavHost` the lifecycle owner
of a `DisposableEffect` is also the back stack entry, not the Activity. Popping In-Flight
to show the arrival celebration therefore fires `ON_STOP` — and any observer attached to
it — *while the landing is still running*, several hundred milliseconds before
`onCleared` cancels `viewModelScope`. That was a real lost-update bug; see
[state.md](state.md)'s concurrent-writers section.

`PilotProgressRepository` is the deliberate exception. It is held by the Activity on an
`appScope` that outlives every ViewModel, which is what keeps the shared derivation warm
across navigation instead of recomputing it on every visit.
