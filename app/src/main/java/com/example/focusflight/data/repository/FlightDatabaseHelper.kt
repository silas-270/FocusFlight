package com.example.focusflight.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.focusflight.data.model.Airport
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
            LIMIT 15
        """.trimIndent()
        val cleanQuery = "%${query.trim()}%"
        
        try {
            db.rawQuery(sql, arrayOf(cleanQuery, cleanQuery, cleanQuery)).use { cursor ->
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
                                ident = cursor.getString(identCol),
                                iataCode = cursor.getString(iataCol),
                                name = cursor.getString(nameCol),
                                lat = cursor.getDouble(latCol),
                                lon = cursor.getDouble(lonCol),
                                elevationFt = cursor.getDouble(elevCol),
                                continent = cursor.getString(contCol),
                                isoCountry = cursor.getString(countryCol),
                                isoRegion = cursor.getString(regionCol),
                                municipality = cursor.getString(munCol),
                                type = cursor.getString(typeCol)
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
}
