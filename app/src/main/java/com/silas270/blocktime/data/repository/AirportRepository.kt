package com.silas270.blocktime.data.repository

import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.data.model.ContinentStats
import com.silas270.blocktime.data.model.FlightLog
import com.silas270.blocktime.data.model.FlightMode
import com.silas270.blocktime.data.model.FlightRoute
import com.silas270.blocktime.data.model.Runway
import com.silas270.blocktime.data.model.VisitedGeography

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
     * The single route from [originIata] to [destIata], if one exists - for a flight that is
     * already booked or in progress, so unlike [getOutboundRoutes] it is not limited to the main
     * network. Throws `AirportDataException` if the lookup fails. The default goes through
     * [getOutboundRoutes], which is all a test fake needs.
     */
    fun findRoute(originIata: String, destIata: String): FlightRoute? =
        getOutboundRoutes(originIata = originIata, searchQuery = destIata).find { it.destIata == destIata }

    /**
     * Whether [iataCode] is in the main route network: reachable from everywhere else, and able to
     * fly back out. Pickers and destination lists only offer these airports (see
     * `RouteNetwork.mainComponent`). Defaults to true for test fakes that model no network.
     */
    fun isInRouteNetwork(iataCode: String): Boolean = true

    /** The nearest large main-network airport to a point, or null - where Story Mode sends a pilot
     *  stranded at a dead end. */
    fun nearestNetworkAirport(lat: Double, lon: Double): Airport? = null

    /**
     * Derives [VisitedGeography] (visited countries, country-to-continent lookup, per-continent
     * stats, and completed continents) from a flight history + home airport. Shared by
     * `FlightSearchViewModel` and `AccountViewModel` so both compute this identically instead of
     * each re-deriving it from [getCountriesForAirports]/[getContinentCountryMap] independently.
     *
     * Only STORY-tagged flights count toward the visited-set (see the isolation matrix in
     * docs/modes.md) - filtered here, once, so both callers stay correct without
     * needing their own STORY-only filtering as FREE/CHALLENGE flights start getting logged.
     *
     * A Story flight's *origin* counts as visited as well as its destination. Under the origin
     * lock a Story flight can only depart from where the pilot really was, so its origin is always
     * a genuine visit - and without it, changing home base un-visited the old home's country for a
     * pilot who had only ever departed from there, taking earned badges with it.
     */
    fun getVisitedGeography(flightHistory: List<FlightLog>, homeAirportIata: String?): VisitedGeography {
        val storyFlights = flightHistory.filter { it.mode == FlightMode.STORY }
        val visitedIatas = storyFlights.flatMap { listOf(it.originIata, it.destIata) }
            .filter { it.isNotBlank() }
            .toMutableList()
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
        val airportByIata = uniqueVisitedIatas.mapNotNull { getAirportByIata(it) }.associateBy { it.iataCode }

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

        // A continent is *reached* if a visited country counts under it, or if a visited airport
        // physically sits on it: landing in Honolulu reaches Oceania even though the US counts
        // under North America, and Istanbul reaches Europe though Turkey counts under Asia.
        val reachedContinents = continentStats
            .filter { it.visitedCountries.isNotEmpty() }
            .mapTo(HashSet()) { it.continentCode }
        uniqueVisitedIatas.mapNotNullTo(reachedContinents) { iata ->
            airportByIata[iata]?.continent?.takeIf { it in worldMap }
        }

        val crossedEquator = storyFlights.any { flight ->
            val origin = airportByIata[flight.originIata]
            val dest = airportByIata[flight.destIata]
            origin != null && dest != null && origin.lat * dest.lat < 0.0
        }
        val highestLandingElevationFt = storyFlights
            .mapNotNull { airportByIata[it.destIata]?.elevationFt }
            .maxOrNull() ?: 0.0

        return VisitedGeography(
            visitedCountries = visitedCountries,
            countryToContinent = countryToContinent,
            continentStats = continentStats,
            completedContinents = completedContinents,
            reachedContinents = reachedContinents,
            crossedEquator = crossedEquator,
            highestLandingElevationFt = highestLandingElevationFt
        )
    }
}
