package com.example.focusflight.ui.viewmodel.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.data.repository.UserRepository
import com.example.focusflight.engine.headless.CesiumHeadlessMapRenderer
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
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userRepository: UserRepository,
    private val cacheDir: java.io.File
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Airport>>(emptyList())
    val searchResults: StateFlow<List<Airport>> = _searchResults.asStateFlow()

    private val _selectedAirport = MutableStateFlow<Airport?>(null)
    val selectedAirport: StateFlow<Airport?> = _selectedAirport.asStateFlow()

    private val mapRenderer = CesiumHeadlessMapRenderer(cacheDir)

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collectLatest { query ->
                    if (query.trim().length >= 2) {
                        val results = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            airportRepository.searchAirports(query)
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
            val airport = airportRepository.getAirportByIata(iataCode)
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

        // Create user profile in Room
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userRepository.createProfile(com.example.focusflight.data.model.UserProfile.generateRandomName(), airport.iataCode)
            } catch (e: Exception) {
                android.util.Log.e("OnboardingViewModel", "Error creating user profile", e)
            }
        }

        return true
    }

    private fun preRenderMap(airport: Airport) {
        viewModelScope.launch(Dispatchers.IO) {
            val outboundRoutes = airportRepository.getOutboundRoutes(airport.iataCode)
            val result = mapRenderer.renderRouteMap(
                centerIata = airport.iataCode,
                centerLat = airport.lat,
                centerLon = airport.lon,
                outboundRoutes = outboundRoutes
            )
            if (result is CesiumHeadlessMapRenderer.Result.Success) {
                android.util.Log.d("OnboardingViewModel", "Pre-rendered onboarding map for ${airport.iataCode} to ${result.path}")
            }
        }
    }
}

class OnboardingViewModelFactory(
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userRepository: UserRepository,
    private val cacheDir: java.io.File
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(airportRepository, preferencesRepository, userRepository, cacheDir) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
