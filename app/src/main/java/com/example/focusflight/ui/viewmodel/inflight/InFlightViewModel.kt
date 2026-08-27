package com.example.focusflight.ui.viewmodel.inflight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.FlightLogRepository
import com.example.focusflight.data.repository.PreferencesRepository
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
    private val cacheDir: java.io.File,
    val flightNumber: String,
    val originIata: String,
    val destIata: String,
    val durationMin: Int,
    val mode: FlightMode = FlightMode.STORY
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

    init {
        val totalSec = durationMin * 60L
        val savedProgress = preferencesRepository.getActiveFlightProgress(flightNumber)
        val initialElapsedMs = savedProgress ?: -3000L
        val savedCamera = preferencesRepository.getActiveFlightCamera(flightNumber)

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

    /** Snapshots the live camera and persists it for this flight, so resuming later restores
     *  the same mode/position/rotation instead of the default framing. Call on the way out
     *  (e.g. ON_STOP), not on a timer — this only needs to be current when the user leaves. */
    fun saveCameraState() {
        val pose = com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeGetCameraPose()
        if (pose.size >= 8) {
            preferencesRepository.saveActiveFlightCamera(
                flightNumber,
                com.example.focusflight.data.repository.CameraPose(
                    mode = pose[0].toInt(),
                    x = pose[1], y = pose[2], z = pose[3],
                    qx = pose[4], qy = pose[5], qz = pose[6], qw = pose[7]
                )
            )
        }
    }

    // [originIata] arrives from the nav route (see Screen.InFlight) instead of being read here
    // via `preferencesRepository.getCurrentAirport()` as it was pre-Phase-2 - for a STORY flight
    // it's still exactly that value (threaded through from FlightSearch/CheckIn unchanged), but
    // a Free Mode flight's origin is a user choice that isn't `currentAirport`, so this is the
    // one place that has to stop assuming the two are the same.
    private fun loadFlightDetails() {
        viewModelScope.launch(Dispatchers.IO) {
            val origin = airportRepository.getAirportByIata(originIata)
            _originAirport.value = origin

            val dest = airportRepository.getAirportByIata(destIata)
            _destAirport.value = dest

            if (origin != null && dest != null) {
                val routes = airportRepository.getOutboundRoutes(originIata = origin.iataCode, searchQuery = destIata)
                val route = routes.find { it.destIata == destIata }
                _routeDetails.value = route

                // Initialize coordinates to origin
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
                
                _uiState.update { state ->
                    val newElapsedMs = state.timeElapsedMs + deltaMs
                    val totalMs = state.totalDurationSeconds * 1000L
                    
                    if (newElapsedMs >= totalMs + 3000L) { // Added 3 second end hold
                        completeFlight()
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
                            preferencesRepository.saveActiveFlightProgress(flightNumber, newElapsedMs)
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
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isRunning = false) }
    }

    fun skipFlight() {
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

    /** Shared landing/completion sequence: stop the timer, persist the new `currentAirport`
     *  (STORY only - see below), clear this flight's saved progress/camera, kick off the
     *  destination pre-render, write the logbook entry (always, tagged with [mode]), run the
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
    private fun completeFlight() {
        timerJob?.cancel()
        timerJob = null

        // Step 3: only a STORY-tagged session moves the player's main position/visited-set.
        // FREE and CHALLENGE sessions are logged (below) but never touch currentAirport.
        if (mode == FlightMode.STORY) {
            preferencesRepository.setCurrentAirport(destIata)
        }

        preferencesRepository.clearActiveFlightProgress(flightNumber)
        preferencesRepository.clearActiveFlightCamera(flightNumber)
        preRenderDestinationMap()
        saveFlightLog()
        checkAchievementsAndChallenges()
        com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetProgress(1.0)
    }

    // ── Post-landing pipeline step 4 (docs/design/mechanics.md) ─────────────────────────
    // Every eligible flight (STORY or CHALLENGE - never FREE) is meant to be checked against
    // all achievements and all active challenges here, then the result surfaced to
    // InFlightScreen's landing sequence per mechanics.md's step 5. Intentionally a no-op stub:
    // achievements and challenges don't exist yet (Phase 3/4 build them). This seam exists so
    // those phases have exactly one place to add that check, rather than re-threading
    // completeFlight() again.
    private fun checkAchievementsAndChallenges() {
        // TODO(Phase 3 - challenges, Phase 4 - achievements): if mode != FlightMode.FREE,
        // evaluate this flight against active challenges / achievements and surface the result
        // to the arrival flow. No-op today.
    }

    private var renderJob: kotlinx.coroutines.Job? = null

    private fun preRenderDestinationMap() {
        renderJob = renderScope.launch {
            val dest = airportRepository.getAirportByIata(destIata) ?: return@launch
            val outboundRoutes = airportRepository.getOutboundRoutes(dest.iataCode)
            android.util.Log.d("InFlightViewModel", "Pre-rendering map for destination ${dest.iataCode}...")
            val result = mapRenderer.renderRouteMap(
                centerIata = dest.iataCode,
                centerLat = dest.lat,
                centerLon = dest.lon,
                outboundRoutes = outboundRoutes
            )
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
    private val cacheDir: java.io.File,
    private val flightNumber: String,
    private val originIata: String,
    private val destIata: String,
    private val durationMin: Int,
    private val mode: FlightMode = FlightMode.STORY
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InFlightViewModel::class.java)) {
            return InFlightViewModel(airportRepository, preferencesRepository, flightLogRepository, cacheDir, flightNumber, originIata, destIata, durationMin, mode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
