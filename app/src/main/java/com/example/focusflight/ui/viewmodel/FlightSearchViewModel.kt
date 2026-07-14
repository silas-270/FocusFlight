package com.example.focusflight.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchMode { TIME, AIRPORT }

class FlightSearchViewModel(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _originAirport = MutableStateFlow<Airport?>(null)
    val originAirport: StateFlow<Airport?> = _originAirport.asStateFlow()

    private val _allRoutes = MutableStateFlow<List<FlightRoute>>(emptyList())
    val allRoutes: StateFlow<List<FlightRoute>> = _allRoutes.asStateFlow()

    private val _intervals = MutableStateFlow<List<Int>>(emptyList())
    val intervals: StateFlow<List<Int>> = _intervals.asStateFlow()

    private val _selectedInterval = MutableStateFlow<Int>(0)
    val selectedInterval: StateFlow<Int> = _selectedInterval.asStateFlow()

    private val _filteredRoutes = MutableStateFlow<List<FlightRoute>>(emptyList())
    val filteredRoutes: StateFlow<List<FlightRoute>> = _filteredRoutes.asStateFlow()

    private val _selectedRoute = MutableStateFlow<FlightRoute?>(null)
    val selectedRoute: StateFlow<FlightRoute?> = _selectedRoute.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.TIME)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _airportSearchQuery = MutableStateFlow("")
    val airportSearchQuery: StateFlow<String> = _airportSearchQuery.asStateFlow()

    private val _airportSearchResults = MutableStateFlow<List<FlightRoute>>(emptyList())
    val airportSearchResults: StateFlow<List<FlightRoute>> = _airportSearchResults.asStateFlow()

    init {
        loadOrigin()
    }

    private fun loadOrigin() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseIata = preferencesRepository.getCurrentAirport()
            Log.d("FlightSearchViewModel", "loadOrigin: baseIata=$baseIata")
            if (baseIata != null) {
                val airport = databaseHelper.getAirportByIata(baseIata)
                Log.d("FlightSearchViewModel", "loadOrigin: airport=${airport?.iataCode}")
                _originAirport.value = airport
                fetchRoutes()
            }
        }
    }

    fun selectRoute(route: FlightRoute?) {
        _selectedRoute.value = route
    }

    fun selectInterval(interval: Int) {
        _selectedInterval.value = interval
        val filtered = _allRoutes.value.filter { it.flightTimeMin in interval..(interval + 9) }
        _filteredRoutes.value = filtered
        
        // Auto-select the first route in the new interval if available
        if (filtered.isNotEmpty()) {
            selectRoute(filtered.first())
        } else {
            selectRoute(null)
        }
    }

    fun toggleSearchMode() {
        val nextMode = if (_searchMode.value == SearchMode.TIME) SearchMode.AIRPORT else SearchMode.TIME
        _searchMode.value = nextMode
        if (nextMode == SearchMode.TIME) {
            _airportSearchQuery.value = ""
            _airportSearchResults.value = emptyList()
            if (_selectedInterval.value > 0) {
                selectInterval(_selectedInterval.value)
            } else if (_intervals.value.isNotEmpty()) {
                selectInterval(_intervals.value.first())
            }
        } else {
            selectRoute(null)
        }
    }

    fun onAirportSearchQueryChanged(query: String) {
        _airportSearchQuery.value = query
        if (query.trim().isNotEmpty()) {
            val originIata = _originAirport.value?.iataCode
            val filtered = _allRoutes.value.filter { route ->
                route.destIata != originIata && (
                    route.destIata.contains(query, ignoreCase = true) ||
                    route.destName.contains(query, ignoreCase = true) ||
                    route.destMunicipality.contains(query, ignoreCase = true)
                )
            }
            _airportSearchResults.value = filtered
            
            val currentSelected = _selectedRoute.value
            if (currentSelected != null && !filtered.any { it.id == currentSelected.id }) {
                _selectedRoute.value = null
            }
        } else {
            _airportSearchResults.value = emptyList()
            _selectedRoute.value = null
        }
    }

    fun resetState() {
        _searchMode.value = SearchMode.TIME
        _airportSearchQuery.value = ""
        _airportSearchResults.value = emptyList()
        if (_intervals.value.isNotEmpty()) {
            selectInterval(_intervals.value.first())
        } else {
            selectRoute(null)
        }
    }

    private fun fetchRoutes() {
        val origin = _originAirport.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            var fetched = databaseHelper.getOutboundRoutes(
                originIata = origin.iataCode,
                searchQuery = "",
                sortBy = "Shortest"
            )
            if (fetched.isEmpty()) {
                // Fallback to LHR if user's airport has no routes so the UI isn't empty
                val fallbackAirport = databaseHelper.getAirportByIata("LHR")
                if (fallbackAirport != null) {
                    _originAirport.value = fallbackAirport
                    preferencesRepository.setCurrentAirport("LHR")
                    fetched = databaseHelper.getOutboundRoutes(
                        originIata = "LHR",
                        searchQuery = "",
                        sortBy = "Shortest"
                    )
                }
            }
            Log.d("FlightSearchViewModel", "fetchRoutes: fetched ${fetched.size} routes for origin ${_originAirport.value?.iataCode}")
            _allRoutes.value = fetched

            if (fetched.isNotEmpty()) {
                val shortest = fetched.first().flightTimeMin
                val longest = fetched.maxOf { it.flightTimeMin }
                val startInterval = (shortest / 10) * 10
                val endInterval = (longest / 10) * 10
                val generatedIntervals = (startInterval..endInterval step 10).filter { interval ->
                    fetched.any { it.flightTimeMin in interval..(interval + 9) }
                }
                _intervals.value = generatedIntervals
                
                // Initialize selection with the first available interval (shortest route)
                if (generatedIntervals.isNotEmpty()) {
                    selectInterval(generatedIntervals.first())
                }
            } else {
                _intervals.value = emptyList()
                _filteredRoutes.value = emptyList()
                selectRoute(null)
            }
        }
    }
}

class FlightSearchViewModelFactory(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlightSearchViewModel::class.java)) {
            return FlightSearchViewModel(databaseHelper, preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
