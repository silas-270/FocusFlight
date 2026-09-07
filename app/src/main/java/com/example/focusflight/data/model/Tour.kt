package com.example.focusflight.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The largest gap, in calendar days, between two consecutive flights that still counts as the
 * same tour. A gap of exactly this many days keeps the tour together; one more day splits it.
 *
 * Deliberately generous. FocusFlight is used in bursts around exams, and the two errors are not
 * symmetric: splitting one real exam period into two tours makes both halves look shorter and
 * less impressive than the work actually was, which is the whole thing tours exist to avoid.
 * Merging two genuinely separate periods into one is the milder mistake.
 */
const val TOUR_GAP_DAYS = 10

/**
 * One campaign of flying - a run of flights with no gap longer than [TOUR_GAP_DAYS] between
 * consecutive ones.
 *
 * This exists because a daily streak is the wrong shape for how this app is used. Sessions come
 * in bursts of a few weeks before an exam and then stop for months, so a streak counter spends
 * most of the year at zero and punishes the off-season, which is not a failure. A tour ends
 * instead of breaking: it closes, keeps its numbers, and the next one starts fresh without
 * anything being lost.
 *
 * [activeDays] over [spanDays] is the pairing that matters for display. "12" on its own reads as
 * a small number; "12 of 14 days" reads as near-perfect attendance, which is what it is.
 *
 * Whether a tour is still running is *not* a field here - see [isOpenAt].
 */
data class Tour(
    /** Newest-first, matching the order of the history this was derived from. */
    val flights: List<FlightLog>,
    /** `completedAt` of the earliest flight in the tour. */
    val startedAt: Long,
    /** `completedAt` of the latest flight in the tour. */
    val endedAt: Long,
    /** Distinct local calendar days on which at least one flight landed. */
    val activeDays: Int,
    /** Calendar days from the first flight's day to the last's, inclusive. Always >= activeDays. */
    val spanDays: Int,
    val totalDistanceKm: Double,
    val totalMinutes: Int
)

/**
 * Whether this tour is still running as of [now].
 *
 * A function rather than a field on [Tour], because the answer changes with the clock and not
 * with the data. Tours are derived inside
 * [PilotProgressRepository][com.example.focusflight.data.repository.PilotProgressRepository],
 * which only recomputes when Room writes - so a stored `isOpen` would keep claiming a tour was
 * live for as long as the pilot did not fly, which is exactly when it stops being true. Keeping
 * it a pure function of (tour, now) is what lets the derived snapshot stay a cache in the sense
 * docs/state.md means: a pure function of Room's data, unable to go stale unless Room is
 * wrong.
 */
fun Tour.isOpenAt(now: Long, zone: ZoneId, gapDays: Int = TOUR_GAP_DAYS): Boolean {
    val lastDay = Instant.ofEpochMilli(endedAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return today.toEpochDay() - lastDay.toEpochDay() <= gapDays
}

/**
 * Splits a flight history into [Tour]s. Pure - no Room, no repository, no ambient clock - so it
 * is directly unit-testable, following the same shape as
 * [HomeBaseCooldown][com.example.focusflight.data.model.HomeBaseCooldown].
 */
object TourSegmentation {

    /**
     * [history] is expected newest-first (the order `getFlightHistoryFlow` returns), but this
     * sorts defensively rather than trusting it, because getting the order wrong here would not
     * fail loudly - it would silently produce wrong tour boundaries.
     *
     * [zone] must be the *device's* zone, not an airport's. "Did I study today" is a question
     * about the pilot's own calendar day, so `util/FlightClock.kt`'s per-airport approximation
     * (which exists for cockpit flavour text) is the wrong clock to reach for here.
     *
     * Returns newest tour first, and each tour's flights newest-first, so both match the order
     * the caller passed in.
     */
    fun segment(
        history: List<FlightLog>,
        zone: ZoneId,
        gapDays: Int = TOUR_GAP_DAYS
    ): List<Tour> {
        if (history.isEmpty()) return emptyList()

        // Walk oldest-first so gaps read forwards in time; the caller's order is restored below.
        val oldestFirst = history.sortedBy { it.completedAt }

        val groups = mutableListOf<List<FlightLog>>()
        var current = mutableListOf<FlightLog>()
        var previousDay: LocalDate? = null

        for (flight in oldestFirst) {
            val day = flight.localDate(zone)
            if (previousDay != null && day.toEpochDay() - previousDay.toEpochDay() > gapDays) {
                groups += current
                current = mutableListOf()
            }
            current += flight
            previousDay = day
        }
        groups += current

        return groups.map { it.toTour(zone) }.reversed()
    }

    /** Receiver must be oldest-first and non-empty; both hold at the single call site above. */
    private fun List<FlightLog>.toTour(zone: ZoneId): Tour {
        val days = map { it.localDate(zone) }
        val firstDay = days.first()
        val lastDay = days.last()
        return Tour(
            flights = reversed(),
            startedAt = first().completedAt,
            endedAt = last().completedAt,
            activeDays = days.toSet().size,
            spanDays = (lastDay.toEpochDay() - firstDay.toEpochDay()).toInt() + 1,
            totalDistanceKm = sumOf { it.distanceKm },
            totalMinutes = sumOf { it.durationMin }
        )
    }

    private fun FlightLog.localDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate()
}
