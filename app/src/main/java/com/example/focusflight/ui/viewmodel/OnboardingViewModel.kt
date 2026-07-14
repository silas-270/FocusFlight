package com.example.focusflight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class OnboardingViewModel(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    private val cacheDir: java.io.File
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Airport>>(emptyList())
    val searchResults: StateFlow<List<Airport>> = _searchResults.asStateFlow()

    private val _selectedAirport = MutableStateFlow<Airport?>(null)
    val selectedAirport: StateFlow<Airport?> = _selectedAirport.asStateFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collectLatest { query ->
                    if (query.trim().length >= 2) {
                        val results = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            databaseHelper.searchAirports(query)
                        }
                        _searchResults.value = results
                    } else {
                        _searchResults.value = emptyList()
                    }
                }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        val selected = _selectedAirport.value
        if (selected != null && 
            !selected.name.contains(newQuery, ignoreCase = true) && 
            !selected.iataCode.equals(newQuery, ignoreCase = true)) {
            _selectedAirport.value = null
        }
    }

    fun selectAirport(airport: Airport) {
        _selectedAirport.value = airport
        _searchQuery.value = "${airport.municipality} (${airport.iataCode})"
        _searchResults.value = emptyList()
        preRenderMap(airport)
    }

    fun selectAirportByIata(iataCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val airport = databaseHelper.getAirportByIata(iataCode)
            if (airport != null) {
                _selectedAirport.value = airport
                _searchQuery.value = "${airport.municipality} (${airport.iataCode})"
                _searchResults.value = emptyList()
                preRenderMap(airport)
            }
        }
    }

    fun clearSelection() {
        _selectedAirport.value = null
        _searchQuery.value = ""
    }

    fun saveHomeAirport(): Boolean {
        val airport = _selectedAirport.value ?: return false
        preferencesRepository.setHomeAirport(airport.iataCode)
        preferencesRepository.setCurrentAirport(airport.iataCode)
        preferencesRepository.setOnboardingCompleted(true)
        return true
    }

    private fun preRenderMap(airport: Airport) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destinations = databaseHelper.getRandomAirports(12, airport.iataCode)
                val routesData = destinations.map { dest ->
                    Pair(Pair(airport.lat, airport.lon), Pair(dest.lat, dest.lon))
                }

                val outFile = java.io.File(cacheDir, "hub_route_map_${airport.iataCode}.png")
                if (outFile.exists()) {
                    outFile.delete()
                }

                val success = com.example.focusflight.data.repository.CesiumRSLibrary.renderRoutes(
                    width = 1080,
                    height = 1320,
                    routesData = routesData,
                    outPath = outFile.absolutePath
                )
                if (success && outFile.exists()) {
                    android.util.Log.d("OnboardingViewModel", "Pre-rendered onboarding map for ${airport.iataCode} to ${outFile.absolutePath}")
                    CacheUtils.pruneMapCache(cacheDir)
                }
            } catch (e: Exception) {
                android.util.Log.e("OnboardingViewModel", "Error pre-rendering onboarding map", e)
            }
        }
    }
}

class OnboardingViewModelFactory(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    private val cacheDir: java.io.File
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(databaseHelper, preferencesRepository, cacheDir) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
