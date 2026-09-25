package com.silas270.blocktime.util

import java.util.Locale
import kotlin.math.roundToInt

// Flight data is stored/simulated in metric internally; these convert to the
// imperial units pilots and passengers actually think in for display only.
private const val KM_TO_MILES = 0.621371
private const val METERS_TO_FEET = 3.28084

fun kmToMiles(km: Double): Double = km * KM_TO_MILES
fun milesToKm(miles: Double): Double = miles / KM_TO_MILES
fun metersToFeet(meters: Double): Double = meters * METERS_TO_FEET
fun kmhToMph(kmh: Int): Int = (kmh * KM_TO_MILES).roundToInt()

fun formatMiles(km: Double): String = String.format(Locale.US, "%,.0f mi", kmToMiles(km))
fun formatFeet(meters: Int): String = String.format(Locale.US, "%,d ft", metersToFeet(meters.toDouble()).roundToInt())
fun formatMph(kmh: Int): String = String.format(Locale.US, "%d mph", kmhToMph(kmh))

/**
 * A length of time as the app writes it everywhere: "45m", "1h", "1h 25m", "134h 27m". Running
 * clocks (the In-Flight countdown and its elapsed/total readout) are timers, not lengths, and
 * keep their hh:mm:ss form.
 */
fun formatDuration(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val h = safe / 60
    val m = safe % 60
    return when {
        h == 0 -> String.format(Locale.US, "%dm", m)
        m == 0 -> String.format(Locale.US, "%dh", h)
        else -> String.format(Locale.US, "%dh %dm", h, m)
    }
}
