package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Schedule
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
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.account.AccountUiState
import java.util.Locale

@Composable
internal fun StatsGrid2x2(state: AccountUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            value = state.stats.totalFlights.toString(),
            label = "FLIGHTS",
            icon = Icons.Outlined.AirplanemodeActive
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = state.stats.airportsVisited.toString(),
            label = "AIRPORTS",
            icon = Icons.Outlined.FlightLand
        )
    }
}

@Composable
internal fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: ImageVector
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DeepNavy)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Slate),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = OffWhite
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Haze,
                letterSpacing = 0.5.sp
            )
        }
    }
}
