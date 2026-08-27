package com.example.focusflight.ui.viewmodel.hub

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightStats
import com.example.focusflight.data.repository.ActiveFlightContext
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.FlightLogRepository
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.engine.headless.CesiumHeadlessMapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class HubViewModel(
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val flightLogRepository: FlightLogRepository,
    private val cacheDir: File
) : ViewModel() {

    private val _currentAirport = MutableStateFlow<Airport?>(null)
    val currentAirport: StateFlow<Airport?> = _currentAirport.asStateFlow()

    private val _flightStats = MutableStateFlow(FlightStats())
    val flightStats: StateFlow<FlightStats> = _flightStats.asStateFlow()

    private val _recentFlights = MutableStateFlow<List<FlightLog>>(emptyList())
    val recentFlights: StateFlow<List<FlightLog>> = _recentFlights.asStateFlow()

    private val _routeMapPath = MutableStateFlow<String?>(null)
    val routeMapPath: StateFlow<String?> = _routeMapPath.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    private val _activeFlightContext = MutableStateFlow<ActiveFlightContext?>(null)
    val activeFlightContext: StateFlow<ActiveFlightContext?> = _activeFlightContext.asStateFlow()

    private val _mapRenderError = MutableStateFlow<String?>(null)
    val mapRenderError: StateFlow<String?> = _mapRenderError.asStateFlow()

    private val mapRenderer = CesiumHeadlessMapRenderer(cacheDir)

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseIata = preferencesRepository.getCurrentAirport()
            if (baseIata != null) {
                val airport = airportRepository.getAirportByIata(baseIata)
                _currentAirport.value = airport
                
                _activeFlightContext.value = preferencesRepository.getActiveFlightContext()
                
                if (airport != null) {
                    generateRouteMap(airport)
                }
            }
            
            // Load real flight stats from Room
            try {
                val stats = flightLogRepository.getFlightStats()
                _flightStats.value = stats
                _recentFlights.value = flightLogRepository.getRecentFlights(5)
            } catch (e: Exception) {
                Log.e("HubViewModel", "Error loading flight stats", e)
                _flightStats.value = FlightStats(
                    totalFlights = 0,
                    totalMinutes = 0,
                    airportsVisited = if (_currentAirport.value != null) 1 else 0
                )
            }
        }
    }

    private fun generateRouteMap(origin: Airport) {
        _mapRenderError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _isRendering.value = true
            try {
                val outboundRoutes = airportRepository.getOutboundRoutes(origin.iataCode)
                val result = mapRenderer.renderRouteMap(
                    centerIata = origin.iataCode,
                    centerLat = origin.lat,
                    centerLon = origin.lon,
                    outboundRoutes = outboundRoutes,
                    reuseCachedFile = true
                )
                when (result) {
                    is CesiumHeadlessMapRenderer.Result.Success -> _routeMapPath.value = result.path
                    is CesiumHeadlessMapRenderer.Result.Failure -> _mapRenderError.value = result.message
                }
            } finally {
                _isRendering.value = false
            }
        }
    }

    fun retryRenderMap() {
        val origin = _currentAirport.value
        if (origin != null) {
            generateRouteMap(origin)
        }
    }
}

class HubViewModelFactory(
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val flightLogRepository: FlightLogRepository,
    private val cacheDir: File
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HubViewModel::class.java)) {
            return HubViewModel(airportRepository, preferencesRepository, flightLogRepository, cacheDir) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
