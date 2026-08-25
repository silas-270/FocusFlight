package com.example.focusflight.engine.headless

import android.util.Log
import com.example.focusflight.data.model.FlightRoute
import java.io.File

/**
 * Renders the "hub_route_map_{IATA}.png" preview image used on the Hub,
 * Onboarding, and post-flight destination screens. Single owner of the
 * fetch-routes -> render -> cache-prune sequence so it only needs fixing
 * in one place.
 */
class CesiumHeadlessMapRenderer(private val cacheDir: File) {

    sealed class Result {
        data class Success(val path: String, val fromCache: Boolean) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun renderRouteMap(
        centerIata: String,
        centerLat: Double,
        centerLon: Double,
        outboundRoutes: List<FlightRoute>,
        reuseCachedFile: Boolean = false
    ): Result {
        val outFile = File(cacheDir, "hub_route_map_$centerIata.png")

        if (reuseCachedFile && outFile.exists() && outFile.length() > 0) {
            Log.d(TAG, "Cached route map found for $centerIata. Reusing: ${outFile.absolutePath}")
            outFile.setLastModified(System.currentTimeMillis())
            MapImageCache.pruneMapCache(cacheDir)
            return Result.Success(outFile.absolutePath, fromCache = true)
        }

        val routesData = outboundRoutes
            .filter { it.distanceKm <= 10000.0 }
            .shuffled()
            .take(12)
            .map { route -> Pair(Pair(centerLat, centerLon), Pair(route.destLat, route.destLon)) }

        if (outFile.exists()) {
            outFile.delete()
        }

        return try {
            Log.d(TAG, "Triggering route rendering for ${routesData.size} routes ($centerIata)...")
            val success = CesiumHeadlessJnaBindings.renderRoutes(
                width = 1080,
                height = 1320,
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
    }
}
