package com.example.focusflight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.FlightLogRepository
import com.example.focusflight.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FlightSortOrder(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    DISTANCE_DESC("Longest Distance"),
    DISTANCE_ASC("Shortest Distance"),
    DURATION_DESC("Longest Duration")
}

data class FlightHighlights(
    val longestFlight: FlightLog? = null,
    val mostVisitedIata: String? = null,
    val mostVisitedCount: Int = 0,
    val equatorRatio: Double = 0.0
)

data class ContinentStats(
    val continentCode: String,
    val totalCountries: Int,
    val visitedCountries: Set<String>,
    val missingCountries: Set<String>,
    val isCompleted: Boolean
)

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
    
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModel(
    private val context: android.content.Context,
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val databaseHelper: FlightDatabaseHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

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

                // 3. Extract unique destination IATAs and include home airport
                val profile = userRepository.getProfile()
                val homeIata = profile?.homeAirportIata
                
                val visitedIatas = history.map { it.destIata }.toMutableList()
                if (homeIata != null) {
                    visitedIatas.add(homeIata)
                }
                val uniqueVisitedIatas = visitedIatas.distinct()
                
                // 4. Translate IATAs to Countries (SQLite)
                val visitedCountries = databaseHelper.getCountriesForAirports(uniqueVisitedIatas)
                
                // 5. Get World Geography Data (SQLite)
                val worldMap = databaseHelper.getContinentCountryMap()
                
                // 6. Reverse map country to continent
                val countryToContinent = mutableMapOf<String, String>()
                worldMap.forEach { (continent, countries) ->
                    countries.forEach { country ->
                        countryToContinent[country] = continent
                    }
                }

                // 7. Calculate Continent Completion
                val continentStatsList = worldMap.map { (continent, allCountriesInContinent) ->
                    val visitedInContinent = allCountriesInContinent.intersect(visitedCountries)
                    val missingInContinent = allCountriesInContinent.subtract(visitedCountries)
                    
                    ContinentStats(
                        continentCode = continent,
                        totalCountries = allCountriesInContinent.size,
                        visitedCountries = visitedInContinent,
                        missingCountries = missingInContinent,
                        isCompleted = missingInContinent.isEmpty() && allCountriesInContinent.isNotEmpty()
                    )
                }.sortedBy { it.continentCode }

                val completedContinents = continentStatsList.filter { it.isCompleted }.map { it.continentCode }.toSet()

                _uiState.update { state ->
                    state.copy(
                        totalFlights = stats.totalFlights,
                        totalMinutes = stats.totalMinutes,
                        airportsVisited = stats.airportsVisited,
                        flightHistory = history,
                        allVisitedCountries = visitedCountries,
                        continentStats = continentStatsList,
                        completedContinents = completedContinents,
                        countryToContinent = countryToContinent,
                        mapPaths = mapPaths,
                        highlights = highlights,
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
    private val databaseHelper: FlightDatabaseHelper
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccountViewModel::class.java)) {
            return AccountViewModel(context, userRepository, flightLogRepository, databaseHelper) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
