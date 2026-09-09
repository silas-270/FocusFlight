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
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.components.FocusStatCard
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.data.model.isOpenAt
import com.example.focusflight.ui.viewmodel.account.AccountUiState
import java.time.ZoneId
import java.util.Locale

/**
 * The tour row only appears once there is something worth reporting: a tour that is still running
 * and has at least this many days on it. "1 OF 1 DAYS" on the day a tour starts is noise, and
 * showing a ratio that cannot yet be impressive undersells the mechanic on first contact.
 */
private const val MIN_ACTIVE_DAYS_TO_SHOW_TOUR = 3

@Composable
internal fun StatsGrid2x2(state: AccountUiState) {
    // Read once per composition rather than per card, so both halves of the row cannot straddle
    // midnight and disagree about whether the tour is still open.
    val now = remember(state.tours) { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }
    val currentTour = state.tours.firstOrNull()
        ?.takeIf { it.isOpenAt(now, zone) && it.activeDays >= MIN_ACTIVE_DAYS_TO_SHOW_TOUR }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FocusStatCard(
                value = state.stats.totalFlights.toString(),
                label = "FLIGHTS",
                icon = Icons.Outlined.AirplanemodeActive,
                modifier = Modifier.weight(1f)
            )
            FocusStatCard(
                value = state.stats.airportsVisited.toString(),
                label = "AIRPORTS",
                icon = Icons.Outlined.FlightLand,
                modifier = Modifier.weight(1f)
            )
        }
        if (currentTour != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FocusStatCard(
                    value = "${currentTour.activeDays} / ${currentTour.spanDays}",
                    label = "TOUR DAYS",
                    icon = Icons.Outlined.Event,
                    modifier = Modifier.weight(1f)
                )
                FocusStatCard(
                    value = currentTour.flights.size.toString(),
                    label = "THIS TOUR",
                    icon = Icons.Outlined.Schedule,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: ImageVector
) {
    FocusStatCard(
        value = value,
        label = label,
        icon = icon,
        modifier = modifier
    )
}
