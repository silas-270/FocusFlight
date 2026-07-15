package com.example.focusflight.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.StarHalf
import androidx.compose.material.icons.outlined.Tour
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.focusflight.ui.viewmodel.FlightHighlights
import com.example.focusflight.ui.viewmodel.FlightSortOrder
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.ui.components.InteractiveWorldMap
import com.example.focusflight.ui.theme.*
import com.example.focusflight.ui.viewmodel.AccountUiState
import com.example.focusflight.ui.viewmodel.AccountViewModel
import com.example.focusflight.ui.viewmodel.ContinentStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Continent display helpers ───────────────────────────────────────────────

private val continentNames = mapOf(
    "AF" to "Africa",
    "AN" to "Antarctica",
    "AS" to "Asia",
    "EU" to "Europe",
    "NA" to "N. America",
    "OC" to "Oceania",
    "SA" to "S. America"
)

private val continentColors = mapOf(
    "AF" to Color(0xFFF59E0B),
    "AN" to Color(0xFF93C5FD),
    "AS" to Color(0xFFEC4899),
    "EU" to Color(0xFF34D399),
    "NA" to Color(0xFF818CF8),
    "OC" to Color(0xFFFB923C),
    "SA" to Color(0xFF22D3EE)
)

// ─── Root composable ─────────────────────────────────────────────────────────

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

// ─── Profile Hero Card ───────────────────────────────────────────────────────

@Composable
private fun ProfileHeroCard(state: AccountUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(DeepNavy, Slate)
                )
            )
            .padding(Spacing.Large)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Amber.copy(alpha = 0.3f), Midnight)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(Spacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.username.ifBlank { "Captain" },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = OffWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(SoftAmber, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#${state.userCode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.homeAirportIata.isNotBlank()) {
                        InfoChip(
                            icon = Icons.Outlined.FlightTakeoff,
                            label = state.homeAirportIata
                        )
                    }
                    if (state.joinDateFormatted.isNotBlank()) {
                        InfoChip(
                            icon = Icons.Outlined.Star,
                            label = state.joinDateFormatted.removePrefix("Joined ")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Haze, modifier = Modifier.size(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Haze)
    }
}

// ─── Stats Grid (2x2 Layout) ──────────────────────────────────────────────────

@Composable
private fun StatsGrid2x2(state: AccountUiState) {
    val hoursInt = state.totalMinutes / 60
    val minutesInt = state.totalMinutes % 60
    val timeStr = String.format(Locale.US, "%02d:%02d", hoursInt, minutesInt)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = state.totalFlights.toString(),
                label = "FLIGHTS",
                icon = Icons.Outlined.AirplanemodeActive
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = timeStr,
                label = "HOURS",
                icon = Icons.Outlined.Schedule
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = state.airportsVisited.toString(),
                label = "AIRPORTS",
                icon = Icons.Outlined.FlightLand
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = state.allVisitedCountries.size.toString(),
                label = "COUNTRIES",
                icon = Icons.Outlined.Language
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: ImageVector
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DeepNavy)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Slate),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = OffWhite
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Haze,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ─── Travel Map Card ─────────────────────────────────────────────────────────

@Composable
private fun TravelMapCard(state: AccountUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
            .padding(Spacing.Medium)
    ) {
        SectionHeader(icon = Icons.Outlined.Public, title = "TRAVEL MAP")
        Spacer(Modifier.height(Spacing.Small))
        InteractiveWorldMap(
            mapPaths = state.mapPaths,
            visitedCountries = state.allVisitedCountries,
            countryToContinent = state.countryToContinent,
            completedContinents = state.completedContinents,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

// ─── Continent Progress Card ──────────────────────────────────────────────────

@Composable
private fun ContinentProgressCard(stats: List<ContinentStats>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        stats.forEach { stat ->
            ContinentProgressRow(stat)
        }
    }
}

@Composable
private fun ContinentProgressRow(stat: ContinentStats) {
    val progress = if (stat.totalCountries > 0)
        stat.visitedCountries.size.toFloat() / stat.totalCountries.toFloat()
    else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progress_${stat.continentCode}"
    )

    val color = continentColors[stat.continentCode] ?: Amber
    val name = continentNames[stat.continentCode] ?: stat.continentCode

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Color dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = OffWhite
                )
                if (stat.isCompleted) {
                    Box(
                        modifier = Modifier
                            .background(Green.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "DONE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Green,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            Text(
                text = "${stat.visitedCountries.size} / ${stat.totalCountries}",
                style = MaterialTheme.typography.labelMedium,
                color = Haze
            )
        }

        // Custom progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Slate)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (stat.isCompleted)
                            Brush.horizontalGradient(listOf(Green, Green.copy(alpha = 0.7f)))
                        else
                            Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f)))
                    )
            )
        }
    }
}

// ─── Logbook Entry ────────────────────────────────────────────────────────────

// Margin line x-position in dp
private val LogbookMarginDp = 36.dp

@Composable
private fun LogbookEntry(flight: FlightLog, entryNumber: Int) {
    val dateStr = remember(flight.completedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(flight.completedAt))
    }
    val hoursInt = flight.durationMin / 60
    val minutesInt = flight.durationMin % 60
    val durationStr = String.format(Locale.US, "%02dh%02dm", hoursInt, minutesInt)
    val distanceStr = "%,d km".format(flight.distanceKm.toInt())

    // ── Paper palette ──────────────────────────────────────────────────────
    val parchment      = Color(0xFFF5E6C0)   // aged cream
    val parchmentDark  = Color(0xFFEDD89A)   // slightly more yellowed patch
    val ruleBlue       = Color(0xFF8EB4D4).copy(alpha = 0.55f)   // classic ink-blue lines
    val marginRed      = Color(0xFFCC1C1C)   // bright red margin
    val inkDark        = Color(0xFF1A1208)   // near-black ink
    val inkMid         = Color(0xFF6B5033)   // warm sepia mid-tone
    val inkFaint       = Color(0xFFB09870)   // faded sepia labels

    // Pre-compute grain points once per card so Canvas never re-allocates per frame
    val grainPoints = remember(flight.id) {
        val rng = java.util.Random(flight.id.toLong() xor 0xDEADBEEF)
        List(600) {
            Triple(rng.nextFloat(), rng.nextFloat(), rng.nextFloat())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        // ── Paper texture Canvas (fills full card) ─────────────────────────
        val density = androidx.compose.ui.platform.LocalDensity.current
        val marginPx     = with(density) { LogbookMarginDp.toPx() }
        val rowHeightPx  = with(density) { 22.dp.toPx() }
        val cardHeightPx = with(density) { 88.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        ) {
            // 1. Parchment base fill
            drawRect(color = parchment)

            // 2. Subtle warm gradient patch (upper-left yellowing effect)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(parchmentDark.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.3f),
                    radius = size.width * 0.55f
                )
            )

            // 3. Paper grain — tiny specks of varying warm tones
            grainPoints.forEach { (fx, fy, falpha) ->
                val grainColor = if (falpha > 0.6f)
                    Color(0xFF8B6914).copy(alpha = falpha * 0.08f)   // warm dark speck
                else
                    Color(0xFFFFFFE0).copy(alpha = falpha * 0.18f)   // lighter highlight
                drawCircle(
                    color = grainColor,
                    radius = 0.5f + falpha * 1.0f,
                    center = Offset(fx * size.width, fy * size.height)
                )
            }

            // 4. Faint vignette edges (paper edge darkening)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF8B6914).copy(alpha = 0.12f),
                        Color.Transparent,
                        Color(0xFF8B6914).copy(alpha = 0.08f)
                    )
                )
            )

            // 5. Blue ruled lines (classic notebook style)
            for (i in 1..3) {
                drawLine(
                    color = ruleBlue,
                    start = Offset(0f, rowHeightPx * i),
                    end   = Offset(size.width, rowHeightPx * i),
                    strokeWidth = 0.9f
                )
            }

            // 6. Red margin line
            drawLine(
                color = marginRed,
                start = Offset(marginPx, 0f),
                end   = Offset(marginPx, size.height),
                strokeWidth = 2.0f
            )
        }

        // ── Content overlay ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(start = 0.dp, end = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Margin: entry number ──
            Box(
                modifier = Modifier.width(LogbookMarginDp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(entryNumber),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    ),
                    color = marginRed.copy(alpha = 0.85f)
                )
            }

            Spacer(Modifier.width(10.dp))

            // ── Main data columns ──
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Row 1: DATE on left, flight number stamp on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateStr.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 1.2.sp
                        ),
                        color = inkFaint
                    )
                    // Flight number — rubber-stamp style
                    Box(
                        modifier = Modifier
                            .border(1.dp, marginRed.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = flight.flightNumber,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.8.sp
                            ),
                            color = marginRed.copy(alpha = 0.75f)
                        )
                    }
                }

                // Row 2: ORIGIN ··✈·· DEST
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = flight.originIata,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp
                        ),
                        color = inkDark
                    )
                    Text(
                        text = "·  ·  ·  ✈  ·  ·  ·",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 8.sp,
                            letterSpacing = 0.sp
                        ),
                        color = inkMid.copy(alpha = 0.45f)
                    )
                    Text(
                        text = flight.destIata,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp
                        ),
                        color = inkDark
                    )
                }

                // Row 3: DIST | TIME
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LogbookDataCell(label = "DIST", value = distanceStr, labelColor = inkFaint, valueColor = inkMid)
                    LogbookDataCell(label = "TIME", value = durationStr, labelColor = inkFaint, valueColor = inkMid)
                }
            }
        }
    }
}

@Composable
private fun LogbookDataCell(
    label: String,
    value: String,
    labelColor: Color = Haze.copy(alpha = 0.6f),
    valueColor: Color = Amber
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 1.5.sp
            ),
            color = labelColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = valueColor
        )
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = Amber
        )
    }
}

// ─── Highlights & Sorting Helper Components ─────────────────────────────────

@Composable
private fun FlightHighlightsRow(highlights: FlightHighlights) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. Longest Flight Card
        highlights.longestFlight?.let { flight ->
            item {
                HighlightCard(
                    title = "LONGEST FLIGHT",
                    value = "${flight.originIata} → ${flight.destIata}",
                    subtext = "%,.0f km".format(flight.distanceKm),
                    icon = Icons.Outlined.AirplanemodeActive
                )
            }
        }
        // 2. Most Visited Card
        highlights.mostVisitedIata?.let { iata ->
            item {
                HighlightCard(
                    title = "MOST VISITED",
                    value = iata,
                    subtext = "${highlights.mostVisitedCount} visits",
                    icon = Icons.Outlined.Tour
                )
            }
        }
        // 3. Equator Ratio Card
        item {
            val equatorRatioPercent = highlights.equatorRatio * 100
            val subtext = if (highlights.equatorRatio >= 1.0) {
                "%.2f equator runs".format(highlights.equatorRatio)
            } else {
                "%.1f%% of the equator".format(equatorRatioPercent)
            }
            HighlightCard(
                title = "EQUATOR PROGRESS",
                value = "🌍",
                subtext = subtext,
                icon = Icons.Outlined.StarHalf
            )
        }
    }
}

@Composable
private fun HighlightCard(
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DeepNavy),
        modifier = Modifier
            .width(170.dp)
            .height(110.dp)
            .border(1.dp, SoftAmber.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                    color = Haze
                )
                Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = OffWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SortDropdown(
    currentOrder: FlightSortOrder,
    onOrderSelected: (FlightSortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = DeepNavy,
                contentColor = OffWhite
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .height(32.dp)
                .border(1.dp, SoftAmber.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = currentOrder.displayName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Amber
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = "Sort order options",
                    tint = OffWhite,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(DeepNavy)
                .border(1.dp, Border, RoundedCornerShape(8.dp))
        ) {
            FlightSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = order.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (order == currentOrder) Amber else OffWhite
                        )
                    },
                    onClick = {
                        onOrderSelected(order)
                        expanded = false
                    }
                )
            }
        }
    }
}
