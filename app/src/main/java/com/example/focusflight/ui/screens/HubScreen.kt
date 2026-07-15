package com.example.focusflight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.HubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    viewModel: HubViewModel,
    onBookFlightClick: () -> Unit,
    onResumeFlightClick: (flightNo: String, destIata: String, durationMin: Int) -> Unit,
    onPassportClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val currentAirport by viewModel.currentAirport.collectAsState()
    val stats by viewModel.flightStats.collectAsState()
    val recentFlights by viewModel.recentFlights.collectAsState()
    val routeMapPath by viewModel.routeMapPath.collectAsState()

    val scaffoldState = rememberBottomSheetScaffoldState()
    val isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContainerColor = DeepNavy,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetDragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 16.dp)
                    .width(80.dp)
                    .height(4.dp)
                    .background(Border, RoundedCornerShape(2.dp))
            )
        },
        sheetPeekHeight = 260.dp,
        containerColor = Midnight,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .padding(bottom = 30.dp)
            ) {
                // Header (Welcome back, Iata Code, Airport Name)
                Text(
                    text = "Welcome back, Captain",
                    style = MaterialTheme.typography.titleMedium,
                    color = Amber,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = currentAirport?.iataCode ?: "---",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 44.sp
                    ),
                    color = OffWhite
                )
                Text(
                    text = currentAirport?.name ?: "Loading base...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Haze
                )

                // Stats and Divider shown ONLY in expanded state
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(30.dp))
                    HorizontalDivider(color = Border, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(30.dp))

                    // Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(value = stats.totalFlights.toString(), label = "FLIGHTS")
                        StatItem(value = "${String.format(java.util.Locale.US, "%.1f", stats.totalHours)}h", label = "HOURS")
                        StatItem(value = stats.airportsVisited.toString(), label = "AIRPORTS")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val activeFlightContext by viewModel.activeFlightContext.collectAsState()

                if (isExpanded) {
                    // Expanded state: show both buttons if active flight exists, else just book flight button
                    if (activeFlightContext != null) {
                        val context = activeFlightContext!!
                        Button(
                            onClick = { onResumeFlightClick(context.first, context.second, context.third) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = OffWhite
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FlightTakeoff,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.Small))
                            Text(
                                text = "RESUME FLIGHT",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = onBookFlightClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepNavy,
                                contentColor = Amber
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Amber)
                        ) {
                            Text(
                                text = "BOOK NEW FLIGHT",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                            )
                        }
                    } else {
                        Button(
                            onClick = onBookFlightClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Amber,
                                contentColor = Midnight
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FlightTakeoff,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.Small))
                            Text(
                                text = "BOOK A FLIGHT",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                            )
                        }
                    }
                } else {
                    // Collapsed state: show only resume flight if active flight exists, else book flight button
                    if (activeFlightContext != null) {
                        val context = activeFlightContext!!
                        Button(
                            onClick = { onResumeFlightClick(context.first, context.second, context.third) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = OffWhite
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FlightTakeoff,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.Small))
                            Text(
                                text = "RESUME FLIGHT",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                            )
                        }
                    } else {
                        Button(
                            onClick = onBookFlightClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Amber,
                                contentColor = Midnight
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FlightTakeoff,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.Small))
                            Text(
                                text = "BOOK A FLIGHT",
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Midnight)
        ) {
            // Background Globe image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.775f)
                    .padding(top = 48.dp)
                    .align(Alignment.TopCenter)
            ) {
                if (routeMapPath != null) {
                    AsyncImage(
                        model = routeMapPath,
                        contentDescription = "Decorative globe routes",
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )
                } else {
                    val mapRenderError by viewModel.mapRenderError.collectAsState()
                    if (mapRenderError != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .background(DeepNavy.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                    .padding(Spacing.Large)
                                    .clickable { viewModel.retryRenderMap() }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Error",
                                    tint = Amber,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = mapRenderError!!,
                                    color = OffWhite,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Tap to retry",
                                    color = Amber,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // Top Header & Navigation (outside solid container so globe is behind)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar (No background container)
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Profile",
                    tint = OffWhite,
                    modifier = Modifier.size(24.dp)
                )

                // Settings Gear
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = OffWhite,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = OffWhite
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Haze,
            letterSpacing = 1.sp
        )
    }
}
