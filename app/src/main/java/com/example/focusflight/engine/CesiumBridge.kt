package com.example.focusflight.engine

object CesiumBridge {
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

    external fun nativeGetTelemetry(): DoubleArray

    external fun nativeSetRenderingEnabled(enabled: Boolean)

    external fun nativeLoadPendingFlight()
}
