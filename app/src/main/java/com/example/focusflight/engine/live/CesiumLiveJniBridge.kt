package com.example.focusflight.engine.live

object CesiumLiveJniBridge {
    init {
        System.loadLibrary("cesium_rs")
    }

    external fun nativeSetPendingFlight(
        depLon: Double,
        depLat: Double,
        arrLon: Double,
        arrLat: Double,
        durationMs: Long
    )

    external fun nativeSetProgress(progress: Double)

    external fun nativeSetCameraMode(mode: Int)

    const val MAP_STYLE_STANDARD: Int = 0
    const val MAP_STYLE_SATELLITE_TERRAIN: Int = 1
    const val MAP_STYLE_OFFLINE: Int = 2

    /**
     * Base map and terrain together: [MAP_STYLE_STANDARD] (CARTO dark basemap, flat globe),
     * [MAP_STYLE_SATELLITE_TERRAIN] (Esri imagery on 3D relief) or [MAP_STYLE_OFFLINE] (the
     * bundled Natural Earth vector map, rasterized on-device - no network at all, flat globe).
     * Unknown values fall back to Standard on the Rust side.
     */
    external fun nativeSetMapStyle(style: Int)

    const val DEFAULT_ROUTE_LINE_BEHIND_NM: Double = 40.0
    const val DEFAULT_ROUTE_LINE_AHEAD_NM: Double = 150.0

    /**
     * How much of the route line to draw: 0 = Full (whole route), 1 = Window (around aircraft),
     * 2 = Hidden (no line). The two distance parameters are in nautical miles and apply to mode 1.
     */
    external fun nativeSetRouteLineMode(mode: Int, behindNm: Double, aheadNm: Double)

    external fun nativeGetTelemetry(): DoubleArray

    /** [mode (0=Free/1=Tracking/2=Cockpit), pos.x, pos.y, pos.z, ori.x, ori.y, ori.z, ori.w] */
    external fun nativeGetCameraPose(): DoubleArray

    /** Applies a saved position/rotation the next time the view resets. Call
     *  nativeSetCameraMode first so the mode itself is already correct when this lands. */
    external fun nativeSetCameraPose(
        x: Double,
        y: Double,
        z: Double,
        qx: Double,
        qy: Double,
        qz: Double,
        qw: Double
    )

    external fun nativeSetRenderingEnabled(enabled: Boolean)

    external fun nativeSetSuspended(suspended: Boolean)

    external fun nativeDestroyEngine()

    external fun nativeLoadPendingFlight()

    /** Field elevations of the next flight's two airports, in metres. Optional - without it
     *  the flight is planned at sea level. Consumed by the next [nativeLoadPendingFlight]. */
    external fun nativeSetFieldElevations(depElevationM: Double, arrElevationM: Double)

    /** Debug-only performance-testing hook (see tools/run_perf_scenario.sh at the repo
     *  root): tags a captured Perfetto trace with [scenarioId] and switches camera mode
     *  for the steady-state scenarios (2=Free, 3=Tracking, 4=Cockpit). Only present in a
     *  `-Pcesium.profile=profiling` build (native `perf_trace` feature) — throws
     *  UnsatisfiedLinkError if called against a normal release .so, so callers must be
     *  gated behind BuildConfig.DEBUG (see PerfScenarioReceiver). */
    external fun nativeRunPerfScenario(scenarioId: Int)

    external fun nativeSetRunways(
        airportIds: IntArray,
        lengthFt: FloatArray,
        widthFt: FloatArray,
        leHeading: FloatArray,
        leLat: DoubleArray,
        leLon: DoubleArray,
        heHeading: FloatArray,
        heLat: DoubleArray,
        heLon: DoubleArray
    )
}
