package com.example.focusflight.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.Composable
import com.example.focusflight.data.network.NetworkMode

/** "Offline maps" - lets the pilot force offline mode to save data. With the switch off, the
 *  app still goes offline on its own when there is no connection; the subtitle says when that
 *  is happening, so the switch being off never looks broken. */
@Composable
internal fun OfflineMapsPreferenceRow(
    dataSaverEnabled: Boolean,
    networkMode: NetworkMode,
    onToggle: (Boolean) -> Unit
) {
    PreferenceToggleRow(
        icon = Icons.Outlined.CloudOff,
        title = "Offline maps",
        subtitle = when {
            dataSaverEnabled -> "Built-in map only · no downloads"
            networkMode == NetworkMode.OFFLINE_NO_CONNECTION -> "No connection · using built-in map"
            else -> "Save data with the built-in map"
        },
        checked = dataSaverEnabled,
        onToggle = onToggle
    )
}
