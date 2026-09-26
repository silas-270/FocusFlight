# UI scale and conventions

A few decisions apply to every screen: how the layout scales to different phones, how modals are
built, how numbers are formatted, and how the 30 Hz flight screen avoids recomposing itself to
death. This file explains each and the reason behind it.

## The design scale

Every screen was designed and tuned on a phone whose shortest side is **360 dp**
(`DESIGN_SHORT_SIDE_DP`), a common width for a mid-size phone at default display zoom. On a bigger phone the
same layout is **scaled up, not reflowed**: `ProvideDesignDensity` (`ui/theme/DesignScale.kt`) wraps
the whole `NavHost` and overrides `LocalDensity` so that the window's shortest side always spans
exactly 360 dp. Paddings, corner radii, fixed heights and text all grow by the same factor, so
proportions, line breaks and column counts are identical on every phone. On a 412 dp-wide phone
everything is about 14% larger.

The factor is `shortestSidePx / 360`, bounded three ways:

- **Never below the system density.** A smaller screen, or one with display zoom turned up, keeps
  the user's zoom instead of being shrunk to fit.
- **At most 1.35× the system density**, and **not at all** when the system shortest side is 480 dp
  or more. On tablets and unfolded foldables a phone layout blown up to fill the screen would be
  absurd, so they get the system density and the layout's natural reflow.
- **The font scale passes through untouched**, so the system font-size setting still applies on top.

It uses the shortest side rather than the width so that In-Flight, the one screen that rotates,
keeps one scale in both orientations. Window insets and pointer input are in pixels and unaffected.

Two rules follow from overriding the density.

**Screen size comes from `LocalDesignScreenSize`**, the window size in design dp.
`LocalConfiguration.current.screenWidthDp` and `screenHeightDp` stay in the system's dp and would
disagree with every other dp value on screen.

**Nothing opens a separate window.** `Dialog`, `Popup`, `DropdownMenu` and `ModalBottomSheet` each
get a window with its own `LocalDensity`, which would not inherit the override and would render at
the system scale, visibly smaller than the screen behind them. Every modal is drawn inside the one
window instead.

The headless globe renders scale their pixel size to the display in the same spirit, so the Hub
globe stays sharp on a denser screen ([maps.md](maps.md#what-gets-drawn)).

## Modals

`ScrimCardModal` is the app's one modal: a full-screen scrim `Box` and a centred card `Box` drawn as
**siblings**, not nested, so a tap on the card can never fall through to the scrim's dismiss handler.
The sort-order picker, the challenge picker, confirmations and info modals are all built on it rather
than on Material's dialogs and menus.

Its behaviour covers what a system dialog would otherwise provide:

- `onScrimTap` is usually "dismiss", but a caller that paused something can resume it there; the
  in-flight leave dialog resumes the flight timer on a scrim tap.
- **System back is routed to the same handler.** Without that, the back gesture would pass through
  an open modal and pop the destination underneath it.
- `onClose` adds a visible ✕ for info-only modals, whose only other ways out (scrim and back) are
  invisible.
- `portraitWidth` caps the card at its portrait width, so in landscape it stays a centred card
  instead of stretching across the screen.

A modal opened from inside a `LazyColumn` item would be clipped to that item, so screens hoist modal
state to the top of the screen and draw the modal as a sibling of the scaffold.

## Numbers and units

- **Numbers are always formatted with `Locale.US`.** A German phone would otherwise show "3,9 %"
  next to "6,838 mi", mixing decimal separators within one screen of English text.
- **Distances, altitudes and speeds are stored metric and shown imperial**: `formatMiles`,
  `formatFeet` and `formatMph` in `util/Units.kt`. Kilometres are what the route data and the
  flight log hold; miles, feet and mph are what pilots and passengers think in. Distance inputs
  (custom distance challenges) are typed in miles and converted to kilometres before storing.
- **Lengths of time use `formatDuration`**: `45m`, `1h`, `1h 25m`, `134h 27m`. Only running clocks
  (the in-flight countdown and its elapsed and total readout) use `hh:mm:ss`, because they are
  timers rather than lengths.
- **Country names** come from the platform's ICU data, always in US English (`countryDisplayName`),
  since the airport data only stores ISO codes. A code ICU does not know is shown as the code itself
  rather than as "Unknown Region", which would hide which country a checklist row is about.

## Theme

`ThemeMode` is `SYSTEM` until the pilot makes an explicit choice in Settings, after which the
explicit light or dark choice sticks and `SYSTEM` is never written back. `ThemeModeHolder` is a
process-wide observable holder seeded from preferences at start-up; `BlocktimeTheme` reads it
directly, so a toggle repaints the whole app at once without recreating anything.

## Recomposition on the flight screen

`InFlightViewModel` updates its UI state every 33 ms. Any composable that reads a field of that state
object subscribes to all of it and would recompose thirty times a second. The flight screen therefore
never reads fast-changing state in scopes that do not draw it:

- the "landing" flag and the restored camera mode are mapped out of the state flow and
  `distinctUntilChanged`, so their scopes recompose once when they change;
- the countdown overlay lives in its own host composable, gated on a boolean that flips once, so
  nothing tracks the 30 Hz value after the first three seconds;
- the landing trigger collects `isCompleted` inside a `LaunchedEffect` rather than reading it during
  composition;
- engine power is collected straight into the audio engine, never into Compose state;
- the arrival clock is keyed to the minute it displays, so time-zone work runs once a minute;
- the instrument panel's frame-driven animation runs only while the bottom sheet is expanded or
  expanding.

The passport's world map gets its own graphics layer, so its thousand-path display list is recorded
once and only repositioned while the list scrolls ([maps.md](maps.md#drawing-it-quickly)).
