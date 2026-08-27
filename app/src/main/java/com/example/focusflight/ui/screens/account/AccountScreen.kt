package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.StarHalf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.focusflight.data.model.FlightLog
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
    onBackClick: () -> Unit
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PILOT PASSPORT",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Midnight
                )
            )
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
                item { ProfileHeroCard(uiState) }

                // ── Stats Row (2x2 Grid) ──────────────────────────────────────
                item { StatsGrid2x2(uiState) }

                // ── Travel Map ────────────────────────────────────────────────
                item { TravelMapCard(uiState) }

                // ── Continent Progress ────────────────────────────────────────
                item { SectionHeader(icon = Icons.Outlined.Public, title = "CONTINENT COVERAGE") }
                item { ContinentProgressCard(uiState.continentStats) }

                // ── Flight Highlights ─────────────────────────────────────────
                item { SectionHeader(icon = Icons.Outlined.StarHalf, title = "FLIGHT HIGHLIGHTS") }
                item { FlightHighlightsRow(uiState.highlights) }

                // ── Flight History Header + Sorting Bar ───────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            icon = Icons.Outlined.AirplanemodeActive,
                            title = "FLIGHT HISTORY"
                        )
                        SortDropdown(
                            currentOrder = uiState.sortOrder,
                            onOrderSelected = { viewModel.setSortOrder(it) }
                        )
                    }
                }

                // ── Paged Logbook Items (with sticky month/year headers) ──────
                for (index in 0 until lazyPagingItems.itemCount) {
                    val headerLabel = monthHeaderLabels[index]
                    if (headerLabel != null) {
                        stickyHeader(key = "header_$headerLabel") {
                            MonthHeader(headerLabel)
                        }
                    }
                    item(
                        key = lazyPagingItems.peek(index)?.id ?: "placeholder_$index",
                        contentType = "flight"
                    ) {
                        val flight = lazyPagingItems[index]
                        if (flight != null) {
                            val entryNo = when (uiState.sortOrder) {
                                FlightSortOrder.DATE_DESC, FlightSortOrder.DISTANCE_DESC, FlightSortOrder.DURATION_DESC -> uiState.totalFlights - index
                                FlightSortOrder.DATE_ASC, FlightSortOrder.DISTANCE_ASC -> index + 1
                            }
                            LogbookEntry(flight = flight, entryNumber = entryNo)
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
}

@Composable
private fun MonthHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Midnight)
            .padding(vertical = Spacing.Small)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = Haze
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
