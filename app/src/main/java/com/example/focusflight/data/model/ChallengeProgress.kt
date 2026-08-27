package com.example.focusflight.data.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure progress math for docs/design/challenges.md's three types - no Room/AirportRepository/JNI
 * dependency, so it's directly unit-testable (see ChallengeProgressTest) independent of the
 * repository layer that calls into it.
 */
object ChallengeProgress {
    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle distance between two lat/lon points, in km. */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)
        val a = sinDLat * sinDLat +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinDLon * sinDLon
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * docs/design/challenges.md's Route progress formula:
     *
     * ```
     * progress = 1 - ( straight_line(current, destination) / straight_line(origin, destination) )
     * ```
     *
     * Clamped to 0f..1f. Deliberately allowed to legitimately *decrease* when a real onward
     * flight happens to point away from the destination - an accepted v1 limitation (straight
     * line is only a proxy for real routing), not a bug to fix here.
     */
    fun routeProgress(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        currentLat: Double,
        currentLon: Double
    ): Float {
        val originToDest = haversineKm(originLat, originLon, destLat, destLon)
        if (originToDest <= 0.0) return 1f
        val currentToDest = haversineKm(currentLat, currentLon, destLat, destLon)
        return (1.0 - (currentToDest / originToDest)).coerceIn(0.0, 1.0).toFloat()
    }
}

/**
 * The UI-facing 0f..1f progress for any challenge, per its own type's metric (challenges.md).
 * Kept as an extension function rather than a [Challenge] member/computed property so Room's
 * entity field-scanning never mistakes it for a column.
 */
fun Challenge.progressFraction(): Float = when (type) {
    ChallengeType.ROUTE -> routeProgressFraction
    ChallengeType.SET_COMPLETION ->
        if (setTotalMembers > 0) (visitedSetMembers.size.toFloat() / setTotalMembers).coerceIn(0f, 1f) else 0f
    ChallengeType.DISTANCE ->
        targetDistanceKm?.takeIf { it > 0 }
            ?.let { (cumulativeDistanceKm / it).toFloat().coerceIn(0f, 1f) }
            ?: 0f
}
