package com.example.focusflight.ui.viewmodel.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ContinentStats
import com.example.focusflight.data.model.FlightHighlights
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightSortOrder
import com.example.focusflight.data.model.HomeBaseCooldown
import com.example.focusflight.data.repository.AchievementsRepository
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.ChallengeRepository
import com.example.focusflight.data.repository.FlightLogRepository
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AccountUiState(
    // Profile Data
    val username: String = "",
    val userCode: String = "",
    val homeAirportIata: String = "",
    val joinDateFormatted: String = "",
    
    // Passport/Stats Data
    val totalFlights: Int = 0,
    val totalMinutes: Int = 0,
    val airportsVisited: Int = 0,
    
    // Flight History (used for map paths/stats)
    val flightHistory: List<FlightLog> = emptyList(),
    
    // Map Data
    val allVisitedCountries: Set<String> = emptySet(),
    val continentStats: List<ContinentStats> = emptyList(),
    val completedContinents: Set<String> = emptySet(),
    val countryToContinent: Map<String, String> = emptyMap(),
    val mapPaths: List<com.example.focusflight.ui.map.CountryPath> = emptyList(),
    
    // Highlights Card Data
    val highlights: FlightHighlights = FlightHighlights(),
    val sortOrder: FlightSortOrder = FlightSortOrder.DATE_DESC,

    // Achievements Data (docs/design/achievements.md) - computed reactively from flightHistory/
    // geography above, not persisted (see AchievementProgress's doc comment). Story Mode only,
    // except completedChallenges which is achievements.md's one confirmed cross-mode exception.
    val geographicAchievements: List<AchievementStatus> = emptyList(),
    val distanceAchievements: List<AchievementStatus> = emptyList(),
    val behavioralAchievements: List<AchievementStatus> = emptyList(),
    val completedChallenges: List<Challenge> = emptyList(),

    // Home base + return (docs/design/story-mode.md) - the two cooldown-gated actions, both
    // computed once per loadData()/action call rather than ticking live every second; see
    // AccountViewModel's doc comment on refreshHomeBaseCooldowns() for why that's an acceptable
    // simplification here.
    val currentAirportIata: String = "",
    val returnHomeEligible: Boolean = true,
    val returnHomeRemainingMillis: Long = 0L,
    val changeHomeBaseEligible: Boolean = true,
    val changeHomeBaseRemainingMillis: Long = 0L,

    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AccountViewModel(
    private val context: android.content.Context,
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val airportRepository: AirportRepository,
    private val challengeRepository: ChallengeRepository,
    private val preferencesRepository: PreferencesRepository,
    private val achievementsRepository: AchievementsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    // ── Change-home-base airport picker (docs/design/story-mode.md) ─────────────────────
    // Structurally identical to FlightSearchViewModel's Free Mode origin picker
    // (observeOriginSearch/onOriginSearchQueryChanged/selectOrigin) and OnboardingViewModel's
    // debounced airport search - the third call site of the same "search any airport by free
    // text" pattern, reusing OriginSearchPanel as the UI per the phase brief rather than building
    // a new picker component.
    private val _homeBaseSearchQuery = MutableStateFlow("")
    val homeBaseSearchQuery: StateFlow<String> = _homeBaseSearchQuery.asStateFlow()

    private val _homeBaseSearchResults = MutableStateFlow<List<Airport>>(emptyList())
    val homeBaseSearchResults: StateFlow<List<Airport>> = _homeBaseSearchResults.asStateFlow()

    fun onHomeBaseSearchQueryChanged(query: String) {
        _homeBaseSearchQuery.value = query
    }

    private fun clearHomeBaseSearch() {
        _homeBaseSearchQuery.value = ""
        _homeBaseSearchResults.value = emptyList()
    }

    private val dateFormat = SimpleDateFormat("MMM yyyy", Locale.US)

    private val _sortOrder = MutableStateFlow(FlightSortOrder.DATE_DESC)
    val sortOrder: StateFlow<FlightSortOrder> = _sortOrder.asStateFlow()

    // Expose Paged Data Flow to UI
    val pagedFlights: Flow<PagingData<FlightLog>> = _sortOrder.flatMapLatest { sort ->
        Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { flightLogRepository.getFlightsPagingSource(sort) }
        ).flow
    }.cachedIn(viewModelScope)

    init {
        loadData()
        refreshHomeBaseCooldowns()
        // Off the UI thread, well before the user can scroll a fast fling down to the
        // logbook (see PaperGrainTexture's doc comment).
        viewModelScope.launch(Dispatchers.Default) {
            com.example.focusflight.ui.screens.account.PaperGrainTexture.warm()
        }
        viewModelScope.launch {
            _homeBaseSearchQuery
                .debounce(300)
                .collectLatest { query ->
                    _homeBaseSearchResults.value = if (query.trim().length >= 2) {
                        withContext(Dispatchers.IO) { airportRepository.searchAirports(query) }
                    } else {
                        emptyList()
                    }
                }
        }
    }

    /**
     * Recomputes both home-base cooldowns' eligibility/remaining-time against "now". Called once
     * at load and again right after [returnHome]/[changeHomeBase] so the UI reflects the new
     * cooldown immediately post-action. Deliberately *not* re-evaluated on a live ticking timer -
     * nothing in docs/design/story-mode.md calls for a second-by-second countdown, and the Account
     * screen is realistically reopened (recreating this ViewModel) long before a multi-day
     * cooldown display would visibly go stale.
     */
    private fun refreshHomeBaseCooldowns() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val lastReturnHome = preferencesRepository.getLastReturnHomeAt()
            val lastHomeBaseChanged = preferencesRepository.getLastHomeBaseChangedAt()
            _uiState.update { state ->
                state.copy(
                    currentAirportIata = preferencesRepository.getCurrentAirport().orEmpty(),
                    returnHomeEligible = HomeBaseCooldown.isEligible(now, lastReturnHome, HomeBaseCooldown.RETURN_HOME_COOLDOWN_DAYS),
                    returnHomeRemainingMillis = HomeBaseCooldown.remainingMillis(now, lastReturnHome, HomeBaseCooldown.RETURN_HOME_COOLDOWN_DAYS),
                    changeHomeBaseEligible = HomeBaseCooldown.isEligible(now, lastHomeBaseChanged, HomeBaseCooldown.CHANGE_HOME_BASE_COOLDOWN_DAYS),
                    changeHomeBaseRemainingMillis = HomeBaseCooldown.remainingMillis(now, lastHomeBaseChanged, HomeBaseCooldown.CHANGE_HOME_BASE_COOLDOWN_DAYS)
                )
            }
        }
    }

    /**
     * Return-home teleport (docs/design/story-mode.md) - a direct state mutation, deliberately
     * NOT routed through Screen.FlightSearch/CheckIn/InFlight: no booking flow, no timer/session,
     * no FlightLog row, no FlightMode tag. Just an instant cut of `currentAirport` to the home
     * base, gated by the 7-day cooldown checked again here (not just at the UI-disabled-state
     * layer) so a stale/racy UI state can't bypass it.
     */
    fun returnHome() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val lastReturnHome = preferencesRepository.getLastReturnHomeAt()
            if (!HomeBaseCooldown.isEligible(now, lastReturnHome, HomeBaseCooldown.RETURN_HOME_COOLDOWN_DAYS)) return@launch
            val homeIata = preferencesRepository.getHomeAirport() ?: return@launch

            preferencesRepository.setCurrentAirport(homeIata)
            preferencesRepository.setLastReturnHomeAt(now)
            refreshHomeBaseCooldowns()
        }
    }

    /**
     * Change-home-base (docs/design/story-mode.md), gated by its own separate 30-day cooldown -
     * never the same clock as [returnHome]'s 7-day one. Updates both stores that carry the home
     * airport today, the same pair `OnboardingViewModel.saveHomeAirport()` writes at onboarding:
     * `PreferencesRepository` (origin-lock/return-home read from here) and the Room `UserProfile`
     * row (the Passport header's display value, via `userRepository.updateHomeAirport`).
     */
    fun changeHomeBase(airport: Airport) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val lastChanged = preferencesRepository.getLastHomeBaseChangedAt()
            if (!HomeBaseCooldown.isEligible(now, lastChanged, HomeBaseCooldown.CHANGE_HOME_BASE_COOLDOWN_DAYS)) return@launch

            preferencesRepository.setHomeAirport(airport.iataCode)
            preferencesRepository.setLastHomeBaseChangedAt(now)
            try {
                userRepository.updateHomeAirport(airport.iataCode)
            } catch (e: Exception) {
                android.util.Log.e("AccountViewModel", "Failed to update home airport", e)
            }
            clearHomeBaseSearch()
            refreshHomeBaseCooldowns()
        }
    }

    private fun loadData() {
        // Collect profile data
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.getProfileFlow().collect { profile ->
                if (profile != null) {
                    val formattedDate = "Joined " + dateFormat.format(Date(profile.createdAt))
                    _uiState.update { state ->
                        state.copy(
                            username = profile.username,
                            userCode = profile.userCode.removePrefix("#").removePrefix("@"),
                            homeAirportIata = profile.homeAirportIata,
                            joinDateFormatted = formattedDate
                        )
                    }
                }
            }
        }

        // Collect flight history and calculate map stats + highlights
        viewModelScope.launch(Dispatchers.IO) {
            // Load SVG map paths once
            val mapPaths = com.example.focusflight.ui.map.WorldMapParser.parseWorldMap(context)

            flightLogRepository.getFlightHistoryFlow().collect { history ->
                // 1. Get base aggregates from Room
                val stats = flightLogRepository.getFlightStats()
                
                // 2. Fetch specific Flight Highlights
                val highlights = flightLogRepository.getFlightHighlights()

                // 3. Fetch home airport for the visited-set calculation
                val profile = userRepository.getProfile()
                val homeIata = profile?.homeAirportIata

                // 4. Derive visited countries / continent breakdown (shared with FlightSearchViewModel)
                val geography = airportRepository.getVisitedGeography(history, homeIata)

                // 5. Achievements - still computed on-demand from the same STORY-scoped geography
                // + full flight history every other card above already uses. `history` is
                // unfiltered (every mode), but AchievementProgress.evaluateDistance/
                // evaluateBehavioral filter to STORY internally themselves, mirroring
                // getVisitedGeography's own filtering. Routed through AchievementsRepository
                // rather than AchievementProgress directly so each status also carries its
                // `unlockedAt` stamp, which the Passport's badges sort by.
                val achievements = achievementsRepository.evaluateBoard(geography, history)

                // 6. Completed-challenges log (achievements.md's cross-mode exception) - a flat
                // log, refetched alongside flightHistory since every landing (any mode) writes a
                // flight_log row, so this flow re-emitting is a reasonable-enough freshness signal
                // without a dedicated Flow from ChallengeRepository.
                val completedChallenges = challengeRepository.listCompletedChallenges()

                _uiState.update { state ->
                    state.copy(
                        totalFlights = stats.totalFlights,
                        totalMinutes = stats.totalMinutes,
                        airportsVisited = stats.airportsVisited,
                        flightHistory = history,
                        allVisitedCountries = geography.visitedCountries,
                        continentStats = geography.continentStats,
                        completedContinents = geography.completedContinents,
                        countryToContinent = geography.countryToContinent,
                        mapPaths = mapPaths,
                        highlights = highlights,
                        geographicAchievements = achievements.geographic,
                        distanceAchievements = achievements.distance,
                        behavioralAchievements = achievements.behavioral,
                        completedChallenges = completedChallenges,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setSortOrder(order: FlightSortOrder) {
        _sortOrder.value = order
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun updateUsername(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userRepository.updateUsername(newName.trim())
            } catch (e: Exception) {
                android.util.Log.e("AccountViewModel", "Failed to update username", e)
            }
        }
    }
}

class AccountViewModelFactory(
    private val context: android.content.Context,
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val airportRepository: AirportRepository,
    private val challengeRepository: ChallengeRepository,
    private val preferencesRepository: PreferencesRepository,
    private val achievementsRepository: AchievementsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccountViewModel::class.java)) {
            return AccountViewModel(context, userRepository, flightLogRepository, airportRepository, challengeRepository, preferencesRepository, achievementsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
