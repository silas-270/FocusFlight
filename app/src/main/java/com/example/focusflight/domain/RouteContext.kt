package com.example.focusflight.domain

import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.local.airport.AirportDataException
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
    // A missing route degrades gracefully everywhere downstream (CheckIn shows the ticket without
    // a distance, InFlight logs the flight with distanceKm = 0.0), so a failed lookup is treated
    // the same as "no such route" rather than propagating. Both callers resolve this inside a
    // plain `viewModelScope.launch`, where an escaping throw would be uncaught and take the
    // process down - and neither has anything better to do with the failure than carry on.
    val route = if (origin != null && dest != null) {
        try {
            // findRoute, not the bookable list: this flight is already booked, and its destination
            // may since have been hidden from new bookings (see AirportRepository.findRoute).
            airportRepository.findRoute(origin.iataCode, destIata)
        } catch (e: AirportDataException) {
            android.util.Log.e("RouteContext", "Route lookup failed for $originIata->$destIata", e)
            null
        }
    } else {
        null
    }
    return RouteContext(origin, dest, route)
}
