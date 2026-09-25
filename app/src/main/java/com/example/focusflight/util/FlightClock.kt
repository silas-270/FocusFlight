package com.example.focusflight.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

// Each airport's local clock comes from its real IANA time zone, and java.time applies that
// zone's offset and daylight-saving rules for the instant in question. This replaced a
// longitude/15 guess with a partial country table and an April-September DST bump for everyone,
// which put Seoul, Singapore, Moscow, Mexico City, Buenos Aires, Atlanta and others an hour or
// more off, gave Iceland DST, and knew nothing about Israel.
//
// flights.db carries no time zone column, so the zone is resolved from what this API already
// receives (country + coordinates) through the generated tables in AirportTimeZones.kt: a direct
// country -> zone entry for single-zone countries, and the nearest anchor airport for countries
// that span several zones. The generator verifies that every airport in flights.db resolves to
// the correct clock. The zone rules themselves come from the device's tz database, so they stay
// current as countries change them.

private val zoneCache = ConcurrentHashMap<String, ZoneId>()

private fun zoneOf(id: String): ZoneId? =
    zoneCache[id] ?: runCatching { ZoneId.of(id) }.getOrNull()?.also { zoneCache[id] = it }

/**
 * The time zone of the airport at ([latDeg], [lonDeg]) in [isoCountry]. Falls back to a whole-hour
 * offset from longitude only for a country the tables don't know, which no airport in the bundled
 * data hits.
 */
fun airportZone(lonDeg: Double, latDeg: Double, isoCountry: String?): ZoneId {
    val country = isoCountry?.takeIf { it.isNotBlank() }
    val zoneName = country?.let { SINGLE_ZONE_COUNTRIES[it] }
        ?: country?.let { ZONE_ANCHORS[it] }?.minByOrNull { anchor ->
            // The same great-circle ranking the generator verified against, so the answer for
            // every bundled airport is exactly the one it checked.
            val dLat = Math.toRadians(anchor.lat - latDeg)
            val dLon = Math.toRadians(anchor.lon - lonDeg)
            val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(latDeg)) * cos(Math.toRadians(anchor.lat)) * sin(dLon / 2).let { it * it }
            a // monotonic in great-circle distance, so no need for the asin
        }?.zoneId
    return zoneName?.let(::zoneOf)
        ?: ZoneOffset.ofHours((lonDeg / 15.0).roundToInt().coerceIn(-12, 14))
}

/** Current UTC offset of the airport's clock at [epochMs], in hours (may be fractional, e.g. 5.5). */
fun approxUtcOffsetHours(lonDeg: Double, latDeg: Double, isoCountry: String?, epochMs: Long): Double =
    airportZone(lonDeg, latDeg, isoCountry).rules.getOffset(Instant.ofEpochMilli(epochMs)).totalSeconds / 3600.0

private val ClockFormatter = DateTimeFormatter.ofPattern("HH:mm")

data class AirportClock(val label: String, val dayOffset: Int)

// dayOffset is this clock's local calendar date minus [relativeToDate], so a flight
// that lands after local midnight at the destination reads e.g. "+1".
fun airportClock(epochMs: Long, lonDeg: Double, latDeg: Double, isoCountry: String?, relativeToDate: LocalDate): AirportClock {
    val zonedTime = Instant.ofEpochMilli(epochMs).atZone(airportZone(lonDeg, latDeg, isoCountry))
    val dayOffset = (zonedTime.toLocalDate().toEpochDay() - relativeToDate.toEpochDay()).toInt()
    return AirportClock(zonedTime.format(ClockFormatter), dayOffset)
}

fun localDateOf(epochMs: Long, lonDeg: Double, latDeg: Double, isoCountry: String?): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(airportZone(lonDeg, latDeg, isoCountry)).toLocalDate()
