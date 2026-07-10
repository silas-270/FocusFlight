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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class OnboardingViewModel(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository
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
                .collect { query ->
                    if (query.trim().length >= 2) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val results = databaseHelper.searchAirports(query)
                            _searchResults.value = results
                        }
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
    }

    fun saveHomeAirport(): Boolean {
        val airport = _selectedAirport.value ?: return false
        preferencesRepository.setHomeAirport(airport.iataCode)
        preferencesRepository.setCurrentAirport(airport.iataCode)
        preferencesRepository.setOnboardingCompleted(true)
        return true
    }
}

class OnboardingViewModelFactory(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(databaseHelper, preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
