package com.example.focusflight.domain

import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.repository.AirportRepository

/** The origin/destination/route triad a flight screen needs to render - fetched together since
 *  the route lookup depends on both airports resolving first. */
data class RouteContext(
    val origin: Airport?,
    val dest: Airport?,
    val route: FlightRoute?
)

/**
 * Resolves [originIata]/[destIata] to their [Airport]s and finds the [FlightRoute] between them -
 * previously duplicated (fetch origin, fetch dest, then filter outbound routes to the destination)
 * in both `InFlightViewModel.loadFlightDetails` and `CheckInViewModel.loadFlightDetails`.
 */
suspend fun loadRouteContext(
    airportRepository: AirportRepository,
    originIata: String,
    destIata: String
): RouteContext {
    val origin = airportRepository.getAirportByIata(originIata)
    val dest = airportRepository.getAirportByIata(destIata)
    val route = if (origin != null && dest != null) {
        airportRepository.getOutboundRoutes(originIata = origin.iataCode, searchQuery = destIata)
            .find { it.destIata == destIata }
    } else {
        null
    }
    return RouteContext(origin, dest, route)
}
