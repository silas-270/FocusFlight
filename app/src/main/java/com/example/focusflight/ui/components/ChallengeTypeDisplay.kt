package com.example.focusflight.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.focusflight.data.model.ChallengeType

/** Shared icon/label per [ChallengeType] - used by the quest log's cards/browse list and by the
 *  per-leg tick-up / completion celebration screens, so all three read the same type consistently
 *  instead of each re-picking its own icon/label. */
fun challengeTypeIcon(type: ChallengeType): ImageVector = when (type) {
    ChallengeType.ROUTE -> Icons.Outlined.FlightTakeoff
    ChallengeType.SET_COMPLETION -> Icons.Outlined.Public
    ChallengeType.DISTANCE -> Icons.Outlined.Straighten
}

fun challengeTypeLabel(type: ChallengeType): String = when (type) {
    ChallengeType.ROUTE -> "ROUTE"
    ChallengeType.SET_COMPLETION -> "SET"
    ChallengeType.DISTANCE -> "DISTANCE"
}
