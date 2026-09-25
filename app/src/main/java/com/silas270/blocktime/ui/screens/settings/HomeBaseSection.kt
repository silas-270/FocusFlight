package com.silas270.blocktime.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.ui.components.ScrimCardModal
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.DeepNavy
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.Midnight
import com.silas270.blocktime.ui.theme.OffWhite
import com.silas270.blocktime.ui.theme.Slate
import com.silas270.blocktime.ui.theme.Spacing
import com.silas270.blocktime.ui.viewmodel.account.AccountUiState

/**
 * Story Mode's home-base actions (docs/modes.md) - return home, and change where home
 * is. They live on the Passport rather than the Challenges screen because neither is a *session*:
 * return-home is an instant teleport, and changing home base is an identity edit.
 *
 * They sit under their own "HOME BASE" header on the Settings screen rather than at the top of the
 * Passport: both are gated by long cooldowns (7 and 30 days) and are close to never used, so
 * permanent real estate on the Passport overstated them badly.
 */
@Composable
internal fun HomeBaseActions(state: AccountUiState, onReturnHomeClick: () -> Unit, onChangeHomeBaseClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeBaseActionRow(
            icon = Icons.Outlined.FlightLand,
            title = "Return home",
            subtitle = if (state.returnHomeEligible) {
                "Teleport instantly to ${state.homeAirportIata.ifBlank { "your home base" }}"
            } else {
                "Available in ${formatCooldownRemaining(state.returnHomeRemainingMillis)}"
            },
            enabled = state.returnHomeEligible,
            onClick = onReturnHomeClick
        )
        Spacer(modifier = Modifier.height(Spacing.Small))
        HomeBaseActionRow(
            icon = Icons.Outlined.EditLocationAlt,
            title = "Change home base",
            subtitle = if (state.changeHomeBaseEligible) {
                "Pick a new home base airport"
            } else {
                "Available in ${formatCooldownRemaining(state.changeHomeBaseRemainingMillis)}"
            },
            enabled = state.changeHomeBaseEligible,
            onClick = onChangeHomeBaseClick
        )
    }
}

/** N days / N hours, coarse enough for a settings-style subtitle - not a live countdown (see
 *  AccountViewModel.refreshHomeBaseCooldowns's doc comment on why a ticking timer isn't needed). */
private fun formatCooldownRemaining(millis: Long): String {
    val totalHours = (millis / (60 * 60 * 1000L)).coerceAtLeast(0L)
    val days = totalHours / 24
    val hours = totalHours % 24
    return when {
        days > 0 && hours > 0 -> "${days}d ${hours}h"
        days > 0 -> "${days}d"
        totalHours > 0 -> "${totalHours}h"
        else -> "under an hour"
    }
}

@Composable
private fun HomeBaseActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        interaction = Modifier.clickable(enabled = enabled, onClick = onClick),
        enabled = enabled
    ) {
        // Says "this opens something" - and is left out while the action is on cooldown.
        if (enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Haze
            )
        }
    }
}

// ── Modals ───────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ReturnHomeConfirmModal(
    homeAirportIata: String,
    currentAirportIata: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Destination of the paused Story flight this teleport discards, if any
     *  (see `AccountViewModel.returnHome`). */
    discardsPausedFlightTo: String? = null
) {
    ScrimCardModal(onScrimTap = onDismiss) {
        Text(
            text = "RETURN HOME?",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = OffWhite
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (currentAirportIata.isNotBlank() && currentAirportIata != homeAirportIata) {
                "You'll teleport instantly from $currentAirportIata to your home base, $homeAirportIata. You can do this again in 7 days."
            } else {
                "You'll teleport instantly to your home base, $homeAirportIata. You can do this again in 7 days."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Haze
        )
        if (discardsPausedFlightTo != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your paused flight to $discardsPausedFlightTo departs from your current airport, so it will be cancelled.",
                style = MaterialTheme.typography.bodyMedium,
                color = Amber
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        HomeBaseModalButtonRow(dismissText = "CANCEL", confirmText = "TELEPORT", onDismiss = onDismiss, onConfirm = onConfirm)
    }
}

/**
 * Local copy of `QuestLogContent.kt`'s private `ModalButtonRow` (same look: a 1:1 weighted Slate/
 * Amber pill pair). That function is private to its file and this phase is meant to stay isolated
 * from the Challenges surface (per the phase brief), so this small, already-duplicated-once
 * pattern is copied again rather than promoting it to a shared component - a minor, low-risk
 * duplication, not a new convention.
 */
@Composable
private fun HomeBaseModalButtonRow(
    dismissText: String,
    confirmText: String?,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)?
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Slate)
                .clickable(onClick = onDismiss)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dismissText,
                color = OffWhite,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
        if (confirmText != null && onConfirm != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Amber)
                    .clickable(onClick = onConfirm)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = confirmText,
                    color = DeepNavy,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }
    }
}
