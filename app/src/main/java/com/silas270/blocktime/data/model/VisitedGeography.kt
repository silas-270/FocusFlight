package com.silas270.blocktime.data.model

/** Per-continent visited/missing country breakdown, derived from [VisitedGeography]. */
data class ContinentStats(
    val continentCode: String,
    val totalCountries: Int,
    val visitedCountries: Set<String>,
    val missingCountries: Set<String>,
    val isCompleted: Boolean
)

/**
 * Everything derived from a user's flight history + home airport that the map/passport UI
 * needs: which countries have been visited, how countries map to continents, per-continent
 * completion detail, and the set of fully-completed continents.
 *
 * Computed by [com.silas270.blocktime.data.repository.AirportRepository.getVisitedGeography]
 * so `FlightSearchViewModel` and `AccountViewModel` share one derivation instead of each
 * re-implementing it.
 */
data class VisitedGeography(
    val visitedCountries: Set<String>,
    val countryToContinent: Map<String, String>,
    val continentStats: List<ContinentStats>,
    val completedContinents: Set<String>,
    /** Continents with at least one visit: a visited country counts under it, or a visited airport
     *  physically sits on it (Honolulu reaches Oceania though the US counts under North America).
     *  Backs "Globetrotter". */
    val reachedContinents: Set<String> = continentStats.filter { it.visitedCountries.isNotEmpty() }
        .mapTo(HashSet()) { it.continentCode },
    /** Whether any Story flight connected the two hemispheres. A great circle between a point
     *  north of the equator and one south of it has to cross it, so the endpoints' latitudes
     *  decide it. Backs "Equator Crossing". */
    val crossedEquator: Boolean = false,
    /** Elevation of the highest airport a Story flight has *landed* at, in feet; `0.0` with no
     *  landings. Departures and the home base do not count. Backs "High Altitude Club". */
    val highestLandingElevationFt: Double = 0.0
)
