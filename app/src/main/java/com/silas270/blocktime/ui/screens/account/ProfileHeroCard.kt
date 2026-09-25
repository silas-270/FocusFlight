package com.silas270.blocktime.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.silas270.blocktime.ui.components.BadgeSize
import com.silas270.blocktime.ui.components.BadgeStyle
import com.silas270.blocktime.ui.components.BadgeVariant
import com.silas270.blocktime.ui.components.CardVariant
import com.silas270.blocktime.ui.components.FocusBadge
import com.silas270.blocktime.ui.components.FocusCard
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.DeepNavy
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.OffWhite
import com.silas270.blocktime.ui.theme.Radius
import com.silas270.blocktime.ui.theme.Slate
import com.silas270.blocktime.ui.theme.Spacing
import com.silas270.blocktime.ui.viewmodel.account.AccountUiState

/**
 * The Passport's identity card - avatar, name, pilot code, and home base/join-date chips. A
 * static display element: it no longer expands to reveal home-base actions (return home, change
 * home base moved to the Settings screen - see `SettingsScreen.kt`), so this card is read-only.
 */
@Composable
internal fun ProfileHeroCard(state: AccountUiState) {
    FocusCard(
        modifier = Modifier.fillMaxWidth(),
        variant = CardVariant.Surface,
        shape = RoundedCornerShape(Radius.ExtraLarge),
        contentPadding = PaddingValues(Spacing.Large)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Slate),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(Spacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.username.ifBlank { "Pilot" },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = OffWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FocusBadge(
                        text = "#${state.userCode}",
                        variant = BadgeVariant.Primary,
                        style = BadgeStyle.Translucent,
                        size = BadgeSize.Compact
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.homeAirportIata.isNotBlank()) {
                        InfoChip(
                            icon = Icons.Outlined.FlightTakeoff,
                            label = state.homeAirportIata
                        )
                    }
                    if (state.joinDateFormatted.isNotBlank()) {
                        InfoChip(
                            icon = Icons.Outlined.Star,
                            label = state.joinDateFormatted.removePrefix("Joined ")
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InfoChip(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Haze, modifier = Modifier.size(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Haze)
    }
}
