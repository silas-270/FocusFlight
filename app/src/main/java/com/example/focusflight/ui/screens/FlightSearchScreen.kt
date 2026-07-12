package com.example.focusflight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.example.focusflight.R
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.SoftAmber
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.FlightSearchViewModel

@Composable
fun FlightSearchScreen(
    viewModel: FlightSearchViewModel,
    onBackClick: () -> Unit,
    onRouteConfirm: (FlightRoute) -> Unit
) {
    val originAirport by viewModel.originAirport.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()
    val routeMapPath by viewModel.routeMapPath.collectAsState()
    val isRendering by viewModel.isRendering.collectAsState()

    val context = LocalContext.current
    val imageLoader = coil3.ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .build()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        // ── Layout Split: Top portion (Globe) + Bottom portion (Search Panel) ──
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Top portion containing the Map / Globe display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
            ) {
                if (routeMapPath != null) {
                    // Rendered high quality route map
                    AsyncImage(
                        model = routeMapPath,
                        contentDescription = "Selected flight route map",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )
                } else {
                    // Fallback to decorative SVG globe
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(R.raw.globe)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = "Globe background fallback",
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                            .offset(x = 100.dp, y = 20.dp)
                            .scale(2.5f),
                        contentScale = ContentScale.Fit,
                        alpha = 0.25f
                    )
                }

                if (isRendering) {
                    CircularProgressIndicator(
                        color = Amber,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center)
                    )
                }

                // Back Button Overlay
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(Spacing.Medium)
                        .size(40.dp)
                        .background(Midnight.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .border(1.dp, Border, RoundedCornerShape(8.dp))
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = OffWhite
                    )
                }
            }

            // Bottom portion: Navigation search panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
                    .background(
                        color = Midnight,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
            ) {
                // Origin Base Info Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "DEPARTING FROM BASE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Haze,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = originAirport?.municipality ?: "Loading origin...",
                            style = MaterialTheme.typography.headlineSmall,
                            color = OffWhite
                        )
                    }

                    Box(
                        modifier = Modifier
                            .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .background(SoftAmber, RoundedCornerShape(8.dp))
                            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
                    ) {
                        Text(
                            text = originAirport?.iataCode ?: "---",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Amber
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))

                // Search TextField
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search destination airport or city...", color = Haze) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Haze) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = Slate,
                        unfocusedContainerColor = DeepNavy,
                        focusedTextColor = OffWhite,
                        unfocusedTextColor = OffWhite,
                        cursorColor = Amber
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.Medium))

                // Filter tabs: Popular, Shortest, Longest
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    val sorts = listOf("Popular", "Shortest", "Longest")
                    sorts.forEach { sort ->
                        val isSelected = sort == selectedSort
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    color = if (isSelected) SoftAmber else Slate,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Amber else Border,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.onSortChanged(sort) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sort.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Amber else Haze,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))

                // Scrollable List of Destinations
                Box(modifier = Modifier.weight(1f)) {
                    if (routes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No connections found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Haze
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(routes, key = { it.id }) { route ->
                                val isSelected = route.destIata == selectedRoute?.destIata
                                RouteRow(
                                    route = route,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectRoute(route) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))

                // Proceed Button
                Button(
                    onClick = { selectedRoute?.let { onRouteConfirm(it) } },
                    enabled = selectedRoute != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber,
                        contentColor = Midnight,
                        disabledContainerColor = Slate,
                        disabledContentColor = Haze
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FlightTakeoff,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.Small))
                    Text(
                        text = "PROCEED TO CHECK-IN",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteRow(
    route: FlightRoute,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) SoftAmber else Slate
    val borderColor = if (isSelected) Amber else Border
    val accentColor = if (isSelected) Amber else Haze

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // IATA badge
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 36.dp)
                .background(
                    color = if (isSelected) SoftAmber else Midnight,
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isSelected) Amber else Border,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = route.destIata,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) Amber else OffWhite
            )
        }

        Spacer(modifier = Modifier.width(Spacing.Medium))

        // City and Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.destMunicipality,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OffWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = route.destName,
                style = MaterialTheme.typography.bodySmall,
                color = Haze,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(Spacing.Medium))

        // Time and Distance
        Column(horizontalAlignment = Alignment.End) {
            val hours = route.flightTimeMin / 60
            val minutes = route.flightTimeMin % 60
            val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = OffWhite
            )
            Text(
                text = "%,d km".format(route.distanceKm.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = Haze
            )
        }
    }
}
