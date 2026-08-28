package com.example.focusflight.ui.viewmodel.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.data.repository.UserRepository
import com.example.focusflight.domain.AirportSearchController
import com.example.focusflight.engine.headless.CesiumHeadlessMapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userRepository: UserRepository,
    private val cacheDir: java.io.File
) : ViewModel() {

    private val airportSearch = AirportSearchController(airportRepository, viewModelScope)
    val searchQuery: StateFlow<String> = airportSearch.query
    val searchResults: StateFlow<List<Airport>> = airportSearch.results

    private val _selectedAirport = MutableStateFlow<Airport?>(null)
    val selectedAirport: StateFlow<Airport?> = _selectedAirport.asStateFlow()

    private val mapRenderer = CesiumHeadlessMapRenderer(cacheDir)

    fun onQueryChanged(newQuery: String) {
        airportSearch.onQueryChanged(newQuery)
        val selected = _selectedAirport.value
        if (selected != null &&
            !selected.name.contains(newQuery, ignoreCase = true) &&
            !selected.iataCode.equals(newQuery, ignoreCase = true)) {
            _selectedAirport.value = null
        }
    }

    fun selectAirport(airport: Airport) {
        _selectedAirport.value = airport
        airportSearch.onQueryChanged("${airport.municipality} (${airport.iataCode})")
        airportSearch.clearResults()
        preRenderMap(airport)
    }

    fun selectAirportByIata(iataCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val airport = airportRepository.getAirportByIata(iataCode)
            if (airport != null) {
                _selectedAirport.value = airport
                airportSearch.onQueryChanged("${airport.municipality} (${airport.iataCode})")
                airportSearch.clearResults()
                preRenderMap(airport)
            }
        }
    }

    fun clearSelection() {
        _selectedAirport.value = null
        airportSearch.onQueryChanged("")
    }

    fun saveHomeAirport(): Boolean {
        val airport = _selectedAirport.value ?: return false
        preferencesRepository.setHomeAirport(airport.iataCode)
        preferencesRepository.setCurrentAirport(airport.iataCode)
        preferencesRepository.setOnboardingCompleted(true)

        // docs/design/story-mode.md: seed "last home base changed" to 31 days in the past so the
        // very first change-home-base call is immediately eligible through the same 30-day
        // cooldown check every later change uses - no separate "grace change" code path. See
        // com.example.focusflight.data.model.HomeBaseCooldown.seedChangeHomeBaseTimestamp.
        preferencesRepository.setLastHomeBaseChangedAt(
            com.example.focusflight.data.model.HomeBaseCooldown.seedChangeHomeBaseTimestamp(System.currentTimeMillis())
        )

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
            val result = mapRenderer.renderRouteMapForAirport(airportRepository, airport)
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
