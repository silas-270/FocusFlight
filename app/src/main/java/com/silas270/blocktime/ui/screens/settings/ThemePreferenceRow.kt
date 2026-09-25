package com.silas270.blocktime.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.runtime.Composable
import com.silas270.blocktime.data.model.ThemeMode

/** "Sky mode" - the light theme's toggle. Reflects whichever explicit mode is active; while the
 *  app is still following [ThemeMode.SYSTEM] (no explicit choice made yet), the switch shows
 *  whatever the system's current light/dark state resolves to, and the first tap turns that into
 *  an explicit override. */
@Composable
internal fun ThemePreferenceRow(
    isLightMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    // Named after what switching it on does. "Sky mode" with the current state as subtitle
    // ("Night cockpit" while off) left it unclear what the switch would change.
    PreferenceToggleRow(
        icon = Icons.Outlined.LightMode,
        title = "Light skies",
        subtitle = "Bright theme for daytime",
        checked = isLightMode,
        onToggle = onToggle
    )
}
