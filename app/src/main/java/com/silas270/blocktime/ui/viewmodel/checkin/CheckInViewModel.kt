package com.silas270.blocktime.ui.viewmodel.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.data.model.FlightRoute
import com.silas270.blocktime.data.repository.AirportRepository
import com.silas270.blocktime.data.repository.UserRepository
import com.silas270.blocktime.domain.loadRouteContext
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
    private val airportRepository: AirportRepository,
    private val userRepository: UserRepository,
    val originIata: String,
    val destIata: String,
    val flightNumber: String
) : ViewModel() {

    private val _originAirport = MutableStateFlow<Airport?>(null)
    val originAirport: StateFlow<Airport?> = _originAirport.asStateFlow()

    private val _destAirport = MutableStateFlow<Airport?>(null)
    val destAirport: StateFlow<Airport?> = _destAirport.asStateFlow()

    private val _routeDetails = MutableStateFlow<FlightRoute?>(null)
    val routeDetails: StateFlow<FlightRoute?> = _routeDetails.asStateFlow()

    /** True once loading finished without finding the route. START FLIGHT needs its duration,
     *  so it stays disabled for good in that case - this lets the screen say why instead of
     *  showing a button that silently never enables. */
    private val _routeMissing = MutableStateFlow(false)
    val routeMissing: StateFlow<Boolean> = _routeMissing.asStateFlow()

    /** The pilot's username for the boarding pass, or null until the profile has loaded. */
    private val _pilotName = MutableStateFlow<String?>(null)
    val pilotName: StateFlow<String?> = _pilotName.asStateFlow()

    val currentDate: String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date()).uppercase()

    init {
        loadFlightDetails()
    }

    // [originIata] arrives from the nav route (see Screen.CheckIn) rather than
    // `PreferencesRepository.getCurrentAirport()` as it did pre-Phase-2 - for a STORY booking
    // the caller always passes the current airport anyway (see FlightSearchViewModel's
    // STORY-only loadOrigin()), so this is value-identical for Story Mode; it's what lets a
    // Free Mode booking's picked origin (which is not `currentAirport`) actually reach this
    // screen and the in-flight session after it.
    private fun loadFlightDetails() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = loadRouteContext(airportRepository, originIata, destIata)
            _originAirport.value = context.origin
            _destAirport.value = context.dest
            _routeDetails.value = context.route
            _routeMissing.value = context.route == null
        }
        viewModelScope.launch(Dispatchers.IO) {
            _pilotName.value = try {
                userRepository.getProfile()?.username?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                // Cosmetic only - the pass falls back to a generic name.
                android.util.Log.e("CheckInViewModel", "Error loading pilot name", e)
                null
            }
        }
    }
}

class CheckInViewModelFactory(
    private val airportRepository: AirportRepository,
    private val userRepository: UserRepository,
    private val originIata: String,
    private val destIata: String,
    private val flightNumber: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CheckInViewModel::class.java)) {
            return CheckInViewModel(airportRepository, userRepository, originIata, destIata, flightNumber) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
