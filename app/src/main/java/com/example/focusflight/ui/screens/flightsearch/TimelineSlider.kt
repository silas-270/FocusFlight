package com.example.focusflight.ui.screens.flightsearch

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.example.focusflight.util.formatDuration
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import kotlinx.coroutines.launch
import kotlin.math.abs

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
            .background(DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
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
                // Soft fades at both ends, so the empty half beside the first or last bucket
                // reads as the end of the scale rather than missing content.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = (EdgeFadeWidth.toPx() / size.width).coerceIn(0f, 0.5f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            fade to Color.Black,
                            1f - fade to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            itemsIndexed(intervals) { index, interval ->
                val isCenter = interval == selectedInterval
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
                        text = formatDuration(interval),
                        style = textStyle,
                        color = color
                    )
                }
            }
        }
    }
}


@Composable
fun rememberInertiaSnapFlingBehavior(
    lazyListState: LazyListState
): FlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    return remember(decay, lazyListState) {
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

private val EdgeFadeWidth = 24.dp
