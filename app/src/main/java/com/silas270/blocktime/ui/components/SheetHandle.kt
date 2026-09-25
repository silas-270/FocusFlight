package com.silas270.blocktime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.silas270.blocktime.ui.theme.Border
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Visible size of the grab bar. */
private val HandleBarWidth = 80.dp
private val HandleBarHeight = 4.dp

/** Default minimum height of the handle's tap strip, whatever its padding. */
val SheetHandleMinTouchHeight = 48.dp

/**
 * Height a [SheetHandle] adds above the sheet content for the given paddings. Exposed so callers
 * that cap the sheet's height (In-Flight landscape) can subtract exactly what the handle takes.
 */
fun sheetHandleHeight(topPadding: Dp, bottomPadding: Dp, minTouchHeight: Dp = SheetHandleMinTouchHeight): Dp =
    maxOf(topPadding + HandleBarHeight + bottomPadding, minTouchHeight)

/**
 * The bottom sheets' grab bar. Swiping works as before; tapping anywhere across the full-width
 * strip now toggles the sheet too - before, a tap on the handle did nothing, and a tap is what
 * most people try first. The bar is drawn [topPadding] below the strip's top edge so existing
 * layouts keep their spacing; the strip grows to [minTouchHeight] below the bar if the
 * paddings alone would make it shorter. A sheet whose peek content is itself a tap target (see
 * [toggle]) can pass a smaller [minTouchHeight] so the handle doesn't push that content down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHandle(
    sheetState: SheetState,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    minTouchHeight: Dp = SheetHandleMinTouchHeight
) {
    val scope = rememberCoroutineScope()
    val expanded = sheetState.currentValue == SheetValue.Expanded
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHandleHeight(topPadding, bottomPadding, minTouchHeight))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = if (expanded) "Collapse" else "Expand"
            ) { scope.toggle(sheetState) },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(top = topPadding)
                .width(HandleBarWidth)
                .height(HandleBarHeight)
                .background(Border, RoundedCornerShape(2.dp))
        )
    }
}

/** Expands a collapsed sheet, collapses an expanded one. For other tap targets on a sheet's peek. */
@OptIn(ExperimentalMaterial3Api::class)
fun CoroutineScope.toggle(sheetState: SheetState) {
    launch {
        if (sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand() else sheetState.expand()
    }
}
