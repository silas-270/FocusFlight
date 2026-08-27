package com.example.focusflight.data.repository

import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.ContinentStats
import com.example.focusflight.data.model.FlightLog
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
     */
    fun getVisitedGeography(flightHistory: List<FlightLog>, homeAirportIata: String?): VisitedGeography {
        val visitedIatas = flightHistory.map { it.destIata }.toMutableList()
        if (homeAirportIata != null) {
            visitedIatas.add(homeAirportIata)
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
