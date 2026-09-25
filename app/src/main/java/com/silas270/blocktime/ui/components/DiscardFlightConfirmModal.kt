package com.silas270.blocktime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silas270.blocktime.data.model.PausedFlight
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.OffWhite
import com.silas270.blocktime.ui.theme.Spacing

@Composable
fun DiscardFlightConfirmModal(
    flight: PausedFlight,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalMs = flight.durationMin * 60 * 1000L
    val elapsedMs = (flight.elapsedMs ?: 0L).coerceIn(0L, totalMs)
    val elapsedMinutes = (elapsedMs / (60 * 1000L)).toInt()
    val elapsedSeconds = ((elapsedMs % (60 * 1000L)) / 1000L).toInt()

    val elapsedText = when {
        elapsedMs <= 0L -> "0 min elapsed"
        elapsedMinutes == 0 -> "${elapsedSeconds}s elapsed"
        elapsedSeconds == 0 -> "${elapsedMinutes}m elapsed"
        else -> "${elapsedMinutes}m ${elapsedSeconds}s elapsed"
    }

    ScrimCardModal(onScrimTap = onDismiss) {
        ModalTitle("ACTIVE FLIGHT")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Proceeding will make you lose your flight progress.",
            style = MaterialTheme.typography.bodyMedium,
            color = Haze
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${flight.originIata} → ${flight.destIata}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = OffWhite
            )
            Text(
                text = "${flight.durationMin} min · $elapsedText",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Amber
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ModalButtonRow(
            dismissText = "CANCEL",
            // Names the loss rather than "CONTINUE", which everywhere else in the app is the safe,
            // non-destructive choice. Kept to one word: two Compact buttons share a 360dp-wide card.
            confirmText = "DISCARD",
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            isDestructive = true
        )
    }
}
