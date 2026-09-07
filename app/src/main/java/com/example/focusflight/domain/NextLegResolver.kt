package com.example.focusflight.domain

import com.example.focusflight.data.model.ChallengeProgress
import com.example.focusflight.data.model.ChallengeStatus
import com.example.focusflight.data.model.predefinedRoute
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.ChallengeRepository
import kotlin.math.abs
import kotlin.math.roundToInt

/** Typical jet cruise speed, km/h - only ever used by the fallback in [resolveNextLeg] below. */
private const val CRUISE_SPEED_KMH = 800.0

/** Floor for a synthesised leg duration. A leg that resolves to zero minutes would land the
 *  instant it took off. */
private const val MIN_SYNTHESISED_LEG_MIN = 30

/**
 * A booking, fully resolved - everything `Screen.CheckIn` needs, with nothing left for the pilot
 * to choose.
 */
data class PendingLeg(
    val originIata: String,
    val destIata: String,
    val durationMin: Int,
    val flightNumber: String
)

/**
 * The flight number for a booking to [destIata].
 *
 * Derived from the destination alone, which means it is *not* globally unique - two paused flights
 * to the same airport produce the same number. That is survivable only because nothing keys off it
 * (see `PausedFlight`, which is stored per slot rather than per flight number). Lifted out of
 * `CesiumGameActivity`'s Flight Search callback so the predefined-route path below produces
 * identical-looking bookings instead of a second, subtly different scheme.
 */
fun flightNumberFor(destIata: String): String = "FF-${abs(destIata.hashCode()) % 1000 + 100}"

/**
 * The next leg of a predefined-route challenge, or null if [challengeId] is not one.
 *
 * This is what replaces the Flight Search step for the predefined submode. A free-form Route
 * challenge still has a real choice to make - which onward flight moves it closer - so it keeps
 * going through Flight Search, and the null return is what routes it there unchanged. A predefined
 * itinerary has no choice left in it: the next hop is authored, so sending the pilot to a
 * destination picker with exactly one correct answer would be theatre.
 *
 * Duration is the leg's real scheduled time from the routes DB, unscaled - a long-haul leg is
 * meant to be paused and resumed rather than shrunk. The haversine fallback exists only for a
 * catalog entry whose leg is missing from `flights.db`: `PredefinedRouteCatalogTest` is supposed to
 * make that unreachable, and degrading to an estimate beats stranding a live challenge if it ever
 * slips through.
 */
suspend fun resolveNextLeg(
    challengeRepository: ChallengeRepository,
    airportRepository: AirportRepository,
    challengeId: Int
): PendingLeg? {
    val challenge = challengeRepository.getChallenge(challengeId) ?: return null
    if (challenge.status != ChallengeStatus.ACTIVE) return null
    val route = challenge.predefinedRoute() ?: return null

    val originIata = route.originOf(challenge.legIndex) ?: return null
    val destIata = route.destOf(challenge.legIndex) ?: return null

    val context = loadRouteContext(airportRepository, originIata, destIata)
    val durationMin = context.route?.durationMin
        ?: estimateDurationMin(context)
        ?: return null

    return PendingLeg(
        originIata = originIata,
        destIata = destIata,
        durationMin = durationMin,
        flightNumber = flightNumberFor(destIata)
    )
}

/** Great-circle distance over a cruise speed, for a leg the routes DB doesn't carry. Null when
 *  even the airports failed to resolve, which leaves the caller with nothing to book. */
private fun estimateDurationMin(context: RouteContext): Int? {
    val origin = context.origin ?: return null
    val dest = context.dest ?: return null
    val km = ChallengeProgress.haversineKm(origin.lat, origin.lon, dest.lat, dest.lon)
    return ((km / CRUISE_SPEED_KMH) * 60).roundToInt().coerceAtLeast(MIN_SYNTHESISED_LEG_MIN)
}
