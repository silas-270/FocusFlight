package com.example.focusflight.data.repository

import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.model.Runway

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
}
