package com.silas270.blocktime.engine.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.silas270.blocktime.data.local.AppDatabase
import com.silas270.blocktime.data.model.FlightLog
import com.silas270.blocktime.data.model.FlightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val userId = db.userProfileDao().getProfile()?.id ?: 1
                val now = System.currentTimeMillis()

                // Remove previous unrealistic seeded flights
                db.openHelper.writableDatabase.execSQL("DELETE FROM flight_log WHERE distance_km > 20000")

                // Realistic long-haul flights (JFK->HND 10,850 km, LHR->SYD 17,015 km, etc.)
                val baseFlights = listOf(
                    FlightLog(
                        userId = userId,
                        flightNumber = "NH-109",
                        originIata = "JFK",
                        destIata = "HND",
                        durationMin = 840,
                        distanceKm = 10_850.0,
                        completedAt = now - 86400000L * 3,
                        mode = FlightMode.STORY
                    ),
                    FlightLog(
                        userId = userId,
                        flightNumber = "QF-2",
                        originIata = "LHR",
                        destIata = "SYD",
                        durationMin = 1320,
                        distanceKm = 17_015.0,
                        completedAt = now - 86400000L * 2,
                        mode = FlightMode.STORY
                    ),
                    FlightLog(
                        userId = userId,
                        flightNumber = "AF-066",
                        originIata = "CDG",
                        destIata = "LAX",
                        durationMin = 690,
                        distanceKm = 9_100.0,
                        completedAt = now - 86400000L,
                        mode = FlightMode.STORY
                    )
                )

                // Additional realistic flights to reach cumulative Moon distance (384,400 km)
                val cumulativeFlights = (1..38).map { i ->
                    FlightLog(
                        userId = userId,
                        flightNumber = "FF-M$i",
                        originIata = if (i % 2 == 0) "FRA" else "SIN",
                        destIata = if (i % 2 == 0) "EZE" else "JFK",
                        durationMin = 750,
                        distanceKm = 10_000.0,
                        completedAt = now - 86400000L * (4 + i),
                        mode = FlightMode.STORY
                    )
                }

                (baseFlights + cumulativeFlights).forEach { db.flightLogDao().insertFlightLog(it) }
                Log.i("DebugSeedReceiver", "Successfully seeded realistic demo flights")
            } catch (e: Exception) {
                Log.e("DebugSeedReceiver", "Failed to seed demo flights", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
