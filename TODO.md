# FocusFlight TODO

Deferred items from the 2026-08-25 architecture cleanup, plus notes for
what's next. Not urgent, but don't forget.

## CesiumRS (external repo, not this one)

- [ ] **Cockpit-mode UI.** InFlightScreen has a `COCKPIT` camera mode
      (`nativeSetCameraMode`), but there's no actual cockpit UI/HUD drawn
      by the engine for it yet — this is a CesiumRS update, not a
      FocusFlight one.

## Build hygiene

- [x] ~~`app/build.gradle.kts`'s `cargoNdkBuild` task hardcodes two absolute
      local machine paths~~ — fixed 2026-08-25: NDK/cargo/CesiumRS paths now
      resolve from `ANDROID_HOME`/`ANDROID_NDK_HOME`/`CESIUM_RS_HOME` and
      `user.home`, with the old hardcoded paths only as a last-resort
      fallback.

## Unbuilt features (not bugs — just not implemented)

- [ ] `InFlightScreen.kt` — quit-confirmation modal before exiting a
      flight (currently exits immediately, no confirmation).
- [ ] `CesiumGameActivity.kt` — Settings navigation is a TODO stub; no
      Settings screen exists yet.

## Debug/scaffolding to remove

- [ ] Skip-flight button in `InFlightScreen` — debug affordance, remove
      before shipping.

## Verification still needed

- [x] The architecture cleanup was actually run and booted (2026-08-25, on
      an emulator) — the app launches and renders real DB-backed data with
      no crash. This also caught and fixed a real bug: the refactor
      renamed `CesiumBridge` → `CesiumLiveJniBridge` (moved to
      `engine.live`), but CesiumRS's `src/android_jni.rs` still exported
      the old mangled JNI symbol names, so every `nativeXxx` call would
      have crashed with `UnsatisfiedLinkError` on a real device too. Fixed
      in CesiumRS by renaming all 9 `Java_com_example_focusflight_...`
      exports to match the new package/class path.
- [ ] Still only click-tested Onboarding. Do a full pass —
      Hub → Search → Check-in → InFlight → Account — on a real device.

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
