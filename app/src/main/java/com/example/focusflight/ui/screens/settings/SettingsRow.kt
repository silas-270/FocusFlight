package com.example.focusflight.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Radius
import com.example.focusflight.ui.theme.Spacing

/**
 * The one row style of the Settings screen: an amber icon in a dark tile, a title with a
 * subtitle, and a [trailing] control (a switch, or a chevron for rows that open something).
 * [interaction] is the whole row's click/toggle modifier, applied inside the rounded clip so its
 * ripple follows the corners. Toggle rows and home-base rows used to be two different designs.
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    interaction: Modifier,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {}
) {
    val contentAlpha = if (enabled) 1f else DisabledRowAlpha
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.Large))
            .background(DeepNavy)
            .then(interaction)
            .padding(horizontal = Spacing.Medium, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Midnight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Amber.copy(alpha = contentAlpha),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(Spacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = OffWhite.copy(alpha = contentAlpha)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Haze.copy(alpha = contentAlpha)
            )
        }
        trailing()
    }
}

private const val DisabledRowAlpha = 0.45f
