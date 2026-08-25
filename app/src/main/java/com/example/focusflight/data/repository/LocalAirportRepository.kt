package com.example.focusflight.data.repository

import com.example.focusflight.data.local.airport.AirportRouteSqliteDataSource
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.model.Runway

class LocalAirportRepository(
    private val dataSource: AirportRouteSqliteDataSource
) : AirportRepository {

    override fun ensureDatabaseCopied() = dataSource.ensureDatabaseCopied()

    override fun searchAirports(query: String): List<Airport> = dataSource.searchAirports(query)

    override fun getAirportByIata(iataCode: String): Airport? = dataSource.getAirportByIata(iataCode)

    override fun getRunwaysForAirport(airportId: Int): List<Runway> = dataSource.getRunwaysForAirport(airportId)

    override fun getOutboundRoutes(
        originIata: String,
        searchQuery: String,
        sortBy: String
    ): List<FlightRoute> = dataSource.getOutboundRoutes(originIata, searchQuery, sortBy)

    override fun getContinentCountryMap(): Map<String, Set<String>> = dataSource.getContinentCountryMap()

    override fun getCountriesForAirports(iatas: List<String>): Set<String> = dataSource.getCountriesForAirports(iatas)
}
