package com.silas270.blocktime.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the (de)serialization [PausedFlight] uses to persist/restore a paused flight - the one
 * format shared by [com.silas270.blocktime.data.repository.PreferencesRepository] (the Story/
 * Free slot) and `Challenge.pausedFlight`'s Room `TypeConverter` (a Route challenge's own slot).
 * No legacy-format parsing (unlike the old `ActiveFlightContext.parse`) - this is pre-release, so
 * there's exactly one format to trust.
 */
class PausedFlightTest {

    @Test
    fun `round-trips a fresh flight with no elapsed time or camera yet`() {
        val flight = PausedFlight(
            flightNumber = "FF-123",
            originIata = "STR",
            destIata = "JFK",
            durationMin = 480,
            mode = FlightMode.STORY
        )

        val parsed = PausedFlight.parse(flight.serialize())

        assertEquals(flight, parsed)
    }

    @Test
    fun `round-trips a CHALLENGE flight with elapsed time and a saved camera pose`() {
        val flight = PausedFlight(
            flightNumber = "FF-456",
            originIata = "LHR",
            destIata = "SYD",
            durationMin = 1320,
            mode = FlightMode.CHALLENGE,
            challengeId = 7,
            elapsedMs = 90_000L,
            camera = CameraPose(mode = 1, x = 1.5, y = -2.25, z = 3.0, qx = 0.1, qy = 0.2, qz = 0.3, qw = 0.9)
        )

        val parsed = PausedFlight.parse(flight.serialize())

        assertEquals(flight, parsed)
    }

    @Test
    fun `unknown mode string defaults to STORY instead of crashing`() {
        val flight = PausedFlight("FF-1", "STR", "JFK", 480, FlightMode.STORY)
        val corrupted = flight.serialize().replaceFirst("STORY", "NOT_A_REAL_MODE")

        val parsed = PausedFlight.parse(corrupted)

        assertEquals(FlightMode.STORY, parsed?.mode)
    }

    @Test
    fun `malformed duration returns null instead of throwing`() {
        val flight = PausedFlight("FF-1", "STR", "JFK", 480, FlightMode.STORY)
        val corrupted = flight.serialize().replaceFirst("|480|", "|not-a-number|")

        assertNull(PausedFlight.parse(corrupted))
    }

    @Test
    fun `garbage input returns null`() {
        assertNull(PausedFlight.parse("totally-unparseable"))
    }
}
