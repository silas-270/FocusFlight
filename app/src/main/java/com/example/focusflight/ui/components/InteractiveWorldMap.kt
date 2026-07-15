package com.example.focusflight.ui.components

import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asAndroidPath
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.ui.map.CountryPath
import com.example.focusflight.ui.map.RobinsonProjection

@Composable
fun InteractiveWorldMap(
    mapPaths: List<CountryPath>,
    visitedCountries: Set<String>,
    countryToContinent: Map<String, String>,
    completedContinents: Set<String>,
    modifier: Modifier = Modifier,
    originAirport: Airport? = null,
    routes: List<FlightRoute> = emptyList(),
    selectedRoute: FlightRoute? = null,
    animationProgress: Float = 0f
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0F172A)) // Slate 900 background
            .aspectRatio(784.077f / 458.627f) // Keep SVG aspect ratio
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (mapPaths.isEmpty()) return@Canvas

            val mapWidth = 784.077f
            val mapHeight = 458.627f

            val scaleX = size.width / mapWidth
            val scaleY = size.height / mapHeight
            val scale = minOf(scaleX, scaleY)

            val offsetX = (size.width - mapWidth * scale) / 2f
            val offsetY = (size.height - mapHeight * scale) / 2f

            translate(left = offsetX, top = offsetY) {
                scale(scale = scale, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    translate(left = -30.767f, top = -241.591f) {
                        // 1. Draw all country fills
                        mapPaths.forEach { countryPath ->
                            val countryCode = countryPath.countryCode
                            val isVisited = visitedCountries.contains(countryCode)
                            
                            val fillColor = if (isVisited) {
                                Color(0xFFF59E0B) // Amber / Orange
                            } else {
                                Color(0xFF1E293B) // Slate 800 (dark unvisited)
                            }

                            countryPath.paths.forEach { path ->
                                drawPath(
                                    path = path.asComposePath(),
                                    color = fillColor
                                )
                            }
                        }

                        // 2. Draw country borders/outlines
                        mapPaths.forEach { countryPath ->
                            val countryCode = countryPath.countryCode
                            val isVisited = visitedCountries.contains(countryCode)
                            val continent = countryToContinent[countryCode]
                            val isContinentCompleted = continent != null && completedContinents.contains(continent)

                            val strokeColor = when {
                                isContinentCompleted -> Color(0xFF10B981) // Emerald / Green for completed continents
                                isVisited -> Color(0xFFCBD5E1) // Silver / Slate 300 for visited
                                else -> Color(0xFF94A3B8) // Muted Silver / Slate 400 for unvisited
                            }

                            val strokeWidth = when {
                                isContinentCompleted -> 1.8f / scale // Thicker green outline
                                else -> 0.7f / scale // Slightly thicker standard border
                            }

                            countryPath.paths.forEach { path ->
                                drawPath(
                                    path = path.asComposePath(),
                                    color = strokeColor,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                        }

                        // 3. Draw routes if origin is present
                        originAirport?.let { origin ->
                            val (cxOrigin, cyOrigin) = RobinsonProjection.toSvgCoordinates(
                                origin.lat.toFloat(),
                                origin.lon.toFloat()
                            )

                            // Draw unselected routes
                            routes.forEach { route ->
                                if (route.id != selectedRoute?.id) {
                                    val (cxDest, cyDest) = RobinsonProjection.toSvgCoordinates(
                                        route.destLat.toFloat(),
                                        route.destLon.toFloat()
                                    )

                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(cxOrigin, cyOrigin)
                                        val dx = cxDest - cxOrigin
                                        val midX = (cxOrigin + cxDest) / 2f
                                        val midY = (cyOrigin + cyDest) / 2f
                                        val controlY = midY - kotlin.math.abs(dx) * 0.15f - 20f
                                        quadraticTo(midX, controlY, cxDest, cyDest)
                                    }

                                    drawPath(
                                        path = path,
                                        color = Color(0xFF94A3B8).copy(alpha = 0.4f), // Muted Haze
                                        style = Stroke(
                                            width = 1.5f / scale, // Scale stroke width so it stays constant size
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                                floatArrayOf(10f / scale, 10f / scale), 0f
                                            ),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    )

                                    drawCircle(
                                        color = Color(0xFF94A3B8).copy(alpha = 0.6f),
                                        radius = 2.5f / scale,
                                        center = androidx.compose.ui.geometry.Offset(cxDest, cyDest)
                                    )
                                }
                            }

                            // Draw selected route
                            selectedRoute?.let { route ->
                                val (cxDest, cyDest) = RobinsonProjection.toSvgCoordinates(
                                    route.destLat.toFloat(),
                                    route.destLon.toFloat()
                                )

                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(cxOrigin, cyOrigin)
                                    val dx = cxDest - cxOrigin
                                    val midX = (cxOrigin + cxDest) / 2f
                                    val midY = (cyOrigin + cyDest) / 2f
                                    val controlY = midY - kotlin.math.abs(dx) * 0.15f - 20f
                                    quadraticTo(midX, controlY, cxDest, cyDest)
                                }

                                // Glow
                                drawPath(
                                    path = path,
                                    color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                    style = Stroke(
                                        width = 6f / scale,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )

                                // Main selected path
                                drawPath(
                                    path = path,
                                    color = Color(0xFFF59E0B),
                                    style = Stroke(
                                        width = 3f / scale,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )

                                // Animated flying dot
                                try {
                                    val pathMeasure = android.graphics.PathMeasure(path.asAndroidPath(), false)
                                    val length = pathMeasure.length
                                    val pos = FloatArray(2)
                                    if (length > 0f) {
                                        pathMeasure.getPosTan(length * animationProgress, pos, null)
                                        val dotX = pos[0]
                                        val dotY = pos[1]

                                        drawCircle(
                                            color = Color(0xFFF59E0B).copy(alpha = 0.8f),
                                            radius = 2.5f / scale,
                                            center = androidx.compose.ui.geometry.Offset(dotX, dotY)
                                        )
                                        drawCircle(
                                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                            radius = (2.5f + 4f * (1f - animationProgress)) / scale,
                                            center = androidx.compose.ui.geometry.Offset(dotX, dotY),
                                            style = Stroke(width = 1f / scale)
                                        )
                                    }
                                } catch (e: Exception) {
                                    // Fallback
                                }

                                // Target ring
                                drawCircle(
                                    color = Color(0xFFF59E0B),
                                    radius = 3.5f / scale,
                                    center = androidx.compose.ui.geometry.Offset(cxDest, cyDest)
                                )
                                drawCircle(
                                    color = Color(0xFFF59E0B),
                                    radius = 7.5f / scale,
                                    center = androidx.compose.ui.geometry.Offset(cxDest, cyDest),
                                    style = Stroke(width = 1.2f / scale)
                                )
                            }

                            // Draw origin airport marker
                            drawCircle(
                                color = Color(0xFFF8FAFC), // OffWhite
                                radius = 4f / scale,
                                center = androidx.compose.ui.geometry.Offset(cxOrigin, cyOrigin)
                            )
                            drawCircle(
                                color = Color(0xFFF59E0B), // Amber
                                radius = 2f / scale,
                                center = androidx.compose.ui.geometry.Offset(cxOrigin, cyOrigin)
                            )
                        }
                    }
                }
            }
        }
    }
}
