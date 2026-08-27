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

    external fun nativeSetMapStyle(style: Int)

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
