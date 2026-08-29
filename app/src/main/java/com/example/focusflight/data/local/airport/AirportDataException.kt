package com.example.focusflight.data.local.airport

/**
 * A query against the bundled `flights.db` reference database genuinely failed — as distinct
 * from succeeding and finding nothing.
 *
 * Thrown by the three queries whose empty result a caller would otherwise *act* on:
 *
 * - [AirportRouteSqliteDataSource.getContinentCountryMap] and
 *   [AirportRouteSqliteDataSource.getCountriesForAirports], which feed
 *   `AirportRepository.getVisitedGeography` — the visited-country map, the continent breakdown,
 *   and every Geographic achievement's progress. A swallowed failure there returned an empty set,
 *   indistinguishable from "this pilot has visited nothing", so a failed read rendered a wiped
 *   passport and reset every geographic achievement to 0/N with no error anywhere.
 * - [AirportRouteSqliteDataSource.getOutboundRoutes], where an empty result is a perfectly
 *   ordinary answer but not an inert one: Story Mode responds to a routeless origin by rehoming
 *   the pilot to LHR and persisting that to `currentAirport`. A swallowed failure could therefore
 *   move someone out of their airport, permanently, because a query briefly broke.
 *
 * That distinction becomes load-bearing once these results are cached: caching an empty set
 * produced by a failure would pin the wrong answer for the rest of the process lifetime.
 * A throw can't be cached by accident; an empty set can.
 *
 * The remaining per-item lookups ([AirportRouteSqliteDataSource.searchAirports],
 * [AirportRouteSqliteDataSource.getAirportByIata], [AirportRouteSqliteDataSource.getRunwaysForAirport])
 * deliberately keep their existing lenient behaviour of logging and returning empty/null: for
 * those, "found nothing" is an ordinary answer their callers already handle, no caller writes
 * anything in response to it, and none of them is cached as a global truth.
 */
class AirportDataException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
