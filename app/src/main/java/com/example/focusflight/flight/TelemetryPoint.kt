package com.example.focusflight.flight

/**
 * A single point in a flight telemetry track.
 *
 * @param timeOffsetMs  Milliseconds since the start of the flight (t = 0 is the moment the
 *                      aircraft begins its ground-roll sequence).
 * @param longitude     WGS-84 longitude in degrees.
 * @param latitude      WGS-84 latitude in degrees.
 * @param altitude      Altitude above mean sea level in meters.
 */
data class TelemetryPoint(
    val timeOffsetMs: Long,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double
)
