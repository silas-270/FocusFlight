package com.silas270.blocktime.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.ui.components.BackTopAppBar
import com.silas270.blocktime.ui.components.FocusButton
import com.silas270.blocktime.ui.components.airportpicker.AirportMapConfirmCard
import com.silas270.blocktime.ui.components.airportpicker.AirportSearchStep
import com.silas270.blocktime.ui.components.airportpicker.AirportSuggestion
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.Dim
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.Midnight
import com.silas270.blocktime.ui.theme.Spacing

/**
 * The airport picker for changing home base, laid out exactly like [com.silas270.blocktime.ui
 * .screens.onboarding.OnboardingScreen]'s search step / confirm card / CTA button - full screen
 * rather than a modal card, since [AirportSearchStep]'s results list needs real room to breathe.
 * Unlike onboarding this isn't a forced first step, so it gets a back arrow instead of being
 * undismissable.
 */
@Composable
internal fun ChangeHomeBaseScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Airport>,
    suggestions: List<Airport>,
    onAirportSelect: (Airport) -> Unit,
    onBackClick: () -> Unit
) {
    var pendingAirport by remember { mutableStateOf<Airport?>(null) }

    BackHandler(onBack = onBackClick)
    Scaffold(
        topBar = { BackTopAppBar(title = "CHANGE HOME BASE", onBackClick = onBackClick) },
        containerColor = Midnight
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Midnight)
                .padding(paddingValues)
                .padding(horizontal = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(Spacing.Large))
            Text(
                text = "Pick a new home base airport. You won't be able to change it again for 30 days.",
                style = MaterialTheme.typography.bodyMedium,
                color = Haze
            )
            Spacer(modifier = Modifier.height(Spacing.Large))

            val airport = pendingAirport
            if (airport == null) {
                AirportSearchStep(
                    headline = "Where's your new home base?",
                    searchQuery = query,
                    onQueryChange = onQueryChange,
                    placeholder = "Search airport or city…",
                    searchResults = results,
                    onAirportSelected = { pendingAirport = it },
                    suggestions = suggestions.map { suggestion ->
                        AirportSuggestion(suggestion.iataCode, suggestion.municipality, suggestion.name) {
                            pendingAirport = suggestion
                        }
                    }
                )
            } else {
                AirportMapConfirmCard(
                    airport = airport,
                    onChangeSelection = { pendingAirport = null }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            FocusButton(
                text = "SET NEW HOME BASE",
                onClick = { airport?.let(onAirportSelect) },
                enabled = airport != null,
                icon = Icons.Outlined.FlightTakeoff,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Spacing.Large))
        }
    }
}
