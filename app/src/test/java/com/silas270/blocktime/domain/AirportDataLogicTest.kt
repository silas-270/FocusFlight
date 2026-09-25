package com.silas270.blocktime.domain

import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.util.airportClock
import com.silas270.blocktime.util.approxUtcOffsetHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneOffset

/** Search ranking, main-network detection, continent assignment and airport clocks. */
class AirportDataLogicTest {

    private fun airport(
        iata: String, name: String, city: String, country: String,
        continent: String = "EU", type: String = "large_airport", icao: String = "X$iata"
    ) = Airport(0, icao, iata, name, 0.0, 0.0, 0.0, continent, country, "", city, type)

    private val index = AirportSearchIndex(
        listOf(
            airport("DUS", "Düsseldorf Airport", "Düsseldorf", "DE", icao = "EDDL"),
            airport("MUC", "Munich Airport", "Munich", "DE", icao = "EDDM"),
            airport("GRU", "São Paulo/Guarulhos International Airport", "São Paulo", "BR", "SA"),
            airport("KRK", "Kraków John Paul II International Airport", "Balice", "PL"),
            airport("DOH", "Hamad International Airport", "Doha", "QA", "AS"),
            airport("DIA", "Doha International Airport", "Doha", "QA", "AS", type = "medium_airport")
        )
    )

    @Test
    fun `search folds accents and matches city, airport name, ICAO and country`() {
        assertEquals("DUS", index.search("dusseldorf").first().iataCode)
        assertEquals("GRU", index.search("sao paulo").first().iataCode)
        assertEquals("KRK", index.search("krakow").first().iataCode)
        assertEquals("MUC", index.search("EDDM").first().iataCode)
        assertEquals("MUC", index.search("munchen").first().iataCode)
        assertEquals(setOf("DUS", "MUC"), index.search("germany").map { it.iataCode }.toSet())
    }

    @Test
    fun `exact IATA ranks first and bigger airports win ties`() {
        assertEquals("DIA", index.search("dia").first().iataCode)
        assertEquals(listOf("DOH", "DIA"), index.search("doha").map { it.iataCode })
    }

    @Test
    fun `main component excludes dead ends and closed clusters`() {
        val edges = listOf(
            "A" to "B", "B" to "A", "A" to "C", "C" to "A", "B" to "C",
            "A" to "DEAD",            // inbound only
            "X" to "Y", "Y" to "X",   // closed cluster
            "B" to "B"                // self route
        )
        assertEquals(setOf("A", "B", "C"), RouteNetwork.mainComponent(edges))
    }

    @Test
    fun `each country counts under exactly one continent`() {
        val map = RouteNetwork.continentCountryMap(
            listOf(
                airport("MAD", "", "", "ES", "EU"), airport("BCN", "", "", "ES", "EU"),
                airport("TFS", "", "", "ES", "AF"),
                airport("SVO", "", "", "RU", "EU"),
                airport("OVB", "", "", "RU", "AS"), airport("VVO", "", "", "RU", "AS")
            )
        )
        assertEquals(setOf("ES", "RU"), map["EU"])
        assertFalse(map.containsKey("AF"))
        assertFalse(map.containsKey("AS"))
    }

    @Test
    fun `airport clocks use the real zone and its DST rules`() {
        val january = ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val july = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(9.0, approxUtcOffsetHours(126.45, 37.46, "KR", july), 0.0)        // Seoul
        assertEquals(8.0, approxUtcOffsetHours(103.99, 1.36, "SG", july), 0.0)         // Singapore
        assertEquals(3.0, approxUtcOffsetHours(37.41, 55.97, "RU", july), 0.0)         // Moscow SVO
        assertEquals(-6.0, approxUtcOffsetHours(-99.07, 19.44, "MX", july), 0.0)       // Mexico City, no DST
        assertEquals(-4.0, approxUtcOffsetHours(-84.43, 33.64, "US", july), 0.0)       // Atlanta EDT
        assertEquals(-5.0, approxUtcOffsetHours(-84.43, 33.64, "US", january), 0.0)    // Atlanta EST
        assertEquals(0.0, approxUtcOffsetHours(-22.61, 63.99, "IS", july), 0.0)        // Iceland, no DST
        assertEquals(3.0, approxUtcOffsetHours(34.89, 32.01, "IL", july), 0.0)         // Tel Aviv
        assertEquals(-3.0, approxUtcOffsetHours(-58.54, -34.82, "AR", july), 0.0)      // Buenos Aires
        assertEquals(5.5, approxUtcOffsetHours(77.1, 28.57, "IN", july), 0.0)          // Delhi
        val clock = airportClock(july, 126.45, 37.46, "KR", LocalDate.of(2026, 7, 15))
        assertEquals("21:00", clock.label)
        assertTrue(clock.dayOffset == 0)
    }
}
