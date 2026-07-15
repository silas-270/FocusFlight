package com.example.focusflight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.viewmodel.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Account View") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Profile", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Username: ${uiState.username}", color = Color.LightGray)
                    Text("Handle: ${uiState.userCode}", color = Color.LightGray)
                    Text("Home Airport: ${uiState.homeAirportIata}", color = Color.LightGray)
                    Text("Join Date: ${uiState.joinDateFormatted}", color = Color.LightGray)
                    HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                }

                item {
                    Text("Travel Map", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    com.example.focusflight.ui.components.InteractiveWorldMap(
                        mapPaths = uiState.mapPaths,
                        visitedCountries = uiState.allVisitedCountries,
                        countryToContinent = uiState.countryToContinent,
                        completedContinents = uiState.completedContinents,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
                }

                item {
                    Text("Stats", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Total Flights: ${uiState.totalFlights}", color = Color.LightGray)
                    val hoursInt = uiState.totalMinutes / 60
                    val minutesInt = uiState.totalMinutes % 60
                    Text("Total Time: ${String.format(java.util.Locale.US, "%02d:%02d", hoursInt, minutesInt)}", color = Color.LightGray)
                    Text("Airports Visited: ${uiState.airportsVisited}", color = Color.LightGray)
                    Text("Unique Countries Visited: ${uiState.allVisitedCountries.size}", color = Color.LightGray)
                    HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                }

                item {
                    Text("Continent Completion", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                items(uiState.continentStats) { stat ->
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(
                            text = "Continent: ${stat.continentCode} ${if (stat.isCompleted) "✅" else ""}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Visited: ${stat.visitedCountries.size} / ${stat.totalCountries}",
                            color = Color.LightGray
                        )
                        if (!stat.isCompleted && stat.missingCountries.isNotEmpty()) {
                            Text(
                                text = "Missing (${stat.missingCountries.size}): ${stat.missingCountries.take(5).joinToString()}...",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    Text("Flight History (${uiState.flightHistory.size})", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                items(uiState.flightHistory) { flight ->
                    Text(
                        text = "[${flight.flightNumber}] ${flight.originIata} -> ${flight.destIata} (${flight.durationMin}m)",
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}
