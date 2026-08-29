package com.example.focusflight.ui.viewmodel.flightsearch

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.local.airport.AirportDataException
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.ChallengeRepository
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.data.repository.UserRepository
import com.example.focusflight.data.repository.PilotProgressRepository
import com.example.focusflight.domain.AirportSearchController
import com.example.focusflight.domain.resolveCurrentAirportIata
import com.example.focusflight.ui.components.airportpicker.resolveSuggestedAirports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchMode { TIME, AIRPORT }

class FlightSearchViewModel(
    private val context: android.content.Context,
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userRepository: UserRepository,
    private val challengeRepository: ChallengeRepository,
    private val pilotProgressRepository: PilotProgressRepository,
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
    // from a fixed origin) - shares AirportSearchController with OnboardingViewModel's home-
    // airport search since both search airports by free text, not by route.
    private val originSearch = AirportSearchController(airportRepository, viewModelScope)
    val originSearchQuery: StateFlow<String> = originSearch.query
    val originSearchResults: StateFlow<List<Airport>> = originSearch.results

    // Same FRA/LHR/BER/MUC tiles Onboarding shows before a search is typed, so the origin picker
    // looks identical to Onboarding's two-step "search -> confirm on map" layout.
    private val _originSuggestions = MutableStateFlow<List<Airport>>(emptyList())
    val originSuggestions: StateFlow<List<Airport>> = _originSuggestions.asStateFlow()

    // World Map Data. Private backing flows with read-only public views: these were public
    // MutableStateFlows, which meant any caller could write the visited-country set from outside
    // the ViewModel. Nothing did - but "who writes this value" has to be answerable from the type
    // before this state can be hoisted and cached, and with a public MutableStateFlow the honest
    // answer is "anyone".
    private val _mapPaths = MutableStateFlow<List<com.example.focusflight.ui.map.CountryPath>>(emptyList())
    val mapPaths: StateFlow<List<com.example.focusflight.ui.map.CountryPath>> = _mapPaths.asStateFlow()

    private val _visitedCountries = MutableStateFlow<Set<String>>(emptySet())
    val visitedCountries: StateFlow<Set<String>> = _visitedCountries.asStateFlow()

    private val _countryToContinent = MutableStateFlow<Map<String, String>>(emptyMap())
    val countryToContinent: StateFlow<Map<String, String>> = _countryToContinent.asStateFlow()

    private val _completedContinents = MutableStateFlow<Set<String>>(emptySet())
    val completedContinents: StateFlow<Set<String>> = _completedContinents.asStateFlow()

    /**
     * Set when Story Mode's origin-lock had to rescue the pilot from an origin with no outbound
     * routes at all (see [fetchRoutes]) - carries the IATA they were moved to. Exposed rather than
     * left as a silent side effect: this rewrites `currentAirport`, and a position change the
     * pilot never asked for should not be invisible.
     * Rendered as a short notice above the route map (see `FlightSearchScreen`). Set only for a
     * genuinely routeless origin - a *failed* route query leaves the pilot where they are and
     * never reaches this.
     */
    private val _originRehomedTo = MutableStateFlow<String?>(null)
    val originRehomedTo: StateFlow<String?> = _originRehomedTo.asStateFlow()

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
            FlightMode.FREE -> loadOriginSuggestions()
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

    fun onOriginSearchQueryChanged(query: String) {
        originSearch.onQueryChanged(query)
    }

    private fun loadOriginSuggestions() {
        viewModelScope.launch(Dispatchers.IO) {
            _originSuggestions.value = resolveSuggestedAirports(airportRepository)
        }
    }

    /** Free Mode only: the player's chosen origin, picked from [originSearchResults]. Kicks off
     *  the same [fetchRoutes] every origin (Story or Free) uses to populate the destination
     *  picker, so everything downstream of this point is identical between modes. */
    fun selectOrigin(airport: Airport) {
        _originAirport.value = airport
        originSearch.onQueryChanged("")
        originSearch.clearResults()
        fetchRoutes()
    }

    /**
     * The visited-country overlay now comes from the shared derivation rather than this screen
     * deriving its own copy - it was running the exact same geography scan the Passport ran, on
     * its own flight-history subscription, every time Flight Search opened. The SVG paths stay
     * local: they are a rendering input, already warmed at app start, and have no dependency on
     * the pilot's data.
     */
    private fun loadMapData() {
        viewModelScope.launch(Dispatchers.IO) {
            _mapPaths.value = com.example.focusflight.ui.map.WorldMapParser.parseWorldMap(context)
        }
        viewModelScope.launch {
            pilotProgressRepository.progress.collect { progress ->
                val geography = progress?.geography ?: return@collect
                _visitedCountries.value = geography.visitedCountries
                _countryToContinent.value = geography.countryToContinent
                _completedContinents.value = geography.completedContinents
            }
        }
    }

    private fun loadOrigin() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseIata = resolveCurrentAirportIata(preferencesRepository, userRepository)
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
        val filtered = _allRoutes.value.filter { it.durationMin in interval..(interval + 9) }
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
            // A failed query must never reach the rehome branch below. It used to return an empty
            // list on failure, indistinguishable from a genuinely routeless origin - so a transient
            // SQLite error silently teleported a Story Mode pilot to London and *persisted* it to
            // `currentAirport`. Now the failure is its own path: show the same empty state a
            // routeless origin shows, but change nothing.
            var fetched = try {
                airportRepository.getOutboundRoutes(
                    originIata = origin.iataCode,
                    searchQuery = "",
                    sortBy = "Shortest"
                )
            } catch (e: AirportDataException) {
                Log.e("FlightSearchViewModel", "fetchRoutes: route query failed for ${origin.iataCode}; leaving origin unchanged", e)
                _allRoutes.value = emptyList()
                _intervals.value = emptyList()
                _filteredRoutes.value = emptyList()
                selectRoute(null)
                return@launch
            }
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
                    Log.w(
                        "FlightSearchViewModel",
                        "Origin ${origin.iataCode} has no outbound routes; rehoming to LHR and rewriting currentAirport"
                    )
                    _originAirport.value = fallbackAirport
                    preferencesRepository.setCurrentAirport("LHR")
                    _originRehomedTo.value = "LHR"
                    fetched = try {
                        airportRepository.getOutboundRoutes(
                            originIata = "LHR",
                            searchQuery = "",
                            sortBy = "Shortest"
                        )
                    } catch (e: AirportDataException) {
                        Log.e("FlightSearchViewModel", "fetchRoutes: LHR fallback query failed", e)
                        emptyList()
                    }
                }
            }
            Log.d("FlightSearchViewModel", "fetchRoutes: fetched ${fetched.size} routes for origin ${_originAirport.value?.iataCode}")
            _allRoutes.value = fetched

            if (fetched.isNotEmpty()) {
                val shortest = fetched.first().durationMin
                val longest = fetched.maxOf { it.durationMin }
                val startInterval = (shortest / 10) * 10
                val endInterval = (longest / 10) * 10
                val generatedIntervals = (startInterval..endInterval step 10).filter { interval ->
                    fetched.any { it.durationMin in interval..(interval + 9) }
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
    private val challengeRepository: ChallengeRepository,
    private val pilotProgressRepository: PilotProgressRepository,
    private val mode: FlightMode = FlightMode.STORY,
    private val challengeId: Int? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlightSearchViewModel::class.java)) {
            return FlightSearchViewModel(context, airportRepository, preferencesRepository, userRepository, challengeRepository, pilotProgressRepository, mode, challengeId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
