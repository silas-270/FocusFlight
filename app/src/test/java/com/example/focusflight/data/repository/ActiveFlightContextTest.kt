package com.example.focusflight.data.repository

import com.example.focusflight.data.model.FlightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the (de)serialization [ActiveFlightContext] uses to persist/restore the Hub's
 * resumable flight (see `PreferencesRepository.saveActiveFlightContext`/`getActiveFlightContext`)
 * - in particular the Phase 2 fix for the resume-after-restart gap flagged in
 * docs/design/codebase-map.md: a resumed flight must carry its real origin and mode, not
 * silently fall back to `currentAirport`/STORY, except for the legacy pre-Phase-2 3-field
 * format, which intentionally still does.
 */
class ActiveFlightContextTest {

    @Test
    fun `round-trips a STORY context through serialize and parse`() {
        val context = ActiveFlightContext(
            flightNumber = "FF-123",
            originIata = "STR",
            destIata = "JFK",
            durationMin = 480,
            mode = FlightMode.STORY
        )

        val parsed = ActiveFlightContext.parse(context.serialize(), currentAirportIata = "STR")

        assertEquals(context, parsed)
    }

    @Test
    fun `round-trips a FREE context whose origin differs from currentAirport`() {
        val context = ActiveFlightContext(
            flightNumber = "FF-456",
            originIata = "LHR",
            destIata = "CDG",
            durationMin = 60,
            mode = FlightMode.FREE
        )

        // currentAirportIata is deliberately different from originIata here - a FREE flight's
        // origin must survive resume independent of whatever the player's Story Mode base is.
        val parsed = ActiveFlightContext.parse(context.serialize(), currentAirportIata = "STR")

        assertEquals(context, parsed)
    }

    @Test
    fun `parses the legacy 3-field format as STORY using currentAirport as origin`() {
        val legacy = "FF-999|JFK|480"

        val parsed = ActiveFlightContext.parse(legacy, currentAirportIata = "STR")

        assertEquals(
            ActiveFlightContext(
                flightNumber = "FF-999",
                originIata = "STR",
                destIata = "JFK",
                durationMin = 480,
                mode = FlightMode.STORY
            ),
            parsed
        )
    }

    @Test
    fun `legacy 3-field format falls back to STR when no currentAirport is known`() {
        val legacy = "FF-999|JFK|480"

        val parsed = ActiveFlightContext.parse(legacy, currentAirportIata = null)

        assertEquals("STR", parsed?.originIata)
    }

    @Test
    fun `unknown mode string defaults to STORY instead of crashing`() {
        val raw = "FF-1|STR|JFK|480|NOT_A_REAL_MODE"

        val parsed = ActiveFlightContext.parse(raw, currentAirportIata = "STR")

        assertEquals(FlightMode.STORY, parsed?.mode)
    }

    @Test
    fun `malformed duration returns null instead of throwing`() {
        val raw = "FF-1|STR|JFK|not-a-number|STORY"

        val parsed = ActiveFlightContext.parse(raw, currentAirportIata = "STR")

        assertNull(parsed)
    }

    @Test
    fun `garbage input returns null`() {
        assertNull(ActiveFlightContext.parse("totally-unparseable", currentAirportIata = "STR"))
    }
}
