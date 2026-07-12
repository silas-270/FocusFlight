package com.example.focusflight.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import com.example.focusflight.ui.viewmodel.SearchMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.focusflight.R
import com.example.focusflight.data.model.Airport
import com.example.focusflight.ui.viewmodel.FlightSearchViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

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

    LaunchedEffect(Unit) {
        viewModel.resetState()
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
        // Main Screen Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Header Bar (incorporating the return button minimalist-style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = OffWhite
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.Medium))
                Text(
                    text = "SELECT ROUTE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = OffWhite,
                        fontFamily = FontFamily.SansSerif
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.toggleSearchMode() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                ) {
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

            // 2. Tactical 2D Route Map (expands to take available vertical space, no border, consistent padding)
            RouteMap(
                originAirport = originAirport,
                routes = if (searchMode == SearchMode.TIME) filteredRoutes else airportSearchResults,
                selectedRoute = selectedRoute,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            )

            Spacer(modifier = Modifier.height(Spacing.Medium))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp) // Maintain identical bottom panel height to preserve map height exactly
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
                                if (index >= 0 && index != pagerState.currentPage) {
                                    pagerState.scrollToPage(index)
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
                                    .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
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

@Composable
fun TimelineSlider(
    intervals: List<Int>,
    selectedInterval: Int,
    onIntervalSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        val containerWidth = maxWidth
        val sidePadding = 16.dp
        val availableWidth = containerWidth - (sidePadding * 2)
        // 5 columns visible at a time for optimal, non-wrapping spacing and perfect symmetry
        val itemWidth = availableWidth / 5

        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current

        // Center the default/programmatically selected interval in the timeline on launch
        var hasCenteredOnLaunch by remember { mutableStateOf(false) }
        LaunchedEffect(selectedInterval, intervals) {
            if (!hasCenteredOnLaunch && intervals.isNotEmpty()) {
                val index = intervals.indexOf(selectedInterval)
                if (index >= 0) {
                    listState.scrollToItem(index)
                    hasCenteredOnLaunch = true
                }
            }
        }

        // Downward caret in the center
        Icon(
            imageVector = Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            tint = OffWhite,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(36.dp)
                .offset(y = (-6).dp)
        )

        // Snapping selection on scroll with native inertia (no snap fling behavior constraint)
        val centeredIndex = remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) 0
                else {
                    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                    visibleItems.minByOrNull { Math.abs((it.offset + it.size / 2) - center) }?.index ?: 0
                }
            }
        }

        LaunchedEffect(centeredIndex.value) {
            if (intervals.isNotEmpty() && centeredIndex.value < intervals.size) {
                val activeInterval = intervals[centeredIndex.value]
                if (activeInterval != selectedInterval) {
                    onIntervalSelected(activeInterval)
                }
            }
        }

        LazyRow(
            state = listState,
            flingBehavior = rememberInertiaSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(horizontal = availableWidth / 2 - itemWidth / 2),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, start = sidePadding, end = sidePadding)
        ) {
            itemsIndexed(intervals) { index, interval ->
                val isCenter = interval == selectedInterval
                val isPast = interval < selectedInterval
                val isFuture = interval > selectedInterval

                val color = if (isCenter) OffWhite else Haze
                val textStyle = if (isCenter) {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                } else {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onIntervalSelected(interval)
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                ) {
                    // Tick Mark
                    Box(
                        modifier = Modifier
                            .width(if (isCenter) 4.dp else 2.dp)
                            .height(if (isCenter) 20.dp else 14.dp)
                            .background(color, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatTime(interval),
                        style = textStyle,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
fun SelectionCard(route: FlightRoute, isSelected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isSelected) 1f else 0.85f, label = "scale")
    val alpha by animateFloatAsState(if (isSelected) 1f else 0.6f, label = "alpha")

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .fillMaxWidth()
            .aspectRatio(1.0f) // Square aspect ratio
            .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .then(
                if (isSelected) Modifier.border(1.5.dp, OffWhite, RoundedCornerShape(20.dp))
                else Modifier.border(1.dp, Border, RoundedCornerShape(20.dp))
            )
            .clickable { onClick() }
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top: Yellow pill badge
            Row(
                modifier = Modifier
                    .background(Amber, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FlightTakeoff,
                    contentDescription = null,
                    tint = Midnight,
                    modifier = Modifier.size(20.dp).rotate(-15f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = route.destIata.uppercase(),
                    color = Midnight,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Middle: Full location name
            Text(
                text = route.destMunicipality,
                color = OffWhite,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom: Duration
            Text(
                text = formatTime(route.flightTimeMin),
                color = Haze,
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp
            )
        }
    }
}

private fun formatTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "%02d:%02d".format(h, m)
}

@Composable
fun rememberInertiaSnapFlingBehavior(
    lazyListState: androidx.compose.foundation.lazy.LazyListState
): FlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    return androidx.compose.runtime.remember(decay, lazyListState) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                var velocityLeft = initialVelocity
                var lastValue = 0f
                AnimationState(
                    initialValue = 0f,
                    initialVelocity = initialVelocity,
                ).animateDecay(decay) {
                    val delta = value - lastValue
                    val consumed = scrollBy(delta)
                    lastValue = value
                    velocityLeft = this.velocity
                    if (abs(delta - consumed) > 0.5f) {
                        this.cancelAnimation()
                    }
                }
                
                val layoutInfo = lazyListState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isNotEmpty()) {
                    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                    val closest = visibleItems.minByOrNull { abs((it.offset + it.size / 2f) - center) }
                    if (closest != null) {
                        val offsetToCenter = (closest.offset + closest.size / 2f) - center
                        if (abs(offsetToCenter) > 0.5f) {
                            var snapLastValue = 0f
                            AnimationState(
                                initialValue = 0f,
                                initialVelocity = velocityLeft
                            ).animateTo(
                                targetValue = offsetToCenter,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            ) {
                                val delta = value - snapLastValue
                                scrollBy(delta)
                                snapLastValue = value
                            }
                        }
                    }
                }
                return 0f
            }
        }
    }
}

@Composable
fun RouteMap(
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Midnight.copy(alpha = 0.35f))
    ) {
        Image(
            painter = painterResource(R.drawable.ic_world_map),
            contentDescription = "World Map",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            alpha = 0.35f
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (originAirport == null) return@Canvas

            val cxOrigin = size.width * ((originAirport.lon + 180) / 360f).toFloat()
            val cyOrigin = size.height * ((90 - originAirport.lat) / 180f).toFloat()

            // 1. Draw non-selected routes in the current interval (muted Haze)
            routes.forEach { route ->
                if (route.id != selectedRoute?.id) {
                    val cxDest = size.width * ((route.destLon + 180) / 360f).toFloat()
                    val cyDest = size.height * ((90 - route.destLat) / 180f).toFloat()

                    val path = Path().apply {
                        moveTo(cxOrigin, cyOrigin)
                        val dx = cxDest - cxOrigin
                        val midX = (cxOrigin + cxDest) / 2f
                        val midY = (cyOrigin + cyDest) / 2f
                        val controlY = midY - kotlin.math.abs(dx) * 0.15f - 20f
                        quadraticTo(midX, controlY, cxDest, cyDest)
                    }

                    drawPath(
                        path = path,
                        color = Haze.copy(alpha = 0.4f),
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                            cap = StrokeCap.Round
                        )
                    )

                    drawCircle(
                        color = Haze.copy(alpha = 0.6f),
                        radius = 3.dp.toPx(),
                        center = Offset(cxDest, cyDest)
                    )
                }
            }

            // 2. Draw the selected route (bright Amber, thick glowing line, animation)
            selectedRoute?.let { route ->
                val cxDest = size.width * ((route.destLon + 180) / 360f).toFloat()
                val cyDest = size.height * ((90 - route.destLat) / 180f).toFloat()

                val path = Path().apply {
                    moveTo(cxOrigin, cyOrigin)
                    val dx = cxDest - cxOrigin
                    val midX = (cxOrigin + cxDest) / 2f
                    val midY = (cyOrigin + cyDest) / 2f
                    val controlY = midY - kotlin.math.abs(dx) * 0.15f - 20f
                    quadraticTo(midX, controlY, cxDest, cyDest)
                }

                // Glow
                drawPath(
                    path = path,
                    color = Amber.copy(alpha = 0.2f),
                    style = Stroke(
                        width = 8f,
                        cap = StrokeCap.Round
                    )
                )

                // Main path
                drawPath(
                    path = path,
                    color = Amber,
                    style = Stroke(
                        width = 4f,
                        cap = StrokeCap.Round
                    )
                )

                // Animated flying dot
                try {
                    val pathMeasure = android.graphics.PathMeasure(path.asAndroidPath(), false)
                    val length = pathMeasure.length
                    val pos = FloatArray(2)
                    if (length > 0f) {
                        pathMeasure.getPosTan(length * animationProgress, pos, null)
                        val dotX = pos[0]
                        val dotY = pos[1]

                        drawCircle(
                            color = Amber.copy(alpha = 0.8f),
                            radius = 3.dp.toPx(),
                            center = Offset(dotX, dotY)
                        )
                        drawCircle(
                            color = Amber.copy(alpha = 0.2f),
                            radius = 3.dp.toPx() + 5.dp.toPx() * (1f - animationProgress),
                            center = Offset(dotX, dotY),
                            style = Stroke(width = 1f.dp.toPx())
                        )
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                // Destination target ring
                drawCircle(
                    color = Amber,
                    radius = 4.dp.toPx(),
                    center = Offset(cxDest, cyDest)
                )
                drawCircle(
                    color = Amber,
                    radius = 9.dp.toPx(),
                    center = Offset(cxDest, cyDest),
                    style = Stroke(width = 1.5f.dp.toPx())
                )
            }

            // 3. Draw origin airport (home base) marker
            drawCircle(
                color = OffWhite,
                radius = 5.dp.toPx(),
                center = Offset(cxOrigin, cyOrigin)
            )
            drawCircle(
                color = Amber,
                radius = 2.5.dp.toPx(),
                center = Offset(cxOrigin, cyOrigin)
            )
        }
    }
}

@Composable
fun AirportSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<FlightRoute>,
    selectedRoute: FlightRoute?,
    onRouteSelect: (FlightRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search airport…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Haze,
                    modifier = Modifier.padding(start = 4.dp)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = Haze,
                    modifier = Modifier.size(24.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Slate,
                unfocusedContainerColor = DeepNavy,
                cursorColor = Amber,
                focusedBorderColor = Amber,
                unfocusedBorderColor = Border.copy(alpha = 0.3f),
                focusedTextColor = OffWhite,
                unfocusedTextColor = OffWhite,
                focusedLeadingIconColor = Amber,
                unfocusedLeadingIconColor = Haze
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        if (query.trim().isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SUGGESTED DESTINATIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Haze
                )
                Spacer(modifier = Modifier.height(Spacing.Medium))

                val suggestions = remember(results) {
                    val popularIatas = listOf("FRA", "LHR", "BER", "MUC", "CDG", "JFK", "DXB", "AMS", "SIN", "HND")
                    val matching = results.filter { popularIatas.contains(it.destIata) }
                    if (matching.isNotEmpty()) {
                        matching.take(4)
                    } else {
                        results.take(4)
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    if (suggestions.size >= 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[0]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.flightTimeMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[1]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.flightTimeMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                        }
                    } else if (suggestions.size == 1) {
                        Box(modifier = Modifier.fillMaxWidth(0.5f)) {
                            val r = suggestions[0]
                            val isSelected = selectedRoute?.id == r.id
                            SuggestionTile(
                                code = r.destIata,
                                city = r.destMunicipality,
                                name = r.destName,
                                durationMin = r.flightTimeMin,
                                isSelected = isSelected,
                                onClick = { onRouteSelect(r) }
                            )
                        }
                    }

                    if (suggestions.size >= 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[2]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.flightTimeMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[3]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.flightTimeMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                        }
                    } else if (suggestions.size == 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                val r = suggestions[2]
                                val isSelected = selectedRoute?.id == r.id
                                SuggestionTile(
                                    code = r.destIata,
                                    city = r.destMunicipality,
                                    name = r.destName,
                                    durationMin = r.flightTimeMin,
                                    isSelected = isSelected,
                                    onClick = { onRouteSelect(r) }
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.Large),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "The airport does not offer direct connections to that airport",
                    color = Haze,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp)),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results.size) { index ->
                    val route = results[index]
                    val isSelected = selectedRoute?.id == route.id
                    val hrs = route.flightTimeMin / 60
                    val mins = route.flightTimeMin % 60
                    val formattedTime = "%02d:%02d".format(hrs, mins)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Slate.copy(alpha = 0.4f) else Midnight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .border(1.dp, if (isSelected) Amber else Border.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { onRouteSelect(route) }
                            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = route.destIata,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (isSelected) Amber else OffWhite
                            )
                            Text(
                                text = "${route.destMunicipality}, ${route.destName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Haze,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (isSelected) Amber else OffWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionTile(
    code: String,
    city: String,
    name: String,
    durationMin: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hrs = durationMin / 60
    val mins = durationMin % 60
    val timeStr = "%02d:%02d".format(hrs, mins)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Midnight.copy(alpha = 0.6f))
            .then(
                if (isSelected) Modifier.border(1.5.dp, Amber, RoundedCornerShape(16.dp))
                else Modifier.border(1.dp, Border, RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick)
            .padding(Spacing.Medium),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 28.dp)
                    .border(
                        width = 1.dp,
                        color = Border,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .background(
                        color = Midnight,
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = OffWhite
                )
            }

            Text(
                text = timeStr,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isSelected) Amber else OffWhite
            )
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))

        Text(
            text = city,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = OffWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = Haze,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
