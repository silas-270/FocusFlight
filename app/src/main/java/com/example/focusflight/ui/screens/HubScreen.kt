package com.example.focusflight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Dim
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.example.focusflight.ui.viewmodel.HubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    viewModel: HubViewModel,
    onBookFlightClick: () -> Unit,
    onPassportClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val currentAirport by viewModel.currentAirport.collectAsState()
    val stats by viewModel.flightStats.collectAsState()
    val recentFlights by viewModel.recentFlights.collectAsState()
    val routeMapPath by viewModel.routeMapPath.collectAsState()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 280.dp, // Height of the collapsed state
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = DeepNavy,
        sheetContentColor = OffWhite,
        sheetDragHandle = {
            // Minimalist drag handle
            Box(
                modifier = Modifier
                    .padding(vertical = Spacing.Medium)
                    .width(48.dp)
                    .height(4.dp)
                    .background(Slate, RoundedCornerShape(2.dp))
            )
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .padding(bottom = Spacing.Large)
            ) {
                // Header: Current Base
                Text(
                    text = "YOUR BASE",
                    style = MaterialTheme.typography.labelMedium,
                    color = Amber,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (currentAirport != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = currentAirport!!.iataCode,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Dim
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentAirport!!.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Haze,
                            modifier = Modifier.padding(bottom = 4.dp),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "Loading base...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Haze
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))
                HorizontalDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.Medium))

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(value = "${stats.totalFlights}", label = "FLIGHTS")
                    StatItem(value = String.format(java.util.Locale.US, "%.1fh", stats.totalHours), label = "HOURS")
                    StatItem(value = "${stats.airportsVisited}", label = "AIRPORTS")
                }

                Spacer(modifier = Modifier.height(Spacing.Large))

                // CTA Button (Always visible in peek state)
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

                Spacer(modifier = Modifier.height(32.dp))

                // --- EXPANDED CONTENT ---

                Text(
                    text = "Recent Flights",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = OffWhite
                )
                Spacer(modifier = Modifier.height(Spacing.Medium))

                if (recentFlights.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Midnight.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No flights recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Dim
                        )
                    }
                } else {
                    // Placeholder for when we have recent flights to map
                    Text("Flight logs will appear here.", color = Dim)
                }

                Spacer(modifier = Modifier.height(Spacing.Large))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    OutlinedButton(
                        onClick = onPassportClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Passport")
                    }

                    OutlinedButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settings")
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // Main Content Area (Background)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (routeMapPath != null) {
                AsyncImage(
                    model = routeMapPath,
                    contentDescription = "Base airport routes map",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Amber)
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
            color = Dim,
            letterSpacing = 1.sp
        )
    }
}
