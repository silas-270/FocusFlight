package com.silas270.blocktime.domain

import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.util.countryDisplayName
import com.silas270.blocktime.util.normalizeForSearch
import com.silas270.blocktime.util.startsAnyWord

/**
 * Accent- and case-insensitive airport search over an in-memory list.
 *
 * The airports table is only a few thousand rows, so normalising every searchable field once and
 * scanning it per keystroke is cheaper than anything SQLite could do - and SQLite's `LIKE` cannot
 * fold "ü" to "u" at all, which is why "dusseldorf", "sao paulo" and "zurich" used to find
 * nothing. Pure (no Android dependency), so the ranking is unit-testable.
 *
 * Matches, best first:
 * 0. the exact IATA code
 * 1. the exact ICAO code (`ident`)
 * 2. an IATA code starting with the query
 * 3. the city (municipality) starting with the query, at any word
 * 4. the airport name starting with the query, at any word
 * 5. the query anywhere in the city or airport name
 * 6. the country name starting with the query ("germany" lists German airports)
 *
 * Ties go to bigger airports, then alphabetically by name, so "Doha" puts the international
 * airport ahead of an airfield that merely shares the city.
 */
class AirportSearchIndex(airports: List<Airport>) {

    private class Entry(
        val airport: Airport,
        val iata: String,
        val icao: String,
        val city: String,
        val name: String,
        val country: String,
        val sizeRank: Int
    )

    private val entries: List<Entry> = airports.map { airport ->
        Entry(
            airport = airport,
            iata = airport.iataCode.lowercase(),
            icao = airport.ident.lowercase(),
            city = normalizeForSearch(airport.municipality),
            name = normalizeForSearch(airport.name),
            country = normalizeForSearch(countryDisplayName(airport.isoCountry)),
            sizeRank = when (airport.type) {
                "large_airport" -> 0
                "medium_airport" -> 1
                else -> 2
            }
        )
    }

    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<Airport> {
        val q = normalizeForSearch(query)
        if (q.length < MIN_QUERY_LENGTH) return emptyList()
        // Local-language city names a pilot might type that the data spells differently (it has
        // "Munich", not "München"). Only ever *adds* candidates, so the plain query still ranks
        // exactly as it would without the alias.
        val aliases = CITY_ALIASES.filter { (alias, _) -> alias.startsWith(q) && q.length >= ALIAS_MIN_LENGTH }
            .map { it.second }

        return entries.asSequence()
            .mapNotNull { entry ->
                val tier = tierOf(entry, q)
                    ?: aliases.mapNotNull { tierOf(entry, it) }.minOrNull()
                tier?.let { entry to it }
            }
            .sortedWith(compareBy({ it.second }, { it.first.sizeRank }, { it.first.airport.name }))
            .take(limit)
            .map { it.first.airport }
            .toList()
    }

    private fun tierOf(e: Entry, q: String): Int? = when {
        e.iata == q -> 0
        e.icao == q -> 1
        e.iata.startsWith(q) -> 2
        startsAnyWord(e.city, q) -> 3
        startsAnyWord(e.name, q) -> 4
        e.city.contains(q) || e.name.contains(q) -> 5
        startsAnyWord(e.country, q) -> 6
        else -> null
    }

    companion object {
        const val DEFAULT_LIMIT = 15
        const val MIN_QUERY_LENGTH = 2
        private const val ALIAS_MIN_LENGTH = 4

        /** Endonym -> the spelling the airport data uses, both already normalised. */
        private val CITY_ALIASES: List<Pair<String, String>> = listOf(
            "munchen" to "munich",
            "koln" to "cologne",
            "wien" to "vienna",
            "praha" to "prague",
            "roma" to "rome",
            "milano" to "milan",
            "lisboa" to "lisbon",
            "moskva" to "moscow",
            "warszawa" to "warsaw",
            "kobenhavn" to "copenhagen",
            "nurnberg" to "nuremberg",
            "geneve" to "geneva",
            "genf" to "geneva",
            "bruxelles" to "brussels",
            "brussel" to "brussels",
            "athina" to "athens",
            "peking" to "beijing",
            "bombay" to "mumbai",
            "calcutta" to "kolkata",
            "madras" to "chennai",
            "saigon" to "ho chi minh"
        )
    }
}
