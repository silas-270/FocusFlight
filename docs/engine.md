# The CesiumRS engine

## What it is

CesiumRS is a Rust/wgpu 3D globe renderer maintained **outside this repository**. The
Android app links it as a single shared library, `libcesium_rs.so`, and talks to it
through two independent bridges that serve different purposes and use different calling
conventions.

| Bridge | Mechanism | Used for | Code |
|---|---|---|---|
| Live | JNI (`System.loadLibrary`) | The interactive 3D scene during check-in and flight | `engine/live/CesiumLiveJniBridge.kt` |
| Headless | JNA (`Native.load`) | Offscreen PNG route-map renders for 2D screens | `engine/headless/CesiumHeadlessJnaBindings.kt` |

They share one `.so` but nothing else. The headless path never touches the live engine's
state, and can run while the live engine is suspended — which is the point: the Hub needs
a route-map image while no flight is in progress.

`arm64-v8a` is the only ABI built. Adding another means adding it to the `targets` map in
`app/build.gradle.kts`'s `cargoNdkBuild` task, not just to an ABI filter.

## The live bridge

`CesiumLiveJniBridge` is a stateless Kotlin `object` — every call is a `external fun`
straight into Rust. It has no lifecycle of its own; the engine's lifecycle is managed by
the two mechanisms below.

### Surface and host activity

The app's activity is `GameActivity` (androidx.games), not `ComponentActivity`. The
engine renders directly into the activity's native surface, *behind* the Compose tree.
Compose is hosted in a `ComposeView` added on top, and on flight screens the Compose
`Surface` colour is set to `Color.Transparent` so the 3D scene shows through. Everywhere
else it is opaque `MaterialTheme.colorScheme.background`.

This is why the Compose UI cannot simply be replaced with a different host: the
transparency and the native surface are the same window.

### Two orthogonal on/off switches

Confusing these is the most common way to break the engine, so they are named
differently on purpose.

**Suspend/resume is lifecycle-scoped.** `CesiumEngineManager`, a
`DefaultLifecycleObserver` attached once in `onCreate()` before `setContent()`, calls
`nativeSetSuspended(false)` on `ON_START` and `nativeSetSuspended(true)` on `ON_STOP`.
This wakes and sleeps winit's event loop so a backgrounded app stops burning CPU and GPU.
It is attached to the *activity's* lifecycle, not a Compose scope, so it survives
recomposition.

**Rendering enable/disable is route-scoped.** `CesiumGameActivity` watches the current
nav destination and calls `nativeSetRenderingEnabled(true)` only while the route starts
with `check_in/` or `in_flight/`. Every other screen renders nothing.

**Destroy is guarded.** `CesiumEngineManager.onDestroy` calls `nativeDestroyEngine()`
only when `!activity.isChangingConfigurations`. Without that guard, rotating the device
mid-flight would drop the Vulkan device while the pilot expects the flight to continue.
InFlight is the one screen that allows rotation, so this guard is reachable in normal use,
not theoretical.

> The clean-exit path (winit loop exiting and releasing Vulkan resources before process
> death) is implemented but has not been verified on a physical device.

### The call surface

**Loading a flight** — always through `PendingFlightLoader`, never by calling the bridge
directly. It looks up both airports, picks each one's longest runway, and pushes runways
then route then load, in that order:

```
nativeSetRunways(...)      → geometry for the departure and arrival runways
nativeSetPendingFlight(depLon, depLat, arrLon, arrLat, durationMs)
nativeLoadPendingFlight()
```

It is the single owner of that sequence because two call sites need it — resuming a
paused flight from the Hub, and confirming a route in Flight Search — and they must not
drift apart.

**Driving the flight** — `nativeSetProgress(0.0..1.0)`, pushed by `InFlightViewModel`'s
timer loop. Progress is the *only* thing the app tells the engine about time; the engine
derives position, attitude and camera from it. Landing pushes `1.0`.

**Reading back** — `nativeGetTelemetry()` returns a `DoubleArray`; the ViewModel reads
indices 1–3 as lat, lon and altitude when the array has at least 8 elements. The HUD's
displayed position and altitude come from the engine, not from interpolating the route
in Kotlin.

**Camera** — `nativeSetCameraMode(mode)` where `0 = Free`, `1 = Chase`, `2 = Cockpit`.
`nativeGetCameraPose()` / `nativeSetCameraPose(...)` save and restore a free-camera
position and quaternion across a pause/resume, so a paused flight comes back to the view
it was left in. Set the mode *before* setting the pose, so the mode is already correct
when the pose lands.

**Map style** — `nativeSetMapStyle(style)` where `0` is the CARTO dark basemap and `1` is
Esri satellite imagery.

### Adding a native call

Both sides must change together. A Kotlin `external fun` whose Rust counterpart does not
exist fails at first call with `UnsatisfiedLinkError`, not at build time. `PerfScenarioReceiver`
shows the pattern for a call that only exists in some builds: gate it behind
`BuildConfig.DEBUG` and catch the link error.

## The headless bridge

JNA rather than JNI because the signature is a plain C function over a struct array, with
no JVM object involved:

```
render_routes_headless(width, height, routes: *const HeadlessRoute, count, outPath) -> bool
```

`LatLon` and `HeadlessRoute` are JNA `Structure`s mirroring the Rust layout. Field order
is declared with `@Structure.FieldOrder` and **must match the Rust struct exactly** —
a mismatch is silent memory corruption, not a compile error.

`CesiumHeadlessMapRenderer` wraps it and owns the whole fetch-routes → render →
prune-cache sequence. Callers never touch the JNA bindings directly. It returns a sealed
`Result` (`Success(path, fromCache)` / `Failure(message)`) rather than throwing, because
every caller runs it in a background scope whose only sensible response to a failure is a
retryable "map unavailable" state.

Three screens use it, and the `reuseCachedFile` flag is what distinguishes them:

| Caller | `reuseCachedFile` | Why |
|---|---|---|
| `HubViewModel.generateRouteMap()` | `true` | The map for the current airport is usually already on disk; re-rendering it would add a visible delay to opening the app |
| `OnboardingViewModel.preRenderMap()` | `false` | Warms the cache for a freshly chosen home base |
| `InFlightViewModel.preRenderDestinationMap()` | `false` | Runs at landing so the destination's map is ready by the time the arrival sequence ends |

Output files are named `hub_route_map_<IATA>.png` in the cache directory.
`MapImageCache.pruneMapCache` keeps the five most recently modified, **plus** any pinned
IATA — the home base is pinned, because return-home is on a 7-day cooldown and would
otherwise always find a cold cache.

## Building the native library

`preBuild` depends on the `cargoNdkBuild` task, so a normal Gradle build cross-compiles
CesiumRS and copies the `.so` into `jniLibs/arm64-v8a/`. It shells out to `cargo ndk`
and needs a CesiumRS checkout on the machine.

| Variable | Purpose | Fallback |
|---|---|---|
| `CESIUM_RS_HOME` | Path to the CesiumRS checkout | `~/CesiumRS` |
| `ANDROID_NDK_HOME` | NDK to build against | derived from `ANDROID_HOME` / `ANDROID_SDK_ROOT` |

The task appends to `cargo_build.log` inside the CesiumRS checkout rather than streaming
to Gradle's output — that log is where a native build failure explains itself.

### Cargo features

Day-to-day builds use `--no-default-features --features debug_panel`. `debug_panel` pulls
in egui only far enough to draw the city-label pills; the actual debug-slider window is
skipped on Android, so no dev UI reaches the real app.

### Profiling builds

```
./gradlew :app:assembleDebug -Pcesium.profile=profiling
tools/run_perf_scenario.sh <scenario_id> [duration_seconds]
```

`-Pcesium.profile=profiling` selects CesiumRS's `profiling` Cargo profile (release
codegen, debug symbols kept) and adds the `perf_trace` feature (ATrace spans and
per-subsystem timings). `packaging.jniLibs.keepDebugSymbols` stops AGP stripping those
symbols, without which an on-device simpleperf or Perfetto capture cannot be symbolicated.

`nativeRunPerfScenario(id)` exists **only** in such a build — it throws
`UnsatisfiedLinkError` against a normal release `.so`, which is why `PerfScenarioReceiver`
is debug-source-set only. Scenario ids `2`, `3` and `4` also switch the camera to Free,
Tracking and Cockpit respectively; any other integer just tags the trace.

The script builds, installs, captures a combined Perfetto trace plus a memory-sample log,
pulls both into `perf_runs/`, and runs CesiumRS's `analyze_perf.py` over them. It needs
`adb` with exactly one device attached.
