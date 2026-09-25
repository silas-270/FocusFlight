# The core loop

One session: pick a route, board, fly for its real duration, land. Everything else in
the app — modes, challenges, achievements, the passport — hangs off this and must not
make it harder to reach.

```
Hub ──▶ Flight Search ──▶ Check-In ──▶ In-Flight ──▶ Arrival ──▶ [Outcome] ──▶ Hub
```

## 1 · Hub

The home screen. Shows the pilot's current airport, a rendered map of its outbound
routes as the background, aggregate stats, and — when one exists — a **Resume flight**
button for a paused session.

**A focused Route challenge takes over the Hub's displayed airport.** When
`focused_route_challenge_id` points at a live challenge, the Hub shows *that challenge's*
`positionIata` and progress instead of the pilot's story-mode airport. The pointer is
display-only and never mutates the challenge row, but it does mean the airport on the Hub
is not always `current_airport_iata` — worth knowing before assuming the two agree.

The route-map background comes from `CesiumHeadlessMapRenderer` with
`reuseCachedFile = true`, so an already-rendered `hub_route_map_<IATA>.png` is used
instantly instead of re-rendering on every open. A missing or zero-length file is deleted
and re-rendered, with a solid colour shown in the interim. See [engine.md](engine.md).

## 2 · Flight Search

Picks the route. Two search modes the pilot toggles between:

- **By time** — a horizontal timeline of duration intervals. Scrolling snaps to the
  nearest interval and auto-selects the first route in it. This is the mode that matches
  how the app is actually used: "I have 90 minutes" is the real question, not "where do I
  want to go".
- **By airport** — search a destination by name or IATA.

Origin is the pilot's current airport in Story Mode, and a free choice in Free Mode.
Selected routes are paged as cards, bidirectionally synced with the timeline: swiping the
pager updates the selection, picking a new interval resets the pager to the first card.
Tapping a partly visible neighbouring card pages to it; tapping the centred card books it,
the same as **Confirm selection**.

**Only the main network is offered.** Destination lists (every mode) and every airport picker
contain only airports in the main route network — the ones reachable from everywhere and able to
fly back out (`RouteNetwork.mainComponent`). About 730 IATA airports with no routes, no
departures, or only a closed local cluster are hidden. Route lists also drop self-routes,
duplicate pairs and physically impossible durations (`SANE_ROUTE` in
`AirportRouteSqliteDataSource`). Airport search is accent- and case-insensitive and also matches
ICAO codes and country names (`AirportSearchIndex`).

**The dead-end fallback.** If a Story Mode origin has no routes into the main network (a pilot
stranded before dead ends were hidden), the ViewModel rehomes the pilot to the nearest large
network airport and persists it, so the screen always has content. It fires only on a genuinely
empty result, never on a *failed* query — a transient SQLite
error used to silently teleport the pilot — and the screen now shows a notice when it
happens rather than moving them in silence.

Confirming a route pushes it into the 3D engine via `PendingFlightLoader` and navigates
to Check-In.

## 3 · Check-In

The boarding card: origin, destination, flight number, duration, distance. The 3D engine
is rendering behind it — this is the first of the two routes where
`nativeSetRenderingEnabled(true)` is on.

A route lookup that fails degrades rather than propagating: the ticket renders without a
distance, and since starting needs the route's duration, **Start flight** stays disabled
with a short note to go back and pick the flight again. `loadRouteContext` treats a failed query the same as "no such route", because
both callers resolve it inside a plain `viewModelScope.launch` where an escaping throw
would take the process down, and neither has anything better to do with the failure.

Starting the flight navigates to In-Flight, popping Check-In.

## 4 · In-Flight

The session itself, and the only screen that allows rotation.

A coroutine timer ticks elapsed time and pushes `nativeSetProgress(elapsed / total)` to
the engine, then reads `nativeGetTelemetry()` back for the HUD's live latitude,
longitude, altitude and speed. Progress is the only thing the app tells the engine about
time; position comes back from the engine, never from interpolating the route in Kotlin.

The screen also:

- Holds a wakelock for the flight's duration (`FLAG_KEEP_SCREEN_ON`), in addition to the
  Activity-wide one.
- Generates procedural cabin rumble — a composite 80 Hz + 40 Hz sine plus white noise
  streamed as raw PCM to an `AudioTrack`, on a background thread inside a
  `DisposableEffect` bound to the sound toggle.
- Offers camera modes (Free / Chase / Cockpit) and map styles (dark basemap / satellite).
- Can be paused. A pause saves the camera pose and elapsed time so the flight resumes in
  the view it was left in. Which slot the paused flight is written to depends on the mode
  — see [modes.md](modes.md). The pilot leaves through the settings panel's
  SLIDE TO LEAVE control, or system back. Both open a "LEAVE FLIGHT?" dialog with RESUME as
  the primary button. The dialog says where the flight can be resumed, which depends on the
  mode: the Hub for Story, Free Mode on Challenges for Free, and the challenge itself for a
  challenge leg.
- Plays a ~3 s hold once the timer reaches 00:00, before landing. During the hold the timer
  reads "LANDING…", and settings and back are disabled, so the pilot can't leave a flight
  that has already been flown without it being logged.

The rank shown on arrival is a pure function of duration, computed here:

| Duration | Rank |
|---|---|
| ≥ 8h | GLOBETROTTER |
| ≥ 4h | COMMANDER |
| ≥ 2h | CAPTAIN |
| < 2h | CO-PILOT |

## The post-landing pipeline

Landing is a five-step pipeline, and **its order is a correctness property, not an
implementation detail**. It is referenced by step number from KDoc across the codebase.

`InFlightViewModel.completeFlight()` runs steps 2–4. It is reached from both the timer
reaching zero and the debug skip shortcut, and is guarded by a `landingStarted` flag
because a flight lands exactly once.

> **Ordering assumption relied on for correctness:** `completeFlight()` must be called
> before, or synchronously with, the `isCompleted = true` emission that triggers
> navigation. `onCleared` cancels `landingScope`, so a landing launched after that point
> is silently dropped. Both call sites satisfy this today with no suspension point in
> between. Adding a suspending step before it breaks landings.

**Step 1 — the engine snaps to arrival.** `nativeSetProgress(1.0)`, and
`preRenderDestinationMap()` starts immediately in parallel. The render is by far the
slowest part and is independent of every data write, so it runs alongside them rather
than queueing behind them — the destination's map should be ready by the time the arrival
animation ends.

The remaining steps run **strictly sequentially** in `landingScope`, an IO scope separate
from `viewModelScope` precisely so that popping the In-Flight screen does not cancel a
landing mid-write.

**Step 2 — write the logbook entry.** The `FlightLog` row is the flight's only durable
record, so it goes first and everything after is conditional on it.

**Step 3 — move the pilot.** Only for a `STORY`-tagged session, and only if step 2
succeeded: `current_airport_iata` moves to the destination and the paused-flight slot is
cleared.

If step 2 failed, both are deliberately left alone. A pilot still at their origin with a
resumable flight is a consistent state they can act on. A pilot standing at a destination
with no flight explaining it is not repairable — the logbook is the only record of how
they got anywhere.

**Step 4 — credit achievements and challenges.** Always runs, even when step 2 failed,
because it is what resolves `LandingResultChannel`, and an unresolved channel hangs the
arrival screen. A landing that could not be logged simply has nothing to credit.

The challenge half delegates to `processLandingForChallenges` — a standalone, JNI-free
suspend function rather than a ViewModel method, so it is unit-testable without
instantiating `InFlightViewModel` (which loads the native engine on first touch). Its
rules are in [challenges.md](challenges.md).

The result is computed by diffing the challenge list before and after
(`resolveLandingOutcome`), producing per-challenge `Advanced(from, to)` or `Completed`
outcomes, or `LandingResult.None`.

**Step 5 — show the outcome.** The arrival celebration always plays the rank stamp. What
follows depends on the channel:

- `LandingResult.ChallengesAffected` → the Challenge Outcome screen, which animates each
  tick-up or completion, then Hub.
- `LandingResult.None` → straight to Hub.

The arrival screen waits at most 5 seconds for the channel before continuing to the Hub
regardless.

## 5 · Arrival

The plane animates off the top of the screen; 750 ms later the rank stamp lands, scaling
down from 5× with a spring and fading in, timed with a haptic touchdown pulse. A photo of
the destination city, prefetched during the flight, is shown behind it when the Pexels
call succeeded. The screen greets the pilot with "Welcome to <city>", looked up from the
destination IATA, and labels the flight's duration "FOCUSED FOR". Its button reads
CONTINUE, because it leads to the Challenge Outcome screen when a challenge moved. The button
only responds once it has faded in, and it disables itself after the first tap while the
landing result is awaited.

## What the loop does not do

There is no fast travel. The pilot's position only moves by flying, which is what makes
the world map mean anything. The two exceptions are both deliberate, both cooldown-gated,
and both live in [modes.md](modes.md): returning home, and changing the home base.
