package com.silas270.blocktime.ui.map

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Places latitude/longitude on the world-map.svg artwork.
 *
 * The artwork is a simplified, hand-edited map that matches no textbook projection exactly - the
 * old Robinson mapping put airports 10+ SVG units away from their country (Singapore, LAX,
 * Johannesburg, Anchorage). It is closest to Winkel Tripel centered on [CENTRAL_MERIDIAN], and a
 * cubic polynomial on top absorbs the rest of the drift. The coefficients were fitted so that each of
 * the ~4000 airports in flights.db lands inside its own country's outline; with them the median
 * airport is inside its country and 90% are within 2.5 SVG units of it (coastal airports sit
 * just off the simplified coastline). Refit if world-map.svg is ever replaced.
 */
object WorldMapProjection {
    private const val CENTRAL_MERIDIAN = 5.66

    // Terms x^i * y^j of the Winkel Tripel coordinates, in the order of the coefficients below.
    private val EXPONENTS = arrayOf(
        0 to 0, 0 to 1, 0 to 2, 0 to 3, 1 to 0, 1 to 1, 1 to 2, 2 to 0, 2 to 1, 3 to 0
    )
    private val SVG_X = doubleArrayOf(
        418.7659353637702, -0.7411732644820042, 4.474476250149516, -4.7658529302618415,
        172.60107721095946, -3.671774493309446, 10.45643294018942, 0.9322441405560733,
        2.5397143653091168, -0.5950094168661253
    )
    private val SVG_Y = doubleArrayOf(
        531.4962839740667, -145.15535255822908, 0.14252492720395793, -12.599653568177342,
        0.027188089969259366, -0.6862601656150353, -0.35549910129308415, 0.5198033189381306,
        -5.062420988762149, -0.3324491369974325
    )

    // cos of Winkel Tripel's standard parallel, arccos(2 / pi).
    private const val COS_STANDARD_PARALLEL = 2.0 / Math.PI

    /** Unscaled Winkel Tripel coordinates, y pointing north. */
    private fun winkelTripel(lat: Double, lon: Double): Pair<Double, Double> {
        val phi = Math.toRadians(lat)
        // Not wrapped to +-180: the artwork's edges sit at the antimeridian, so a longitude just
        // past it after the shift must stay on its own side of the map.
        val lambda = Math.toRadians(lon - CENTRAL_MERIDIAN)
        val alpha = acos(cos(phi) * cos(lambda / 2))
        val sincAlpha = if (alpha < 1e-9) 1.0 else sin(alpha) / alpha
        val x = 0.5 * (lambda * COS_STANDARD_PARALLEL + 2 * cos(phi) * sin(lambda / 2) / sincAlpha)
        val y = 0.5 * (phi + sin(phi) / sincAlpha)
        return x to y
    }

    /** Latitude/longitude to world-map.svg viewBox coordinates. */
    fun toSvgCoordinates(lat: Float, lon: Float): Pair<Float, Float> {
        val (x, y) = winkelTripel(lat.toDouble(), lon.toDouble())
        var svgX = 0.0
        var svgY = 0.0
        EXPONENTS.forEachIndexed { k, (i, j) ->
            val term = pow(x, i) * pow(y, j)
            svgX += SVG_X[k] * term
            svgY += SVG_Y[k] * term
        }
        return svgX.toFloat() to svgY.toFloat()
    }

    private fun pow(base: Double, exponent: Int): Double {
        var result = 1.0
        repeat(exponent) { result *= base }
        return result
    }
}
