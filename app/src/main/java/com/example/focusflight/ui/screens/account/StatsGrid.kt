package com.example.focusflight.ui.screens.account

import com.example.focusflight.ui.theme.Border
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.ConnectingAirports
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
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

    // One read-only panel split by hairlines, not four separate tiles: four filled cards with
    // icon bubbles looked like buttons that did nothing when tapped.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
    ) {
        StatRow(
            left = { StatCell(state.stats.totalFlights.toString(), "FLIGHTS", Icons.Outlined.AirplanemodeActive) },
            right = { StatCell(state.stats.airportsVisited.toString(), "AIRPORTS", Icons.Outlined.FlightLand) }
        )
        if (currentTour != null) {
            HorizontalDivider(color = Border, thickness = 1.dp)
            StatRow(
                left = {
                    StatCell("${currentTour.activeDays} / ${currentTour.spanDays}", "DAYS FLOWN", Icons.Outlined.Event)
                },
                right = {
                    StatCell(currentTour.flights.size.toString(), "TOUR FLIGHTS", Icons.Outlined.ConnectingAirports)
                }
            )
        }
    }
}

@Composable
private fun StatRow(left: @Composable RowScope.() -> Unit, right: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        left()
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Border)
        )
        right()
    }
}

@Composable
private fun RowScope.StatCell(value: String, label: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .weight(1f)
            .padding(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
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
