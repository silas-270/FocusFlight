package com.example.focusflight.engine.headless

import android.util.Log
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.data.local.airport.AirportDataException
import com.example.focusflight.data.repository.AirportRepository
import java.io.File

/**
 * Renders the route map preview image (see [MapImageCache.fileNameFor]) used on the Hub,
 * Onboarding, and post-flight destination screens. Single owner of the
 * fetch-routes -> render -> cache-prune sequence so it only needs fixing
 * in one place.
 */
class CesiumHeadlessMapRenderer(private val cacheDir: File) {

    sealed class Result {
        data class Success(val path: String, val fromCache: Boolean) : Result()
        data class Failure(val message: String) : Result()
    }

    /**
     * Fetches [airport]'s outbound routes and renders them, so callers don't each re-implement
     * the "fetch routes, then render" pairing - previously duplicated in `OnboardingViewModel`,
     * `HubViewModel`, and `InFlightViewModel`.
     */
    suspend fun renderRouteMapForAirport(
        airportRepository: AirportRepository,
        airport: Airport,
        reuseCachedFile: Boolean = false
    ): Result {
        // Every caller runs this in a background scope whose failure surfaces as a retryable
        // "map unavailable" state, so a broken route query belongs in [Result.Failure] rather than
        // thrown into a coroutine that has no handler for it.
        val outboundRoutes = try {
            airportRepository.getOutboundRoutes(airport.iataCode)
        } catch (e: AirportDataException) {
            Log.e(TAG, "Route fetch failed for ${airport.iataCode}", e)
            return Result.Failure("Failed to load routes.")
        }
        return renderRouteMap(airport.iataCode, airport.lat, airport.lon, outboundRoutes, reuseCachedFile)
    }

    suspend fun renderRouteMap(
        centerIata: String,
        centerLat: Double,
        centerLon: Double,
        outboundRoutes: List<FlightRoute>,
        reuseCachedFile: Boolean = false
    ): Result {
        val outFile = File(cacheDir, MapImageCache.fileNameFor(centerIata))

        if (reuseCachedFile && outFile.exists() && outFile.length() > 0) {
            Log.d(TAG, "Cached route map found for $centerIata. Reusing: ${outFile.absolutePath}")
            outFile.setLastModified(System.currentTimeMillis())
            MapImageCache.pruneMapCache(cacheDir)
            return Result.Success(outFile.absolutePath, fromCache = true)
        }

        val routesData = selectRoutesToRender(outboundRoutes)
            .map { route -> Pair(Pair(centerLat, centerLon), Pair(route.destLat, route.destLon)) }

        if (outFile.exists()) {
            outFile.delete()
        }

        return try {
            Log.d(TAG, "Triggering route rendering for ${routesData.size} routes ($centerIata)...")
            val success = CesiumHeadlessJnaBindings.renderRoutes(
                width = renderWidth,
                height = renderHeight,
                routesData = routesData,
                outPath = outFile.absolutePath
            )

            if (success && outFile.exists()) {
                Log.d(TAG, "Route rendering succeeded: ${outFile.absolutePath}")
                MapImageCache.pruneMapCache(cacheDir)
                Result.Success(outFile.absolutePath, fromCache = false)
            } else {
                Log.e(TAG, "Route rendering failed or file not created for $centerIata.")
                Result.Failure("Failed to render map.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in route rendering for $centerIata", e)
            Result.Failure("Failed to render map.")
        }
    }

    companion object {
        private const val TAG = "CesiumHeadlessMapRenderer"
        /** Reference size: an exact 1:1 fit for the Hub globe box on the S23 it was tuned on. */
        internal const val RENDER_WIDTH = 1080
        internal const val RENDER_HEIGHT = 1670

        /**
         * Actual render size: the reference aspect scaled to this display's shortest side, so the
         * Hub globe stays pixel-sharp on higher-resolution screens where the UI is upscaled (see
         * ProvideDesignDensity) instead of stretching a 1080px image. Identical on the S23;
         * bounded so a very dense panel can't make the offscreen render arbitrarily expensive.
         */
        private val renderWidth: Int by lazy {
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            minOf(metrics.widthPixels, metrics.heightPixels).coerceIn(RENDER_WIDTH, MAX_RENDER_WIDTH)
        }
        private val renderHeight: Int by lazy { renderWidth * RENDER_HEIGHT / RENDER_WIDTH }
        private const val MAX_RENDER_WIDTH = 1440
        internal const val MAX_DISTANCE_KM = 10000.0
        internal const val MAX_ROUTES = 12
    }
}

/**
 * Picks which outbound routes get drawn on a rendered map: routes within
 * [CesiumHeadlessMapRenderer.MAX_DISTANCE_KM], capped at
 * [CesiumHeadlessMapRenderer.MAX_ROUTES] and shuffled so repeated renders
 * for a busy hub don't always show the same subset. Pulled out as a pure
 * function so the selection logic is testable without the native renderer.
 */
internal fun selectRoutesToRender(routes: List<FlightRoute>): List<FlightRoute> =
    routes
        .filter { it.distanceKm <= CesiumHeadlessMapRenderer.MAX_DISTANCE_KM }
        .shuffled()
        .take(CesiumHeadlessMapRenderer.MAX_ROUTES)
