# Codebase map

**Status: 🟢 decided** (as a map of *today's* code — will go stale as
implementation proceeds; treat it as a snapshot from 2026-08-27, not
a living spec like the other files).

Where the concepts in [mechanics.md](mechanics.md) and the other
feature files actually anchor in the existing Kotlin/Compose codebase,
gathered by two read-only exploration passes so implementation agents
don't have to rediscover this. Two purposes: (1) a concrete map of
what exists today, (2) a minimal-diff recommendation for where each
new concept slots in, so nobody invents a second way to do something
that already has a place.

## Current state

### Flight/session data model

- Room entity `FlightLog` (`data/model/FlightLog.kt:21-31`): `id`,
  `userId`, `flightNumber`, `originIata`, `destIata`, `durationMin`,
  `distanceKm`, `completedAt`, `createdAt`. **No mode/tag column.**
- `FlightLogDao` / `FlightLogRepository.logFlight(...)`: plain insert,
  no mode parameter. Every read query is unconditional — every
  consumer reads the whole table.
- `LogbookEntry.kt` is a stateless `@Composable` row renderer for a
  `FlightLog`, not a data class.

### `currentAirport` + visited-set

- `currentAirport` lives in plain `SharedPreferences`
  (`PreferencesRepository.kt:18-50`, key `current_airport_iata`), not
  Room/DataStore.
- Written inline inside `InFlightViewModel`'s timer-completion branch
  (`InFlightViewModel.kt:151-167`) — **and duplicated verbatim** in
  `skipFlight()` (`InFlightViewModel.kt:215-234`, a debug affordance
  TODO.md flags for removal). No dedicated "flight landed" event/hook
  exists — it's two copies of the same inline sequence.
- `visitedCountries`/`completedContinents` (achievements.md's
  "existing groundwork" claim) is **independently recomputed in two
  places**: `FlightSearchViewModel.kt:67-101` and
  `AccountViewModel.kt:121-188, 156-180`. Both reactively re-derive
  from the entire flight history on every table change — reactive,
  not event-driven, and since there's no tag column today it
  necessarily includes every logged flight.

### Landing flow & navigation

- `ArrivalCelebrationScreen` exists (`ui/screens/arrival/`) — a dumb,
  stateless composable. The "rank" it shows (CO-PILOT / CAPTAIN /
  COMMANDER / GLOBETROTTER) is computed purely from `durationMin` in
  `InFlightScreen.kt:628-634` — cosmetic, unrelated to achievements or
  challenges. Don't confuse it with the mode tag.
- Nav graph: one `NavHost` in `CesiumGameActivity.kt`, routes defined
  as a sealed class in `ui/Screen.kt`, parameterized routes carry
  string path args (e.g. `rank` is threaded this way today). Adding a
  destination today means: add to `Screen.kt`, add a `composable{}`
  block in `CesiumGameActivity.kt`, wire a lambda from the caller.
- Hub (`ui/screens/hub/HubScreen.kt`): primary "BOOK A FLIGHT" /
  "RESUME FLIGHT" button (`:78-142`), a profile icon (`:294-318`), and
  — precedent worth knowing — an existing conditional secondary CTA
  ("BOOK NEW FLIGHT") inside the Hub's own `BottomSheetScaffold` sheet
  (`:207-230`), shown only when a flight is already active.
- Overlay/modal convention: the app has exactly one, used consistently
  — a full-screen scrim `Box` + a sibling centered card `Box`
  (`InFlightScreen.kt:667-754`), **not** `AlertDialog`/`Dialog` (both
  essentially unused — `Dialog` is imported but never invoked). Per
  commit `fc160b0`, nesting the card inside the scrim was tried and
  rejected — tap-through wasn't reliably blocked — so this
  sibling-Box shape is a hard-won fix, not a style preference.
- `AccountScreen.kt` (`:106-190`) is a single `LazyColumn` of
  `item { SectionHeader(...) }` blocks (CONTINENT COVERAGE, FLIGHT
  HIGHLIGHTS, FLIGHT HISTORY, ...) — a clean, additive list, not tabs.
- `InteractiveWorldMap` (`ui/components/InteractiveWorldMap.kt:80-90`)
  is stateless/parameterized (origin, routes, selected route all
  passed in) — no baked-in "current route" singleton, so it won't
  collide with a Route challenge's own position at the component
  level. The collision risk is one layer up: `FlightSearchViewModel`
  sources its origin exclusively from the same global
  `preferencesRepository.getCurrentAirport()` (`FlightSearchViewModel.kt:105`)
  — there's no injectable/alternate origin today.

## Pre-existing issues this work will touch anyway

Not introduced by the game-modes design — found during mapping — but
exactly the code the new pipeline needs to hook into, so worth fixing
as part of this work rather than building the tag/isolation logic on
top of duplicated ground:

- **`InFlightViewModel`'s completion sequence is duplicated** between
  normal timer-completion and `skipFlight()`. Consolidate into one
  `completeFlight()` before adding tag-conditional behavior to it —
  otherwise the new mode-tag branching has to be written (and kept in
  sync) twice.
- **`visitedCountries`/`completedContinents` is duplicated** between
  `FlightSearchViewModel` and `AccountViewModel`. Once flights carry a
  tag, both call sites need identical STORY-only filtering — do that
  once, in one shared place, not patched into both copies.

## Where each design concept slots in

| Concept | Anchor today | Recommended slot-in |
|---|---|---|
| Mode tag (STORY/FREE/CHALLENGE) | No tag exists; `FlightLog` has no such column | Add a column to `FlightLog` + migration; thread through nav args the same way `rank` already is, defaulting to `STORY` at every existing call site so the core loop is untouched |
| Post-landing pipeline ([mechanics.md](mechanics.md#post-landing-pipeline)) | `InFlightViewModel`'s (duplicated) completion branch | The consolidated `completeFlight()` (see above) is the one choke point: write logbook (tagged) → conditionally write `currentAirport`/visited-set only if STORY → run the new shared achievement/challenge check → surface the result to `InFlightScreen`'s existing landing `LaunchedEffect` |
| Hub secondary button | `HubScreen.kt:294-318` (profile icon) | Sibling icon in the same `Row`, new `onModeSelectClick` param — pure additive param, no restructuring |
| Quest-log + Free Mode menu | No equivalent menu surface exists | New `ModalBottomSheet`/second sheet state, reusing the `BottomSheetScaffold` idiom already used for the Hub's own sheet |
| Route-challenge confirmation modal; home-base-teleport modal (future) | The scrim+card `Box` pattern, `InFlightScreen.kt:667-754` | Extract that pattern into a shared composable and reuse it for both — do **not** reach for `AlertDialog`/`Dialog` |
| Achievements section + Challenges-completed log | `AccountScreen.kt`'s `LazyColumn` of `item{}` sections; `LogbookEntry.kt`'s styling | New `item{}` sections, same pattern; a parallel `ChallengeCompletionEntry` composable reusing `LogbookEntry`'s visual primitives — it's a separate log, not a row in the flight logbook itself |
| Route challenge's isolated position pointer + scoping `FlightSearchViewModel` to it | No analog exists — see [Open questions](#open-questions) | Not a drop-in reuse; needs an actual decision, below |

## Open questions

Resolved now (functional, not taste — no need to loop back):

- **Mode tag is write-once at session start**, not mutable after the
  fact — nothing in the design implies reclassifying a flight later,
  and a write-once column is the simpler migration.
- **`LogbookEntry.kt` has an unrelated uncommitted diff** (a
  paper-grain-texture rendering perf fix) sitting in the working tree
  right now. Recommend committing/finishing that before starting
  game-modes implementation, so an implementing agent isn't untangling
  two unrelated changes to the same file.
- **Where the quest-log/Free-Mode menu physically lives (bottom sheet
  vs. a dedicated screen) and exact Achievements-section placement**
  stay build-time details, as already stated in core-loop.md and
  achievements.md — not blocking, and the Hub already has one sheet
  in play worth being aware of if stacking a second gets visually
  awkward.

Resolved after this map surfaced them:

- **Route challenge position-pointer storage and scoping**: a single
  challenges store holds all active/completed challenges (all types),
  with a position-pointer field used only by Route. Continuing a Route
  challenge threads its id through the booking flow so the existing
  flight-picking screen reads/writes that row instead of
  `currentAirport`. See [challenges.md](challenges.md#persistence--route-scoping).

- **Rank stamp vs. challenge event composition**: always sequenced,
  never replaced — the existing rank-stamp arrival screen shows first
  unchanged, then leads into the per-leg tick-up or completion
  celebration if step 4 of the pipeline found one. See
  [mechanics.md](mechanics.md#post-landing-pipeline).
