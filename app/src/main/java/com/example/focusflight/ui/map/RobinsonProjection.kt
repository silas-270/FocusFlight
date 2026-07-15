package com.example.focusflight.ui.map

import java.util.Locale

object RobinsonProjection {
    private val LATS = floatArrayOf(0f, 5f, 10f, 15f, 20f, 25f, 30f, 35f, 40f, 45f, 50f, 55f, 60f, 65f, 70f, 75f, 80f, 85f, 90f)
    private val PLENS = floatArrayOf(1.0000f, 0.9986f, 0.9954f, 0.9900f, 0.9822f, 0.9730f, 0.9600f, 0.9430f, 0.9216f, 0.8962f, 0.8679f, 0.8350f, 0.7986f, 0.7597f, 0.7186f, 0.6732f, 0.6213f, 0.5722f, 0.5322f)
    private val PDFSER = floatArrayOf(0.0000f, 0.0620f, 0.1240f, 0.1860f, 0.2480f, 0.3100f, 0.3720f, 0.4340f, 0.4958f, 0.5571f, 0.6176f, 0.6769f, 0.7346f, 0.7903f, 0.8435f, 0.8936f, 0.9394f, 0.9761f, 1.0000f)

    fun project(lat: Float, lon: Float): Pair<Float, Float> {
        val absLat = kotlin.math.abs(lat).coerceAtMost(90f)
        val idx = (absLat / 5f).toInt().coerceIn(0, 17)
        val fraction = (absLat % 5f) / 5f

        // Interpolate length of parallel and Y offset
        val pLen = PLENS[idx] + (PLENS[idx + 1] - PLENS[idx]) * fraction
        val yOffset = PDFSER[idx] + (PDFSER[idx + 1] - PDFSER[idx]) * fraction

        // Convert to normalized coordinates (-1 to 1 range)
        val x = (lon / 180f) * pLen
        val y = yOffset * kotlin.math.sign(lat)

        return Pair(x, y)
    }

    /**
     * Translates latitude and longitude directly to SVG viewBox pixel coordinates.
     * Bounding box configured specifically for world-map.svg.
     */
    fun toSvgCoordinates(lat: Float, lon: Float): Pair<Float, Float> {
        val (rx, ry) = project(lat, lon)
        val svgX = 401.0f + rx * 445.0f
        val svgY = 546.0f - ry * 260.0f
        return Pair(svgX, svgY)
    }
}
