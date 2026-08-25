package com.example.focusflight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
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
    val progress: Float = 0.0f
)

class InFlightViewModel(
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val flightLogRepository: FlightLogRepository,
    private val cacheDir: java.io.File,
    val flightNumber: String,
    val destIata: String,
    val durationMin: Int
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

        _uiState.value = InFlightState(
            timeRemainingSeconds = totalSec - (initialElapsedMs.coerceAtLeast(0L) / 1000L),
            timeElapsedMs = initialElapsedMs, // 3 second start hold if not saved
            totalDurationSeconds = totalSec,
            timeElapsedSeconds = initialElapsedMs.coerceAtLeast(0L) / 1000L
        )
        loadFlightDetails()
        startTimer()
    }

    private fun loadFlightDetails() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseIata = preferencesRepository.getCurrentAirport()
            if (baseIata != null) {
                val origin = airportRepository.getAirportByIata(baseIata)
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
                        timerJob?.cancel()
                        timerJob = null
                        preferencesRepository.setCurrentAirport(destIata)
                        preferencesRepository.clearActiveFlightProgress(flightNumber)
                        preRenderDestinationMap()
                        saveFlightLog()
                        com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetProgress(1.0)
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
        timerJob?.cancel()
        timerJob = null
        preferencesRepository.setCurrentAirport(destIata)
        preferencesRepository.clearActiveFlightProgress(flightNumber)
        preRenderDestinationMap()
        saveFlightLog()
        com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetProgress(1.0)
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
                val origin = _originAirport.value?.iataCode ?: "STR"
                val route = _routeDetails.value
                val distanceKm = route?.distanceKm ?: 0.0

                flightLogRepository.logFlight(
                    flightNumber = flightNumber,
                    originIata = origin,
                    destIata = destIata,
                    durationMin = durationMin,
                    distanceKm = distanceKm
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
    private val destIata: String,
    private val durationMin: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InFlightViewModel::class.java)) {
            return InFlightViewModel(airportRepository, preferencesRepository, flightLogRepository, cacheDir, flightNumber, destIata, durationMin) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
