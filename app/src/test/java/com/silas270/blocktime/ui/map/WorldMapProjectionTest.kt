package com.silas270.blocktime.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldMapProjectionTest {

    // Expected positions come from the fit against world-map.svg, and each sits inside its
    // country's outline there.
    private fun assertPlaced(lat: Float, lon: Float, svgX: Float, svgY: Float) {
        val (x, y) = WorldMapProjection.toSvgCoordinates(lat, lon)
        assertEquals(svgX, x, 0.05f)
        assertEquals(svgY, y, 0.05f)
    }

    @Test
    fun `airports land on their country in the artwork`() {
        assertPlaced(51.47f, -0.45f, 405.46f, 392.10f) // LHR
        assertPlaced(1.36f, 103.99f, 661.31f, 527.68f) // SIN
        assertPlaced(33.94f, -118.41f, 145.12f, 428.61f) // LAX
        assertPlaced(-33.95f, 151.18f, 755.83f, 646.25f) // SYD
        assertPlaced(-23.43f, -46.47f, 293.99f, 594.08f) // GRU
    }
}
