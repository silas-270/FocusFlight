package com.example.focusflight.ui.screens.flightsearch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.ui.components.InteractiveWorldMap
import com.example.focusflight.ui.viewmodel.flightsearch.FlightSearchViewModel

@Composable
fun RouteMap(
    viewModel: FlightSearchViewModel,
    originAirport: Airport?,
    routes: List<FlightRoute>,
    selectedRoute: FlightRoute?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "map_animation")
    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flight_progress"
    )

    val mapPaths by viewModel.mapPaths.collectAsState()
    val visitedCountries by viewModel.visitedCountries.collectAsState()
    val countryToContinent by viewModel.countryToContinent.collectAsState()
    val completedContinents by viewModel.completedContinents.collectAsState()

    InteractiveWorldMap(
        mapPaths = mapPaths,
        visitedCountries = visitedCountries,
        countryToContinent = countryToContinent,
        completedContinents = completedContinents,
        originAirport = originAirport,
        routes = routes,
        selectedRoute = selectedRoute,
        animationProgress = animationProgress,
        focusOnSelection = true,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
    )
}
