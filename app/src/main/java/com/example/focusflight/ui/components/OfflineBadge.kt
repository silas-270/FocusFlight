package com.example.focusflight.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.network.NetworkMode

/**
 * "OFFLINE" status chip shown in the Hub header (the in-flight HUD draws its own non-button status pill). It draws nothing while
 * [mode] is ONLINE. The icon tells the pilot which kind of offline this is: a cloud for a lost
 * connection, the data-saver symbol when they switched Offline maps on in Settings.
 *
 * Sized to the 40dp square header buttons it sits next to.
 */
@Composable
fun OfflineBadge(mode: NetworkMode, modifier: Modifier = Modifier) {
    if (!mode.isOffline) return
    val dataSaver = mode == NetworkMode.OFFLINE_DATA_SAVER
    FocusBadge(
        text = "OFFLINE",
        modifier = modifier
            .height(40.dp)
            .semantics {
                contentDescription = if (dataSaver) "Offline: data saver on" else "Offline: no connection"
            },
        variant = BadgeVariant.Neutral,
        style = BadgeStyle.Filled,
        icon = if (dataSaver) Icons.Outlined.DataSaverOn else Icons.Outlined.CloudOff,
        shape = RoundedCornerShape(12.dp)
    )
}
