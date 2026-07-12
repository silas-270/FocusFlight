package com.example.focusflight.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import java.io.FileOutputStream
import java.io.IOException

class FlightDatabaseHelper(private val context: Context) {

    companion object {
        private const val TAG = "FlightDatabaseHelper"
        private const val DATABASE_NAME = "flights.db"
    }

    private val dbPath = context.getDatabasePath(DATABASE_NAME)

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

    private fun getReadableDatabase(): SQLiteDatabase {
        ensureDatabaseCopied()
        return SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

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
              END, name ASC
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
        } finally {
            db.close()
        }
        return airportsList
    }

    fun getAirportByIata(iataCode: String): Airport? {
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
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying database by IATA", e)
        } finally {
            db.close()
        }
        return null
    }

    fun getRandomAirports(limit: Int, excludeIata: String): List<Airport> {
        val airportsList = mutableListOf<Airport>()
        val db = getReadableDatabase()
        val sql = "SELECT * FROM airports WHERE iata_code != '' AND iata_code != ? ORDER BY RANDOM() LIMIT ?"
        try {
            db.rawQuery(sql, arrayOf(excludeIata, limit.toString())).use { cursor ->
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
            Log.e(TAG, "Error querying random airports", e)
        } finally {
            db.close()
        }
        return airportsList
    }

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

        sql += " LIMIT 30"

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
                                flightTimeMin = cursor.getInt(timeCol),
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
        } finally {
            db.close()
        }
        return routesList
    }
}
