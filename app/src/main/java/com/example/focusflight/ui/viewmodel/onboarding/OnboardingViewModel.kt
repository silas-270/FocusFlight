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
import kotlinx.coroutines.withContext

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

    /**
     * Commits the chosen home airport and finishes onboarding. Returns false if nothing was
     * selected or the profile could not be written, in which case the pilot stays on this screen
     * and can simply tap again.
     *
     * The write order here is load-bearing. This used to mark onboarding complete *synchronously*
     * and then create the Room profile in a fire-and-forget coroutine whose failure was only
     * logged - so a failed profile insert left the app permanently "onboarded" with no
     * `user_profile` row at all. Every repository resolves the user through
     * `UserProfileDao.requireProfileId()`, which throws when that row is missing - so the
     * Challenges screen would crash on open, forever, with no way back to onboarding to repair
     * it.
     *
     * Now the profile is written first and awaited, and `onboarding_completed` is the very last
     * thing set - so the only state that can survive a failure is "not yet onboarded", which is
     * both true and recoverable.
     *
     * The home airport goes to Room only. It is no longer also written to `SharedPreferences`;
     * see [com.example.focusflight.domain.resolveHomeAirportIata] for why that duplication had to
     * end.
     */
    suspend fun saveHomeAirport(): Boolean {
        val airport = _selectedAirport.value ?: return false

        val profileWritten = withContext(Dispatchers.IO) {
            try {
                // Idempotent on retry: a previous attempt that got as far as inserting the row but
                // failed afterwards must not leave a second profile behind, since getProfile()
                // takes `LIMIT 1` and would then silently pick whichever came first.
                val existing = userRepository.getProfile()
                if (existing == null) {
                    userRepository.createProfile(
                        com.example.focusflight.data.model.UserProfile.generateRandomName(),
                        airport.iataCode
                    )
                } else {
                    userRepository.updateHomeAirport(airport.iataCode)
                }
                true
            } catch (e: Exception) {
                android.util.Log.e("OnboardingViewModel", "Error creating user profile", e)
                false
            }
        }
        if (!profileWritten) return false

        preferencesRepository.setCurrentAirport(airport.iataCode)

        // docs/modes.md: seed "last home base changed" to 31 days in the past so the
        // very first change-home-base call is immediately eligible through the same 30-day
        // cooldown check every later change uses - no separate "grace change" code path. See
        // com.example.focusflight.data.model.HomeBaseCooldown.seedChangeHomeBaseTimestamp.
        preferencesRepository.setLastHomeBaseChangedAt(
            com.example.focusflight.data.model.HomeBaseCooldown.seedChangeHomeBaseTimestamp(System.currentTimeMillis())
        )

        // Last, and only once everything above succeeded.
        preferencesRepository.setOnboardingCompleted(true)
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
