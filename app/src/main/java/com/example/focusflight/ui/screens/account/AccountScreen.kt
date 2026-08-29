package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.StarHalf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.ui.components.BackTopAppBar
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.account.AccountViewModel
import com.example.focusflight.data.model.FlightSortOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBackClick: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyPagingItems = viewModel.pagedFlights.collectAsLazyPagingItems()

    // Sticky month/year headers only make sense when the list is chronologically
    // sorted — grouping by distance/duration order would scatter single-item groups.
    // Computed from uiState.flightHistory (already fully loaded for the map/stats,
    // independent of the paging window) so this never forces extra pages to load.
    val monthHeaderLabels = remember(uiState.flightHistory, uiState.sortOrder) {
        buildMonthHeaderLabels(uiState.flightHistory, uiState.sortOrder)
    }

    // Home base + return (docs/design/story-mode.md) - two ScrimCardModal overlays, shown as
    // siblings of the Scaffold below (not nested inside it) so they draw on top of the whole
    // screen, same reasoning as every other ScrimCardModal use in this codebase.
    var showReturnHomeModal by remember { mutableStateOf(false) }
    var showReturningHomeModal by remember { mutableStateOf(false) }
    // Full screen rather than a ScrimCardModal overlay (see ChangeHomeBaseScreen's and
    // HomeBaseCelebrationScreen's doc comments) - all three are drawn as the last siblings of the
    // Box below so they cover the whole screen instead of sitting inside the Scaffold's content
    // area. The two celebrations come last of all, since either can open on top of the picker.
    var showChangeHomeBase by remember { mutableStateOf(false) }
    var showWelcomeHome by remember { mutableStateOf(false) }
    var homeBaseSetAirport by remember { mutableStateOf<Airport?>(null) }
    var showTravelMapModal by remember { mutableStateOf(false) }
    // Same ScrimCardModal convention for the sort-order picker, replacing the old inline
    // DropdownMenu — this app never uses DropdownMenu/AlertDialog/Dialog elsewhere.
    var showSortModal by remember { mutableStateOf(false) }
    // Months start collapsed; tapping a MonthHeader adds/removes its label here. Only
    // meaningful when sorted by date — monthHeaderLabels is empty for distance/duration
    // order, so nothing ever gets collapsed there.
    var expandedMonths by remember { mutableStateOf(setOf<String>()) }
    // Hoisted out of ProfileHeroCard so it survives the card scrolling out of the LazyColumn's
    // viewport and back in.
    var heroExpanded by remember { mutableStateOf(false) }
    // Owned here, not inside AchievementBadgeGrid: a ScrimCardModal opened from inside a
    // LazyColumn item is clipped to that item, so it has to be a sibling of the Scaffold.
    var selectedBadge by remember { mutableStateOf<AchievementStatus?>(null) }
    val homeBaseSearchQuery by viewModel.homeBaseSearchQuery.collectAsState()
    val homeBaseSearchResults by viewModel.homeBaseSearchResults.collectAsState()
    val homeBaseSuggestions by viewModel.homeBaseSuggestions.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BackTopAppBar(title = "PILOT PASSPORT", onBackClick = onBackClick)
        },
        containerColor = Midnight
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Amber, strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(
                    start = Spacing.Medium,
                    end = Spacing.Medium,
                    top = Spacing.Small,
                    bottom = Spacing.ExtraLarge
                )
            ) {
                // ── Hero Profile Card ─────────────────────────────────────────
                // Tap to expand for the home-base actions (see HomeBaseSection.kt) - they used to
                // be a permanent section right here, which overstated two actions behind 7- and
                // 30-day cooldowns.
                item {
                    ProfileHeroCard(
                        state = uiState,
                        expanded = heroExpanded,
                        onToggleExpanded = { heroExpanded = !heroExpanded },
                        onReturnHomeClick = { showReturnHomeModal = true },
                        onChangeHomeBaseClick = { showChangeHomeBase = true }
                    )
                }

                // ── Travel Map ────────────────────────────────────────────────
                // Straight under the hero card: shows the whole playthrough at a glance.
                // Tap to open world exploration modal detail.
                item { TravelMapCard(uiState, onClick = { showTravelMapModal = true }) }

                // ── Achievements ─────────────────────────────────────────────
                // Earned badges only - a trophy case, sorted by difficulty (gold, silver, bronze).
                item { SectionHeader(title = "ACHIEVEMENTS") }
                item {
                    AchievementBadgeGrid(
                        achievements = uiState.unlockedAchievements,
                        onBadgeClick = { selectedBadge = it }
                    )
                }

                // ── Flight Highlights ─────────────────────────────────────────
                item { SectionHeader(title = "FLIGHT HIGHLIGHTS") }
                item { FlightHighlightsRow(uiState.highlights) }

                // ── Flight History Header + Sorting Bar ───────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "FLIGHT HISTORY")
                        SortOrderButton(
                            currentOrder = uiState.sortOrder,
                            onClick = { showSortModal = true }
                        )
                    }
                }

                // ── Paged Logbook Items (with sticky month/year headers) ──────
                // currentMonthLabel tracks which month index i falls in even when i isn't a
                // header boundary itself, so every item's visibility can be checked against
                // expandedMonths, not just the first item of each month.
                var currentMonthLabel: String? = null
                for (index in 0 until lazyPagingItems.itemCount) {
                    val headerLabel = monthHeaderLabels[index]
                    if (headerLabel != null) {
                        currentMonthLabel = headerLabel
                        val isExpanded = headerLabel in expandedMonths
                        stickyHeader(key = "header_$headerLabel") {
                            MonthHeader(
                                label = headerLabel,
                                expanded = isExpanded,
                                onToggle = {
                                    expandedMonths = if (isExpanded) {
                                        expandedMonths - headerLabel
                                    } else {
                                        expandedMonths + headerLabel
                                    }
                                }
                            )
                        }
                    }
                    val monthLabel = currentMonthLabel
                    if (monthLabel == null || monthLabel in expandedMonths) {
                        item(
                            key = lazyPagingItems.peek(index)?.id ?: "placeholder_$index",
                            contentType = "flight"
                        ) {
                            val flight = lazyPagingItems[index]
                            if (flight != null) {
                                val entryNo = when (uiState.sortOrder) {
                                    FlightSortOrder.DATE_DESC, FlightSortOrder.DISTANCE_DESC, FlightSortOrder.DURATION_DESC -> uiState.stats.totalFlights - index
                                    FlightSortOrder.DATE_ASC, FlightSortOrder.DISTANCE_ASC -> index + 1
                                }
                                LogbookEntry(flight = flight, entryNumber = entryNo)
                            }
                        }
                    }
                }

                // ── Loading Footer ────────────────────────────────────────────
                val loadState = lazyPagingItems.loadState
                if (loadState.append is LoadState.Loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Amber, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    if (showReturnHomeModal) {
        ReturnHomeConfirmModal(
            homeAirportIata = uiState.homeAirportIata,
            currentAirportIata = uiState.currentAirportIata,
            onConfirm = {
                showReturnHomeModal = false
                showReturningHomeModal = true
            },
            onDismiss = { showReturnHomeModal = false }
        )
    }

    if (showReturningHomeModal) {
        ReturningHomeModal(
            onComplete = {
                viewModel.returnHome()
                showReturningHomeModal = false
                showWelcomeHome = true
            }
        )
    }

    if (showSortModal) {
        SortOrderModal(
            currentOrder = uiState.sortOrder,
            onOrderSelected = { viewModel.setSortOrder(it) },
            onDismiss = { showSortModal = false }
        )
    }

    selectedBadge?.let { achievement ->
        AchievementBadgeModal(
            achievement = achievement,
            onDismiss = { selectedBadge = null }
        )
    }

    if (showTravelMapModal) {
        val totalCountries = remember(uiState.mapPaths, uiState.countryToContinent) {
            val fromPaths = uiState.mapPaths.map { it.countryCode }.filter { it.isNotBlank() }.distinct().size
            if (fromPaths > 0) fromPaths else if (uiState.countryToContinent.isNotEmpty()) uiState.countryToContinent.size else 195
        }
        TravelMapDetailModal(
            visitedCountriesCount = uiState.allVisitedCountries.size,
            totalCountriesCount = totalCountries,
            onDismiss = { showTravelMapModal = false }
        )
    }

    // Drawn last so it's the topmost sibling, fully covering everything above it.
    if (showChangeHomeBase) {
        ChangeHomeBaseScreen(
            query = homeBaseSearchQuery,
            onQueryChange = { viewModel.onHomeBaseSearchQueryChanged(it) },
            results = homeBaseSearchResults,
            suggestions = homeBaseSuggestions,
            onAirportSelect = { airport ->
                viewModel.changeHomeBase(airport)
                showChangeHomeBase = false
                homeBaseSetAirport = airport
            },
            onBackClick = { showChangeHomeBase = false }
        )
    }

    // ── Celebrations ─────────────────────────────────────────────────
    // Topmost of all: either can open over the picker above, and both end the flow by handing the
    // pilot back to the Hub rather than returning them here.
    if (showWelcomeHome) {
        val home = uiState.homeAirport
        HomeBaseCelebrationScreen(
            eyebrow = "WELCOME BACK",
            iata = uiState.homeAirportIata,
            airportName = home?.name.orEmpty(),
            locationLine = locationLine(home?.municipality, home?.isoCountry),
            onContinue = {
                showWelcomeHome = false
            },
            inlineHero = { HomeBaseSetHero() }
        )
    }

    homeBaseSetAirport?.let { airport ->
        HomeBaseCelebrationScreen(
            eyebrow = "HOME BASE SET",
            iata = airport.iataCode,
            airportName = airport.name,
            locationLine = locationLine(airport.municipality, airport.isoCountry),
            onContinue = {
                homeBaseSetAirport = null
            },
            inlineHero = { HomeBaseSetHero() }
        )
    }
    }
}

/** "MUNICH · GERMANY". [isoCountry] is a 2-letter code, and the app has no code-to-name table -
 *  `Locale` already ships one, and falls back to blank for anything it doesn't recognise. */
private fun locationLine(city: String?, isoCountry: String?): String {
    val country = isoCountry
        ?.takeIf { it.isNotBlank() }
        ?.let { Locale("", it).getDisplayCountry(Locale.US) }
        ?.takeIf { it.isNotBlank() }
    return listOfNotNull(city?.takeIf { it.isNotBlank() }, country)
        .joinToString(" · ")
        .uppercase(Locale.US)
}

@Composable
private fun MonthHeader(label: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Midnight)
            .clickable(onClick = onToggle)
            .padding(vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = Haze
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Collapse month" else "Expand month",
            tint = Haze,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (expanded) 180f else 0f)
        )
    }
}

private val monthHeaderFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

// Maps each paged-item index to the month/year header that should precede it —
// only the index where a new month starts is present in the map. Built from the
// full (non-paged) flight history so header boundaries are known up front instead
// of depending on which pages happen to be loaded.
private fun buildMonthHeaderLabels(
    flightHistory: List<FlightLog>,
    sortOrder: FlightSortOrder
): Map<Int, String> {
    val chronological = when (sortOrder) {
        FlightSortOrder.DATE_DESC -> flightHistory.sortedByDescending { it.completedAt }
        FlightSortOrder.DATE_ASC -> flightHistory.sortedBy { it.completedAt }
        else -> return emptyMap()
    }

    val headers = mutableMapOf<Int, String>()
    var lastLabel: String? = null
    chronological.forEachIndexed { index, flight ->
        val label = monthHeaderFormat.format(Date(flight.completedAt)).uppercase(Locale.US)
        if (label != lastLabel) {
            headers[index] = label
            lastLabel = label
        }
    }
    return headers
}
