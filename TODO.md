# FocusFlight TODO

Deferred items from the 2026-08-25 architecture cleanup, plus notes for
what's next. Not urgent, but don't forget.

## CesiumRS (external repo, not this one)

- [ ] **Cockpit-mode UI.** InFlightScreen has a `COCKPIT` camera mode
      (`nativeSetCameraMode`), but there's no actual cockpit UI/HUD drawn
      by the engine for it yet — this is a CesiumRS update, not a
      FocusFlight one.

## Build hygiene

- [ ] `app/build.gradle.kts`'s `cargoNdkBuild` task hardcodes two absolute
      local machine paths for the CesiumRS checkout
      (`/home/silas270/CesiumRS`, `c:/Users/kamme/Desktop/CesiumRS`) —
      only builds on those two specific machines. Move to an env var or
      `local.properties` entry.

## Unbuilt features (not bugs — just not implemented)

- [ ] `InFlightScreen.kt` — quit-confirmation modal before exiting a
      flight (currently exits immediately, no confirmation).
- [ ] `CesiumGameActivity.kt` — Settings navigation is a TODO stub; no
      Settings screen exists yet.

## Debug/scaffolding to remove

- [ ] Skip-flight button in `InFlightScreen` — debug affordance, remove
      before shipping.

## Verification still needed

- [ ] Nothing from the architecture cleanup (repository layer, map
      renderer consolidation, file/package restructuring) has been run on
      a real device/emulator — this dev environment has no display and no
      CesiumRS native checkout. Confirmed via clean `compileDebugKotlin`
      and passing unit tests only. Do a manual click-through of
      Hub → Search → Check-in → InFlight → Account before trusting it
      fully.

## Test coverage

Real unit tests now exist for the pieces touched by the cleanup
(`LegacyFlightLogMigrator`, `selectRoutesToRender`, `FlightSortOrder`) —
12 tests, `app/src/test`. Still no coverage for:
- [ ] ViewModels (`HubViewModel`, `FlightSearchViewModel`, etc.) — would
      need `kotlinx-coroutines-test` `runTest`/`TestDispatcher` wiring
      around `viewModelScope`, and fakes for the Room DAOs/Flows they
      collect.
- [ ] `CesiumHeadlessMapRenderer`'s actual render path (the JNA call
      itself is untestable without either faking `CesiumHeadlessJnaBindings`
      behind an interface, or an instrumented test with the real native
      lib).
- [ ] No `androidTest` UI tests exist beyond the stock boilerplate.

## Later — big feature, only once everything above is settled

- [ ] Challenges feature: users start challenges, eventually shareable
      with friends to compare progress. Intentionally not started —
      wait until the codebase is confirmed solid first.
