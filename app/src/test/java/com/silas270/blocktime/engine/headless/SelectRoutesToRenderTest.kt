package com.silas270.blocktime.engine.headless

import com.silas270.blocktime.data.model.FlightRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectRoutesToRenderTest {

    private fun route(destIata: String, distanceKm: Double) = FlightRoute(
        id = distanceKm.toInt(),
        originIata = "STR",
        destIata = destIata,
        distanceKm = distanceKm,
        durationMin = 120,
        carriers = "",
        destName = "",
        destMunicipality = "",
        destCountry = "",
        destLat = 0.0,
        destLon = 0.0
    )

    @Test
    fun `drops routes farther than the max render distance`() {
        val routes = listOf(
            route("NEAR", 500.0),
            route("FAR", CesiumHeadlessMapRenderer.MAX_DISTANCE_KM + 1.0)
        )

        val selected = selectRoutesToRender(routes)

        assertEquals(listOf("NEAR"), selected.map { it.destIata })
    }

    @Test
    fun `keeps routes exactly at the max render distance`() {
        val routes = listOf(route("EDGE", CesiumHeadlessMapRenderer.MAX_DISTANCE_KM))

        val selected = selectRoutesToRender(routes)

        assertEquals(listOf("EDGE"), selected.map { it.destIata })
    }

    @Test
    fun `caps the result at the max route count`() {
        val routes = (1..50).map { route("D$it", 100.0) }

        val selected = selectRoutesToRender(routes)

        assertEquals(CesiumHeadlessMapRenderer.MAX_ROUTES, selected.size)
    }

    @Test
    fun `never returns a route that was filtered out, regardless of shuffle`() {
        val inRange = (1..20).map { route("IN$it", 100.0) }
        val outOfRange = (1..20).map { route("OUT$it", CesiumHeadlessMapRenderer.MAX_DISTANCE_KM + 1.0) }

        repeat(20) {
            val selected = selectRoutesToRender(inRange + outOfRange)
            assertTrue(selected.all { it.distanceKm <= CesiumHeadlessMapRenderer.MAX_DISTANCE_KM })
        }
    }

    @Test
    fun `empty input produces empty output`() {
        assertEquals(emptyList<FlightRoute>(), selectRoutesToRender(emptyList()))
    }
}
