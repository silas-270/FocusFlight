package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.AccountViewModel
import com.example.focusflight.ui.viewmodel.FlightSortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyPagingItems = viewModel.pagedFlights.collectAsLazyPagingItems()

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

                // ── Paged Logbook Items ───────────────────────────────────────
                items(
                    count = lazyPagingItems.itemCount,
                    key = lazyPagingItems.itemKey { it.id },
                    contentType = lazyPagingItems.itemContentType { "flight" }
                ) { index ->
                    val flight = lazyPagingItems[index]
                    if (flight != null) {
                        val entryNo = when (uiState.sortOrder) {
                            FlightSortOrder.DATE_DESC, FlightSortOrder.DISTANCE_DESC, FlightSortOrder.DURATION_DESC -> uiState.totalFlights - index
                            FlightSortOrder.DATE_ASC, FlightSortOrder.DISTANCE_ASC -> index + 1
                        }
                        LogbookEntry(flight = flight, entryNumber = entryNo)
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
