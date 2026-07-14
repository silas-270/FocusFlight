package com.example.focusflight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.PreferencesRepository
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
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
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
                val origin = databaseHelper.getAirportByIata(baseIata)
                _originAirport.value = origin
                
                val dest = databaseHelper.getAirportByIata(destIata)
                _destAirport.value = dest

                if (origin != null && dest != null) {
                    val routes = databaseHelper.getOutboundRoutes(originIata = origin.iataCode, searchQuery = destIata)
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
                        saveFlightLogToPrefs()
                        com.example.focusflight.engine.CesiumBridge.nativeSetProgress(1.0)
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
                        com.example.focusflight.engine.CesiumBridge.nativeSetProgress(newProgress.toDouble())

                        val telemetry = com.example.focusflight.engine.CesiumBridge.nativeGetTelemetry()
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
        saveFlightLogToPrefs()
        com.example.focusflight.engine.CesiumBridge.nativeSetProgress(1.0)
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
            try {
                val dest = databaseHelper.getAirportByIata(destIata) ?: return@launch
                val destinations = databaseHelper.getRandomAirports(12, dest.iataCode)
                val routesData = destinations.map { d ->
                    Pair(Pair(dest.lat, dest.lon), Pair(d.lat, d.lon))
                }

                val outFile = java.io.File(cacheDir, "hub_route_map_${dest.iataCode}.png")
                if (outFile.exists()) {
                    outFile.delete()
                }

                android.util.Log.d("InFlightViewModel", "Pre-rendering map for destination ${dest.iataCode}...")
                val success = com.example.focusflight.data.repository.CesiumRSLibrary.renderRoutes(
                    width = 1080,
                    height = 1320,
                    routesData = routesData,
                    outPath = outFile.absolutePath
                )
                if (success && outFile.exists()) {
                    android.util.Log.d("InFlightViewModel", "Pre-rendering succeeded: ${outFile.absolutePath}")
                    CacheUtils.pruneMapCache(cacheDir)
                } else {
                    android.util.Log.e("InFlightViewModel", "Pre-rendering failed.")
                }
            } catch (e: java.lang.Exception) {
                android.util.Log.e("InFlightViewModel", "Error in pre-rendering destination map", e)
            }
        }
    }

    private fun saveFlightLogToPrefs() {
        val origin = _originAirport.value?.iataCode ?: "STR"
        val dest = destIata
        val duration = durationMin
        val hrs = duration / 60.0
        val rank = when {
            hrs >= 8.0 -> "GLOBETROTTER"
            hrs >= 4.0 -> "COMMANDER"
            hrs >= 2.0 -> "CAPTAIN"
            else -> "CO-PILOT"
        }
        val dateStr = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(java.util.Date()).uppercase()
        preferencesRepository.saveFlightLog(origin, dest, duration, flightNumber, rank, dateStr)
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
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    private val cacheDir: java.io.File,
    private val flightNumber: String,
    private val destIata: String,
    private val durationMin: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InFlightViewModel::class.java)) {
            return InFlightViewModel(databaseHelper, preferencesRepository, cacheDir, flightNumber, destIata, durationMin) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
