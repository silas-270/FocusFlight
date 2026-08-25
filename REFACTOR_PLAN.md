# FocusFlight Restructuring Plan

Goal: clean architecture, zero duplicated logic, and a strict naming rule —
**every file's `path + name` must fully describe what it contains, and it
must contain nothing else.** Work this list top to bottom; each phase
should compile and run before starting the next. Commit after each phase.

Baseline before starting: `compileDebugKotlin` passes clean (verified
2026-08-25). 37 Kotlin files, 7,636 lines, under `app/src/main/java/com/example/focusflight`.

---

## Phase 0 — Safety net

- [ ] Create a branch for this work (e.g. `refactor/architecture-cleanup`).
- [ ] Confirm `./gradlew :app:compileDebugKotlin` is clean before touching anything (already verified).

---

## Phase 1 — Kill the raw-DB-access anti-pattern

**Problem:** `data/repository/FlightDatabaseHelper.kt` (354 lines, raw SQLite,
no interface) is called *directly* by 6 ViewModels and by `CesiumGameActivity`
itself — 8 methods, 20+ call sites. Meanwhile `FlightLog`/`UserProfile` data
correctly goes through `Repository` interfaces. This is the single biggest
inconsistency in the codebase and it's exactly the layer a future
Challenges feature will need to extend, so it must be fixed first.

1. [ ] Rename `data/repository/FlightDatabaseHelper.kt` →
      `data/local/airport/AirportRouteSqliteDataSource.kt`. It keeps its
      current job (opening `flights.db`, running the raw queries, copying
      the asset DB on first launch) but nothing else — no more public API
      that ViewModels touch directly.
2. [ ] Create `data/repository/AirportRepository.kt` (interface) +
      `data/repository/LocalAirportRepository.kt` (impl wrapping the new
      `AirportRouteSqliteDataSource`), matching the existing
      `UserRepository`/`LocalUserRepository` pattern. Expose exactly the
      methods actually used today:
      `ensureDatabaseCopied()`, `searchAirports(query)`,
      `getAirportByIata(iata)`, `getRunwaysForAirport(iata)`,
      `getOutboundRoutes(iata)`, `getContinentCountryMap()`,
      `getCountriesForAirports(iatas)`.
3. [ ] Update every call site to go through `AirportRepository` instead of
      the data source directly:
      - `HubViewModel.kt` (`getAirportByIata` L62, `getOutboundRoutes` L104)
      - `CheckInViewModel.kt` (`getAirportByIata` L46/L49, `getOutboundRoutes` L53)
      - `AccountViewModel.kt` (`getCountriesForAirports` L151, `getContinentCountryMap` L154)
      - `OnboardingViewModel.kt` (`searchAirports` L43, `getAirportByIata` L72, `getOutboundRoutes` L108)
      - `FlightSearchViewModel.kt` (`getCountriesForAirports` L82, `getContinentCountryMap` L85, `getAirportByIata` L108/L193, `getOutboundRoutes` L186/L197)
      - `InFlightViewModel.kt` (`getAirportByIata` L81/L84/L203, `getOutboundRoutes` L88/L204)
4. [ ] `CesiumGameActivity.kt` currently instantiates the data source
      directly and calls `ensureDatabaseCopied`/`getAirportByIata`/
      `getRunwaysForAirport`/`getOutboundRoutes` (L87, 96, 197-264, 439).
      Inject `AirportRepository` here too (an Activity doing raw DB access
      is itself a layering violation — see Phase 5).
5. [ ] Wire `AirportRepository` through whatever DI/manual-construction
      pattern the ViewModels already use for `FlightLogRepository`, so the
      pattern is consistent across the app.
6. [ ] Build, run, sanity-check onboarding search, hub load, route search,
      check-in, and account stats all still work.

---

## Phase 2 — Collapse the triple-duplicated map-render logic

**Problem:** the exact sequence "fetch ≤12 routes → delete stale cache
file → `CesiumRSLibrary.renderRoutes` → prune cache" is copy-pasted in
`HubViewModel.kt` L97-126, `OnboardingViewModel.kt` L108-126, and
`InFlightViewModel.kt` L204-223. This is the root cause of the old
"silent map render failure" bug (fixed three times or zero times).

1. [ ] Create `engine/headless/CesiumHeadlessMapRenderer.kt` — a single
      class that owns: taking an origin airport + up to 12 routes, deleting
      a stale output file, calling `CesiumRSLibrary.renderRoutes`, pruning
      the cache, and returning success/failure + the resulting path (or a
      typed error instead of silently swallowing it — this is also where
      bug-list item "silent failure leaves blank Hub" gets fixed for good).
2. [ ] Move `ui/viewmodel/CacheUtils.kt` → `engine/headless/MapImageCache.kt`
      (it's cache-file pruning for rendered map images, not a ViewModel
      concern — used only by the new renderer).
3. [ ] Replace the three duplicated blocks in `HubViewModel`,
      `OnboardingViewModel`, `InFlightViewModel` with calls to
      `CesiumHeadlessMapRenderer`.
4. [ ] Rename `data/repository/CesiumRSLibrary.kt` →
      `engine/headless/CesiumHeadlessJnaBindings.kt` (it's the raw JNA
      bindings, not a "repository" — `CesiumHeadlessMapRenderer` is the
      only thing that should call it directly now).
5. [ ] Rename `engine/CesiumBridge.kt` → `engine/live/CesiumLiveJniBridge.kt`
      (raw JNI bindings for the live rendering/telemetry loop, to sit
      clearly apart from the headless JNA path). Update its 4 call sites
      (`CesiumGameActivity.kt`, `CesiumEngineManager.kt`,
      `InFlightScreen.kt` L145, `InFlightViewModel.kt`).
6. [ ] Move `engine/CesiumEngineManager.kt` → `engine/live/CesiumEngineManager.kt`
      to sit alongside the bridge it manages.
7. [ ] Build, run, verify: Hub map renders on first load, onboarding
      pre-render still works, destination map renders after a flight
      completes/skips.

---

## Phase 3 — Split the two dumping-ground screen files

**Problem:** one file = one Composable was violated for the two biggest
screens; both mix the main screen with unrelated sub-components.

### `ui/screens/AccountScreen.kt` (967 lines, 14 top-level declarations) →
`ui/screens/account/`
- [ ] `AccountScreen.kt` — top-level `AccountScreen` composable only (screen scaffold/layout, wires the sub-components below)
- [ ] `ProfileHeroCard.kt` — `ProfileHeroCard`, `InfoChip`
- [ ] `StatsGrid.kt` — `StatsGrid2x2`, `StatCard`
- [ ] `TravelMapCard.kt` — `TravelMapCard`
- [ ] `ContinentProgressCard.kt` — `ContinentProgressCard`, `ContinentProgressRow`
- [ ] `LogbookEntry.kt` — `LogbookEntry`, `LogbookDataCell`
- [ ] `FlightHighlightsRow.kt` — `FlightHighlightsRow`, `HighlightCard`
- [ ] `SortDropdown.kt` — `SortDropdown`
- [ ] `SectionHeader.kt` — `SectionHeader` (shared enough it may belong in `ui/components/` instead — check for reuse outside Account before placing)

### `ui/screens/FlightSearchScreen.kt` (988 lines, 8 top-level declarations) →
`ui/screens/flightsearch/`
- [ ] `FlightSearchScreen.kt` — top-level `FlightSearchScreen` composable only
- [ ] `TimelineSlider.kt` — `TimelineSlider`, `rememberInertiaSnapFlingBehavior`, private `formatTime` helper
- [ ] `SelectionCard.kt` — `SelectionCard`
- [ ] `RouteMap.kt` — `RouteMap`
- [ ] `AirportSearchPanel.kt` — `AirportSearchPanel`, `SuggestionTile`

- [ ] Update all `import` statements across the codebase that reference
      moved composables.
- [ ] Build, run, verify Account and Flight Search screens render and
      behave identically (scroll, sort, timeline snap, pager sync).

---

## Phase 4 — Feature-based package taxonomy

**Problem:** packages are flat and one level deep (`ui/screens` has 8
files, `ui/viewmodel` has 8) — already at the point where a filename alone
doesn't tell you which screen it belongs to. Group by feature so
`path + name` is unambiguous, matching the split from Phase 3.

- [ ] `ui/screens/hub/HubScreen.kt` + `ui/viewmodel/hub/HubViewModel.kt`
- [ ] `ui/screens/onboarding/OnboardingScreen.kt` + `ui/viewmodel/onboarding/OnboardingViewModel.kt`
- [ ] `ui/screens/flightsearch/*` (from Phase 3) + `ui/viewmodel/flightsearch/FlightSearchViewModel.kt`
- [ ] `ui/screens/checkin/CheckInScreen.kt` + `ui/viewmodel/checkin/CheckInViewModel.kt`
- [ ] `ui/screens/inflight/InFlightScreen.kt` + `ui/viewmodel/inflight/InFlightViewModel.kt`
- [ ] `ui/screens/arrival/ArrivalCelebrationScreen.kt`
- [ ] `ui/screens/account/*` (from Phase 3) + `ui/viewmodel/account/AccountViewModel.kt`
- [ ] Leave `ui/components/`, `ui/map/`, `ui/theme/` as cross-feature shared
      code (that's what they actually are — no change needed there beyond
      what Phase 3 decides for `SectionHeader`).
- [ ] `data/model/` — check whether these are all genuinely shared, or
      whether any (e.g. anything Account-specific) should move nearer its
      feature; keep shared domain models (`Airport`, `FlightRoute`,
      `Runway`, `FlightLog`, `UserProfile`) in `data/model/` since they're
      used across features.
- [ ] Update `settings.gradle.kts`/imports as needed (should be mechanical
      given Kotlin package-per-directory).
- [ ] Build, run, full smoke test of every screen in the nav graph.

---

## Phase 5 — Remaining cleanup

- [ ] Delete orphaned fixtures `app/flight_STR_FRA.json` and
      `app/flight_STR_JFK.json` — confirmed zero references anywhere in
      the repo.
- [ ] Move DB access out of `CesiumGameActivity.kt` entirely if any
      remains after Phase 1 step 4 — an Activity should drive lifecycle/UI
      glue, not query the database.
- [ ] Re-skim `focusflight_stategraph.md` after Phases 1-4 land and update
      any section that now describes the old structure (e.g. state
      ownership descriptions referencing the old file layout).
- [ ] Fix the three inline TODOs found earlier while files are already
      open for related work (skip only if out of scope for this pass):
      - `InFlightScreen.kt` — quit-confirmation modal
      - `CesiumGameActivity.kt` — Settings navigation stub
      - `HubViewModel.kt` (now in `ui/viewmodel/hub/`) — the "silent
        failure" TODO should already be resolved by Phase 2's typed
        error handling; delete the TODO comment once confirmed.
- [ ] `app/build.gradle.kts`'s `cargoNdkBuild` task hardcodes two absolute
      local machine paths for the CesiumRS checkout — not part of the
      naming/architecture cleanup, but flag it for a follow-up (e.g. an
      env var or `local.properties` entry) since it currently only builds
      on two specific machines.
- [ ] Delete this file (`REFACTOR_PLAN.md`) once every box above is
      checked, or move its unchecked remainder into a fresh, shorter
      follow-up list — don't let it become a second stale bug_list.md.

---

## Explicitly out of scope for this pass

- The Challenges/friends feature — nothing here should anticipate it
  beyond leaving `AirportRepository`/`RouteRepository`-shaped seams that a
  future feature can extend cleanly.
- Real automated test coverage — `app/src/test` and `app/src/androidTest`
  are both untouched Android Studio boilerplate with no real assertions;
  worth a from-scratch test-writing pass later, not part of this
  restructuring.
- The in-flight UI cleanup / removing the debug skip-flight button — a
  separate, smaller task the user mentioned for later.
