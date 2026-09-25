package com.silas270.blocktime.data.repository

import com.silas270.blocktime.data.local.airport.AirportRouteSqliteDataSource
import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.data.model.FlightRoute
import com.silas270.blocktime.data.model.Runway

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

    override fun findRoute(originIata: String, destIata: String): FlightRoute? = dataSource.findRoute(originIata, destIata)

    override fun isInRouteNetwork(iataCode: String): Boolean = dataSource.isInRouteNetwork(iataCode)

    override fun nearestNetworkAirport(lat: Double, lon: Double): Airport? = dataSource.nearestNetworkAirport(lat, lon)
}
