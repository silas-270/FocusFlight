package com.example.focusflight.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.repository.CesiumRSLibrary
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class FlightSearchViewModel(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    private val cacheDir: File
) : ViewModel() {

    private val _originAirport = MutableStateFlow<Airport?>(null)
    val originAirport: StateFlow<Airport?> = _originAirport.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSort = MutableStateFlow("Popular") // Default sort
    val selectedSort: StateFlow<String> = _selectedSort.asStateFlow()

    private val _routes = MutableStateFlow<List<FlightRoute>>(emptyList())
    val routes: StateFlow<List<FlightRoute>> = _routes.asStateFlow()

    private val _selectedRoute = MutableStateFlow<FlightRoute?>(null)
    val selectedRoute: StateFlow<FlightRoute?> = _selectedRoute.asStateFlow()

    private val _routeMapPath = MutableStateFlow<String?>(null)
    val routeMapPath: StateFlow<String?> = _routeMapPath.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    init {
        loadOrigin()
    }

    private fun loadOrigin() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseIata = preferencesRepository.getCurrentAirport()
            if (baseIata != null) {
                val airport = databaseHelper.getAirportByIata(baseIata)
                _originAirport.value = airport
                fetchRoutes()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        fetchRoutes()
    }

    fun onSortChanged(sort: String) {
        _selectedSort.value = sort
        fetchRoutes()
    }

    fun selectRoute(route: FlightRoute?) {
        _selectedRoute.value = route
        generateRouteMap(route)
    }

    private fun fetchRoutes() {
        val origin = _originAirport.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val fetched = databaseHelper.getOutboundRoutes(
                originIata = origin.iataCode,
                searchQuery = _searchQuery.value,
                sortBy = _selectedSort.value
            )
            _routes.value = fetched
            // Determine selected route
            val currentSelected = _selectedRoute.value
            if (currentSelected == null && fetched.isNotEmpty()) {
                selectRoute(fetched.first())
            } else if (fetched.isEmpty()) {
                selectRoute(null)
            } else if (currentSelected != null) {
                val stillExists = fetched.find { it.destIata == currentSelected.destIata }
                if (stillExists == null) {
                    selectRoute(fetched.first())
                } else {
                    // Update reference in case distance/time/etc changed (unlikely, but clean)
                    _selectedRoute.value = stillExists
                }
            }
        }
    }

    private fun generateRouteMap(selected: FlightRoute?) {
        val origin = _originAirport.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isRendering.value = true
            try {
                val routesData = if (selected != null) {
                    listOf(Pair(Pair(origin.lat, origin.lon), Pair(selected.destLat, selected.destLon)))
                } else {
                    emptyList()
                }

                if (routesData.isEmpty()) {
                    _routeMapPath.value = null
                    return@launch
                }

                val outFile = File(cacheDir, "search_route_map.png")
                if (outFile.exists()) {
                    outFile.delete()
                }

                Log.d("FlightSearchViewModel", "Triggering route rendering for selected route to ${selected?.destIata}...")
                val success = CesiumRSLibrary.renderRoutes(
                    width = 1080,
                    height = 1320,
                    routesData = routesData,
                    outPath = outFile.absolutePath
                )

                if (success && outFile.exists()) {
                    _routeMapPath.value = outFile.absolutePath
                } else {
                    _routeMapPath.value = null
                }
            } catch (t: Throwable) {
                Log.e("FlightSearchViewModel", "Error in route rendering", t)
                _routeMapPath.value = null
            } finally {
                _isRendering.value = false
            }
        }
    }
}

class FlightSearchViewModelFactory(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    private val cacheDir: File
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlightSearchViewModel::class.java)) {
            return FlightSearchViewModel(databaseHelper, preferencesRepository, cacheDir) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
