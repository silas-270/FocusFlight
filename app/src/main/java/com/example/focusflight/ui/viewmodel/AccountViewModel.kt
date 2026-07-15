package com.example.focusflight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.FlightLogRepository
import com.example.focusflight.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    
    // Flight History
    val flightHistory: List<FlightLog> = emptyList(),
    
    // Map Data
    val allVisitedCountries: Set<String> = emptySet(),
    val continentStats: List<ContinentStats> = emptyList(),
    val completedContinents: Set<String> = emptySet(),
    val countryToContinent: Map<String, String> = emptyMap(),
    val mapPaths: List<com.example.focusflight.ui.map.CountryPath> = emptyList(),
    
    val isLoading: Boolean = true
)

class AccountViewModel(
    private val context: android.content.Context,
    private val userRepository: UserRepository,
    private val flightLogRepository: FlightLogRepository,
    private val databaseHelper: FlightDatabaseHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("MMM yyyy", Locale.US)

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
                            userCode = profile.userCode,
                            homeAirportIata = profile.homeAirportIata,
                            joinDateFormatted = formattedDate
                        )
                    }
                }
            }
        }

        // Collect flight history and calculate map stats
        viewModelScope.launch(Dispatchers.IO) {
            // Load SVG map paths once
            val mapPaths = com.example.focusflight.ui.map.WorldMapParser.parseWorldMap(context)

            flightLogRepository.getFlightHistoryFlow().collect { history ->
                // 1. Get base aggregates from Room
                val stats = flightLogRepository.getFlightStats()

                // 2. Extract unique destination IATAs and include home airport
                val profile = userRepository.getProfile()
                val homeIata = profile?.homeAirportIata
                
                val visitedIatas = history.map { it.destIata }.toMutableList()
                if (homeIata != null) {
                    visitedIatas.add(homeIata)
                }
                val uniqueVisitedIatas = visitedIatas.distinct()
                
                // 3. Translate IATAs to Countries (SQLite)
                val visitedCountries = databaseHelper.getCountriesForAirports(uniqueVisitedIatas)
                
                // 4. Get World Geography Data (SQLite)
                val worldMap = databaseHelper.getContinentCountryMap()
                
                // 5. Reverse map country to continent
                val countryToContinent = mutableMapOf<String, String>()
                worldMap.forEach { (continent, countries) ->
                    countries.forEach { country ->
                        countryToContinent[country] = continent
                    }
                }

                // 6. Calculate Continent Completion
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
                        isLoading = false
                    )
                }
            }
        }
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
