package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.ui.components.BackTopAppBar
import com.example.focusflight.data.model.Tour
import com.example.focusflight.util.formatMiles
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.account.AccountViewModel
import com.example.focusflight.ui.viewmodel.account.HomeBaseActionResult
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

    // Rows and headers both come from uiState.flightHistory, which matters more than it looks.
    // This list used to render from a separate Paging 3 query while the headers were computed
    // from this one, with index equality as the only contract between them and nothing checking
    // it - so a sort change or a fresh landing could slide every header one row out of position.
    // Month headers hid that (a header one row early inside a run of same-month flights looks
    // plausible); tour headers would not, because the boundary *is* the feature and the header
    // states a mileage total the rows beneath it would not add up to. The full history is already
    // in memory for the map, stats, highlights and achievements, so paging a second copy of it
    // bought nothing - LazyColumn only composes what is visible either way.
    val sortedFlights = remember(uiState.flightHistory, uiState.sortOrder) {
        sortFlights(uiState.flightHistory, uiState.sortOrder)
    }

    // Sticky tour headers only make sense when the list is chronologically sorted — grouping by
    // distance/duration order would scatter single-item groups.
    val tourHeaders = remember(uiState.tours, uiState.sortOrder) {
        buildTourHeaders(uiState.tours, uiState.sortOrder)
    }

    // Home base + return (docs/modes.md) - two ScrimCardModal overlays, shown as
    // siblings of the Scaffold below (not nested inside it) so they draw on top of the whole
    // screen, same reasoning as every other ScrimCardModal use in this codebase.
    var showReturnHomeModal by remember { mutableStateOf(false) }
    var showReturningHomeModal by remember { mutableStateOf(false) }
    // Full screen rather than a ScrimCardModal overlay (see ChangeHomeBaseScreen's and
    // HomeBaseCelebrationScreen's doc comments) - all three are drawn as the last siblings of the
    // Box below so they cover the whole screen instead of sitting inside the Scaffold's content
    // area. The two celebrations come last of all, since either can open on top of the picker.
    var showChangeHomeBase by remember { mutableStateOf(false) }
    // Neither celebration flag is set at its call site any more. Both home-base actions re-check
    // their cooldown inside the ViewModel and can refuse silently, so tapping "confirm" is not
    // evidence that anything happened - these are flipped from homeBaseActionResult below, i.e.
    // only once the write has actually landed.
    var showWelcomeHome by remember { mutableStateOf(false) }
    var homeBaseSetAirport by remember { mutableStateOf<Airport?>(null) }
    var showTravelMapModal by remember { mutableStateOf(false) }
    // Same ScrimCardModal convention for the sort-order picker, replacing the old inline
    // DropdownMenu — this app never uses DropdownMenu/AlertDialog/Dialog elsewhere.
    var showSortModal by remember { mutableStateOf(false) }
    // Tapping a TourHeader adds/removes its id here. Only meaningful when sorted by date —
    // tourHeaders is empty for distance/duration order, so nothing ever gets collapsed there.
    //
    // Keyed on the tour's id rather than its displayed title, and that is load-bearing: this set
    // and the stickyHeader key below are both derived from it, and two tours can easily produce
    // the same title. A duplicate key inside a LazyColumn is a crash, not a cosmetic bug.
    var expandedTours by rememberSaveable(stateSaver = ExpandedToursSaver) {
        mutableStateOf(setOf<String>())
    }
    // The newest tour opens on arrival. Everything used to start collapsed, which left the
    // logbook looking empty. Keyed on the id so a brand new tour opens itself, while a tour the
    // pilot deliberately collapsed stays that way.
    val newestTourId = uiState.tours.firstOrNull()?.let { tourId(it) }
    LaunchedEffect(newestTourId) {
        if (newestTourId != null) expandedTours = expandedTours + newestTourId
    }
    // Hoisted out of ProfileHeroCard so it survives the card scrolling out of the LazyColumn's
    // viewport and back in.
    var heroExpanded by remember { mutableStateOf(false) }
    // Owned here, not inside AchievementBadgeGrid: a ScrimCardModal opened from inside a
    // LazyColumn item is clipped to that item, so it has to be a sibling of the Scaffold.
    //
    // Two, because a ladder tile opens onto its tiers while a lone badge opens straight onto its
    // own card. Only ever one of them is non-null.
    var selectedStack by remember { mutableStateOf<AchievementStack?>(null) }
    var selectedAchievement by remember { mutableStateOf<AchievementStatus?>(null) }
    val homeBaseSearchQuery by viewModel.homeBaseSearchQuery.collectAsState()
    val homeBaseSearchResults by viewModel.homeBaseSearchResults.collectAsState()
    val homeBaseSuggestions by viewModel.homeBaseSuggestions.collectAsState()
    val homeBaseActionResult by viewModel.homeBaseActionResult.collectAsState()

    // The single place either celebration is armed. The work runs in viewModelScope (it must
    // outlive this composable), so the result arrives here asynchronously some time after the tap
    // that started it - and it can just as well say the cooldown re-check refused the action, in
    // which case the correct behaviour is to show nothing at all. There is deliberately no error
    // dialog or snackbar: this codebase uses neither, and a refusal is a no-op, not a fault the
    // pilot has to acknowledge. Consumed either way so a result is acted on exactly once and can't
    // replay on the next recomposition or a config change.
    LaunchedEffect(homeBaseActionResult) {
        when (val result = homeBaseActionResult) {
            null -> Unit
            is HomeBaseActionResult.ReturnedHome -> {
                showWelcomeHome = true
                viewModel.consumeHomeBaseActionResult()
            }
            is HomeBaseActionResult.HomeBaseSet -> {
                // The airport comes from the result, not from uiState: the Room profile flow that
                // feeds uiState.homeAirportIata may not have re-emitted yet, and this is the value
                // that was actually written.
                homeBaseSetAirport = result.airport
                viewModel.consumeHomeBaseActionResult()
            }
            HomeBaseActionResult.Ineligible, HomeBaseActionResult.Failed -> {
                viewModel.consumeHomeBaseActionResult()
            }
        }
    }

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
                        stacks = uiState.achievementStacks,
                        onStackClick = { stack ->
                            // A lone achievement has no tier list worth scrolling - open the badge
                            // itself, exactly as it always did. Only a real ladder opens the stack.
                            if (stack.isStacked) {
                                selectedStack = stack
                            } else {
                                selectedAchievement = stack.top
                            }
                        }
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

                // ── Logbook Items (with sticky tour headers) ──────────────────
                // currentTourId tracks which tour index i falls in even when i isn't a header
                // boundary itself, so every item's visibility can be checked against
                // expandedTours, not just the first item of each tour.
                var currentTourId: String? = null
                sortedFlights.forEachIndexed { index, flight ->
                    val header = tourHeaders[index]
                    if (header != null) {
                        currentTourId = header.id
                        val isExpanded = header.id in expandedTours
                        stickyHeader(key = header.id) {
                            TourHeader(
                                header = header,
                                expanded = isExpanded,
                                onToggle = {
                                    expandedTours = if (isExpanded) {
                                        expandedTours - header.id
                                    } else {
                                        expandedTours + header.id
                                    }
                                }
                            )
                        }
                    }
                    val tourId = currentTourId
                    if (tourId == null || tourId in expandedTours) {
                        item(key = flight.id, contentType = "flight") {
                            val entryNo = when (uiState.sortOrder) {
                                FlightSortOrder.DATE_DESC, FlightSortOrder.DISTANCE_DESC, FlightSortOrder.DURATION_DESC ->
                                    sortedFlights.size - index
                                FlightSortOrder.DATE_ASC, FlightSortOrder.DISTANCE_ASC -> index + 1
                            }
                            LogbookEntry(flight = flight, entryNumber = entryNo)
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

    // The teleport's outcome is settled the moment this modal appears - it cannot be dismissed and
    // it ends by attempting the teleport - so the globe the Hub will want is rendered during the
    // animation instead of after it. Keyed on Unit inside the `if` so it fires once per showing.
    if (showReturningHomeModal) {
        LaunchedEffect(Unit) { viewModel.prepareReturnHome() }
    }

    if (showReturningHomeModal) {
        // The teleport animation finishing only means it's time to *attempt* the teleport; whether
        // "WELCOME BACK" follows is decided by the result the ViewModel publishes (see the
        // LaunchedEffect above), not by this callback.
        ReturningHomeModal(
            onComplete = {
                viewModel.returnHome()
                showReturningHomeModal = false
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

    selectedStack?.let { stack ->
        AchievementStackModal(
            stack = stack,
            onDismiss = { selectedStack = null }
        )
    }

    selectedAchievement?.let { achievement ->
        AchievementBadgeModal(
            achievement = achievement,
            onDismiss = { selectedAchievement = null }
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
            // Close the picker on select, but leave the celebration to the published result -
            // changeHomeBase re-checks the 30-day cooldown and may write nothing at all.
            onAirportSelect = { airport ->
                viewModel.changeHomeBase(airport)
                showChangeHomeBase = false
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
                onNavigateHome()
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
                onNavigateHome()
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

/**
 * One tour's header. [id] is the identity - the collapse set and the `stickyHeader` key both
 * derive from it - while [title]/[subtitle] are display only and free to collide.
 */
private data class TourHeaderData(val id: String, val title: String, val subtitle: String)

/** Two tours cannot share a first-flight timestamp, which is what makes this a safe key. */
private fun tourId(tour: Tour): String = "tour_${tour.startedAt}"

private val ExpandedToursSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() }
)

@Composable
private fun TourHeader(header: TourHeaderData, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Midnight)
            .clickable(onClick = onToggle)
            .padding(vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = Amber
            )
            Text(
                text = header.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Haze
            )
        }
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Collapse tour" else "Expand tour",
            tint = Haze,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (expanded) 180f else 0f)
        )
    }
}

/**
 * The one place the row order is decided. [buildTourHeaders] indexes against exactly this
 * ordering, so the two must agree - which is why they are neighbours here rather than one living
 * in a ViewModel and one in the composable.
 */
private fun sortFlights(history: List<FlightLog>, sortOrder: FlightSortOrder): List<FlightLog> =
    when (sortOrder) {
        FlightSortOrder.DATE_DESC -> history.sortedByDescending { it.completedAt }
        FlightSortOrder.DATE_ASC -> history.sortedBy { it.completedAt }
        FlightSortOrder.DISTANCE_DESC -> history.sortedByDescending { it.distanceKm }
        FlightSortOrder.DISTANCE_ASC -> history.sortedBy { it.distanceKm }
        FlightSortOrder.DURATION_DESC -> history.sortedByDescending { it.durationMin }
    }

/**
 * Maps each row index to the tour header that should precede it - only the index where a new tour
 * starts is present, so the render loop carries the current tour forward for every other row.
 *
 * Tours are chronological by definition, so the distance/duration orders get no headers at all
 * (and, via the null-tour branch in the render loop, no collapsing either) - the same escape the
 * month headers used, for the same reason: grouping a distance-sorted list by date would scatter
 * single-item groups.
 */
private fun buildTourHeaders(tours: List<Tour>, sortOrder: FlightSortOrder): Map<Int, TourHeaderData> {
    val ordered = when (sortOrder) {
        FlightSortOrder.DATE_DESC -> tours
        FlightSortOrder.DATE_ASC -> tours.asReversed()
        else -> return emptyMap()
    }

    val headers = mutableMapOf<Int, TourHeaderData>()
    var index = 0
    ordered.forEach { tour ->
        headers[index] = TourHeaderData(
            id = tourId(tour),
            title = tourDateRange(tour.startedAt, tour.endedAt),
            subtitle = tourSubtitle(tour)
        )
        index += tour.flights.size
    }
    return headers
}

private val dayMonthFormat = SimpleDateFormat("d MMM", Locale.US)
private val dayMonthYearFormat = SimpleDateFormat("d MMM yyyy", Locale.US)

private fun tourDateRange(startedAt: Long, endedAt: Long): String {
    val start = dayMonthYearFormat.format(Date(startedAt)).uppercase(Locale.US)
    val end = dayMonthYearFormat.format(Date(endedAt)).uppercase(Locale.US)
    if (start == end) return end
    // Drop the repeated year from the start of a tour that does not cross one.
    val sameYear = start.takeLast(4) == end.takeLast(4)
    val shownStart = if (sameYear) dayMonthFormat.format(Date(startedAt)).uppercase(Locale.US) else start
    return "$shownStart – $end"
}

/**
 * "8 FLIGHTS · 12,400 MI · 11 OF 14 DAYS".
 *
 * The days ratio is the point of the whole feature - a bare "11" reads as a small number, while
 * "11 of 14 days" reads as near-perfect attendance, which is what it actually is. It is dropped
 * for a single-day tour, where "1 of 1 days" says nothing.
 */
private fun tourSubtitle(tour: Tour): String {
    val flights = if (tour.flights.size == 1) "1 FLIGHT" else "${tour.flights.size} FLIGHTS"
    val parts = mutableListOf(flights, formatMiles(tour.totalDistanceKm).uppercase(Locale.US))
    if (tour.spanDays > 1) parts += "${tour.activeDays} OF ${tour.spanDays} DAYS"
    return parts.joinToString(" · ")
}
