package com.example.focusflight.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConnectingAirports
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeType
import com.example.focusflight.data.model.CuratedChallengeTemplate

object ChallengeIcons {
    private val REGISTRY = mapOf(
        "flight_takeoff" to Icons.Outlined.FlightTakeoff,
        "flight" to Icons.Outlined.Flight,
        "connecting_airports" to Icons.Outlined.ConnectingAirports,
        "public" to Icons.Outlined.Public,
        "globe" to Icons.Outlined.Public,
        "language" to Icons.Outlined.Language,
        "straighten" to Icons.Outlined.Straighten,
        "speed" to Icons.Outlined.Speed,
        "event_repeat" to Icons.Outlined.EventRepeat,
        "fire" to Icons.Outlined.LocalFireDepartment,
        "city" to Icons.Outlined.LocationCity,
        "flag" to Icons.Outlined.Flag,
        "explore" to Icons.Outlined.Explore,
        "map" to Icons.Outlined.Map,
        "timer" to Icons.Outlined.Timer,
        "star" to Icons.Outlined.Star
    )

    fun get(name: String?): ImageVector? = name?.let { REGISTRY[it] }
}

/** Shared default icon per [ChallengeType]. */
fun challengeTypeIcon(type: ChallengeType): ImageVector = when (type) {
    ChallengeType.ROUTE -> Icons.Outlined.FlightTakeoff
    ChallengeType.SET_COMPLETION -> Icons.Outlined.Public
    ChallengeType.DISTANCE -> Icons.Outlined.Straighten
    ChallengeType.STREAK -> Icons.Outlined.EventRepeat
}

/** Resolves the icon for a challenge, respecting optional [iconName] override. */
fun challengeIcon(type: ChallengeType, iconName: String? = null): ImageVector {
    return ChallengeIcons.get(iconName) ?: challengeTypeIcon(type)
}

fun Challenge.icon(): ImageVector = challengeIcon(type, iconName)

fun CuratedChallengeTemplate.resolvedIcon(): ImageVector = challengeIcon(type, iconName)

fun challengeTypeLabel(type: ChallengeType): String = when (type) {
    ChallengeType.ROUTE -> "ROUTE"
    ChallengeType.SET_COMPLETION -> "SET"
    ChallengeType.DISTANCE -> "DISTANCE"
    ChallengeType.STREAK -> "STREAK"
}

fun challengeTypeDescription(type: ChallengeType): String = when (type) {
    ChallengeType.ROUTE -> "Fly a connected multi-leg route from origin to destination."
    ChallengeType.SET_COMPLETION -> "Visit every destination in a curated set of regions or cities."
    ChallengeType.DISTANCE -> "Accumulate total distance flown across your focus sessions."
    ChallengeType.STREAK -> "Fly consecutive days in a row without breaking your streak."
}
