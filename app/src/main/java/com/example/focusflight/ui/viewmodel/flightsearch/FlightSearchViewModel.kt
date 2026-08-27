package com.example.focusflight.ui.viewmodel.flightsearch

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.ChallengeRepository
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.data.repository.UserRepository
import com.example.focusflight.data.repository.FlightLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchMode { TIME, AIRPORT }

@OptIn(FlowPreview::class)
class FlightSearchViewModel(
    private val context: android.content.Context,
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val challengeRepository: ChallengeRepository,
    private val mode: FlightMode = FlightMode.STORY,
    private val challengeId: Int? = null
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

    // ── Free Mode origin picker ──────────────────────────────────────────────────────────
    // Story Mode's origin is always `currentAirport` (origin-locked, per docs/design/story-mode.md)
    // - loadOrigin() below still sources it exactly as before Phase 2, unchanged. Free Mode has
    // no origin lock (docs/design/free-mode.md), so this is the "smallest addition" the design
    // doc asks for: a second airport search, structurally identical to the existing destination
    // search (onAirportSearchQueryChanged/airportSearchResults above), just over
    // `airportRepository.searchAirports()` (any airport) instead of `getOutboundRoutes()` (routes
    // from a fixed origin) - reusing OnboardingViewModel's debounced-search pattern since that's
    // the other place in the app that already searches airports by free text, not by route.
    private val _originSearchQuery = MutableStateFlow("")
    val originSearchQuery: StateFlow<String> = _originSearchQuery.asStateFlow()

    private val _originSearchResults = MutableStateFlow<List<Airport>>(emptyList())
    val originSearchResults: StateFlow<List<Airport>> = _originSearchResults.asStateFlow()

    // World Map Data
    val mapPaths = MutableStateFlow<List<com.example.focusflight.ui.map.CountryPath>>(emptyList())
    val visitedCountries = MutableStateFlow<Set<String>>(emptySet())
    val countryToContinent = MutableStateFlow<Map<String, String>>(emptyMap())
    val completedContinents = MutableStateFlow<Set<String>>(emptySet())

    init {
        // STORY (default): origin is the existing origin-locked behavior, untouched.
        // FREE: origin starts unset - the screen shows the origin picker until selectOrigin()
        // is called, instead of ever reading currentAirport.
        // CHALLENGE: origin is read-only context from that Route challenge's own stored position
        // pointer (docs/design/challenges.md#persistence--route-scoping) - mirrors Story Mode's
        // origin-lock, just pointed at a different value, so FlightSearchScreen's existing
        // `mode == FlightMode.FREE && originAirport == null` picker-gate is never true here.
        when (mode) {
            FlightMode.STORY -> loadOrigin()
            FlightMode.FREE -> observeOriginSearch()
            FlightMode.CHALLENGE -> loadChallengeOrigin()
        }
        loadMapData()
    }

    private fun loadChallengeOrigin() {
        viewModelScope.launch(Dispatchers.IO) {
            val positionIata = challengeId?.let { challengeRepository.getChallenge(it) }?.positionIata
            if (positionIata != null) {
                _originAirport.value = airportRepository.getAirportByIata(positionIata)
                fetchRoutes()
            }
        }
    }

    private fun observeOriginSearch() {
        viewModelScope.launch {
            _originSearchQuery
                .debounce(300)
                .collectLatest { query ->
                    if (query.trim().length >= 2) {
                        _originSearchResults.value = withContext(Dispatchers.IO) {
                            airportRepository.searchAirports(query)
                        }
                    } else {
                        _originSearchResults.value = emptyList()
                    }
                }
        }
    }

    fun onOriginSearchQueryChanged(query: String) {
        _originSearchQuery.value = query
    }

    /** Free Mode only: the player's chosen origin, picked from [originSearchResults]. Kicks off
     *  the same [fetchRoutes] every origin (Story or Free) uses to populate the destination
     *  picker, so everything downstream of this point is identical between modes. */
    fun selectOrigin(airport: Airport) {
        _originAirport.value = airport
        _originSearchQuery.value = ""
        _originSearchResults.value = emptyList()
        fetchRoutes()
    }

    private fun loadMapData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Load SVG map paths
            mapPaths.value = com.example.focusflight.ui.map.WorldMapParser.parseWorldMap(context)

            // Get profile and calculate stats
            val profile = userRepository.getProfile()
            val homeIata = profile?.homeAirportIata

            flightLogRepository.getFlightHistoryFlow().collect { history ->
                val geography = airportRepository.getVisitedGeography(history, homeIata)
                visitedCountries.value = geography.visitedCountries
                countryToContinent.value = geography.countryToContinent
                completedContinents.value = geography.completedContinents
            }
        }
    }

    private fun loadOrigin() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseIata = preferencesRepository.getCurrentAirport()
            Log.d("FlightSearchViewModel", "loadOrigin: baseIata=$baseIata")
            if (baseIata != null) {
                val airport = airportRepository.getAirportByIata(baseIata)
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
            var fetched = airportRepository.getOutboundRoutes(
                originIata = origin.iataCode,
                searchQuery = "",
                sortBy = "Shortest"
            )
            // STORY only: silently rehoming the player to LHR when their locked origin has no
            // routes is a Story Mode convenience (their origin is a fixed value they didn't pick
            // this session, so a total dead-end needs a way out). It writes `currentAirport` as
            // a side effect, which must never happen for a FREE-tagged session (see the
            // isolation matrix in docs/design/mechanics.md) - a Free Mode player who deliberately
            // picked a routeless origin just sees the existing "No flights available" empty
            // state instead, same as picking a duration with no matching routes today.
            if (fetched.isEmpty() && mode == FlightMode.STORY) {
                val fallbackAirport = airportRepository.getAirportByIata("LHR")
                if (fallbackAirport != null) {
                    _originAirport.value = fallbackAirport
                    preferencesRepository.setCurrentAirport("LHR")
                    fetched = airportRepository.getOutboundRoutes(
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
    private val context: android.content.Context,
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val challengeRepository: ChallengeRepository,
    private val mode: FlightMode = FlightMode.STORY,
    private val challengeId: Int? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlightSearchViewModel::class.java)) {
            return FlightSearchViewModel(context, airportRepository, preferencesRepository, userRepository, flightLogRepository, challengeRepository, mode, challengeId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
