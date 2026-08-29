package com.example.focusflight.data.local.airport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.model.Runway
import java.io.FileOutputStream
import java.io.IOException

class AirportRouteSqliteDataSource(private val context: Context) {

    companion object {
        private const val TAG = "AirportRouteSqliteDataSource"
        private const val DATABASE_NAME = "flights.db"
    }

    private val dbPath = context.getDatabasePath(DATABASE_NAME)

    @Synchronized
    fun ensureDatabaseCopied() {
        if (!dbPath.exists()) {
            dbPath.parentFile?.mkdirs()
            try {
                context.assets.open(DATABASE_NAME).use { inputStream ->
                    FileOutputStream(dbPath).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var length: Int
                        while (inputStream.read(buffer).also { length = it } > 0) {
                            outputStream.write(buffer, 0, length)
                        }
                    }
                }
                Log.d(TAG, "Database successfully copied to ${dbPath.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "Error copying database from assets", e)
                throw RuntimeException("Failed to copy database asset", e)
            }
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
     * successful read. It was a `SELECT DISTINCT` full-table scan over the whole airports table on
     * every Passport and Flight Search open, for an answer that cannot change between app updates.
     *
     * Assigned only on success. A failure throws (see [AirportDataException]) and therefore cannot
     * be memoised - which is the entire reason that distinction had to exist before this cache
     * could: pinning an empty map here would wipe the visited-country denominator permanently.
     */
    @Volatile
    private var cachedContinentCountryMap: Map<String, Set<String>>? = null

    /**
     * Airports never change within a build, and [getAirportByIata] is the single hottest query in
     * the app - the Hub, check-in, in-flight, challenge crediting and the landing pipeline all
     * resolve codes through it, three times over in `advanceRouteChallenge` alone. Only successful
     * lookups are cached; an unknown code stays a miss, which keeps the cache free of entries that
     * exist only to record an absence.
     */
    private val airportByIataCache = android.util.LruCache<String, Airport>(128)

    // Stays lenient on failure (log + empty list) on purpose: a typo-ahead search that finds
    // nothing is an ordinary answer the picker already renders as "no results", so a failure is
    // no worse than a miss and nothing downstream caches it. See [AirportDataException] for why
    // only the two aggregate queries had to stop doing this.
    fun searchAirports(query: String): List<Airport> {
        if (query.trim().length < 2) return emptyList()
        val airportsList = mutableListOf<Airport>()
        val db = getReadableDatabase()
        val sql = """
            SELECT * FROM airports 
            WHERE (iata_code LIKE ? OR municipality LIKE ? OR name LIKE ?) 
              AND iata_code != '' 
            ORDER BY 
              CASE 
                WHEN iata_code = ? COLLATE NOCASE THEN 1
                WHEN iata_code LIKE ? THEN 2
                WHEN municipality LIKE ? THEN 3
                ELSE 4 
              END,
              CASE 
                WHEN type = 'large_airport' THEN 1
                WHEN type = 'medium_airport' THEN 2
                ELSE 3
              END,
              name ASC
            LIMIT 15
        """.trimIndent()
        val cleanQuery = "%${query.trim()}%"
        val trimmed = query.trim()
        val startQuery = "${query.trim()}%"
        
        try {
            db.rawQuery(sql, arrayOf(cleanQuery, cleanQuery, cleanQuery, trimmed, startQuery, startQuery)).use { cursor ->
                if (cursor.moveToFirst()) {
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
                    
                    do {
                        airportsList.add(
                            Airport(
                                id = cursor.getInt(idCol),
                                ident = cursor.getString(identCol) ?: "",
                                iataCode = cursor.getString(iataCol) ?: "",
                                name = cursor.getString(nameCol) ?: "",
                                lat = cursor.getDouble(latCol),
                                lon = cursor.getDouble(lonCol),
                                elevationFt = cursor.getDouble(elevCol),
                                continent = cursor.getString(contCol) ?: "",
                                isoCountry = cursor.getString(countryCol) ?: "",
                                isoRegion = cursor.getString(regionCol) ?: "",
                                municipality = cursor.getString(munCol) ?: "",
                                type = cursor.getString(typeCol) ?: ""
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying database", e)
        }
        return airportsList
    }

    // Lenient on failure (log + null) on purpose: every caller already has to handle an unknown
    // IATA code, so a null here lands in a branch that exists regardless, and the answer is per-
    // airport rather than a global truth anything caches. See [AirportDataException].
    fun getAirportByIata(iataCode: String): Airport? {
        airportByIataCache.get(iataCode)?.let { return it }
        val db = getReadableDatabase()
        val sql = "SELECT * FROM airports WHERE iata_code = ? LIMIT 1"
        try {
            db.rawQuery(sql, arrayOf(iataCode)).use { cursor ->
                if (cursor.moveToFirst()) {
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
                    
                    return Airport(
                        id = cursor.getInt(idCol),
                        ident = cursor.getString(identCol) ?: "",
                        iataCode = cursor.getString(iataCol) ?: "",
                        name = cursor.getString(nameCol) ?: "",
                        lat = cursor.getDouble(latCol),
                        lon = cursor.getDouble(lonCol),
                        elevationFt = cursor.getDouble(elevCol),
                        continent = cursor.getString(contCol) ?: "",
                        isoCountry = cursor.getString(countryCol) ?: "",
                        isoRegion = cursor.getString(regionCol) ?: "",
                        municipality = cursor.getString(munCol) ?: "",
                        type = cursor.getString(typeCol) ?: ""
                    ).also { airportByIataCache.put(iataCode, it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying database by IATA", e)
        }
        return null
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



    // Throws on failure rather than returning empty, unlike the other per-item lookups. A routeless
    // origin IS an ordinary answer here - but it is not an inert one: Story Mode reacts to it by
    // rehoming the pilot to LHR and *writing* `currentAirport`. A swallowed failure would therefore
    // teleport someone out of their airport because a query briefly broke, and persist it. Empty
    // has to mean empty wherever a caller acts on it. See [AirportDataException].
    fun getOutboundRoutes(
        originIata: String,
        searchQuery: String = "",
        sortBy: String = ""
    ): List<FlightRoute> {
        val routesList = mutableListOf<FlightRoute>()
        val db = getReadableDatabase()

        var sql = """
            SELECT r.id, r.origin_iata, r.dest_iata, r.distance_km, r.flight_time_min, r.carriers,
                   a.name AS dest_name, a.municipality AS dest_municipality, a.iso_country AS dest_country,
                   a.lat AS dest_lat, a.lon AS dest_lon
            FROM routes r
            INNER JOIN airports a ON r.dest_iata = a.iata_code
            WHERE r.origin_iata = ?
        """.trimIndent()

        val selectionArgs = mutableListOf<String>()
        selectionArgs.add(originIata)

        if (searchQuery.isNotBlank()) {
            sql += " AND (r.dest_iata LIKE ? OR a.municipality LIKE ? OR a.name LIKE ?)"
            val cleanQuery = "%${searchQuery.trim()}%"
            selectionArgs.add(cleanQuery)
            selectionArgs.add(cleanQuery)
            selectionArgs.add(cleanQuery)
        }

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

    fun getContinentCountryMap(): Map<String, Set<String>> {
        cachedContinentCountryMap?.let { return it }
        val map = mutableMapOf<String, MutableSet<String>>()
        val db = getReadableDatabase()
        val sql = "SELECT DISTINCT continent, iso_country FROM airports WHERE type IN ('large_airport', 'medium_airport') AND iso_country != '' AND continent != ''"
        
        try {
            db.rawQuery(sql, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val contCol = cursor.getColumnIndexOrThrow("continent")
                    val countryCol = cursor.getColumnIndexOrThrow("iso_country")
                    
                    do {
                        val continent = cursor.getString(contCol) ?: continue
                        val country = cursor.getString(countryCol) ?: continue
                        
                        map.getOrPut(continent) { mutableSetOf() }.add(country)
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            // Throws rather than returning the partially-filled map: this is the denominator for
            // every Geographic achievement ("12/54 countries in Europe"), so a short map doesn't
            // read as an error, it reads as a smaller world in which the player has done better
            // than they have. There is no value here that means "the query failed", which is
            // exactly what [AirportDataException] exists to express.
            Log.e(TAG, "Error querying continent country map", e)
            throw AirportDataException("Failed to query continent/country map", e)
        }
        return map.also { cachedContinentCountryMap = it }
    }

    fun getCountriesForAirports(iatas: List<String>): Set<String> {
        if (iatas.isEmpty()) return emptySet()
        
        val countries = mutableSetOf<String>()
        val db = getReadableDatabase()
        
        // SQLite has a limit on the number of variables in IN (?), so we chunk if necessary, 
        // but for a user's flight log it shouldn't exceed 999.
        val placeholders = iatas.joinToString(",") { "?" }
        val sql = "SELECT DISTINCT iso_country FROM airports WHERE iata_code IN ($placeholders) AND iso_country != ''"
        
        try {
            db.rawQuery(sql, iatas.toTypedArray()).use { cursor ->
                if (cursor.moveToFirst()) {
                    val countryCol = cursor.getColumnIndexOrThrow("iso_country")
                    
                    do {
                        val country = cursor.getString(countryCol) ?: continue
                        countries.add(country)
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            // Throws rather than returning what was collected so far: the caller asked about a
            // non-empty list of flown-to airports, so anything short of the real answer silently
            // un-visits countries the player has actually been to - a wiped passport with no
            // error. The empty-input case above is the only legitimate empty result.
            Log.e(TAG, "Error querying countries for airports", e)
            throw AirportDataException("Failed to query countries for ${iatas.size} airports", e)
        }
        return countries
    }
}
