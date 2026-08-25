package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.StarHalf
import androidx.compose.material.icons.outlined.Tour
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.SoftAmber
import com.example.focusflight.data.model.FlightHighlights

@Composable
internal fun FlightHighlightsRow(highlights: FlightHighlights) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. Longest Flight Card
        highlights.longestFlight?.let { flight ->
            item {
                HighlightCard(
                    title = "LONGEST FLIGHT",
                    value = "${flight.originIata} → ${flight.destIata}",
                    subtext = "%,.0f km".format(flight.distanceKm),
                    icon = Icons.Outlined.AirplanemodeActive
                )
            }
        }
        // 2. Most Visited Card
        highlights.mostVisitedIata?.let { iata ->
            item {
                HighlightCard(
                    title = "MOST VISITED",
                    value = iata,
                    subtext = "${highlights.mostVisitedCount} visits",
                    icon = Icons.Outlined.Tour
                )
            }
        }
        // 3. Equator Ratio Card
        item {
            val equatorRatioPercent = highlights.equatorRatio * 100
            val subtext = if (highlights.equatorRatio >= 1.0) {
                "%.2f equator runs".format(highlights.equatorRatio)
            } else {
                "%.1f%% of the equator".format(equatorRatioPercent)
            }
            HighlightCard(
                title = "EQUATOR PROGRESS",
                value = "🌍",
                subtext = subtext,
                icon = Icons.Outlined.StarHalf
            )
        }
    }
}

@Composable
internal fun HighlightCard(
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DeepNavy),
        modifier = Modifier
            .width(170.dp)
            .height(110.dp)
            .border(1.dp, SoftAmber.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                    color = Haze
                )
                Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = OffWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
