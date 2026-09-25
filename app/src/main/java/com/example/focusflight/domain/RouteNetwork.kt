package com.example.focusflight.domain

import com.example.focusflight.data.model.Airport

/**
 * Which airports belong to the main route network, and which continent each country counts under.
 * Pure functions over reference data, so they are unit-testable without `flights.db`.
 */
object RouteNetwork {

    /**
     * The airports a pilot can both reach *and* fly on from: the strongly connected component of
     * the route graph that contains its busiest hub.
     *
     * An airport outside it is a trap for a pilot whose position only moves by flying (Story Mode's
     * origin lock, a Route challenge's pointer). The bundled data has ~665 IATA airports with no
     * routes at all, ~30 that can be flown *to* but have no departures, and a few small closed
     * clusters (ISC/LEQ, FIE/FOA/LWK, ...) that only connect to each other. Every airport inside
     * this set can reach every other one.
     *
     * Found as "reachable from the hub" ∩ "can reach the hub" - the SCC containing the hub -
     * rather than a full Tarjan pass. The busiest hub is in the giant component of any real
     * airline network, and two BFS passes are far simpler to read.
     */
    fun mainComponent(edges: Collection<Pair<String, String>>): Set<String> {
        val forward = HashMap<String, MutableList<String>>()
        val backward = HashMap<String, MutableList<String>>()
        for ((from, to) in edges) {
            if (from == to) continue
            forward.getOrPut(from) { mutableListOf() }.add(to)
            backward.getOrPut(to) { mutableListOf() }.add(from)
        }
        val hub = forward.maxByOrNull { it.value.size }?.key ?: return emptySet()
        val reachable = bfs(hub, forward)
        return bfs(hub, backward).filterTo(HashSet()) { it in reachable }
    }

    private fun bfs(start: String, adjacency: Map<String, List<String>>): Set<String> {
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            for (next in adjacency[queue.removeFirst()].orEmpty()) {
                if (seen.add(next)) queue.addLast(next)
            }
        }
        return seen
    }

    /**
     * Where a country is on the map when its airports disagree: Russia is the only one whose
     * majority (most of its airports are in Asia) contradicts the conventional grouping.
     */
    private val PRIMARY_CONTINENT_OVERRIDES = mapOf("RU" to "EU")

    /**
     * Continent -> the countries that count under it, each country under exactly **one**
     * continent, built only from [airports] (the caller passes the main network).
     *
     * The raw data files a few countries under two continents (Spain's Canary Islands are in
     * Africa, Honolulu is in Oceania, Istanbul is in Europe, ...). Listing each such country under
     * both made "Master of Africa" require Spain, and made "World Traveler"'s denominator (the sum
     * over continents) larger than the number of countries that exist, so it could never unlock.
     * A country now counts under the continent most of its airports are on, with
     * [PRIMARY_CONTINENT_OVERRIDES] for the one case where that reads wrong.
     */
    fun continentCountryMap(airports: Collection<Airport>): Map<String, Set<String>> {
        val countsByCountry = HashMap<String, MutableMap<String, Int>>()
        for (airport in airports) {
            if (airport.isoCountry.isBlank() || airport.continent.isBlank()) continue
            val counts = countsByCountry.getOrPut(airport.isoCountry) { HashMap() }
            counts[airport.continent] = (counts[airport.continent] ?: 0) + 1
        }
        val result = HashMap<String, MutableSet<String>>()
        for ((country, counts) in countsByCountry) {
            val continent = PRIMARY_CONTINENT_OVERRIDES[country]?.takeIf { it in counts }
                ?: counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .first().key
            result.getOrPut(continent) { HashSet() }.add(country)
        }
        return result
    }
}
