package com.silas270.blocktime.ui.screens.flightsearch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.data.model.FlightMode
import com.silas270.blocktime.data.model.FlightRoute
import com.silas270.blocktime.ui.components.BackTopAppBar
import com.silas270.blocktime.ui.components.PrimaryActionButton
import com.silas270.blocktime.ui.components.airportpicker.AirportMapConfirmCard
import com.silas270.blocktime.ui.components.airportpicker.AirportSearchStep
import com.silas270.blocktime.ui.components.airportpicker.AirportSuggestion
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.Border
import com.silas270.blocktime.ui.theme.DeepNavy
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.Midnight
import com.silas270.blocktime.ui.theme.OffWhite
import com.silas270.blocktime.ui.theme.Slate
import com.silas270.blocktime.ui.theme.Spacing
import com.silas270.blocktime.ui.viewmodel.flightsearch.FlightSearchViewModel
import com.silas270.blocktime.ui.viewmodel.flightsearch.SearchMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightSearchScreen(
    viewModel: FlightSearchViewModel,
    mode: FlightMode = FlightMode.STORY,
    onBackClick: () -> Unit,
    onRouteConfirm: (FlightRoute) -> Unit
) {
    val intervals by viewModel.intervals.collectAsState()
    val selectedInterval by viewModel.selectedInterval.collectAsState()
    val allRoutes by viewModel.allRoutes.collectAsState()
    val filteredRoutes by viewModel.filteredRoutes.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()
    val originAirport by viewModel.originAirport.collectAsState()
    val originRehomedTo by viewModel.originRehomedTo.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val airportSearchQuery by viewModel.airportSearchQuery.collectAsState()
    val airportSearchResults by viewModel.airportSearchResults.collectAsState()

    // Free Mode has no origin lock, so unlike Story Mode (whose origin is always
    // `currentAirport`, loaded before this screen even composes), there's a genuine "no origin
    // chosen yet" state to render - the origin picker, ahead of the existing route-selection UI
    // below, which starts working unmodified for whatever origin ends up in `originAirport`.
    if (mode == FlightMode.FREE && originAirport == null) {
        val originSearchQuery by viewModel.originSearchQuery.collectAsState()
        val originSearchResults by viewModel.originSearchResults.collectAsState()
        val originSuggestions by viewModel.originSuggestions.collectAsState()
        var pendingOrigin by remember { mutableStateOf<Airport?>(null) }

        Scaffold(
            topBar = {
                BackTopAppBar(title = "FREE MODE", onBackClick = onBackClick)
            },
            containerColor = Midnight
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (pendingOrigin == null) {
                        AirportSearchStep(
                            headline = "Where does your flight start?",
                            searchQuery = originSearchQuery,
                            onQueryChange = { viewModel.onOriginSearchQueryChanged(it) },
                            placeholder = "Search origin airport…",
                            searchResults = originSearchResults,
                            onAirportSelected = { pendingOrigin = it },
                            suggestions = originSuggestions.map { airport ->
                                AirportSuggestion(airport.iataCode, airport.municipality, airport.name) {
                                    pendingOrigin = airport
                                }
                            }
                        )
                    } else {
                        AirportMapConfirmCard(
                            airport = pendingOrigin!!,
                            onChangeSelection = { pendingOrigin = null }
                        )
                    }
                }

                if (pendingOrigin != null) {
                    Spacer(modifier = Modifier.height(Spacing.Medium))
                    PrimaryActionButton(
                        text = "CONFIRM ORIGIN",
                        onClick = { pendingOrigin?.let { viewModel.selectOrigin(it) } }
                    )
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "SELECT ROUTE",
                onBackClick = onBackClick,
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
                }
            )
        },
        containerColor = Midnight
    ) { paddingValues ->
        // The window is edge-to-edge, so the keyboard overlays this screen instead of resizing
        // it. imePadding() lifts the bottom of the layout (results list + CONFIRM SELECTION) above
        // it, and while it's up the route map steps aside so the results keep a usable height -
        // with the map in place the airport search's list was down to a sliver. Only the airport
        // search has a text field, so the time search never sees either.
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        // Main Screen Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(bottom = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Story Mode's origin is locked, so an origin with no outbound routes at all is a dead
            // end the pilot cannot navigate out of - the app rehomes them to LHR and rewrites their
            // current airport to match. That is a real change to their position, and it used to
            // happen with nothing on screen to say so. See FlightSearchViewModel.originRehomedTo.
            originRehomedTo?.let { iata ->
                Text(
                    text = "No departures from your last airport - you've been rerouted to $iata.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large)
                        .padding(bottom = Spacing.Small)
                )
            }

            // 2. Tactical 2D Route Map (expands to take available vertical space, no border, consistent padding)
            AnimatedVisibility(
                visible = !imeVisible,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                Column {
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
                }
            }

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
                            val pagerScope = rememberCoroutineScope()

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
                                    // A peeking neighbour used to ripple and then do nothing.
                                    // Tapping it now brings it to the centre (which selects it,
                                    // via the page -> selectedRoute sync above), and tapping the
                                    // centred card still books it - the standard carousel
                                    // "tap the focused item to open it", and it keeps the
                                    // one-tap path to a session that architecture.md protects.
                                    onClick = {
                                        if (isSelected) {
                                            onRouteConfirm(route)
                                        } else {
                                            pagerScope.launch { pagerState.animateScrollToPage(page) }
                                        }
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
                        originCity = originAirport?.municipality,
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
            PrimaryActionButton(
                text = "CONFIRM SELECTION",
                modifier = Modifier.padding(horizontal = Spacing.Large),
                enabled = selectedRoute != null,
                onClick = { selectedRoute?.let { onRouteConfirm(it) } }
            )
        }
    }
}
