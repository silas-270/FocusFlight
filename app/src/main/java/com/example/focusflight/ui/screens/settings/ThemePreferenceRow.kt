package com.example.focusflight.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.runtime.Composable
import com.example.focusflight.data.model.ThemeMode

/** "Sky mode" - the light theme's toggle. Reflects whichever explicit mode is active; while the
 *  app is still following [ThemeMode.SYSTEM] (no explicit choice made yet), the switch shows
 *  whatever the system's current light/dark state resolves to, and the first tap turns that into
 *  an explicit override. */
@Composable
internal fun ThemePreferenceRow(
    isLightMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    PreferenceToggleRow(
        icon = if (isLightMode) Icons.Outlined.LightMode else Icons.Outlined.NightsStay,
        title = "Sky mode",
        subtitle = if (isLightMode) "Light skies" else "Night cockpit",
        checked = isLightMode,
        onToggle = onToggle
    )
}
