package com.example.focusflight.ui.viewmodel

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class CheckInViewModel(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    val destIata: String,
    val flightNumber: String
) : ViewModel() {

    private val _originAirport = MutableStateFlow<Airport?>(null)
    val originAirport: StateFlow<Airport?> = _originAirport.asStateFlow()

    private val _destAirport = MutableStateFlow<Airport?>(null)
    val destAirport: StateFlow<Airport?> = _destAirport.asStateFlow()

    private val _routeDetails = MutableStateFlow<FlightRoute?>(null)
    val routeDetails: StateFlow<FlightRoute?> = _routeDetails.asStateFlow()

    val currentDate: String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date()).uppercase()

    init {
        loadFlightDetails()
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
                }
            }
        }
    }
}

class CheckInViewModelFactory(
    private val databaseHelper: FlightDatabaseHelper,
    private val preferencesRepository: PreferencesRepository,
    private val destIata: String,
    private val flightNumber: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CheckInViewModel::class.java)) {
            return CheckInViewModel(databaseHelper, preferencesRepository, destIata, flightNumber) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
