package com.example.focusflight.ui.viewmodel.hub

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeStatus
import com.example.focusflight.data.model.ChallengeType
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.FlightStats
import com.example.focusflight.data.model.PausedFlight
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.ChallengeRepository
import com.example.focusflight.data.repository.FlightLogRepository
import com.example.focusflight.data.repository.PilotProgressRepository
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.data.repository.UserRepository
import com.example.focusflight.domain.resolveCurrentAirportIata
import com.example.focusflight.domain.resolveHomeAirportIata
import com.example.focusflight.engine.headless.CesiumHeadlessMapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class HubViewModel(
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val challengeRepository: ChallengeRepository,
    private val pilotProgressRepository: PilotProgressRepository,
    private val cacheDir: File
) : ViewModel() {

    private val _currentAirport = MutableStateFlow<Airport?>(null)
    val currentAirport: StateFlow<Airport?> = _currentAirport.asStateFlow()

    /** The Route challenge currently focused on the Hub (its position/progress drives
     *  [currentAirport] and the globe/progress-strip below), or null when the Hub is showing the
     *  normal story-mode airport. See PreferencesRepository.getFocusedRouteChallengeId's doc. */
    private val _focusedChallenge = MutableStateFlow<Challenge?>(null)
    val focusedChallenge: StateFlow<Challenge?> = _focusedChallenge.asStateFlow()

    private val _flightStats = MutableStateFlow(FlightStats())
    val flightStats: StateFlow<FlightStats> = _flightStats.asStateFlow()

    private val _recentFlights = MutableStateFlow<List<FlightLog>>(emptyList())
    val recentFlights: StateFlow<List<FlightLog>> = _recentFlights.asStateFlow()

    private val _routeMapPath = MutableStateFlow<String?>(null)
    val routeMapPath: StateFlow<String?> = _routeMapPath.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    private val _pausedFlight = MutableStateFlow<PausedFlight?>(null)
    val pausedFlight: StateFlow<PausedFlight?> = _pausedFlight.asStateFlow()

    private val _mapRenderError = MutableStateFlow<String?>(null)
    val mapRenderError: StateFlow<String?> = _mapRenderError.asStateFlow()

    private val mapRenderer = CesiumHeadlessMapRenderer(cacheDir)

    init {
        loadData()
        viewModelScope.launch {
            pilotProgressRepository.progress.collect { progress ->
                _flightStats.value = progress?.stats ?: FlightStats()
            }
        }
    }

    /** Re-runs [loadData]. `init` only fires once per ViewModel instance, but the Hub's nav
     *  back-stack entry (and this ViewModel with it) survives a `popBackStack()` from screens
     *  like Account/Passport - so anything that can change `currentAirport` while Hub isn't the
     *  active screen (e.g. story-mode.md's return-home teleport) needs an explicit re-fetch on
     *  return, not just a fresh load on first creation. See HubScreen's ON_START observer. */
    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val focused = resolveFocusedChallenge()
            _focusedChallenge.value = focused

            // A focused Route challenge takes over the displayed airport (its own position
            // pointer) - the Hub shows "where the challenge is", not the story-mode base - until
            // the player explicitly exits it. See exitFocusedChallenge().
            val baseIata = focused?.positionIata
                ?: resolveCurrentAirportIata(preferencesRepository, userRepository)
            if (baseIata != null) {
                val airport = airportRepository.getAirportByIata(baseIata)
                _currentAirport.value = airport

                // A focused challenge's paused flight (its own row, not the Story slot below)
                // drives Resume/Book here - so switching focus never shows a Resume button for a
                // flight that belongs to a different mode/challenge. See Challenge.pausedFlight's
                // doc. The Hub only ever shows Story Mode's own slot - Free Mode's paused flight
                // (a fully separate slot, see PreferencesRepository.pausedFreeFlightStore) has
                // its own Resume row on the Challenges screen instead.
                _pausedFlight.value = if (focused != null) {
                    focused.pausedFlight
                } else {
                    preferencesRepository.pausedFlightStore(FlightMode.STORY).get()
                }

                if (airport != null) {
                    generateRouteMap(airport)
                }
            }

            // Recent flights stay a Hub-local query - nothing else in the app shows them, so
            // there is nothing to share. The headline stats no longer come from here at all:
            // they arrive already computed from PilotProgressRepository (see init), instead of
            // being recomputed on every ON_START refresh of this screen.
            try {
                _recentFlights.value = flightLogRepository.getRecentFlights()
            } catch (e: Exception) {
                Log.e("HubViewModel", "Error loading recent flights", e)
            }
        }
    }

    private fun generateRouteMap(origin: Airport) {
        _mapRenderError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _isRendering.value = true
            try {
                val result = mapRenderer.renderRouteMapForAirport(airportRepository, origin, reuseCachedFile = true)
                when (result) {
                    is CesiumHeadlessMapRenderer.Result.Success -> _routeMapPath.value = result.path
                    is CesiumHeadlessMapRenderer.Result.Failure -> _mapRenderError.value = result.message
                }
            } finally {
                _isRendering.value = false
            }
        }
    }

    fun retryRenderMap() {
        val origin = _currentAirport.value
        if (origin != null) {
            generateRouteMap(origin)
        }
    }

    /** Reads the focused-challenge pref and validates it still points at something focusable -
     *  an ACTIVE Route challenge. Self-heals a stale pref (abandoned/completed elsewhere) by
     *  clearing it, so a leftover id never silently strands the Hub. */
    private suspend fun resolveFocusedChallenge(): Challenge? {
        val id = preferencesRepository.getFocusedRouteChallengeId() ?: return null
        val challenge = challengeRepository.getChallenge(id)
        val stillFocusable = challenge != null &&
            challenge.type == ChallengeType.ROUTE &&
            challenge.status == ChallengeStatus.ACTIVE
        if (!stillFocusable) {
            preferencesRepository.clearFocusedRouteChallengeId()
            return null
        }
        return challenge
    }

    /** Pauses the focused challenge: the Hub reverts to the story-mode airport, but the challenge
     *  itself is untouched - still ACTIVE, at its saved position - and can be refocused later. */
    fun exitFocusedChallenge() {
        preferencesRepository.clearFocusedRouteChallengeId()
        refresh()
    }
}

class HubViewModelFactory(
    private val airportRepository: AirportRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val challengeRepository: ChallengeRepository,
    private val pilotProgressRepository: PilotProgressRepository,
    private val cacheDir: File
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HubViewModel::class.java)) {
            return HubViewModel(airportRepository, preferencesRepository, userRepository, flightLogRepository, challengeRepository, pilotProgressRepository, cacheDir) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
