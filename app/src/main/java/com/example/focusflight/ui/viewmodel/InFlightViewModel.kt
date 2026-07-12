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

data class InFlightState(
    val timeRemainingSeconds: Long = 0,
    val timeElapsedSeconds: Long = 0,
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

    init {
        val totalSec = durationMin * 60L
        _uiState.value = InFlightState(
            timeRemainingSeconds = totalSec,
            totalDurationSeconds = totalSec
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
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { state ->
                    if (state.timeRemainingSeconds <= 1) {
                        timerJob?.cancel()
                        timerJob = null
                        state.copy(
                            timeRemainingSeconds = 0,
                            timeElapsedSeconds = state.totalDurationSeconds,
                            progress = 1.0f,
                            isRunning = false,
                            isCompleted = true
                        )
                    } else {
                        val newRemaining = state.timeRemainingSeconds - 1
                        val newElapsed = state.timeElapsedSeconds + 1
                        val newProgress = newElapsed.toFloat() / state.totalDurationSeconds.toFloat()
                        
                        // Linear interpolation of coordinates
                        val origin = _originAirport.value
                        val dest = _destAirport.value
                        val currentLat = if (origin != null && dest != null) {
                            origin.lat + (dest.lat - origin.lat) * newProgress
                        } else state.currentLat
                        val currentLon = if (origin != null && dest != null) {
                            origin.lon + (dest.lon - origin.lon) * newProgress
                        } else state.currentLon

                        // Subtle variations for flight instruments realism
                        val speedOffset = ((newElapsed % 60).toInt() - 30) / 10
                        val currentSpeed = 840 + speedOffset
                        val altOffset = ((newElapsed % 120).toInt() - 60) / 10
                        val currentAlt = 10600 + altOffset

                        state.copy(
                            timeRemainingSeconds = newRemaining,
                            timeElapsedSeconds = newElapsed,
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
        _uiState.update { state ->
            state.copy(
                timeRemainingSeconds = 0,
                timeElapsedSeconds = state.totalDurationSeconds,
                progress = 1.0f,
                isRunning = false,
                isCompleted = true
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

class InFlightViewModelFactory(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    private val flightNumber: String,
    private val destIata: String,
    private val durationMin: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InFlightViewModel::class.java)) {
            return InFlightViewModel(databaseHelper, preferencesRepository, flightNumber, destIata, durationMin) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
