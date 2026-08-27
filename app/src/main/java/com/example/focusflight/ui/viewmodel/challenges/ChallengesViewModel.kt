package com.example.focusflight.ui.viewmodel.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.repository.AchievementsRepository
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.ChallengeRepository
import com.example.focusflight.data.repository.StartChallengeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the Challenges screen (docs/design/challenges.md#entry--management-surface): the three
 * active-challenge slots, the completed-challenges log beneath them, the Achievements tab's
 * still-unearned list, and the start/abandon/custom-create flows. One instance is created per
 * composition of that screen - cheap, since it holds no flight/engine state.
 */
@OptIn(FlowPreview::class)
class ChallengesViewModel(
    private val challengeRepository: ChallengeRepository,
    private val airportRepository: AirportRepository,
    private val achievementsRepository: AchievementsRepository
) : ViewModel() {

    /** Active challenges (cap of 3), reactively updated - drives the three slots directly.
     *  `WhileSubscribed` rather than `Eagerly` since this is only ever collected while the
     *  Challenges screen is actually open. */
    val activeChallenges: StateFlow<List<Challenge>> =
        challengeRepository.listActiveChallengesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Completed challenges, newest-first (the DAO orders by `completed_at DESC`) - the logbook-
     *  style list below the slots. Refreshed off [activeChallenges] rather than its own Flow:
     *  a challenge can only ever reach this list by leaving the active one, so that emission is
     *  an exact signal, not an approximation. */
    private val _completedChallenges = MutableStateFlow<List<Challenge>>(emptyList())
    val completedChallenges: StateFlow<List<Challenge>> = _completedChallenges.asStateFlow()

    /** Still-unearned achievements, flat and ungrouped (no category headers by design), ordered
     *  closest-to-done first so the next reachable goal is always on top. Earned ones are
     *  deliberately absent - they live on the Passport as badges. */
    private val _unfinishedAchievements = MutableStateFlow<List<AchievementStatus>>(emptyList())
    val unfinishedAchievements: StateFlow<List<AchievementStatus>> = _unfinishedAchievements.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Re-read both derived lists whenever the active set changes - covers starting,
            // abandoning, and completing a challenge without a second subscription. On IO because
            // the achievement board's geography derivation reaches the airport SQLite DB.
            activeChallenges.collect { refreshDerivedLists() }
        }
    }

    private suspend fun refreshDerivedLists() {
        _completedChallenges.value = challengeRepository.listCompletedChallenges()
        val board = achievementsRepository.loadBoard()
        _unfinishedAchievements.value = (board.geographic + board.distance + board.behavioral)
            .filterNot { it.isUnlocked }
            .sortedByDescending { it.progress }
    }

    /** Result of the most recent start attempt (curated or custom) - surfaced once (e.g. a
     *  [StartChallengeResult.CapReached] message) then cleared via [clearStartResult] so it
     *  doesn't reappear on an unrelated recomposition. Null means "nothing to show". */
    private val _startResult = MutableStateFlow<StartChallengeResult?>(null)
    val startResult: StateFlow<StartChallengeResult?> = _startResult.asStateFlow()

    // ── Custom Route creation: origin/destination airport search ────────────────────────
    // Structurally identical to FlightSearchViewModel's Free-Mode origin picker (debounced
    // search over AirportRepository.searchAirports()) - reused here twice, once per endpoint,
    // since a custom Route challenge needs both ends picked rather than one fixed + one browsed.
    private val _originQuery = MutableStateFlow("")
    val originQuery: StateFlow<String> = _originQuery.asStateFlow()
    private val _originResults = MutableStateFlow<List<Airport>>(emptyList())
    val originResults: StateFlow<List<Airport>> = _originResults.asStateFlow()

    private val _destQuery = MutableStateFlow("")
    val destQuery: StateFlow<String> = _destQuery.asStateFlow()
    private val _destResults = MutableStateFlow<List<Airport>>(emptyList())
    val destResults: StateFlow<List<Airport>> = _destResults.asStateFlow()

    init {
        observeSearch(_originQuery, _originResults)
        observeSearch(_destQuery, _destResults)
    }

    private fun observeSearch(query: MutableStateFlow<String>, results: MutableStateFlow<List<Airport>>) {
        viewModelScope.launch {
            query.debounce(300).collectLatest { q ->
                results.value = if (q.trim().length >= 2) {
                    withContext(Dispatchers.IO) { airportRepository.searchAirports(q) }
                } else {
                    emptyList()
                }
            }
        }
    }

    fun onOriginQueryChanged(query: String) { _originQuery.value = query }
    fun onDestQueryChanged(query: String) { _destQuery.value = query }

    fun clearRouteSearch() {
        _originQuery.value = ""
        _originResults.value = emptyList()
        _destQuery.value = ""
        _destResults.value = emptyList()
    }

    fun startCurated(catalogId: String) {
        viewModelScope.launch {
            _startResult.value = challengeRepository.startCuratedChallenge(catalogId)
        }
    }

    /** [origin]/[dest] named from city names, per challenges.md's "Where it comes from" for Route
     *  ("Stuttgart → Beijing," not "STR → PEK"). */
    fun startCustomRoute(origin: Airport, dest: Airport) {
        val name = "${origin.municipality} → ${dest.municipality}"
        viewModelScope.launch {
            _startResult.value = challengeRepository.startCustomRouteChallenge(origin.iataCode, dest.iataCode, name)
            clearRouteSearch()
        }
    }

    fun startCustomDistance(targetKm: Double) {
        val name = "Custom Distance - ${formatKm(targetKm)}"
        viewModelScope.launch {
            _startResult.value = challengeRepository.startCustomDistanceChallenge(targetKm, name)
        }
    }

    fun abandon(id: Int) {
        viewModelScope.launch { challengeRepository.abandonChallenge(id) }
    }

    fun clearStartResult() {
        _startResult.value = null
    }
}

/** "10,000 km" style formatting shared by the custom-distance naming above and the create form. */
fun formatKm(km: Double): String {
    val rounded = km.toLong()
    return "${String.format(java.util.Locale.US, "%,d", rounded)} km"
}

class ChallengesViewModelFactory(
    private val challengeRepository: ChallengeRepository,
    private val airportRepository: AirportRepository,
    private val achievementsRepository: AchievementsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChallengesViewModel::class.java)) {
            return ChallengesViewModel(challengeRepository, airportRepository, achievementsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
