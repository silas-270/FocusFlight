package com.example.focusflight.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// No real timezone/DST database is bundled for the airport list, so each airport's
// local clock is approximated:
//  - a small table of standard-time UTC offsets for countries whose political time
//    zone diverges noticeably from pure solar longitude (mostly Europe, where the
//    "Central European" zone reaches much further west than longitude/15 rounding
//    would suggest)
//  - longitude/15 as a fallback for everything else, which already works reasonably
//    well for large countries whose internal zones roughly track longitude (US,
//    Russia, Australia, Brazil, China's neighbours, ...)
//  - a rough daylight-saving bump, gated to the countries that actually observe it
// This is flavor text for a flight game, not aviation-grade timekeeping.

private val STANDARD_UTC_OFFSET_HOURS: Map<String, Double> = mapOf(
    // Western Europe (UTC+0 standard)
    "GB" to 0.0, "IE" to 0.0, "PT" to 0.0, "IS" to 0.0,
    // Central Europe (UTC+1 standard) - politically shifted west of solar UTC+0
    "FR" to 1.0, "DE" to 1.0, "ES" to 1.0, "IT" to 1.0, "NL" to 1.0, "BE" to 1.0,
    "CH" to 1.0, "AT" to 1.0, "DK" to 1.0, "NO" to 1.0, "SE" to 1.0, "PL" to 1.0,
    "CZ" to 1.0, "HU" to 1.0, "SK" to 1.0, "SI" to 1.0, "HR" to 1.0, "RS" to 1.0,
    "BA" to 1.0, "ME" to 1.0, "MK" to 1.0, "AL" to 1.0, "LU" to 1.0, "MT" to 1.0,
    // Eastern Europe / Eastern Mediterranean (UTC+2 standard)
    "GR" to 2.0, "FI" to 2.0, "EE" to 2.0, "LV" to 2.0, "LT" to 2.0, "RO" to 2.0,
    "BG" to 2.0, "UA" to 2.0, "MD" to 2.0, "CY" to 2.0, "IL" to 2.0, "EG" to 2.0,
    "ZA" to 2.0,
    "TR" to 3.0, "SA" to 3.0, "QA" to 3.0, "KW" to 3.0, "IQ" to 3.0,
    "AE" to 4.0, "OM" to 4.0,
    // Large single-zone countries where longitude/15 badly undershoots
    "CN" to 8.0, "IN" to 5.5
)

// Countries that observe daylight saving in the modern era - kept separate from the
// (much larger) set of non-DST countries so those never get a spurious summer bump.
private val OBSERVES_DST: Set<String> = setOf(
    "GB", "IE", "PT", "IS", "FR", "DE", "ES", "IT", "NL", "BE", "CH", "AT", "DK",
    "NO", "SE", "PL", "CZ", "HU", "SK", "SI", "HR", "RS", "BA", "ME", "MK", "AL",
    "LU", "MT", "GR", "FI", "EE", "LV", "LT", "RO", "BG", "MD", "UA",
    "US", "CA", "AU", "NZ", "MX", "CL"
)

fun approxUtcOffsetHours(lonDeg: Double, latDeg: Double, isoCountry: String?, epochMs: Long): Double {
    val base = isoCountry?.let { STANDARD_UTC_OFFSET_HOURS[it] }
        ?: (lonDeg / 15.0).roundToInt().toDouble().coerceIn(-12.0, 14.0)

    if (isoCountry == null || isoCountry !in OBSERVES_DST) return base

    val month = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).monthValue
    val northernSummer = month in 4..9
    val inDstSeason = if (latDeg >= 0) northernSummer else !northernSummer
    return if (inDstSeason) base + 1.0 else base
}

private val ClockFormatter = DateTimeFormatter.ofPattern("HH:mm")

data class AirportClock(val label: String, val dayOffset: Int)

private fun zoneOffsetOf(hours: Double): ZoneOffset = ZoneOffset.ofTotalSeconds((hours * 3600).roundToInt())

// dayOffset is this clock's local calendar date minus [relativeToDate], so a flight
// that lands after local midnight at the destination reads e.g. "+1".
fun airportClock(epochMs: Long, lonDeg: Double, latDeg: Double, isoCountry: String?, relativeToDate: LocalDate): AirportClock {
    val zonedTime = Instant.ofEpochMilli(epochMs).atZone(zoneOffsetOf(approxUtcOffsetHours(lonDeg, latDeg, isoCountry, epochMs)))
    val dayOffset = (zonedTime.toLocalDate().toEpochDay() - relativeToDate.toEpochDay()).toInt()
    return AirportClock(zonedTime.format(ClockFormatter), dayOffset)
}

fun localDateOf(epochMs: Long, lonDeg: Double, latDeg: Double, isoCountry: String?): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(zoneOffsetOf(approxUtcOffsetHours(lonDeg, latDeg, isoCountry, epochMs))).toLocalDate()
