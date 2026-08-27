# FocusFlight TODO

Deferred items from the 2026-08-25 architecture cleanup, plus notes for
what's next. Not urgent, but don't forget.

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
- [x] ~~`CesiumGameActivity.kt` — Settings navigation is a TODO stub; no
      Settings screen exists yet.~~ — decided 2026-08-26: no Settings
      screen planned. Removed the gear icon from HubScreen entirely;
      the Account icon now sits in its place on the right, styled like
      the in-flight flight-settings button (40dp DeepNavy rounded box).

## Debug/scaffolding to remove

- [ ] Skip-flight button in `InFlightScreen` — debug affordance, remove
      before shipping.

## Big feature — "game modes" (Story / Free / Challenges)

Design/planning in progress, no implementation yet — see
`docs/design/README.md` for the full spec-in-progress (one file per
feature, decisions log, open items). Don't duplicate detail here;
update the docs folder instead and just keep this as a pointer.
