package com.example.focusflight.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.data.model.ThemeMode

/** "Sky mode" - the light theme's toggle. Lives on the Account screen since there is no
 *  dedicated Settings screen yet. Reflects whichever explicit mode is active; while the app is
 *  still following [ThemeMode.SYSTEM] (no explicit choice made yet), the switch shows whatever
 *  the system's current light/dark state resolves to, and the first tap turns that into an
 *  explicit override. */
@Composable
internal fun ThemePreferenceRow(
    isLightMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small + 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isLightMode) Icons.Outlined.LightMode else Icons.Outlined.NightsStay,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sky mode",
                style = MaterialTheme.typography.bodyLarge,
                color = OffWhite
            )
            Text(
                text = if (isLightMode) "Light skies" else "Night cockpit",
                style = MaterialTheme.typography.bodySmall,
                color = Haze
            )
        }
        Switch(
            checked = isLightMode,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Amber,
                checkedTrackColor = Amber.copy(alpha = 0.4f),
                uncheckedThumbColor = Haze,
                uncheckedTrackColor = Midnight
            )
        )
    }
}
