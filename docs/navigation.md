# Navigation

One `NavHost`, defined inline in `CesiumGameActivity`, over the ten destinations declared in
`ui/Screen.kt`. There is no nested graph and no bottom navigation bar: the Hub is the single
place everything returns to.

## Start destination

Chosen once, when the content is first composed:

```
onboarding_completed == true  AND  a user_profile row exists   →  Hub
otherwise                                                        →  Onboarding
```

Both conditions are checked, not just the flag. Every repository resolves the pilot through
`UserProfileDao.requireProfileId()`, which throws when the profile row is missing, so a Hub
without a profile would crash on the first screen that reads it. Onboarding writes the flag last
for exactly this reason ([state.md](state.md#write-ordering)), and `onCreate` additionally resets
a flag that is set without a profile before the tree is built.

## Destinations

| Screen | Route pattern | Arguments |
|---|---|---|
| Onboarding | `onboarding` | none |
| Hub | `hub` | none |
| Challenges | `challenges` | none |
| Account (Passport) | `account` | none |
| Settings | `settings` | none |
| Flight Search | `flight_search?mode={mode}&challengeId={challengeId}` | both optional |
| Check-In | `check_in/{originIata}/{flightNo}/{destIata}/{durationMin}/{mode}?challengeId=` | five path, one query |
| In-Flight | `in_flight/{originIata}/{flightNo}/{destIata}/{durationMin}/{mode}?challengeId=` | same as Check-In |
| Arrival Celebration | `arrival_celebration/{flightNo}/{destIata}/{durationMin}/{rank}/{mode}` | five path |
| Challenge Outcome | `challenge_outcome` | none, reads a channel |

Every `Screen` with arguments has a `createRoute(...)` that encodes them; the route constant
itself is only the registration pattern.

Account and Settings are both backed by `AccountViewModel`, each with its own instance. Account is
the read-only passport (map, stats, badges, logbook); Settings owns everything that changes app
state or preferences (theme, offline maps, return home, change home base, credits).

## The graph

```
Onboarding ──▶ Hub                          popUpTo Onboarding, inclusive

Hub ──▶ Flight Search                       Book a flight, STORY
    ──▶ Flight Search / Check-In            Book a flight while a Route challenge is focused
    ──▶ In-Flight                           Resume flight
    ──▶ Challenges · Account · Settings

Challenges ──▶ Flight Search                Free Mode, FREE
           ──▶ Flight Search                continue a free-form Route challenge, CHALLENGE + id
           ──▶ Check-In                     continue a predefined itinerary, next leg booked
           ──▶ In-Flight                    resume a Free flight or a challenge leg
           ──▶ Hub                          a Route challenge was started, popUpTo Hub, inclusive

Flight Search ──▶ Check-In                  route confirmed

Check-In ──▶ In-Flight                      popUpTo Check-In, inclusive

In-Flight ──▶ Arrival Celebration           landed, popUpTo In-Flight, inclusive
          ──▶ Hub                           left, popUpTo Hub, inclusive

Arrival Celebration ──▶ Challenge Outcome   a challenge moved, popUpTo Arrival, inclusive
                    ──▶ Hub                 otherwise, popUpTo Hub, inclusive

Challenge Outcome ──▶ Challenges            any challenge completed, popUpTo Hub, exclusive
                  ──▶ Hub                   otherwise, popUpTo Hub, inclusive

Settings ──▶ Hub                            after return home or a home-base change,
                                            popUpTo Hub, inclusive
```

## Rules that hold across the graph

**Forward steps in a flight pop what they replace.** Check-In → In-Flight and In-Flight →
Arrival both pop the screen they leave. A flight is not a stack to back out of halfway: going
back from In-Flight to Check-In would leave a running session behind a boarding pass. In-Flight
intercepts system back itself and turns it into the leave dialog.

**Everything terminal ends at a single Hub.** Terminal navigations use `popUpTo(Hub) { inclusive
= true }` and push a fresh Hub, so Hubs never accumulate across sessions. The one exclusive
variant, Challenge Outcome → Challenges, keeps the Hub underneath so that back from Challenges
lands there.

**A tap navigates only from a settled screen.** Every tap-driven navigation goes through
`navigateFrom(entry, ...)` or `popBackStackFrom(entry)`, which act only while the originating
back-stack entry is `RESUMED`. The moment one navigation starts, the outgoing entry drops below
`RESUMED`, so a double tap, or a coroutine finishing late, is ignored rather than pushing a
duplicate destination. For a double-tapped back arrow the guard is what stops the second tap from
popping the Hub itself and leaving the `NavHost` empty. The landing navigation from In-Flight is
the one deliberate exception: it is not tap-driven, and it must happen.

**The destination drives the 3D engine.** `CesiumGameActivity` watches the current route and
enables native rendering, shows the engine's surface view and keeps the screen awake only while
the route starts with `check_in/` or `in_flight/`. Free rotation is allowed only on `in_flight/`;
everything else is locked to portrait ([engine.md](engine.md#two-independent-switches)).

## Argument threading

**The origin travels with the booking.** Check-In and In-Flight take `originIata` as an argument
instead of reading the pilot's current airport. Only in Story Mode is the origin the current
airport; in Free Mode it is a choice made in Flight Search, and for a challenge leg it is the
challenge's own position pointer, so it has to ride along like every other field of the booking.

**`challengeId` is an `Int` with a `-1` sentinel.** `NavType.IntType` has no nullable variant,
so `createRoute()` encodes `null` as `-1` and every reader treats a negative value as absent.
Check-In only passes it through; In-Flight uses it to pick the challenge's paused-flight slot and
to credit the right Route challenge on landing.

**Optional arguments are query parameters.** That keeps `Screen.FlightSearch.route` a valid
registration pattern with defaults (`mode = STORY`, `challengeId = -1`), so the Hub's plain "Book
a flight" navigates with neither.

**The destination city is looked up, not passed.** The arrival screen's "Welcome to <city>"
resolves the city from `destIata` on arrival instead of carrying free text through the route,
where it would need escaping.

## What travels outside the route

Two pieces of per-landing state cross In-Flight → Arrival → Outcome through Activity-scoped
channels instead of the route string:

- `LandingResultChannel`: the resolved challenge outcome of this landing. A sealed class with a
  different shape per case does not encode into a URL, and it only ever needs to reach the next
  two screens in the same process.
- `DestinationPhotoChannel`: the destination photo prefetched during the flight. A URL contains
  exactly the characters the route format does not escape.

Both are owned by the Activity, so they outlive `InFlightViewModel`, which is destroyed the
moment the landing navigates. In-Flight resets both when it starts, so a previous flight's result
or photo can never leak into this one.

`LandingResult` has a `Pending` state distinct from `None`. The challenge check runs on an IO
dispatcher and can still be running when the pilot taps CONTINUE, so the arrival screen waits for
anything other than `Pending` instead of acting on whatever is there. The wait is bounded by
`LANDING_RESULT_TIMEOUT_MS` (5 s). The channel also records whether any flight in this process
armed it: after process death Navigation can restore the arrival screen with a fresh channel that
nothing will ever publish to, and in that case CONTINUE goes straight to the Hub. A Challenge
Outcome screen restored the same way has no outcomes to show and forwards itself to the Hub; the
completions it would have shown still play on the Challenges screen, whose queue comes from the
database ([challenges.md](challenges.md#completion-presentation)).

## Shared entry points

Two flows are complex enough to live as functions in `CesiumGameActivity` instead of being
repeated at each call site.

**`resumeFlight(...)`** loads a paused flight into the engine and navigates straight into
In-Flight. It is shared by the Hub's Resume button, the Free Mode row on Challenges, and a
challenge's own resume action, because `PausedFlight` already carries everything any of them
needs ([paused-flights.md](paused-flights.md)).

**`continueRouteChallenge(...)`** is one button with two journeys behind it. A free-form Route
challenge still has a decision to make, which onward flight gets it closer, so it opens Flight
Search scoped to the challenge. A predefined itinerary does not: its next hop is authored, so
`resolveNextLeg` books it and the pilot lands directly on the boarding pass. The `null` return of
`resolveNextLeg` is what separates the two; the free-form path is reached by falling through,
not by re-deriving what kind of challenge it is
([route-network.md](route-network.md#the-next-leg-of-an-itinerary)).

## ViewModel lifetime, and one consequence

Each destination owns one ViewModel, built by a factory from the repositories the Activity
constructed. They are scoped to the back-stack entry, so popping a destination destroys its
ViewModel.

The same is true of lifecycle observers inside a `NavHost`: a composable's `LocalLifecycleOwner`
is its back-stack entry, not the Activity. Popping In-Flight to show the arrival screen therefore
fires `ON_STOP` on In-Flight's observer, which saves the camera pose, while the landing pipeline
is still writing. That interleaving is why paused-flight writes are sealed by the landing and why
challenge rows are updated column by column ([state.md](state.md#concurrent-writers)).

`PilotProgressRepository` is the deliberate exception to per-screen lifetime. It lives on an
Activity-level scope, so the derivation of the pilot's progress stays warm across navigation
instead of being rebuilt on every visit ([achievements.md](achievements.md#the-shared-derivation)).
