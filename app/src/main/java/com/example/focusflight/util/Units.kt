package com.example.focusflight.util

import java.util.Locale
import kotlin.math.roundToInt

// Flight data is stored/simulated in metric internally; these convert to the
// imperial units pilots and passengers actually think in for display only.
private const val KM_TO_MILES = 0.621371
private const val METERS_TO_FEET = 3.28084

fun kmToMiles(km: Double): Double = km * KM_TO_MILES
fun metersToFeet(meters: Double): Double = meters * METERS_TO_FEET
fun kmhToMph(kmh: Int): Int = (kmh * KM_TO_MILES).roundToInt()

fun formatMiles(km: Double): String = String.format(Locale.US, "%,.0f mi", kmToMiles(km))
fun formatFeet(meters: Int): String = String.format(Locale.US, "%,d ft", metersToFeet(meters.toDouble()).roundToInt())
fun formatMph(kmh: Int): String = String.format(Locale.US, "%d mph", kmhToMph(kmh))
