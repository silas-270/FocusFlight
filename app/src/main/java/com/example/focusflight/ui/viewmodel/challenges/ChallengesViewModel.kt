package com.example.focusflight.ui.viewmodel.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.Challenge
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
 * Backs the quest log (Phase 3b - docs/design/challenges.md#entry--management-surface): the
 * active-challenges list in the Hub's mode-select sheet, plus the "start new" browse/custom-create
 * flows and abandon. One instance is created per composition of the Hub screen (see
 * `CesiumGameActivity`'s `Screen.Hub` composable) - cheap, since it holds no flight/engine state.
 */
@OptIn(FlowPreview::class)
class ChallengesViewModel(
    private val challengeRepository: ChallengeRepository,
    private val airportRepository: AirportRepository
) : ViewModel() {

    /** Active challenges (cap of 3), reactively updated - drives the quest-log list directly.
     *  `WhileSubscribed` rather than `Eagerly` since this is only ever collected while the mode
     *  sheet is actually open. */
    val activeChallenges: StateFlow<List<Challenge>> =
        challengeRepository.listActiveChallengesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    private val airportRepository: AirportRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChallengesViewModel::class.java)) {
            return ChallengesViewModel(challengeRepository, airportRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
