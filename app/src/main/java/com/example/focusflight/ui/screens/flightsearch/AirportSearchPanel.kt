package com.example.focusflight.ui.screens.flightsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.ui.components.CaptionLabel
import com.example.focusflight.ui.components.IataBadge
import com.example.focusflight.ui.components.SearchTextField
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing

@Composable
fun AirportSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<FlightRoute>,
    selectedRoute: FlightRoute?,
    onRouteSelect: (FlightRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        SearchTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search airport…",
            unfocusedBorderColor = Border.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        if (query.trim().isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                CaptionLabel(text = "SUGGESTED DESTINATIONS")
                Spacer(modifier = Modifier.height(Spacing.Medium))

                val suggestions = remember(results) {
                    val popularIatas = listOf("FRA", "LHR", "BER", "MUC", "CDG", "JFK", "DXB", "AMS", "SIN", "HND")
                    val matching = results.filter { popularIatas.contains(it.destIata) }
                    if (matching.isNotEmpty()) {
                        matching.take(4)
                    } else {
                        results.take(4)
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    if (suggestions.size >= 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[0]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.durationMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[1]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.durationMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                        }
                    } else if (suggestions.size == 1) {
                        Box(modifier = Modifier.fillMaxWidth(0.5f)) {
                            val r = suggestions[0]
                            val isSelected = selectedRoute?.id == r.id
                            SuggestionTile(
                                code = r.destIata,
                                city = r.destMunicipality,
                                name = r.destName,
                                durationMin = r.durationMin,
                                isSelected = isSelected,
                                onClick = { onRouteSelect(r) }
                            )
                        }
                    }

                    if (suggestions.size >= 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[2]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.durationMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[3]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.durationMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                        }
                    } else if (suggestions.size == 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[2]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.durationMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.Large),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "The airport does not offer direct connections to that airport",
                    color = Haze,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp)),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results.size) { index ->
                    val route = results[index]
                    val isSelected = selectedRoute?.id == route.id
                    val hrs = route.durationMin / 60
                    val mins = route.durationMin % 60
                    val formattedTime = "%02d:%02d".format(hrs, mins)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Slate.copy(alpha = 0.4f) else DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .border(1.dp, if (isSelected) Amber else Border.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { onRouteSelect(route) }
                            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = route.destIata,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (isSelected) Amber else OffWhite
                            )
                            Text(
                                text = "${route.destMunicipality}, ${route.destName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Haze,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (isSelected) Amber else OffWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SuggestionTile(
    code: String,
    city: String,
    name: String,
    durationMin: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hrs = durationMin / 60
    val mins = durationMin % 60
    val timeStr = "%02d:%02d".format(hrs, mins)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeepNavy)
            .then(
                if (isSelected) Modifier.border(1.5.dp, Amber, RoundedCornerShape(16.dp))
                else Modifier.border(1.dp, Border, RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick)
            .padding(Spacing.Medium),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IataBadge(code = code, borderColor = Border, backgroundColor = Midnight, textColor = OffWhite)

            Text(
                text = timeStr,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isSelected) Amber else OffWhite
            )
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))

        Text(
            text = city,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = OffWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = Haze,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
