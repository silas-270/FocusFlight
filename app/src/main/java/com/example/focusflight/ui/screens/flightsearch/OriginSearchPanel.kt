package com.example.focusflight.ui.screens.flightsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.Airport
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing

/**
 * Free Mode's origin picker: search any airport (not just ones reachable from somewhere) and
 * pick one to fly from. Deliberately mirrors [AirportSearchPanel]'s look (search field styling,
 * result-row shape) since that's the app's existing "search an airport, tap a result" surface -
 * it just operates over plain [Airport] results from `AirportRepository.searchAirports()`
 * instead of [com.example.focusflight.data.model.FlightRoute]s scoped to a fixed origin, because
 * no origin is chosen yet at this point in Free Mode's booking flow.
 */
@Composable
fun OriginSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Airport>,
    onAirportSelect: (Airport) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "FLY FROM ANYWHERE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Haze
        )
        Spacer(modifier = Modifier.height(Spacing.Small))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search origin airport…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Haze,
                    modifier = Modifier.padding(start = 4.dp)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = Haze,
                    modifier = Modifier.size(24.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Slate,
                unfocusedContainerColor = DeepNavy,
                cursorColor = Amber,
                focusedBorderColor = Amber,
                unfocusedBorderColor = Border.copy(alpha = 0.3f),
                focusedTextColor = OffWhite,
                unfocusedTextColor = OffWhite,
                focusedLeadingIconColor = Amber,
                unfocusedLeadingIconColor = Haze
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        when {
            query.trim().length < 2 -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Type at least 2 characters to search any airport in the world.",
                        color = Haze,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Spacing.Large)
                    )
                }
            }
            results.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No airports match that search.",
                        color = Haze,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.id }) { airport ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .border(1.dp, Border.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable { onAirportSelect(airport) }
                                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
                        ) {
                            Text(
                                text = airport.iataCode,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = OffWhite
                            )
                            Text(
                                text = "${airport.municipality}, ${airport.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Haze,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
