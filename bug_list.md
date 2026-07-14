# FocusFlight Bug List

1. **Main menu Cesium headless image doesn't update chosen route on arrival at new airport**
   * **Description:** When the flight finishes, `InFlightViewModel` attempts to pre-render the new hub image by launching a coroutine on `viewModelScope`. However, the screen immediately navigates to `ArrivalCelebration`, destroying the ViewModel and cancelling the rendering process before it finishes. `HubViewModel` then falls back to an old cached image.
   * **Fix:** Move the `preRenderDestinationMap` logic to a repository, a global application scope, or `WorkManager` so that it is decoupled from the UI lifecycle and executes fully regardless of navigation.

2. **Flight timer UI reappears on app resume mid-flight, as if flight just started**
   * **Description:** While `InFlightViewModel` survives screen rotations, it does not survive process death (e.g., app minimized and OS reclaims memory). When resumed, `InFlightViewModel` initializes `timeElapsedMs` back to `-3000L`.
   * **Fix:** Persist `timeElapsedMs` and the flight progress via `SavedStateHandle` or `PreferencesRepository` so the ViewModel can recover the exact timer state upon recreation.

3. **Camera angle/position/mode reset in Android/Kotlin state but NOT in Rust/Cesium side**
   * **Description:** `selectedCamera` defaults to `1` (Tracking) via Compose `remember` state. On rotation, Android UI resets to `1`, but the Rust engine retains the previous camera mode because `nativeSetCameraMode` is only called on button clicks, not on composition.
   * **Fix:** Add a `LaunchedEffect(selectedCamera)` in `InFlightScreen.kt` to actively sync the Kotlin camera state across the JNI bridge whenever the Composable enters the screen or the state changes.

4. **NBJ airport rendering in the ocean instead of Angola (Null Island Bug)**
   * **Description:** In `CesiumRSLibrary.kt`, `HeadlessRoute` defines `start` and `end` as `@JvmField var start: LatLon = LatLon()`. JNA treats these nested classes as pointers by default. When written to memory, pointer addresses are passed to Rust instead of raw floats, resulting in corrupted coordinates that default to `(0.0, 0.0)`.
   * **Fix:** Update the JNA structure definitions to use inline by-value structs: `@JvmField var start: LatLon.ByValue = LatLon.ByValue()`.

5. **Search Progress Wiped on Screen Rotation**
   * **Description:** `FlightSearchScreen` contains a `LaunchedEffect(Unit) { viewModel.resetState() }`. Every time the user rotates their phone, the screen recomposes and unconditionally wipes their current search query and selected route.
   * **Fix:** Remove `viewModel.resetState()` from the `LaunchedEffect(Unit)` in the screen. Only trigger `resetState()` on explicit clear actions or when first navigating into the search flow from the Hub.

6. **Stale Touch-Interception Boundaries (Ghost Touches)**
   * **Description:** `CesiumGameActivity` uses static `sheetTopY` and `topBarBottomY` variables to pass touches down to the Rust engine. These are never reset when leaving the flight screen, meaning touches on the `HubScreen` can accidentally pan the invisible 3D camera in the background.
   * **Fix:** Add a `DisposableEffect` to `InFlightScreen.kt` that resets these static boundary variables to `Float.MAX_VALUE` and `0f` in its `onDispose` block.

7. **Orphaned Rust Engine Event Loop consuming resources**
   * **Description:** Navigating back to the `HubScreen` disables Vulkan rendering (`nativeSetRenderingEnabled(false)`), but the Rust `winit` event loop continues to spin indefinitely, processing touches and telemetry.
   * **Fix:** Implement a "suspend" command over the JNI bridge that explicitly halts the Rust engine's internal update loop and ignores inputs when the user is not actively in a 3D view.

8. **Race Condition in Database Initialization (Singleton Trap)**
   * **File(s) + variable(s) involved:** `FlightDatabaseHelper.kt` (`ensureDatabaseCopied()`)
   * **Trigger/reproduction scenario:** On the very first launch of the app, `HubViewModel` and `OnboardingViewModel` may both concurrently instantiate the DB on `Dispatchers.IO`.
   * **Expected vs actual state behavior:** The app should safely copy the asset DB once. Instead, because there is no `synchronized` lock, multiple IO threads can concurrently check `!dbPath.exists()` and open `FileOutputStream`, corrupting the SQLite file during the concurrent overwrite.
   * **Confidence:** Confirmed by trace.

9. **UI vs ViewModel Pager Scroll Fight (Ordering/Race Dependency)**
   * **File(s) + variable(s) involved:** `FlightSearchScreen.kt` (`pagerState.currentPage` vs `viewModel.selectedRoute`)
   * **Trigger/reproduction scenario:** User rapidly flings or drags the horizontal route carousel to look at flights.
   * **Expected vs actual state behavior:** The carousel should physics-scroll smoothly. Instead, the bidirectional `LaunchedEffect` immediately updates the ViewModel on page change, which fires back to the UI and calls `pagerState.scrollToPage(index)`. This instantly halts the user's physics fling mid-animation and hard-snaps the carousel.
   * **Confidence:** Confirmed by trace.

10. **Orphaned Selected Route on Search Query Change (Missing Reset Site)**
   * **File(s) + variable(s) involved:** `FlightSearchViewModel.kt` (`_selectedRoute`, `_airportSearchQuery`)
   * **Trigger/reproduction scenario:** User selects a valid flight route from the carousel, then changes the search query to a term that filters out the currently selected route.
   * **Expected vs actual state behavior:** `_selectedRoute` should clear when the route is no longer present in `_airportSearchResults`. Actually, it retains the hidden route in memory, keeping the "Confirm Selection" button enabled and allowing the user to blindly book a flight that is no longer on their screen.
   * **Confidence:** Confirmed by trace.

11. **Stale Search Results via Concurrent Debounce (Stale Closure)**
   * **File(s) + variable(s) involved:** `OnboardingViewModel.kt` (`_searchQuery.collect`)
   * **Trigger/reproduction scenario:** User types a query (e.g., "San Francisco"), waits 300ms, then quickly backspaces and types a new query ("JFK") before the first SQLite query returns.
   * **Expected vs actual state behavior:** Only the results for the latest query should be displayed. Actually, because the flow uses `collect { launch(Dispatchers.IO) { ... } }`, it spawns un-cancelled parallel coroutines. If the first query is slower than the second, the stale results arrive last and overwrite the fresh results, displaying San Francisco airports for the "JFK" query.
   * **Confidence:** Likely (Depends on SQLite thread-scheduling, but structurally guaranteed to race).

12. **Silent Map Rendering Failure Leaves Permanent Blank State (Silent Failure Path)**
   * **File(s) + variable(s) involved:** `HubViewModel.kt` (`_isRendering`, `generateRouteMap()`)
   * **Trigger/reproduction scenario:** `CesiumRSLibrary.renderRoutes` throws an exception or returns false due to memory pressure or JNI issues.
   * **Expected vs actual state behavior:** The app should display an error banner or retry. Actually, the `catch` block simply turns off `_isRendering` but leaves `_routeMapPath` as `null` forever, resulting in a completely blank Hub screen with no feedback to the user.
   * **Confidence:** Confirmed by trace.
