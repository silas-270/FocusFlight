package com.example.focusflight.ui.components

import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.ui.map.CountryPath
import com.example.focusflight.ui.map.RobinsonProjection
import com.example.focusflight.ui.theme.MapCompletedContinentStroke
import com.example.focusflight.ui.theme.MapGraticule
import com.example.focusflight.ui.theme.MapOcean
import com.example.focusflight.ui.theme.MapRouteArc
import com.example.focusflight.ui.theme.MapUnvisitedLand
import com.example.focusflight.ui.theme.MapUnvisitedStroke
import com.example.focusflight.ui.theme.MapVisitedLand
import com.example.focusflight.ui.theme.MapVisitedStroke
import com.example.focusflight.ui.theme.OffWhite

// One merged outline per fill/stroke bucket instead of ~1000 individual country
// sub-paths. Compose Canvas issues drawPath as an immediate Skia call, so drawing
// every country separately (fill pass + border pass) was the ~65ms main-thread
// stall seen on first composition of the Travel Map card (profiled via
// `adb shell dumpsys gfxinfo` while opening the Account screen). Merging same-color
// countries into one android.graphics.Path via addPath collapses that to a handful
// of drawPath calls with identical visual output.
private class MergedMapPaths(
    val visitedFill: androidx.compose.ui.graphics.Path,
    val unvisitedFill: androidx.compose.ui.graphics.Path,
    val completedStroke: androidx.compose.ui.graphics.Path,
    val visitedStroke: androidx.compose.ui.graphics.Path,
    val unvisitedStroke: androidx.compose.ui.graphics.Path
)

/**
 * Single-entry memo for [buildMergedMapPaths], keyed on the inputs that actually vary.
 *
 * The `remember` at the call site only survives as long as the composition, and both screens that
 * draw this map own a ViewModel that is destroyed on `popBackStack` - so every reopen of the
 * Passport rebuilt all ~1000 country sub-paths from scratch, on the composition thread, for a
 * result identical to the one just thrown away. The merge inputs only change when the pilot
 * actually visits somewhere new.
 *
 * One entry rather than a map: the Passport and Flight Search draw the same visited-set as each
 * other, so a second slot would never be used. Guarded because compositions on different threads
 * could in principle both miss at once - the worst case is a duplicated merge, never a torn read.
 */
private object MergedMapPathsCache {
    private data class Key(
        val mapPaths: List<CountryPath>,
        val visitedCountries: Set<String>,
        val countryToContinent: Map<String, String>,
        val completedContinents: Set<String>
    )

    private var key: Key? = null
    private var value: MergedMapPaths? = null

    fun get(
        mapPaths: List<CountryPath>,
        visitedCountries: Set<String>,
        countryToContinent: Map<String, String>,
        completedContinents: Set<String>
    ): MergedMapPaths {
        val requested = Key(mapPaths, visitedCountries, countryToContinent, completedContinents)
        synchronized(this) {
            if (key == requested) value?.let { return it }
        }
        val merged = buildMergedMapPaths(mapPaths, visitedCountries, countryToContinent, completedContinents)
        synchronized(this) {
            key = requested
            value = merged
        }
        return merged
    }
}

private fun buildMergedMapPaths(
    mapPaths: List<CountryPath>,
    visitedCountries: Set<String>,
    countryToContinent: Map<String, String>,
    completedContinents: Set<String>
): MergedMapPaths {
    val visitedFill = Path()
    val unvisitedFill = Path()
    val completedStroke = Path()
    val visitedStroke = Path()
    val unvisitedStroke = Path()

    mapPaths.forEach { countryPath ->
        val countryCode = countryPath.countryCode
        val isVisited = visitedCountries.contains(countryCode)
        val continent = countryToContinent[countryCode]
        val isContinentCompleted = continent != null && completedContinents.contains(continent)

        val fillTarget = if (isVisited) visitedFill else unvisitedFill
        val strokeTarget = when {
            isContinentCompleted -> completedStroke
            isVisited -> visitedStroke
            else -> unvisitedStroke
        }

        countryPath.paths.forEach { path ->
            val androidPath = path.asAndroidPath()
            fillTarget.addPath(androidPath)
            strokeTarget.addPath(androidPath)
        }
    }

    return MergedMapPaths(
        visitedFill = visitedFill.asComposePath(),
        unvisitedFill = unvisitedFill.asComposePath(),
        completedStroke = completedStroke.asComposePath(),
        visitedStroke = visitedStroke.asComposePath(),
        unvisitedStroke = unvisitedStroke.asComposePath()
    )
}

/**
 * World map drawn in Robinson projection using paths extracted from natural_earth_vectors.svg.
 *
 * Visited countries are filled with Amber; unvisited countries are dark slate.
 */
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
    val merged = remember(mapPaths, visitedCountries, countryToContinent, completedContinents) {
        MergedMapPathsCache.get(mapPaths, visitedCountries, countryToContinent, completedContinents)
    }

    Box(
        modifier = modifier
            .background(MapOcean)
            .aspectRatio(784.077f / 458.627f) // Keep SVG aspect ratio
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Own graphics layer: the map is ~1000 drawPath calls (a fill pass plus a
                // border pass over every country) but its content is static. With its own
                // render node the display list is recorded once and merely re-positioned
                // while the profile list scrolls, instead of re-executing every path on
                // each frame — which was costing frames over budget during scrolling.
                .graphicsLayer { }
        ) {
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
                        // 1. Draw all country fills (one merged path per fill color)
                        drawPath(path = merged.unvisitedFill, color = MapUnvisitedLand)
                        drawPath(path = merged.visitedFill, color = MapVisitedLand)

                        // 2. Draw country borders/outlines (one merged path per stroke bucket)
                        drawPath(
                            path = merged.unvisitedStroke,
                            color = MapUnvisitedStroke,
                            style = Stroke(width = 0.7f / scale)
                        )
                        drawPath(
                            path = merged.visitedStroke,
                            color = MapVisitedStroke,
                            style = Stroke(width = 0.7f / scale)
                        )
                        drawPath(
                            path = merged.completedStroke,
                            color = MapCompletedContinentStroke,
                            style = Stroke(width = 1.8f / scale) // Thicker outline
                        )

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
                                        color = MapGraticule.copy(alpha = 0.4f),
                                        style = Stroke(
                                            width = 1.5f / scale,
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                                floatArrayOf(10f / scale, 10f / scale), 0f
                                            ),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    )

                                    drawCircle(
                                        color = MapGraticule.copy(alpha = 0.6f),
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
                                    color = MapRouteArc.copy(alpha = 0.2f),
                                    style = Stroke(
                                        width = 6f / scale,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )

                                // Main selected path
                                drawPath(
                                    path = path,
                                    color = MapRouteArc,
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
                                            color = MapRouteArc.copy(alpha = 0.8f),
                                            radius = 2.5f / scale,
                                            center = androidx.compose.ui.geometry.Offset(dotX, dotY)
                                        )
                                        drawCircle(
                                            color = MapRouteArc.copy(alpha = 0.2f),
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
                                    color = MapRouteArc,
                                    radius = 3.5f / scale,
                                    center = androidx.compose.ui.geometry.Offset(cxDest, cyDest)
                                )
                                drawCircle(
                                    color = MapRouteArc,
                                    radius = 7.5f / scale,
                                    center = androidx.compose.ui.geometry.Offset(cxDest, cyDest),
                                    style = Stroke(width = 1.2f / scale)
                                )
                            }

                            // Draw origin airport marker
                            drawCircle(
                                color = OffWhite,
                                radius = 4f / scale,
                                center = androidx.compose.ui.geometry.Offset(cxOrigin, cyOrigin)
                            )
                            drawCircle(
                                color = MapRouteArc,
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
