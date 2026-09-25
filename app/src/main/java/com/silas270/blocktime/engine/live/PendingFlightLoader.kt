package com.silas270.blocktime.engine.live

import com.silas270.blocktime.data.repository.AirportRepository

/**
 * Looks up the origin/destination airports and their longest runways, then
 * pushes them (with the field elevations) across the JNI bridge as the flight the Rust engine should
 * load next. Single owner of that lookup-and-push sequence so the two
 * navigation call sites (resuming a flight from the Hub, confirming a route
 * in Flight Search) can't drift from each other.
 */
class PendingFlightLoader(private val airportRepository: AirportRepository) {

    /** The flight this loader last pushed to the engine in this process, or null if none yet. */
    @Volatile
    private var loadedFlightKey: String? = null

    /**
     * Loads the flight only if it isn't already the one in the engine. Check-In and In-Flight call
     * this on entry: normally the booking/resume path has already loaded it and this is a no-op,
     * but when Navigation restores either screen after process death the fresh engine is empty
     * (no aircraft, no route, zeroed telemetry) and nothing else would ever load it.
     */
    suspend fun ensureLoaded(originIata: String, destIata: String, durationMin: Int): Boolean {
        if (loadedFlightKey == keyOf(originIata, destIata, durationMin)) return true
        return loadPendingFlight(originIata, destIata, durationMin)
    }

    suspend fun loadPendingFlight(originIata: String, destIata: String, durationMin: Int): Boolean {
        val origin = airportRepository.getAirportByIata(originIata) ?: return false
        val dest = airportRepository.getAirportByIata(destIata) ?: return false

        val originRunway = airportRepository.getRunwaysForAirport(origin.id).maxByOrNull { it.lengthFt }
        val destRunway = airportRepository.getRunwaysForAirport(dest.id).maxByOrNull { it.lengthFt }
        val allRunways = listOfNotNull(originRunway, destRunway)

        CesiumLiveJniBridge.nativeSetRunways(
            allRunways.map { it.airportId }.toIntArray(),
            allRunways.map { it.lengthFt }.toFloatArray(),
            allRunways.map { it.widthFt }.toFloatArray(),
            allRunways.map { it.leHeading }.toFloatArray(),
            allRunways.map { it.leLat }.toDoubleArray(),
            allRunways.map { it.leLon }.toDoubleArray(),
            allRunways.map { it.heHeading }.toFloatArray(),
            allRunways.map { it.heLat }.toDoubleArray(),
            allRunways.map { it.heLon }.toDoubleArray()
        )
        // The engine fits the aircraft onto whatever ground it draws near each airport, so
        // the real elevations are right in every map style, flat or with relief.
        CesiumLiveJniBridge.nativeSetFieldElevations(
            origin.elevationFt * FEET_TO_METERS, dest.elevationFt * FEET_TO_METERS
        )
        CesiumLiveJniBridge.nativeSetPendingFlight(
            origin.lon, origin.lat, dest.lon, dest.lat, (durationMin * 60 * 1000).toLong()
        )
        CesiumLiveJniBridge.nativeLoadPendingFlight()
        loadedFlightKey = keyOf(originIata, destIata, durationMin)
        return true
    }

    private companion object {
        const val FEET_TO_METERS = 0.3048
        fun keyOf(originIata: String, destIata: String, durationMin: Int) = "$originIata-$destIata-$durationMin"
    }
}
