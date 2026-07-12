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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
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
    val filteredRoutes by viewModel.filteredRoutes.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
        // Back Button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(Spacing.Medium)
                .size(40.dp)
                .background(Midnight.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .border(1.dp, Border, RoundedCornerShape(8.dp))
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = OffWhite)
        }

        // ── Foreground UI Stack ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = Spacing.Large),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Timeline Slider Container (with 1 hour visible bounds)
            if (intervals.isNotEmpty()) {
                TimelineSlider(
                    intervals = intervals,
                    selectedInterval = selectedInterval,
                    onIntervalSelected = { viewModel.selectInterval(it) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. Selection Cards (Carousel / Empty State)
            if (filteredRoutes.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { filteredRoutes.size })

                // Sync current pager selection with active selected route in ViewModel
                LaunchedEffect(pagerState.currentPage, filteredRoutes) {
                    if (pagerState.currentPage < filteredRoutes.size) {
                        viewModel.selectRoute(filteredRoutes[pagerState.currentPage])
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 80.dp),
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

                Spacer(modifier = Modifier.height(32.dp))

                // 3. Pagination Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until filteredRoutes.size) {
                        val isActive = pagerState.currentPage == i
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(8.dp)
                                    .background(Slate, RoundedCornerShape(50))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(OffWhite, CircleShape)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Confirm Selection Button
                Button(
                    onClick = { selectedRoute?.let { onRouteConfirm(it) } },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Midnight)
                ) {
                    Text(
                        text = "CONFIRM SELECTION",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    )
                }
            } else {
                // Empty state for current interval
                Box(
                    modifier = Modifier
                        .padding(horizontal = 80.dp)
                        .fillMaxWidth()
                        .aspectRatio(0.85f)
                        .background(Midnight.copy(alpha = 0.9f), RoundedCornerShape(32.dp))
                        .border(1.dp, Border, RoundedCornerShape(32.dp))
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
                Spacer(modifier = Modifier.height(112.dp)) // Spacer to keep layout heights balanced
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
            .fillMaxWidth(0.95f)
            .height(110.dp)
            .background(Midnight.copy(alpha = 0.9f), MaterialTheme.shapes.extraLarge)
            .padding(top = 12.dp, bottom = 16.dp)
    ) {
        val containerWidth = maxWidth
        val sidePadding = 16.dp
        val availableWidth = containerWidth - (sidePadding * 2)
        // 1 hour visible width difference = 60 minutes = 6 columns (each of 10m)
        val itemWidth = availableWidth / 6

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
                .padding(top = 28.dp, start = sidePadding, end = sidePadding)
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
            .aspectRatio(0.85f)
            .background(Midnight.copy(alpha = 0.95f), RoundedCornerShape(32.dp))
            .then(
                if (isSelected) Modifier.border(4.dp, OffWhite, RoundedCornerShape(32.dp))
                else Modifier
            )
            .clickable { onClick() }
            .padding(if (isSelected) 24.dp else 16.dp),
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
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp).rotate(-15f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = route.destIata.uppercase(),
                    color = Color.Black,
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
