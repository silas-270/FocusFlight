package com.silas270.blocktime.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pure-function coverage for [TourSegmentation] - no Room, no repository, and no ambient clock,
 * the same shape as [HomeBaseCooldown]'s tests.
 *
 * Everything below runs in UTC so a "day" is exactly 24 hours and the boundary cases are about
 * the gap rule rather than about time zones. The one exception is the DST test, which uses a real
 * zone precisely because that is where a naive `millis / 86_400_000` would be wrong.
 */
class TourSegmentationTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val day0: LocalDate = LocalDate.of(2026, 3, 1)

    /** A flight landing [daysAfterStart] days after [day0], at midday so nothing sits on a boundary. */
    private fun flight(daysAfterStart: Long, distanceKm: Double = 100.0, durationMin: Int = 30): FlightLog {
        val completedAt = day0.plusDays(daysAfterStart)
            .atStartOfDay(utc)
            .plusHours(12)
            .toInstant()
            .toEpochMilli()
        return FlightLog(
            userId = 1,
            flightNumber = "FF${daysAfterStart}",
            originIata = "ORI",
            destIata = "DST",
            durationMin = durationMin,
            distanceKm = distanceKm,
            completedAt = completedAt
        )
    }

    /** Callers pass newest-first, matching `getFlightHistoryFlow`'s ordering. */
    private fun history(vararg daysAfterStart: Long): List<FlightLog> =
        daysAfterStart.map { flight(it) }.sortedByDescending { it.completedAt }

    // ── boundaries ───────────────────────────────────────────────────────────────────────

    @Test
    fun `an empty history has no tours`() {
        assertEquals(emptyList<Tour>(), TourSegmentation.segment(emptyList(), utc))
    }

    @Test
    fun `a single flight is a one-day tour`() {
        val tours = TourSegmentation.segment(history(0), utc)

        assertEquals(1, tours.size)
        assertEquals(1, tours[0].flights.size)
        assertEquals(1, tours[0].activeDays)
        assertEquals(1, tours[0].spanDays)
    }

    @Test
    fun `a gap of exactly the limit stays one tour`() {
        val tours = TourSegmentation.segment(history(0, TOUR_GAP_DAYS.toLong()), utc)

        assertEquals(1, tours.size)
        assertEquals(2, tours[0].flights.size)
    }

    @Test
    fun `a gap of one day over the limit splits into two tours`() {
        val tours = TourSegmentation.segment(history(0, TOUR_GAP_DAYS + 1L), utc)

        assertEquals(2, tours.size)
        assertEquals(1, tours[0].flights.size)
        assertEquals(1, tours[1].flights.size)
    }

    // ── the days ratio ───────────────────────────────────────────────────────────────────

    @Test
    fun `two flights on the same day count as one active day`() {
        val twice = listOf(flight(0), flight(0, distanceKm = 250.0), flight(1))
        val tours = TourSegmentation.segment(twice.sortedByDescending { it.completedAt }, utc)

        assertEquals(1, tours.size)
        assertEquals(3, tours[0].flights.size)
        assertEquals(2, tours[0].activeDays)
        assertEquals(2, tours[0].spanDays)
    }

    @Test
    fun `a tour with days off has fewer active days than its span`() {
        // Flown on days 0, 1, 4, 5 - a four-day attendance across a six-day span.
        val tours = TourSegmentation.segment(history(0, 1, 4, 5), utc)

        assertEquals(1, tours.size)
        assertEquals(4, tours[0].activeDays)
        assertEquals(6, tours[0].spanDays)
    }

    @Test
    fun `active days never exceed the span`() {
        val tours = TourSegmentation.segment(history(0, 0, 1, 2, 2, 3, 9), utc)

        tours.forEach { assertTrue(it.activeDays <= it.spanDays) }
    }

    // ── aggregates and ordering ──────────────────────────────────────────────────────────

    @Test
    fun `totals are summed across the whole tour`() {
        val flights = listOf(
            flight(0, distanceKm = 100.0, durationMin = 30),
            flight(1, distanceKm = 250.5, durationMin = 45)
        ).sortedByDescending { it.completedAt }

        val tour = TourSegmentation.segment(flights, utc).single()

        assertEquals(350.5, tour.totalDistanceKm, 0.001)
        assertEquals(75, tour.totalMinutes)
    }

    @Test
    fun `tours come back newest first with their flights newest first`() {
        val tours = TourSegmentation.segment(history(0, 1, 30, 31), utc)

        assertEquals(2, tours.size)
        // The newest tour (days 30-31) leads.
        assertTrue(tours[0].startedAt > tours[1].endedAt)
        // And within it, so does the newest flight.
        assertTrue(tours[0].flights.first().completedAt > tours[0].flights.last().completedAt)
    }

    @Test
    fun `startedAt and endedAt bracket the tour`() {
        val tour = TourSegmentation.segment(history(0, 2, 5), utc).single()

        assertEquals(flight(0).completedAt, tour.startedAt)
        assertEquals(flight(5).completedAt, tour.endedAt)
    }

    @Test
    fun `an unsorted history is segmented the same as a sorted one`() {
        val shuffled = listOf(flight(30), flight(0), flight(31), flight(1))

        val fromShuffled = TourSegmentation.segment(shuffled, utc)
        val fromSorted = TourSegmentation.segment(history(0, 1, 30, 31), utc)

        assertEquals(fromSorted.map { it.startedAt }, fromShuffled.map { it.startedAt })
        assertEquals(fromSorted.map { it.flights.size }, fromShuffled.map { it.flights.size })
    }

    // ── time zones ───────────────────────────────────────────────────────────────────────

    @Test
    fun `day counting follows the calendar across a DST transition`() {
        // Europe/Berlin springs forward on 29 March 2026, making that local day 23 hours long.
        val berlin = ZoneId.of("Europe/Berlin")
        val days = listOf(
            LocalDate.of(2026, 3, 28),
            LocalDate.of(2026, 3, 29),
            LocalDate.of(2026, 3, 30)
        )
        val flights = days.mapIndexed { i, date ->
            FlightLog(
                userId = 1,
                flightNumber = "FF$i",
                originIata = "ORI",
                destIata = "DST",
                durationMin = 30,
                distanceKm = 100.0,
                completedAt = date.atStartOfDay(berlin).plusHours(12).toInstant().toEpochMilli()
            )
        }.sortedByDescending { it.completedAt }

        val tour = TourSegmentation.segment(flights, berlin).single()

        // Three calendar days, even though they are not three equal 24-hour blocks.
        assertEquals(3, tour.activeDays)
        assertEquals(3, tour.spanDays)
    }

    // ── isOpenAt ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a tour flown today is open`() {
        val tour = TourSegmentation.segment(history(0, 1), utc).single()
        val now = day0.plusDays(1).atStartOfDay(utc).plusHours(20).toInstant().toEpochMilli()

        assertTrue(tour.isOpenAt(now, utc))
    }

    @Test
    fun `a tour stays open right up to the gap limit and closes one day later`() {
        val tour = TourSegmentation.segment(history(0), utc).single()

        val atLimit = day0.plusDays(TOUR_GAP_DAYS.toLong()).atStartOfDay(utc).toInstant().toEpochMilli()
        val pastLimit = day0.plusDays(TOUR_GAP_DAYS + 1L).atStartOfDay(utc).toInstant().toEpochMilli()

        assertTrue(tour.isOpenAt(atLimit, utc))
        assertFalse(tour.isOpenAt(pastLimit, utc))
    }
}
