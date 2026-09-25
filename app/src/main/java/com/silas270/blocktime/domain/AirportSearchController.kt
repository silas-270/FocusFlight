package com.silas270.blocktime.domain

import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.data.repository.AirportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The free-text airport search pipeline shared by the onboarding home-airport search and Free
 * Mode's origin picker: 300ms debounce, a 2-character minimum, dispatched to `Dispatchers.IO`.
 * Previously duplicated verbatim in `OnboardingViewModel` and `FlightSearchViewModel` (the
 * latter's own comment called out reusing the former's pattern) - extracted here so both
 * ViewModels own one instance instead of the pipeline.
 */
@OptIn(FlowPreview::class)
class AirportSearchController(
    private val airportRepository: AirportRepository,
    scope: CoroutineScope
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Airport>>(emptyList())
    val results: StateFlow<List<Airport>> = _results.asStateFlow()

    init {
        scope.launch {
            _query
                .debounce(300)
                .collectLatest { query ->
                    _results.value = if (query.trim().length >= 2) {
                        withContext(Dispatchers.IO) { airportRepository.searchAirports(query) }
                    } else {
                        emptyList()
                    }
                }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    /** Immediately clears results without waiting for the debounce - e.g. right after a selection. */
    fun clearResults() {
        _results.value = emptyList()
    }
}
