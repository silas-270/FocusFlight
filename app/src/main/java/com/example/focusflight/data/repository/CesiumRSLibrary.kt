package com.example.focusflight.data.repository

import com.sun.jna.Structure
import com.sun.jna.Library
import com.sun.jna.Native

// Equivalent to Rust's LatLon
@Structure.FieldOrder("lat", "lon")
open class LatLon : Structure() {
    @JvmField var lat: Double = 0.0
    @JvmField var lon: Double = 0.0

    class ByValue : LatLon(), Structure.ByValue
}

// Equivalent to Rust's HeadlessRoute
@Structure.FieldOrder("start", "end")
open class HeadlessRoute : Structure() {
    @JvmField var start: LatLon = LatLon()
    @JvmField var end: LatLon = LatLon()

    class ByValue : HeadlessRoute(), Structure.ByValue
}

// Interface to load the shared library
interface CesiumRSLibrary : Library {
    companion object {
        val INSTANCE: CesiumRSLibrary by lazy {
            Native.load("cesium_rs", CesiumRSLibrary::class.java)
        }

        @Suppress("UNCHECKED_CAST")
        fun renderRoutes(
            width: Int,
            height: Int,
            routesData: List<Pair<Pair<Double, Double>, Pair<Double, Double>>>,
            outPath: String
        ): Boolean {
            val count = routesData.size
            if (count == 0) return false

            // Allocate contiguous memory for the array of structures
            val routeArray = HeadlessRoute().toArray(count) as Array<HeadlessRoute>
            for (i in 0 until count) {
                val data = routesData[i]
                routeArray[i].start.lat = data.first.first
                routeArray[i].start.lon = data.first.second
                routeArray[i].end.lat = data.second.first
                routeArray[i].end.lon = data.second.second
                // Sync Java memory to native memory buffer
                routeArray[i].write()
            }

            return INSTANCE.render_routes_headless(
                width = width,
                height = height,
                routes = routeArray[0],
                routesCount = count.toLong(),
                outPath = outPath
            )
        }
    }

    fun render_routes_headless(
        width: Int,
        height: Int,
        routes: HeadlessRoute,
        routesCount: Long,
        outPath: String
    ): Boolean
}
