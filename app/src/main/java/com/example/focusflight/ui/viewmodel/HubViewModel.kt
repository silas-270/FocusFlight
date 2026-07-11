package com.example.focusflight.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.repository.CesiumRSLibrary
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class FlightStats(
    val totalFlights: Int = 0,
    val totalHours: Double = 0.0,
    val airportsVisited: Int = 0
)

class HubViewModel(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    private val cacheDir: File
) : ViewModel() {

    private val _currentAirport = MutableStateFlow<Airport?>(null)
    val currentAirport: StateFlow<Airport?> = _currentAirport.asStateFlow()

    private val _flightStats = MutableStateFlow(FlightStats())
    val flightStats: StateFlow<FlightStats> = _flightStats.asStateFlow()

    private val _recentFlights = MutableStateFlow<List<Any>>(emptyList())
    val recentFlights: StateFlow<List<Any>> = _recentFlights.asStateFlow()

    private val _routeMapPath = MutableStateFlow<String?>(null)
    val routeMapPath: StateFlow<String?> = _routeMapPath.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseIata = preferencesRepository.getCurrentAirport()
            if (baseIata != null) {
                val airport = databaseHelper.getAirportByIata(baseIata)
                _currentAirport.value = airport
                if (airport != null) {
                    generateRouteMap(airport)
                }
            }
            
            _flightStats.value = FlightStats(
                totalFlights = 0,
                totalHours = 0.0,
                airportsVisited = if (_currentAirport.value != null) 1 else 0
            )
        }
    }

    private fun generateRouteMap(origin: Airport) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRendering.value = true
            try {
                // Fetch 12 random destination airports for routes display
                val destinations = databaseHelper.getRandomAirports(12, origin.iataCode)
                val routesData = destinations.map { dest ->
                    Pair(Pair(origin.lat, origin.lon), Pair(dest.lat, dest.lon))
                }

                val outFile = File(cacheDir, "hub_route_map.png")
                if (outFile.exists()) {
                    outFile.delete()
                }

                Log.d("HubViewModel", "Triggering route rendering for ${routesData.size} routes...")
                val success = CesiumRSLibrary.renderRoutes(
                    width = 1080,
                    height = 1320,
                    routesData = routesData,
                    outPath = outFile.absolutePath
                )

                if (success && outFile.exists()) {
                    Log.d("HubViewModel", "Route rendering succeeded: ${outFile.absolutePath}")
                    _routeMapPath.value = outFile.absolutePath
                } else {
                    Log.e("HubViewModel", "Route rendering failed or file not created.")
                }
            } catch (e: Exception) {
                Log.e("HubViewModel", "Error in route rendering", e)
            } finally {
                _isRendering.value = false
            }
        }
    }
}

class HubViewModelFactory(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    private val cacheDir: File
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HubViewModel::class.java)) {
            return HubViewModel(databaseHelper, preferencesRepository, cacheDir) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
