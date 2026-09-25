package com.silas270.blocktime.ui.screens.account

import com.silas270.blocktime.ui.theme.ScreenGutter
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.silas270.blocktime.data.model.AchievementStatus
import com.silas270.blocktime.data.model.FlightLog
import com.silas270.blocktime.ui.components.BackTopAppBar
import com.silas270.blocktime.ui.components.SectionHeader
import com.silas270.blocktime.data.model.Tour
import com.silas270.blocktime.util.formatMiles
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.Midnight
import com.silas270.blocktime.ui.theme.Spacing
import com.silas270.blocktime.ui.viewmodel.account.AccountViewModel
import com.silas270.blocktime.data.model.FlightSortOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Pilot Passport: a read-only trophy case and logbook (identity card, travel map,
 * achievements, flight highlights, flight history). Anything that changes app state or
 * preferences - theme, return home, change home base - lives on the separate Settings screen
 * (see `SettingsScreen.kt`) instead, reached from the Hub's own icon rather than from here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBackClick: () -> Unit
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

    // A logbook entry's number belongs to the flight, not to its row: the pilot's first flight is
    // №01 in every sort order. It used to be the row position (counted from whichever end the sort
    // started at), so re-sorting renumbered the whole book - and under the distance/duration
    // orders the number meant nothing at all.
    val entryNumbers = remember(uiState.flightHistory) {
        uiState.flightHistory
            .sortedBy { it.completedAt }
            .withIndex()
            .associate { (index, flight) -> flight.id to index + 1 }
    }

    // Sticky tour headers only make sense when the list is chronologically sorted — grouping by
    // distance/duration order would scatter single-item groups.
    val tourHeaders = remember(uiState.tours, uiState.sortOrder) {
        buildTourHeaders(uiState.tours, uiState.sortOrder)
    }

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
    // Every tour starts collapsed - expanding the newest one by default forced its flights
    // (each a Canvas-drawn parchment card) to compose immediately, slowing the initial render.
    var expandedTours by rememberSaveable(stateSaver = ExpandedToursSaver) {
        mutableStateOf(setOf<String>())
    }
    // Owned here, not inside AchievementBadgeGrid: a ScrimCardModal opened from inside a
    // LazyColumn item is clipped to that item, so it has to be a sibling of the Scaffold.
    //
    // Two, because a ladder tile opens onto its tiers while a lone badge opens straight onto its
    // own card. Only ever one of them is non-null.
    var selectedStack by remember { mutableStateOf<AchievementStack?>(null) }
    var selectedAchievement by remember { mutableStateOf<AchievementStatus?>(null) }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BackTopAppBar(title = "PILOT PASSPORT", onBackClick = onBackClick, scrolled = listState.canScrollBackward)
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
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(
                    start = ScreenGutter,
                    end = ScreenGutter,
                    top = Spacing.Small,
                    bottom = Spacing.ExtraLarge
                )
            ) {
                // ── Hero Profile Card ─────────────────────────────────────────
                // Static identity card - no longer expandable. The home-base actions it used to
                // reveal live on the Settings screen now (see SettingsScreen.kt).
                item { ProfileHeroCard(state = uiState) }

                // ── Travel Map ────────────────────────────────────────────────
                // Straight under the hero card: shows the whole playthrough at a glance.
                // Tap to open world exploration modal detail.
                item { TravelMapCard(uiState, onClick = { showTravelMapModal = true }) }

                // ── Stats ─────────────────────────────────────────────────────
                // Flights and airports, plus the running tour once it is worth reporting. Right
                // under the map on purpose: `airportsVisited` is STORY-scoped precisely so it agrees
                // with the map beside it (docs/modes.md).
                item { StatsGrid2x2(uiState) }

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
                // The sort control only appears once there is something to sort.
                val hasFlights = sortedFlights.isNotEmpty()
                item {
                    SectionHeader(
                        title = "FLIGHT HISTORY",
                        trailingAction = if (hasFlights) {
                            {
                                SortOrderButton(
                                    currentOrder = uiState.sortOrder,
                                    onClick = { showSortModal = true }
                                )
                            }
                        } else {
                            null
                        }
                    )
                }

                // Same quiet one-liner as the Achievements and highlights empty states.
                if (!hasFlights) {
                    item {
                        Text(
                            text = "No flights logged yet - your first landing will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Haze
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
                            LogbookEntry(
                                flight = flight,
                                entryNumber = entryNumbers[flight.id] ?: (index + 1)
                            )
                        }
                    }
                }
            }
        }
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
            visitedCountryCodes = uiState.allVisitedCountries,
            totalCountriesCount = totalCountries,
            onDismiss = { showTravelMapModal = false }
        )
    }
    }
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
