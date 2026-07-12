package com.example.focusflight.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
            }
        }

        // Foreground Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header & Navigation (outside solid container so globe is behind)
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

            Spacer(modifier = Modifier.weight(1f))

            // 3. Layout & Typography - Solid bottom card container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = DeepNavy,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .padding(Spacing.Large)
            ) {
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

                Spacer(modifier = Modifier.height(30.dp))
                HorizontalDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(30.dp))

                // Static Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(value = "0", label = "FLIGHTS")
                    StatItem(value = "0.0h", label = "HOURS")
                    StatItem(value = "1", label = "AIRPORTS")
                }

                Spacer(modifier = Modifier.height(48.dp))

                // 4. Call to Action
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
