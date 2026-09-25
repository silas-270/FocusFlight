package com.silas270.blocktime.data.model

/**
 * A fixed itinerary a Route challenge can follow instead of picking its own path - the "predefined
 * route" submode of [ChallengeType.ROUTE].
 *
 * A normal Route challenge is two endpoints and any path between them, scored by the geometric
 * proxy in [ChallengeProgress.routeProgress]. That shape cannot express a *circuit*: with
 * start == end the proxy's `originToDest <= 0.0` guard reports 1f before a single leg is flown, and
 * even without the guard a pilot would satisfy "around the world" by never leaving the origin.
 *
 * So this scores by distance flown along a known itinerary instead. [waypoints] is an ordered list
 * of airports, each consecutive pair is one leg, and progress is the kilometres behind you over the
 * kilometres in the whole route - see [progressAt]. Completion is a leg count, never a coordinate
 * comparison, which is exactly what makes `waypoints.first() == waypoints.last()` a legitimate
 * itinerary rather than a degenerate one.
 *
 * Distance-weighted rather than leg-counted because legs are not interchangeable: on
 * [PredefinedRouteCatalog.AROUND_THE_WORLD], Hong Kong to Tokyo is 2,962 km and Tokyo to Los
 * Angeles is 8,772 km. Counting legs would pay both the same 12.5%, which is wrong in the direction
 * that matters - it makes the hardest leg of the route feel like the cheapest.
 *
 * The pilot never picks a destination under one of these: the next hop is already known, so the
 * booking flow skips Flight Search entirely (see `domain/NextLegResolver.kt`). That also means
 * every leg has to be a real route in `flights.db` - see `PredefinedRouteCatalogTest`, which is the
 * thing standing between an authoring typo and a challenge nobody can fly.
 *
 * Curated content only. There is deliberately no way for a player to author one of these:
 * `ChallengeRepository.startCustomRouteChallenge` keeps producing free-form routes, the same way
 * [ChallengeSetDefinition] is curated-only.
 */
data class PredefinedRoute(
    val id: String,
    /** IATA codes in flying order. At least three - two would just be a free-form route with the
     *  choosing taken away. */
    val waypoints: List<String>,
    /**
     * Each leg's real distance in km, in the same order as the legs - authored, not derived.
     *
     * Copied from `flights.db`, which `PredefinedRouteCatalogTest` re-checks leg by leg, so these
     * cannot silently drift from the distances the flights themselves are flown at. They live here
     * rather than being looked up because [progressAt] has to stay a pure function: it is what the
     * slot ring, the Hub card and the info modal all call while composing, and none of them can
     * reach the airports database from there.
     *
     * Empty is legal and means "score this route by leg count" - an itinerary is still authorable
     * without pulling every distance first, and the test says which numbers to paste in.
     */
    val legDistancesKm: List<Double> = emptyList()
) {
    val legCount: Int get() = waypoints.size - 1

    /** Whether [legDistancesKm] can actually carry the scoring - one usable distance per leg. */
    val hasDistances: Boolean
        get() = legDistancesKm.size == legCount && legCount > 0 && legDistancesKm.all { it > 0.0 }

    val totalDistanceKm: Double get() = legDistancesKm.sum()

    /** Kilometres covered once [legIndex] legs have been flown. */
    fun distanceFlownKm(legIndex: Int): Double =
        legDistancesKm.take(legIndex.coerceIn(0, legCount)).sum()

    /**
     * Progress after [legIndex] legs, 0f..1f - kilometres behind you over kilometres in the route,
     * falling back to legs flown over [legCount] for an itinerary authored without distances.
     *
     * Note both forms reach exactly 1f on the final leg and exactly 0f on the first, whatever the
     * shape of the route in between. A circuit is scored the same way as any other itinerary; there
     * is no special case, because the distance is measured *along the route* rather than between
     * two endpoints that happen to be the same airport.
     */
    fun progressAt(legIndex: Int): Float {
        if (legCount <= 0) return 0f
        val flown = legIndex.coerceIn(0, legCount)
        if (!hasDistances) return (flown.toFloat() / legCount).coerceIn(0f, 1f)
        val total = totalDistanceKm
        if (total <= 0.0) return (flown.toFloat() / legCount).coerceIn(0f, 1f)
        return (distanceFlownKm(flown) / total).toFloat().coerceIn(0f, 1f)
    }

    /** Where leg [leg] (0-based) departs from. Equals the challenge's `positionIata` while that
     *  leg is the one still to be flown. */
    fun originOf(leg: Int): String? = waypoints.getOrNull(leg)

    /** Where leg [leg] (0-based) lands - the only airport that advances the challenge. */
    fun destOf(leg: Int): String? = waypoints.getOrNull(leg + 1)

    /** Whether this itinerary returns to where it started. Not used for scoring (legs handle
     *  that on their own) - it is here for display, since "a circuit" is worth saying out loud. */
    val isCircuit: Boolean get() = waypoints.size > 1 && waypoints.first() == waypoints.last()
}

/**
 * The authored itineraries. Names/descriptions/icons are *not* here - they live on the
 * [CuratedChallengeTemplate] that references the route, so a route can be re-presented under a
 * different framing without duplicating its waypoints.
 *
 * Leg durations are the real route times from `flights.db`, unscaled. A 10-hour leg is intended:
 * a long-haul leg is meant to be paused and resumed across sessions (see [Challenge.pausedFlight]),
 * not shrunk to fit one sitting.
 */
object PredefinedRouteCatalog {

    val AROUND_THE_WORLD_80_DAYS = PredefinedRoute(
        id = "around_the_world_80_days",
        waypoints = listOf("LHR", "BOM", "HKG", "SFO", "LHR"),
        legDistancesKm = listOf(7220.0, 4280.0, 11144.0, 8639.0)
    )

    val RACE_OF_MERCY = PredefinedRoute(
        id = "race_of_mercy",
        waypoints = listOf("ANC", "FAI", "ANC", "OME"),
        legDistancesKm = listOf(420.0, 420.0, 866.0)
    )

    val SILK_ROAD = PredefinedRoute(
        id = "silk_road",
        waypoints = listOf("VCE", "IST", "SKD", "XIY", "PEK"),
        legDistancesKm = listOf(1407.0, 3221.0, 3726.0, 934.0)
    )

    val PACIFIC_RIM = PredefinedRoute(
        id = "pacific_rim",
        waypoints = listOf("SIN", "BKK", "HKG", "ICN", "NRT", "SIN"),
        legDistancesKm = listOf(1409.0, 1689.0, 2063.0, 1260.0, 5349.0)
    )

    val AROUND_THE_WORLD = AROUND_THE_WORLD_80_DAYS

    val ALL: List<PredefinedRoute> = listOf(
        AROUND_THE_WORLD_80_DAYS,
        RACE_OF_MERCY,
        SILK_ROAD,
        PACIFIC_RIM
    )

    fun find(id: String): PredefinedRoute? = ALL.find { it.id == id }
}

/** The predefined itinerary this challenge is following, or null if it is a free-form Route
 *  challenge (or not a Route challenge at all). The one place the submode is decided - every
 *  branch on "is this predefined" goes through here rather than testing the column directly. */
fun Challenge.predefinedRoute(): PredefinedRoute? =
    if (type != ChallengeType.ROUTE) null
    else predefinedRouteId?.let { PredefinedRouteCatalog.find(it) }
