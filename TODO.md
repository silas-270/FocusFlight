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

- [x] ~~`InFlightScreen.kt` — quit-confirmation modal before exiting a
      flight~~ — fixed 2026-08-25: pause/leave button (replacing the old
      settings-gear icon, now an airplane icon for camera view) opens a
      "Leave Flight?" confirmation with Resume/Leave, wired into the
      back-button handler too. Progress is already saved periodically so
      Leave just navigates out.
- [ ] `CesiumGameActivity.kt` — Settings navigation is a TODO stub; no
      Settings screen exists yet.

## Debug/scaffolding to remove

- [ ] Skip-flight button in `InFlightScreen` — debug affordance, remove
      before shipping.

## Later — big feature, only once everything above is settled

- [ ] Challenges feature: users start challenges, eventually shareable
      with friends to compare progress. Intentionally not started —
      wait until the codebase is confirmed solid first.
