package com.example.focusflight.ui.screens.flightsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.FlightRoute
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.FlightSearchViewModel
import com.example.focusflight.ui.viewmodel.SearchMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightSearchScreen(
    viewModel: FlightSearchViewModel,
    onBackClick: () -> Unit,
    onRouteConfirm: (FlightRoute) -> Unit
) {
    val intervals by viewModel.intervals.collectAsState()
    val selectedInterval by viewModel.selectedInterval.collectAsState()
    val allRoutes by viewModel.allRoutes.collectAsState()
    val filteredRoutes by viewModel.filteredRoutes.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()
    val originAirport by viewModel.originAirport.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val airportSearchQuery by viewModel.airportSearchQuery.collectAsState()
    val airportSearchResults by viewModel.airportSearchResults.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SELECT ROUTE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Amber
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = OffWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSearchMode() }) {
                        Icon(
                            imageVector = if (searchMode == SearchMode.TIME) {
                                Icons.Outlined.Search
                            } else {
                                Icons.Outlined.Timer
                            },
                            contentDescription = "Toggle Search Mode",
                            tint = Amber
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Midnight
                )
            )
        },
        containerColor = Midnight
    ) { paddingValues ->
        // Main Screen Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(bottom = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 2. Tactical 2D Route Map (expands to take available vertical space, no border, consistent padding)
            RouteMap(
                viewModel = viewModel,
                originAirport = originAirport,
                routes = if (searchMode == SearchMode.TIME) filteredRoutes else airportSearchResults,
                selectedRoute = selectedRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            )

            Spacer(modifier = Modifier.height(Spacing.Medium))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Let the bottom panel shrink to fill remaining space
            ) {
                if (searchMode == SearchMode.TIME) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 3. Timeline Slider Container (aligned to grid margin)
                        if (intervals.isNotEmpty()) {
                            TimelineSlider(
                                intervals = intervals,
                                selectedInterval = selectedInterval,
                                onIntervalSelected = { viewModel.selectInterval(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.Large)
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.Large))

                        // 4. Carousel / Cards (or Empty State)
                        if (filteredRoutes.isNotEmpty()) {
                            val pagerState = rememberPagerState(pageCount = { filteredRoutes.size })

                            // Reset pager selection back to the first option when the filtered list changes
                            LaunchedEffect(filteredRoutes) {
                                pagerState.scrollToPage(0)
                            }

                            // Sync VM selectedRoute -> UI pagerState page (bidirectional)
                            LaunchedEffect(selectedRoute, filteredRoutes) {
                                val index = filteredRoutes.indexOfFirst { it.id == selectedRoute?.id }
                                if (index >= 0 && index != pagerState.currentPage && !pagerState.isScrollInProgress) {
                                    pagerState.animateScrollToPage(index)
                                }
                            }

                            // Sync UI pagerState page -> VM selectedRoute (bidirectional)
                            LaunchedEffect(pagerState.currentPage) {
                                if (pagerState.currentPage < filteredRoutes.size) {
                                    val route = filteredRoutes[pagerState.currentPage]
                                    if (selectedRoute?.id != route.id) {
                                        viewModel.selectRoute(route)
                                    }
                                }
                            }

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 64.dp),
                                pageSpacing = 16.dp
                            ) { page ->
                                val route = filteredRoutes[page]
                                val isSelected = selectedRoute?.id == route.id
                                SelectionCard(
                                    route = route,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelected) onRouteConfirm(route)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(Spacing.Medium))

                            // 5. Pagination Indicator
                            Row(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0 until filteredRoutes.size) {
                                    val isActive = pagerState.currentPage == i
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .width(24.dp)
                                                .height(6.dp)
                                                .background(Slate, RoundedCornerShape(50))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(OffWhite.copy(alpha = 0.4f), CircleShape)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Empty state for current interval (matching card aspect ratio and padding)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 64.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1.0f)
                                    .background(DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                    .border(1.dp, Border, RoundedCornerShape(20.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FlightTakeoff,
                                        contentDescription = null,
                                        tint = Haze,
                                        modifier = Modifier.size(48.dp).alpha(0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No flights available",
                                        color = OffWhite,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "There are no outbound routes matching this duration. Scroll the timeline to see other durations.",
                                        color = Haze,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Mode B: Search by Airport Panel
                    AirportSearchPanel(
                        query = airportSearchQuery,
                        onQueryChange = { viewModel.onAirportSearchQueryChanged(it) },
                        results = if (airportSearchQuery.isEmpty()) allRoutes else airportSearchResults,
                        selectedRoute = selectedRoute,
                        onRouteSelect = { viewModel.selectRoute(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.Large)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Large))

            // 6. Common Confirm Selection Button (always displayed at the bottom)
            Button(
                onClick = { selectedRoute?.let { onRouteConfirm(it) } },
                enabled = selectedRoute != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor = Midnight,
                    disabledContainerColor = Amber.copy(alpha = 0.35f),
                    disabledContentColor = Midnight.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "CONFIRM SELECTION",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                )
            }
        }
    }
}
