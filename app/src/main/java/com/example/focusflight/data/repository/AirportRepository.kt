package com.example.focusflight.data.repository

import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.ContinentStats
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.model.Runway
import com.example.focusflight.data.model.VisitedGeography

interface AirportRepository {
    fun ensureDatabaseCopied()
    fun searchAirports(query: String): List<Airport>
    fun getAirportByIata(iataCode: String): Airport?
    fun getRunwaysForAirport(airportId: Int): List<Runway>
    fun getOutboundRoutes(
        originIata: String,
        searchQuery: String = "",
        sortBy: String = ""
    ): List<FlightRoute>
    fun getContinentCountryMap(): Map<String, Set<String>>
    fun getCountriesForAirports(iatas: List<String>): Set<String>

    /**
     * Derives [VisitedGeography] (visited countries, country-to-continent lookup, per-continent
     * stats, and completed continents) from a flight history + home airport. Shared by
     * `FlightSearchViewModel` and `AccountViewModel` so both compute this identically instead of
     * each re-deriving it from [getCountriesForAirports]/[getContinentCountryMap] independently.
     *
     * Only STORY-tagged flights count toward the visited-set (see the isolation matrix in
     * docs/design/mechanics.md) - filtered here, once, so both callers stay correct without
     * needing their own STORY-only filtering as FREE/CHALLENGE flights start getting logged.
     */
    fun getVisitedGeography(flightHistory: List<FlightLog>, homeAirportIata: String?): VisitedGeography {
        val storyFlights = flightHistory.filter { it.mode == FlightMode.STORY }
        val visitedIatas = storyFlights.map { it.destIata }.toMutableList()
        // Blank is treated as "no home base", not as an airport code. Callers reach the home
        // airport by different routes - some through `domain.resolveHomeAirportIata` (which
        // already normalises blank to null), some by reading the profile field directly - and
        // without this they would disagree about what an empty field means. Normalising at the
        // one place that consumes the value keeps them consistent regardless of how they got it.
        val homeIata = homeAirportIata?.takeIf { it.isNotBlank() }
        if (homeIata != null) {
            visitedIatas.add(homeIata)
        }
        val uniqueVisitedIatas = visitedIatas.distinct()

        val visitedCountries = getCountriesForAirports(uniqueVisitedIatas)

        val worldMap = getContinentCountryMap()
        val countryToContinent = mutableMapOf<String, String>()
        worldMap.forEach { (continent, countries) ->
            countries.forEach { country ->
                countryToContinent[country] = continent
            }
        }

        val continentStats = worldMap.map { (continent, allCountriesInContinent) ->
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

        val completedContinents = continentStats.filter { it.isCompleted }.map { it.continentCode }.toSet()

        return VisitedGeography(
            visitedCountries = visitedCountries,
            countryToContinent = countryToContinent,
            continentStats = continentStats,
            completedContinents = completedContinents
        )
    }
}
