package com.example.focusflight.ui.screens.settings

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight

/**
 * The Settings screen's switch row. The whole row toggles, not just the switch: the row is a
 * large card, and a tap on its text used to do nothing.
 */
@Composable
internal fun PreferenceToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        interaction = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Amber,
                checkedTrackColor = Amber.copy(alpha = 0.4f),
                uncheckedThumbColor = Haze,
                uncheckedTrackColor = Midnight
            )
        )
    }
}
