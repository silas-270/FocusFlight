package com.example.focusflight.data.local.airport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.ChallengeProgress
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.model.Runway
import com.example.focusflight.domain.AirportSearchIndex
import com.example.focusflight.domain.RouteNetwork
import com.example.focusflight.util.normalizeForSearch
import java.io.FileOutputStream
import java.io.IOException

class AirportRouteSqliteDataSource(private val context: Context) {

    companion object {
        private const val TAG = "AirportRouteSqliteDataSource"
        private const val DATABASE_NAME = "flights.db"

        /**
         * No airliner covers ground faster than this over a whole block time (gate to gate), even
         * with a strong jet stream behind it. The data has routes that break it by an order of
         * magnitude - 797 km in 1 minute - so a route faster than this is a data error, not a
         * flight, and is left out rather than shown with a duration that isn't real.
         */
        private const val MAX_BLOCK_SPEED_KMH = 1_200

        /**
         * The routes worth offering. Drops the four self-routes (ACC->ACC etc., 0 km) and the
         * physically impossible durations described at [MAX_BLOCK_SPEED_KMH]. Short hops are kept:
         * a 5-minute, 66 km island hop is a real (short) focus session. Applied to both the route
         * lists and the network graph, so the two can never disagree about what exists.
         */
        private const val SANE_ROUTE =
            "r.origin_iata != r.dest_iata AND r.flight_time_min > 0 AND r.distance_km > 0 " +
                "AND r.distance_km * 60 <= r.flight_time_min * $MAX_BLOCK_SPEED_KMH"
    }

    private val dbPath = context.getDatabasePath(DATABASE_NAME)

    /**
     * Copies the bundled `flights.db` into the databases directory when it is missing *or* stale.
     *
     * Stale means the app was installed or updated since the last copy (tracked by the package's
     * `lastUpdateTime` in a sidecar stamp file). Checking only for existence used to leave every
     * upgraded install - and every backup restore - on whatever reference data it first copied,
     * so fixes to the asset never reached existing pilots.
     *
     * The copy goes to a temp file that is renamed into place only once complete, so a process
     * death mid-copy can never leave a truncated database behind that later launches would trust.
     * A failed refresh keeps the previous copy; only a failure with no usable copy at all throws.
     */
    @Synchronized
    fun ensureDatabaseCopied() {
        val stampFile = java.io.File(dbPath.parentFile, "$DATABASE_NAME.stamp")
        val installStamp = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        }.getOrDefault("")
        val isCurrent = dbPath.exists() && installStamp.isNotEmpty() &&
            runCatching { stampFile.readText() }.getOrNull() == installStamp
        if (isCurrent) return

        dbPath.parentFile?.mkdirs()
        val tmpFile = java.io.File(dbPath.parentFile, "$DATABASE_NAME.tmp")
        try {
            context.assets.open(DATABASE_NAME).use { inputStream ->
                FileOutputStream(tmpFile).use { outputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 8192)
                    outputStream.fd.sync()
                }
            }
            for (suffix in listOf("-journal", "-wal", "-shm")) {
                java.io.File(dbPath.path + suffix).delete()
            }
            if (!tmpFile.renameTo(dbPath)) throw IOException("Could not move $tmpFile into place")
            stampFile.writeText(installStamp)
            Log.d(TAG, "Database copied to ${dbPath.absolutePath}")
        } catch (e: IOException) {
            tmpFile.delete()
            Log.e(TAG, "Error copying database from assets", e)
            // An older complete copy is still better than no reference data at all.
            if (!dbPath.exists()) throw RuntimeException("Failed to copy database asset", e)
        }
    }

    /**
     * One long-lived read-only connection, opened on first use and deliberately never closed.
     *
     * This used to open and close the 4MB `flights.db` on *every single query* - and every airport
     * lookup, route fetch and runway lookup in the app goes through here, several per screen. The
     * file is a read-only bundled asset that no code path ever writes, so there is nothing to
     * flush and no reason to reopen it; `SQLiteDatabase` does its own internal locking, which
     * makes a shared read-only handle safe to use concurrently from the IO dispatcher.
     *
     * Held for the process lifetime rather than reference-counted: the alternative buys nothing
     * (the connection is wanted again within milliseconds on every screen) and reintroduces the
     * close/reopen cost this exists to remove.
     */
    @Volatile
    private var connection: SQLiteDatabase? = null

    private fun getReadableDatabase(): SQLiteDatabase {
        connection?.let { if (it.isOpen) return it }
        return synchronized(this) {
            connection?.takeIf { it.isOpen } ?: run {
                ensureDatabaseCopied()
                SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    .also { connection = it }
            }
        }
    }


    /**
     * Immutable reference data, so this is memoised for the process lifetime after the first
     * successful read. Assigned only on success. A failure throws (see [AirportDataException]) and
     * therefore cannot be memoised - pinning an empty map here would wipe the visited-country
     * denominator permanently.
     */
    @Volatile
    private var cachedContinentCountryMap: Map<String, Set<String>>? = null

    /**
     * Every airport with an IATA code, loaded once and held for the process lifetime.
     *
     * A few thousand small rows of immutable reference data. Holding them in memory turns
     * [getAirportByIata] - the single hottest lookup in the app (the Hub, check-in, in-flight,
     * challenge crediting and the landing pipeline all resolve codes through it) - into a map
     * read, and is what makes accent-insensitive search possible at all (see [searchAirports]).
     *
     * Assigned only on success, like every other cache here: a failed load throws
     * [AirportDataException] and the next call retries.
     */
    private class AirportIndex(val byIata: Map<String, Airport>) {
        val search = AirportSearchIndex(byIata.values.toList())
    }

    @Volatile
    private var airportIndex: AirportIndex? = null

    private fun airports(): AirportIndex {
        airportIndex?.let { return it }
        return synchronized(this) {
            airportIndex ?: loadAirports().also { airportIndex = it }
        }
    }

    private fun loadAirports(): AirportIndex {
        val byIata = HashMap<String, Airport>(4096)
        try {
            getReadableDatabase().rawQuery("SELECT * FROM airports WHERE iata_code != ''", null).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow("airport_id")
                val identCol = cursor.getColumnIndexOrThrow("ident")
                val iataCol = cursor.getColumnIndexOrThrow("iata_code")
                val nameCol = cursor.getColumnIndexOrThrow("name")
                val latCol = cursor.getColumnIndexOrThrow("lat")
                val lonCol = cursor.getColumnIndexOrThrow("lon")
                val elevCol = cursor.getColumnIndexOrThrow("elevation_ft")
                val contCol = cursor.getColumnIndexOrThrow("continent")
                val countryCol = cursor.getColumnIndexOrThrow("iso_country")
                val regionCol = cursor.getColumnIndexOrThrow("iso_region")
                val munCol = cursor.getColumnIndexOrThrow("municipality")
                val typeCol = cursor.getColumnIndexOrThrow("type")
                while (cursor.moveToNext()) {
                    val region = cursor.getString(regionCol) ?: ""
                    val airport = Airport(
                        id = cursor.getInt(idCol),
                        ident = cursor.getString(identCol) ?: "",
                        iataCode = cursor.getString(iataCol) ?: "",
                        name = cursor.getString(nameCol) ?: "",
                        lat = cursor.getDouble(latCol),
                        lon = cursor.getDouble(lonCol),
                        elevationFt = cursor.getDouble(elevCol),
                        continent = cursor.getString(contCol) ?: "",
                        isoCountry = countryOf(cursor.getString(countryCol), region),
                        isoRegion = region,
                        municipality = cursor.getString(munCol) ?: "",
                        type = cursor.getString(typeCol) ?: ""
                    )
                    byIata[airport.iataCode] = airport
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading airports", e)
            throw AirportDataException("Failed to load airports", e)
        }
        return AirportIndex(byIata)
    }

    /**
     * Namibia's ISO code is "NA", which the tool that built `flights.db` read as a missing value -
     * every Namibian airport has a blank `iso_country`. The region code ("NA-KH") still carries
     * it, so the country is recovered from there rather than leaving Namibia off the map and out
     * of every country count.
     */
    private fun countryOf(isoCountry: String?, isoRegion: String): String =
        isoCountry?.takeIf { it.isNotBlank() } ?: isoRegion.substringBefore('-', "").takeIf { it.length == 2 } ?: ""

    /**
     * The main route network - see [RouteNetwork.mainComponent] for what that means and why it
     * matters. Computed once from the (sane) routes and held for the process lifetime; assigned
     * only on success.
     */
    @Volatile
    private var cachedNetwork: Set<String>? = null

    private fun network(): Set<String> {
        cachedNetwork?.let { return it }
        return synchronized(this) {
            cachedNetwork ?: loadNetwork().also { cachedNetwork = it }
        }
    }

    private fun loadNetwork(): Set<String> {
        val edges = ArrayList<Pair<String, String>>(60_000)
        try {
            getReadableDatabase().rawQuery(
                "SELECT DISTINCT r.origin_iata, r.dest_iata FROM routes r WHERE $SANE_ROUTE", null
            ).use { cursor ->
                while (cursor.moveToNext()) edges.add(cursor.getString(0) to cursor.getString(1))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading route network", e)
            throw AirportDataException("Failed to load route network", e)
        }
        // Only airports that actually exist in the airports table - a route to an unknown code
        // could never be displayed or resolved anyway.
        val known = airports().byIata
        return RouteNetwork.mainComponent(edges).filterTo(HashSet()) { it in known }
    }

    /** Whether [iataCode] is in the main route network (see [RouteNetwork.mainComponent]). An
     *  unreadable network answers false, which only ever hides an airport - never moves anyone. */
    fun isInRouteNetwork(iataCode: String): Boolean =
        runCatching { iataCode in network() }.getOrDefault(false)

    /**
     * The closest large airport in the main network to ([lat], [lon]), or null if none can be
     * read. Where Story Mode sends a pilot whose current airport is a dead end - see
     * `FlightSearchViewModel.fetchRoutes`. Nearest rather than a fixed London: it keeps the pilot
     * in the part of the world they were already flying.
     */
    fun nearestNetworkAirport(lat: Double, lon: Double): Airport? = runCatching {
        val net = network()
        airports().byIata.values
            .filter { it.type == "large_airport" && it.iataCode in net }
            .minByOrNull { ChallengeProgress.haversineKm(lat, lon, it.lat, it.lon) }
    }.onFailure { Log.e(TAG, "Error finding nearest network airport", it) }.getOrNull()

    /**
     * Free-text airport search for every airport picker (onboarding, change home base, Free Mode
     * origin, custom Route challenges). Accent- and case-insensitive, and also matches ICAO codes
     * and country names - see [AirportSearchIndex] for the ranking.
     *
     * Only airports in the main route network are offered. Anything else is a place a pilot could
     * pick and then never fly out of (or never fly to): hundreds of IATA airports have no routes
     * at all, which used to put e.g. an old Doha airfield with no flights above Hamad.
     *
     * Stays lenient on failure (log + empty list) on purpose: a typo-ahead search that finds
     * nothing is an ordinary answer the picker already renders as "no results", so a failure is
     * no worse than a miss and nothing downstream caches it. See [AirportDataException].
     */
    fun searchAirports(query: String): List<Airport> {
        if (query.trim().length < 2) return emptyList()
        return try {
            val net = network()
            airports().search.search(query, limit = Int.MAX_VALUE)
                .asSequence()
                .filter { it.iataCode in net }
                .take(AirportSearchIndex.DEFAULT_LIMIT)
                .toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error searching airports", e)
            emptyList()
        }
    }

    // Lenient on failure (log + null) on purpose: every caller already has to handle an unknown
    // IATA code, so a null here lands in a branch that exists regardless, and the answer is per-
    // airport rather than a global truth anything caches. See [AirportDataException].
    fun getAirportByIata(iataCode: String): Airport? =
        try {
            airports().byIata[iataCode]
        } catch (e: Exception) {
            Log.e(TAG, "Error querying database by IATA", e)
            null
        }
    // Lenient on failure (log + empty list) on purpose: plenty of airports genuinely have no
    // runway rows in the reference data, so callers already treat "none" as normal and degrade to
    // a generic approach rather than showing an error. See [AirportDataException].
    fun getRunwaysForAirport(airportId: Int): List<Runway> {
        val runways = mutableListOf<Runway>()
        val db = getReadableDatabase()
        val sql = "SELECT * FROM runways WHERE airport_id = ?"
        try {
            db.rawQuery(sql, arrayOf(airportId.toString())).use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow("runway_id")
                    val airportIdCol = cursor.getColumnIndexOrThrow("airport_id")
                    val lengthCol = cursor.getColumnIndexOrThrow("length_ft")
                    val widthCol = cursor.getColumnIndexOrThrow("width_ft")
                    val leHeadingCol = cursor.getColumnIndexOrThrow("le_heading")
                    val heHeadingCol = cursor.getColumnIndexOrThrow("he_heading")
                    val leLatCol = cursor.getColumnIndexOrThrow("le_lat")
                    val leLonCol = cursor.getColumnIndexOrThrow("le_lon")
                    val heLatCol = cursor.getColumnIndexOrThrow("he_lat")
                    val heLonCol = cursor.getColumnIndexOrThrow("he_lon")
                    
                    do {
                        if (cursor.isNull(leLatCol) || cursor.isNull(leLonCol)) {
                            continue
                        }

                        val lengthFt = cursor.getFloat(lengthCol)
                        val widthFt = cursor.getFloat(widthCol)
                        val leHeading = cursor.getFloat(leHeadingCol)
                        val heHeading = cursor.getFloat(heHeadingCol)
                        val leLat = cursor.getDouble(leLatCol)
                        val leLon = cursor.getDouble(leLonCol)
                        val heLat = cursor.getDouble(heLatCol)
                        val heLon = cursor.getDouble(heLonCol)

                        runways.add(
                            Runway(
                                runwayId = cursor.getInt(idCol),
                                airportId = cursor.getInt(airportIdCol),
                                lengthFt = lengthFt,
                                widthFt = widthFt,
                                leHeading = leHeading,
                                heHeading = heHeading,
                                leLat = leLat,
                                leLon = leLon,
                                heLat = heLat,
                                heLon = heLon
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying runways", e)
        }
        return runways
    }




    /**
     * The routes a pilot can book from [originIata]: only sane routes (see [SANE_ROUTE]), one per
     * destination, and only to destinations in the main network - landing somewhere a pilot could
     * never fly out of again is not something to offer (see [RouteNetwork.mainComponent]).
     *
     * Throws on failure rather than returning empty, unlike the per-item lookups. A routeless
     * origin IS an ordinary answer here - but it is not an inert one: Story Mode reacts to it by
     * rehoming the pilot and *writing* `currentAirport`. A swallowed failure would therefore
     * teleport someone out of their airport because a query briefly broke, and persist it. Empty
     * has to mean empty wherever a caller acts on it. See [AirportDataException].
     */
    fun getOutboundRoutes(
        originIata: String,
        searchQuery: String = "",
        sortBy: String = ""
    ): List<FlightRoute> {
        val net = try {
            network()
        } catch (e: AirportDataException) {
            throw AirportDataException("Failed to query outbound routes from $originIata", e)
        }
        return queryRoutes(originIata, destIata = null, sortBy = sortBy)
            .filter { it.destIata in net }
            .let { routes ->
                if (searchQuery.isBlank()) routes else {
                    val q = normalizeForSearch(searchQuery)
                    routes.filter {
                        it.destIata.equals(searchQuery.trim(), ignoreCase = true) ||
                            normalizeForSearch(it.destMunicipality).contains(q) ||
                            normalizeForSearch(it.destName).contains(q)
                    }
                }
            }
    }

    /**
     * The one route from [originIata] to [destIata], or null. Deliberately *not* limited to the
     * main network: it resolves a flight that is already booked or in progress (check-in,
     * in-flight, a paused flight resumed after an update), whose destination was valid when it was
     * picked. Throws [AirportDataException] on failure, like [getOutboundRoutes].
     */
    fun findRoute(originIata: String, destIata: String): FlightRoute? =
        queryRoutes(originIata, destIata = destIata, sortBy = "").firstOrNull()

    private fun queryRoutes(originIata: String, destIata: String?, sortBy: String): List<FlightRoute> {
        val routesList = mutableListOf<FlightRoute>()
        val db = getReadableDatabase()

        // GROUP BY with a bare MIN(r.id): SQLite takes every other column from that same row, so
        // this keeps the first of each duplicated origin->destination pair (the data has 56 exact
        // duplicates, which used to render as two identical cards).
        var sql = """
            SELECT MIN(r.id) AS id, r.origin_iata, r.dest_iata, r.distance_km, r.flight_time_min, r.carriers,
                   a.name AS dest_name, a.municipality AS dest_municipality,
                   COALESCE(NULLIF(a.iso_country, ''), substr(a.iso_region, 1, 2)) AS dest_country,
                   a.lat AS dest_lat, a.lon AS dest_lon
            FROM routes r
            INNER JOIN airports a ON r.dest_iata = a.iata_code
            WHERE r.origin_iata = ? AND $SANE_ROUTE
        """.trimIndent()

        val selectionArgs = mutableListOf(originIata)
        if (destIata != null) {
            sql += " AND r.dest_iata = ?"
            selectionArgs.add(destIata)
        }
        sql += " GROUP BY r.dest_iata"

        sql += when (sortBy) {
            "Shortest" -> " ORDER BY r.flight_time_min ASC"
            "Longest" -> " ORDER BY r.flight_time_min DESC"
            "Popular" -> " ORDER BY (LENGTH(r.carriers) - LENGTH(REPLACE(r.carriers, ',', '')) + 1) DESC, r.flight_time_min ASC"
            else -> " ORDER BY r.dest_iata ASC"
        }

        try {
            db.rawQuery(sql, selectionArgs.toTypedArray()).use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow("id")
                    val originCol = cursor.getColumnIndexOrThrow("origin_iata")
                    val destCol = cursor.getColumnIndexOrThrow("dest_iata")
                    val distCol = cursor.getColumnIndexOrThrow("distance_km")
                    val timeCol = cursor.getColumnIndexOrThrow("flight_time_min")
                    val carriersCol = cursor.getColumnIndexOrThrow("carriers")
                    val nameCol = cursor.getColumnIndexOrThrow("dest_name")
                    val munCol = cursor.getColumnIndexOrThrow("dest_municipality")
                    val countryCol = cursor.getColumnIndexOrThrow("dest_country")
                    val latCol = cursor.getColumnIndexOrThrow("dest_lat")
                    val lonCol = cursor.getColumnIndexOrThrow("dest_lon")

                    do {
                        routesList.add(
                            FlightRoute(
                                id = cursor.getInt(idCol),
                                originIata = cursor.getString(originCol) ?: "",
                                destIata = cursor.getString(destCol) ?: "",
                                distanceKm = cursor.getDouble(distCol),
                                durationMin = cursor.getInt(timeCol),
                                carriers = cursor.getString(carriersCol) ?: "",
                                destName = cursor.getString(nameCol) ?: "",
                                destMunicipality = cursor.getString(munCol) ?: "",
                                destCountry = cursor.getString(countryCol) ?: "",
                                destLat = cursor.getDouble(latCol),
                                destLon = cursor.getDouble(lonCol)
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying outbound routes", e)
            throw AirportDataException("Failed to query outbound routes from $originIata", e)
        }
        return routesList
    }

    /**
     * Continent -> countries, built only from airports in the main route network, with every
     * country under exactly one continent (see [RouteNetwork.continentCountryMap]). This is the
     * denominator of every Geographic achievement, so it must only contain countries a pilot can
     * actually fly to: it used to include Wake Island (no inbound routes), Antarctica's single
     * routeless airstrip, and six countries twice.
     *
     * Throws rather than returning a partial map: a short map doesn't read as an error, it reads as
     * a smaller world in which the player has done better than they have. There is no value here
     * that means "the query failed", which is exactly what [AirportDataException] exists to
     * express. Memoised only on success.
     */
    fun getContinentCountryMap(): Map<String, Set<String>> {
        cachedContinentCountryMap?.let { return it }
        val net = network()
        val index = airports()
        return RouteNetwork.continentCountryMap(net.mapNotNull { index.byIata[it] })
            .also { cachedContinentCountryMap = it }
    }

    /**
     * Throws rather than returning what was collected so far: the caller asked about a non-empty
     * list of flown-to airports, so anything short of the real answer silently un-visits countries
     * the player has actually been to - a wiped passport with no error. The empty-input case is
     * the only legitimate empty result.
     */
    fun getCountriesForAirports(iatas: List<String>): Set<String> {
        if (iatas.isEmpty()) return emptySet()
        val index = airports()
        return iatas.mapNotNullTo(HashSet()) { iata -> index.byIata[iata]?.isoCountry?.takeIf { it.isNotBlank() } }
    }
}
