package com.example.focusflight.ui.viewmodel.inflight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.CameraPose
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.ChallengeRepository
import com.example.focusflight.data.repository.FlightLogRepository
import com.example.focusflight.data.repository.LandingResult
import com.example.focusflight.data.repository.LandingResultChannel
import com.example.focusflight.data.repository.PausedFlightStore
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.data.repository.processLandingForChallenges
import com.example.focusflight.data.repository.resolveLandingOutcome
import com.example.focusflight.domain.loadRouteContext
import com.example.focusflight.engine.headless.CesiumHeadlessMapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

data class InFlightState(
    val timeRemainingSeconds: Long = 0,
    val timeElapsedSeconds: Long = 0,
    val timeElapsedMs: Long = 0,
    val totalDurationSeconds: Long = 0,
    val isRunning: Boolean = true,
    val isCompleted: Boolean = false,
    val speedKmh: Int = 840,
    val altitudeMeters: Int = 10600,
    val currentLat: Double = 0.0,
    val currentLon: Double = 0.0,
    val progress: Float = 0.0f,
    /** Camera mode (0=Free/1=Tracking/2=Cockpit) restored from a saved flight, if any — lets
     *  the camera-view picker UI reflect it instead of defaulting to Chase. Null for a
     *  brand-new flight, which keeps resetting to the default as before. */
    val restoredCameraMode: Int? = null
)

class InFlightViewModel(
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val flightLogRepository: FlightLogRepository,
    private val challengeRepository: ChallengeRepository,
    /** Phase 3b's landing-result channel (see docs/design/mechanics.md's post-landing pipeline
     *  step 5) - published into at the end of [checkAchievementsAndChallenges], read by
     *  `CesiumGameActivity`'s `Screen.ArrivalCelebration` `onContinue` once this ViewModel (and
     *  its nav entry) may already be gone. */
    private val landingResultChannel: LandingResultChannel,
    private val cacheDir: java.io.File,
    val flightNumber: String,
    val originIata: String,
    val destIata: String,
    val durationMin: Int,
    val mode: FlightMode = FlightMode.STORY,
    /** Which Route challenge this CHALLENGE-tagged session is scoped to (see
     *  docs/design/challenges.md#persistence--route-scoping) - null for STORY/FREE, and
     *  meaningless when [mode] isn't CHALLENGE. Threaded through the same nav-arg mechanism
     *  Phase 2 used for [originIata] (see Screen.InFlight). */
    val challengeId: Int? = null
) : ViewModel() {

    private val _originAirport = MutableStateFlow<Airport?>(null)
    val originAirport: StateFlow<Airport?> = _originAirport.asStateFlow()

    private val _destAirport = MutableStateFlow<Airport?>(null)
    val destAirport: StateFlow<Airport?> = _destAirport.asStateFlow()

    private val _routeDetails = MutableStateFlow<FlightRoute?>(null)
    val routeDetails: StateFlow<FlightRoute?> = _routeDetails.asStateFlow()

    private val _uiState = MutableStateFlow(InFlightState())
    val uiState: StateFlow<InFlightState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mapRenderer = CesiumHeadlessMapRenderer(cacheDir)

    /** Where this session's paused flight lives: the global Story/Free slot, or this specific
     *  Route challenge's own row. Picked once here instead of re-branching on mode/challengeId
     *  on every read/write/clear - see [PausedFlightStore]'s own doc. */
    private val pausedFlightStore: PausedFlightStore =
        if (mode == FlightMode.CHALLENGE && challengeId != null) {
            challengeRepository.pausedFlightStore(challengeId)
        } else {
            preferencesRepository.pausedFlightStore
        }

    init {
        // A fresh nav entry (new flight, or resuming one) always means a fresh ViewModel
        // instance - clear out whatever the *previous* flight's landing left behind so a stale
        // result can never leak into this flight's own landing sequence.
        landingResultChannel.reset()

        val totalSec = durationMin * 60L

        // Reading the paused flight (for its elapsed time / camera, if any) is a suspend Room
        // call for CHALLENGE sessions - see pausedFlightStore - so the rest of what used to run
        // synchronously in init now runs after it resolves, in the same order as before.
        viewModelScope.launch {
            val saved = pausedFlightStore.get()
            val initialElapsedMs = saved?.elapsedMs ?: -3000L
            val savedCamera = saved?.camera

            _uiState.value = InFlightState(
                timeRemainingSeconds = totalSec - (initialElapsedMs.coerceAtLeast(0L) / 1000L),
                timeElapsedMs = initialElapsedMs, // 3 second start hold if not saved
                totalDurationSeconds = totalSec,
                timeElapsedSeconds = initialElapsedMs.coerceAtLeast(0L) / 1000L,
                restoredCameraMode = savedCamera?.mode
            )
            loadFlightDetails()
            startTimer()

            // Only present when resuming a flight that was previously saved with a camera pose —
            // a brand-new flight never has one, so the engine's own default framing applies
            // unchanged. Mode is set first so it's already correct by the time the pose lands.
            if (savedCamera != null) {
                com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetCameraMode(savedCamera.mode)
                com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetCameraPose(
                    savedCamera.x, savedCamera.y, savedCamera.z,
                    savedCamera.qx, savedCamera.qy, savedCamera.qz, savedCamera.qw
                )
            }
        }
    }

    /** Snapshots the live camera and persists it for this flight, so resuming later restores
     *  the same mode/position/rotation instead of the default framing. Call on the way out
     *  (e.g. ON_STOP), not on a timer — this only needs to be current when the user leaves. */
    fun saveCameraState() {
        val pose = com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeGetCameraPose()
        if (pose.size < 8) return
        val camera = CameraPose(
            mode = pose[0].toInt(),
            x = pose[1], y = pose[2], z = pose[3],
            qx = pose[4], qy = pose[5], qz = pose[6], qw = pose[7]
        )
        viewModelScope.launch {
            val current = pausedFlightStore.get() ?: return@launch
            pausedFlightStore.save(current.copy(camera = camera))
        }
    }

    // [originIata] arrives from the nav route (see Screen.InFlight) instead of being read here
    // via `preferencesRepository.getCurrentAirport()` as it was pre-Phase-2 - for a STORY flight
    // it's still exactly that value (threaded through from FlightSearch/CheckIn unchanged), but
    // a Free Mode flight's origin is a user choice that isn't `currentAirport`, so this is the
    // one place that has to stop assuming the two are the same.
    private fun loadFlightDetails() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = loadRouteContext(airportRepository, originIata, destIata)
            _originAirport.value = context.origin
            _destAirport.value = context.dest
            _routeDetails.value = context.route

            // Initialize coordinates to origin
            context.origin?.let { origin ->
                _uiState.update { it.copy(currentLat = origin.lat, currentLon = origin.lon) }
            }
        }
    }

    fun startTimer() {
        if (timerJob != null) return
        _uiState.update { it.copy(isRunning = true) }
        val tickDelayMs = 33L
        var lastTickTime = android.os.SystemClock.elapsedRealtime()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(tickDelayMs)
                val now = android.os.SystemClock.elapsedRealtime()
                val deltaMs = now - lastTickTime
                lastTickTime = now
                
                // Persisting elapsed time can be a suspend Room write (CHALLENGE sessions), so it
                // can't happen inside _uiState.update {}'s lambda - computed here, then actually
                // written just below, still inside this same timer coroutine. Same reason
                // completeFlight() (now suspend) is called after the update rather than inside it.
                var elapsedToPersist: Long? = null
                var justCompleted = false

                _uiState.update { state ->
                    val newElapsedMs = state.timeElapsedMs + deltaMs
                    val totalMs = state.totalDurationSeconds * 1000L

                    if (newElapsedMs >= totalMs + 3000L) { // Added 3 second end hold
                        justCompleted = true
                        state.copy(
                            timeRemainingSeconds = 0,
                            timeElapsedSeconds = state.totalDurationSeconds,
                            timeElapsedMs = totalMs,
                            progress = 1.0f,
                            isRunning = false,
                            isCompleted = true
                        )
                    } else {
                        val displayElapsedMs = newElapsedMs.coerceIn(0L, totalMs)
                        val newProgress = (displayElapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                        com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetProgress(newProgress.toDouble())

                        val telemetry = com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeGetTelemetry()
                        val newElapsedSec = displayElapsedMs / 1000L
                        val newRemainingSec = state.totalDurationSeconds - newElapsedSec

                        if (newElapsedSec != state.timeElapsedSeconds) {
                            elapsedToPersist = newElapsedMs
                        }

                        var currentLat = state.currentLat
                        var currentLon = state.currentLon
                        var currentAlt = state.altitudeMeters
                        var currentSpeed = state.speedKmh

                        if (telemetry.size >= 8) {
                            currentLat = telemetry[1]
                            currentLon = telemetry[2]
                            currentAlt = telemetry[3].toInt()
                            currentSpeed = (telemetry[4] * 3.6).toInt() // Convert m/s to km/h
                        }

                        state.copy(
                            timeRemainingSeconds = newRemainingSec,
                            timeElapsedSeconds = newElapsedSec,
                            timeElapsedMs = newElapsedMs, // Keep underlying timer going
                            progress = newProgress,
                            currentLat = currentLat,
                            currentLon = currentLon,
                            speedKmh = currentSpeed,
                            altitudeMeters = currentAlt
                        )
                    }
                }

                elapsedToPersist?.let { persistElapsed(it) }
                if (justCompleted) completeFlight()
            }
        }
    }

    private suspend fun persistElapsed(elapsedMs: Long) {
        val current = pausedFlightStore.get() ?: return
        pausedFlightStore.save(current.copy(elapsedMs = elapsedMs))
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isRunning = false) }
    }

    fun skipFlight() {
        viewModelScope.launch {
            completeFlight()
            _uiState.update { state ->
                state.copy(
                    timeRemainingSeconds = 0,
                    timeElapsedSeconds = state.totalDurationSeconds,
                    timeElapsedMs = state.totalDurationSeconds * 1000L,
                    progress = 1.0f,
                    isRunning = false,
                    isCompleted = true
                )
            }
        }
    }

    /** Shared landing/completion sequence: stop the timer, persist the new `currentAirport`
     *  (STORY only - see below), clear this session's paused flight (see [pausedFlightStore]),
     *  kick off the destination pre-render, write the logbook entry (always, tagged with [mode]), run the
     *  post-landing achievement/challenge check, and snap the native engine to 100% progress.
     *  Invoked by both the normal timer-completion branch and the debug [skipFlight] shortcut
     *  so the two paths can't drift out of sync. Does not touch [_uiState] — each call site
     *  applies its own (identical) completed-state update.
     *
     *  Follows docs/design/mechanics.md's post-landing pipeline: step 2 (logbook, always) →
     *  step 3 (currentAirport/visited-set, STORY only) → step 4 (achievement/challenge check,
     *  stubbed - see [checkAchievementsAndChallenges]). Every flight is STORY today (Free Mode
     *  and Challenges don't exist yet), so the STORY branch is the only one exercised in
     *  practice - but the branch is real, not a placeholder. */
    private suspend fun completeFlight() {
        timerJob?.cancel()
        timerJob = null

        // Step 3: only a STORY-tagged session moves the player's main position/visited-set.
        // FREE and CHALLENGE sessions are logged (below) but never touch currentAirport.
        if (mode == FlightMode.STORY) {
            preferencesRepository.setCurrentAirport(destIata)
        }

        pausedFlightStore.clear()
        preRenderDestinationMap()
        saveFlightLog()
        checkAchievementsAndChallenges()
        com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetProgress(1.0)
    }

    // ── Post-landing pipeline step 4 (docs/design/mechanics.md) ─────────────────────────
    // Every eligible flight (STORY or CHALLENGE - never FREE) is checked against all active
    // challenges here, and the result is published to [landingResultChannel] for step 5's landing
    // sequence (the rank stamp always shows first, unchanged; this decides what - if anything -
    // follows it). The challenge half delegates to processLandingForChallenges (a standalone,
    // JNI-free function so it's unit-testable without instantiating this ViewModel - see
    // ChallengeLandingTest); the before/after diffing that turns its side effects into a
    // `LandingResult` is [resolveLandingOutcome], pulled out the same way for the same reason -
    // see LandingResultTest.
    //
    // Phase 4 (achievements) deliberately has no achievement half here, and this isn't a stub -
    // docs/design/achievements.md never specifies an unlock-celebration screen or landing-sequence
    // beat for achievements the way achievements.md/challenges.md explicitly do for challenge
    // completion. Unlike Challenge progress (which lives on the `challenges` row and has to be
    // mutated somewhere), every v1 achievement category is fully re-derivable on read: Geographic
    // reads `AirportRepository.getVisitedGeography`'s output, Distance/Behavioral fold over the
    // STORY-tagged flight history, and Challenges-completed is a live query - see
    // `AchievementProgress`/`AccountViewModel`. So achievement state is computed on-demand
    // whenever the Account screen is viewed (same reactive pattern `AccountViewModel`/
    // `FlightSearchViewModel` already use for `visitedCountries`/`completedContinents`), not
    // checked/persisted here after every landing - there's no "just unlocked" flag to set, and
    // no new Room migration needed for this feature.
    private fun checkAchievementsAndChallenges() {
        val distanceKm = _routeDetails.value?.distanceKm ?: 0.0
        viewModelScope.launch(Dispatchers.IO) {
            // FREE never reaches processLandingForChallenges's own checks anyway (it's a no-op
            // for FREE), but short-circuiting here too skips two DB round trips and resolves the
            // channel near-instantly rather than leaving it Pending until a query completes.
            if (mode == FlightMode.FREE) {
                landingResultChannel.publish(LandingResult.None)
                return@launch
            }

            val before: List<Challenge> = challengeRepository.listActiveChallenges()
            processLandingForChallenges(challengeRepository, mode, challengeId, destIata, distanceKm)
            // Re-fetched by id (not re-listing "active" challenges) because a challenge that just
            // *completed* this landing is no longer ACTIVE - listing active-only here would make
            // every completion invisible to the diff. See resolveLandingOutcome's own note.
            val after: List<Challenge> = before.mapNotNull { challengeRepository.getChallenge(it.id) }
            landingResultChannel.publish(resolveLandingOutcome(before, after))
        }
    }

    private var renderJob: kotlinx.coroutines.Job? = null

    private fun preRenderDestinationMap() {
        renderJob = renderScope.launch {
            val dest = airportRepository.getAirportByIata(destIata) ?: return@launch
            android.util.Log.d("InFlightViewModel", "Pre-rendering map for destination ${dest.iataCode}...")
            val result = mapRenderer.renderRouteMapForAirport(airportRepository, dest)
            when (result) {
                is CesiumHeadlessMapRenderer.Result.Success ->
                    android.util.Log.d("InFlightViewModel", "Pre-rendering succeeded: ${result.path}")
                is CesiumHeadlessMapRenderer.Result.Failure ->
                    android.util.Log.e("InFlightViewModel", "Pre-rendering failed: ${result.message}")
            }
        }
    }

    private fun saveFlightLog() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val route = _routeDetails.value
                val distanceKm = route?.distanceKm ?: 0.0

                flightLogRepository.logFlight(
                    flightNumber = flightNumber,
                    originIata = originIata,
                    destIata = destIata,
                    durationMin = durationMin,
                    distanceKm = distanceKm,
                    mode = mode
                )
            } catch (e: Exception) {
                android.util.Log.e("InFlightViewModel", "Error saving flight log to Room", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        
        // Wait for the render job to finish (if any) before cancelling the scope
        renderScope.launch {
            renderJob?.join()
            renderScope.cancel()
        }
    }
}

class InFlightViewModelFactory(
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val flightLogRepository: FlightLogRepository,
    private val challengeRepository: ChallengeRepository,
    private val landingResultChannel: LandingResultChannel,
    private val cacheDir: java.io.File,
    private val flightNumber: String,
    private val originIata: String,
    private val destIata: String,
    private val durationMin: Int,
    private val mode: FlightMode = FlightMode.STORY,
    private val challengeId: Int? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InFlightViewModel::class.java)) {
            return InFlightViewModel(airportRepository, preferencesRepository, flightLogRepository, challengeRepository, landingResultChannel, cacheDir, flightNumber, originIata, destIata, durationMin, mode, challengeId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
