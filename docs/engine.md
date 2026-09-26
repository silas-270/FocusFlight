# The CesiumRS engine bridge

CesiumRS is the Rust/wgpu globe renderer that draws the flight. It is developed in its own
repository and linked into the app as one shared library, `libcesium_rs.so`. This file covers the
Android side of that boundary: how the engine is hosted, the live JNI bridge call by call, and how
the library is built. The offscreen renders drawn through the JNA bridge are described in
[maps.md](maps.md#headless-globe-renders); how the engine turns a pair of airports and a duration
into a flight profile is documented in CesiumRS's own `docs/flight-plan.md`.

| Bridge | Mechanism | Used for | Code |
|---|---|---|---|
| Live | JNI (`System.loadLibrary`) | the interactive scene on Check-In and In-Flight | `engine/live/CesiumLiveJniBridge.kt` |
| Headless | JNA (`Native.load`) | offscreen PNG globes for the 2D screens | `engine/headless/CesiumHeadlessJnaBindings.kt` |

The two share the `.so` and nothing else. The headless path never touches the live engine's state
and works while the live engine is suspended, which is the point: the Hub needs a globe image while
no flight is running.

## Hosting the engine

The app's Activity is a `GameActivity` (androidx.games) rather than a `ComponentActivity`, because
the engine runs its own event loop on the native side (`android_main`) and renders into the
Activity's native surface. `CesiumGameActivity` takes that `SurfaceView` out of its default parent
and puts it in a `FrameLayout` underneath a `ComposeView`, so the Compose UI is drawn on top of the
globe.

On the two flight screens the root Compose `Surface` is transparent and the surface view is visible,
so the scene shows through the HUD. Everywhere else the surface view is `GONE` and the Compose
background is opaque. The transparency and the native surface belong to the same window, which is
why the UI host cannot simply be swapped for a different Activity type.

### Two independent switches

The engine has two on/off states that answer different questions, and they are named differently
so they are not confused.

**Suspend and resume follow the Activity lifecycle.** `CesiumEngineManager`, a
`DefaultLifecycleObserver` attached once in `onCreate()` before any Compose content, calls
`nativeSetSuspended(false)` on `ON_START` and `nativeSetSuspended(true)` on `ON_STOP`. Suspending
puts the engine's event loop into a blocking wait, so a backgrounded app burns no CPU or GPU on it.
Because it observes the Activity and not a composition, recomposition never re-triggers it.

**Rendering follows the navigation route.** `CesiumGameActivity` calls
`nativeSetRenderingEnabled(true)` only while the current route starts with `check_in/` or
`in_flight/`. Every other screen leaves the engine awake but drawing nothing, so switching to a
flight screen is immediate while the menus cost nothing. The same route test shows or hides the
surface view and keeps the screen awake.

**Destruction is guarded.** `CesiumEngineManager.onDestroy` calls `nativeDestroyEngine()`, which
tells the event loop to exit and release its Vulkan resources, only when
`!activity.isChangingConfigurations`. The manifest declares orientation, size, UI-mode, density and
the other common configuration changes as handled by the Activity itself, so rotating on In-Flight,
the one screen that allows it, does not recreate the Activity at all; the guard covers any
configuration change that still does.

## The call surface

Every call is an `external fun` on the stateless `CesiumLiveJniBridge` object; its `init` block
loads the library, which is why a class that touches it cannot run in a JVM unit test. No structs
cross JNI: every parameter is a primitive or a primitive array.

### Loading a flight

Always through `PendingFlightLoader`, never directly. It resolves both airports, picks each
airport's **longest** runway, and pushes, in this order:

```
nativeSetRunways(ids, lengthFt, widthFt, leHeading, leLat, leLon, heHeading, heLat, heLon)
nativeSetFieldElevations(depElevationM, arrElevationM)
nativeSetPendingFlight(depLon, depLat, arrLon, arrLat, durationMs)
nativeLoadPendingFlight()
```

The first three only stage data; `nativeLoadPendingFlight` consumes it and plans the flight.
Runways are sent as parallel arrays, one entry per airport, and an airport without runway rows is
simply absent, in which case the engine lays out a runway along the route's own bearing. Elevations
are converted from feet to metres; the engine plans takeoff and landing at the real field elevation.

The loader is the single owner of this sequence because several paths need it (confirming a route in
Flight Search, booking the next itinerary leg, resuming a paused flight), and one implementation
means they cannot drift apart. It also remembers the last flight it loaded in this process, so `ensureLoaded` on Check-In and
In-Flight is a no-op on the normal path and a reload after process death
([paused-flights.md](paused-flights.md#process-death)).

### Driving the flight

`nativeSetProgress(0.0..1.0)`, pushed by `InFlightViewModel` on every 33 ms tick. Progress is the
**only** thing the app tells the engine about time. The engine derives position, attitude, camera
and lighting from it, and the flight lands exactly when progress reaches 1. The landing pipeline
pushes 1.0 explicitly.

### Reading telemetry

`nativeGetTelemetry()` returns eight doubles:

| Index | Value | Unit |
|---|---|---|
| 0 | progress | 0..1 |
| 1 | latitude | degrees |
| 2 | longitude | degrees |
| 3 | altitude | metres |
| 4 | speed | m/s |
| 5 | heading | radians |
| 6 | pitch | radians, nose up positive |
| 7 | roll | radians |

All zeros means "no telemetry yet", and the ViewModel ignores such a reading rather than showing the
aircraft at 0°N 0°E. The HUD's position, altitude and speed come from here, rounded for display.
`EnginePowerModel` reads the raw doubles instead, because the rounded copies lose exactly the
precision it needs ([engine-sound.md](engine-sound.md#reading-the-telemetry)).

### Camera

`nativeSetCameraMode(mode)`: `0` Free, `1` Tracking (labelled CHASE in the app), `2` Cockpit;
unknown values fall back to Free. A new flight starts in Tracking.

`nativeGetCameraPose()` returns `[mode, x, y, z, qx, qy, qz, qw]`, and `nativeSetCameraPose(...)`
applies a saved position and rotation the next time the view resets. Together they let a paused
flight come back to the view it was left in. The mode is set before the pose, so it is already
correct when the pose lands.

### Map style

`nativeSetMapStyle(style)`:

| Id | Constant | Imagery | Terrain | Network |
|---|---|---|---|---|
| `0` | `MAP_STYLE_STANDARD` | CARTO dark basemap | flat | yes |
| `1` | `MAP_STYLE_SATELLITE_TERRAIN` | Esri World Imagery | 3D relief from Terrarium elevation tiles | yes |
| `2` | `MAP_STYLE_OFFLINE` | Natural Earth vector map compiled into the `.so`, rasterised on the CPU | flat | none |

Unknown ids fall back to Standard on the Rust side. The screen never pushes the pilot's stored
preference directly; it pushes `InFlightViewModel.effectiveMapStyle`, which is the offline map
whenever the app is offline, so the preference survives an outage untouched
([network.md](network.md#the-live-globe)).

### Route line

`nativeSetRouteLineMode(mode, behindNm, aheadNm)`: `0` the whole route (the default), `1` a window
around the aircraft that fades out at both ends, `2` no line. The distances are in nautical miles
and only apply to the window; the app passes 40 NM behind and 150 NM ahead. They cross the boundary
as parameters rather than being compiled into the engine, so a change of taste does not need a new
native build. Showing the whole route from the first second of a long-haul flight both gives the
route away and fills the screen with a line nowhere near the aircraft, which is why the window
exists.

Nothing on the native side persists across restarts. The app owns every setting (camera, style,
route line) and pushes it again whenever In-Flight starts.

### Calls that exist only in some builds

A Kotlin `external fun` without a Rust counterpart fails at its first call with
`UnsatisfiedLinkError`, not at build time. `nativeRunPerfScenario(id)` is exported only when the
library is built with the `perf_trace` feature, so its only caller, the debug-only
`PerfScenarioReceiver`, catches the error and logs it on any other build.

## Building the native library

`preBuild` depends on the `cargoNdkBuild` task, so every Gradle build cross-compiles CesiumRS first.
For each target (`aarch64-linux-android` → `arm64-v8a`, `x86_64-linux-android` → `x86_64`) it runs

```
cargo ndk --target <target> build --lib --release --no-default-features --features debug_panel
```

in the CesiumRS checkout and copies `target/<target>/release/libcesium_rs.so` into
`app/src/main/jniLibs/<abi>/`. Cargo's output is appended to `cargo_build.log` in the CesiumRS
checkout rather than streamed into Gradle's, so that log is where a native build failure explains
itself.

| Variable | Purpose | Fallback |
|---|---|---|
| `CESIUM_RS_HOME` | path to the CesiumRS checkout | `~/CesiumRS` |
| `ANDROID_NDK_HOME` | NDK to build against | `$ANDROID_HOME/ndk/27.1.12297006` (or `ANDROID_SDK_ROOT`) |

`CARTO_API_KEY` and `ESRI_API_KEY` from `local.properties` are passed to Cargo as environment
variables, because CesiumRS reads them at compile time into the tile URLs
([network.md](network.md#map-tiles)).

The ABI list is set in two places that have to agree: the task's target map and
`defaultConfig.ndk.abiFilters`. The filter matters on its own account: JNA ships its own
`libjnidispatch.so` for 32-bit ABIs too, and without the filter those would pull 32-bit ABIs into
the bundle, and the app would be installable on 32-bit devices that have no engine library.

The `debug_panel` feature pulls in just enough of egui to draw the city-label pills on the globe;
the engine's debug-slider window is skipped on Android, so no developer UI reaches the app.

### Profiling builds

```
./gradlew :app:assembleDebug -Pcesium.profile=profiling
tools/run_perf_scenario.sh <scenario_id> [duration_seconds]
```

`-Pcesium.profile=profiling` builds CesiumRS's `profiling` Cargo profile (release code generation
with debug symbols kept) with the `perf_trace` feature, which adds ATrace spans and per-subsystem
timings. `packaging.jniLibs.keepDebugSymbols` stops the Android Gradle plugin from stripping those
symbols, without which an on-device simpleperf or Perfetto capture cannot be symbolicated.

`run_perf_scenario.sh` builds and installs such an APK, captures a Perfetto trace and a memory log for
one scenario (ids `2`, `3` and `4` also switch the camera to Free, Tracking and Cockpit), pulls both
into `perf_runs/`, and runs CesiumRS's `analyze_perf.py` over them. `run_perf_multimode.sh` and
`analyze_multimode.py` capture one 35-minute flight that walks all three camera modes, so the three
measurement windows share a flight, a warm start and the same terrain, and break the trace down per
subsystem and per mode.
