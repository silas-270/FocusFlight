package com.example.focusflight.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The shortest-side width, in dp, that every screen was designed and tuned on (a Galaxy S23 at
 * default display zoom). [ProvideDesignDensity] scales the whole UI so this many dp always spans
 * the screen's shortest side.
 */
const val DESIGN_SHORT_SIDE_DP = 360f

/** Shortest sides at or beyond this (in the system's own dp) are tablets/unfolded foldables. */
private const val TABLET_SHORT_SIDE_DP = 480f

/** Upper bound on the upscale, relative to the system density. */
private const val MAX_SCALE = 1.35f

/**
 * The window's size in *design* dp - i.e. under [ProvideDesignDensity]'s density. Use this instead
 * of `LocalConfiguration.current.screenWidthDp/screenHeightDp`, which stay in the system's dp and
 * would disagree with every other dp value on screen once the density is scaled.
 */
val LocalDesignScreenSize = staticCompositionLocalOf { DpSize.Unspecified }

/**
 * Makes a bigger phone render the S23 layout *upscaled* rather than re-flowed: the density is set
 * so the window's shortest side is exactly [DESIGN_SHORT_SIDE_DP] dp, which scales every dp and sp
 * (paddings, radii, fixed heights, text) by the same factor. On the S23 the factor is 1 and nothing
 * changes; on a 412 dp-wide phone everything is ~14% larger, with identical proportions, line
 * breaks and column counts.
 *
 * Deliberately bounded:
 * - never *below* the system density, so a smaller or display-zoomed screen keeps the user's zoom
 *   instead of being shrunk;
 * - at most [MAX_SCALE]x, and not at all on tablets / unfolded foldables, where a phone layout
 *   blown up to fill the screen would be absurd;
 * - `fontScale` is passed through untouched, so the system font-size setting still applies.
 *
 * Shortest side (not width) so the rotating In-Flight screen keeps one scale in both orientations.
 * Everything here renders inside the one window (no Dialog/Popup windows, which would not inherit
 * an overridden LocalDensity); window insets and pointer input are px-based and unaffected.
 */
@Composable
fun ProvideDesignDensity(content: @Composable () -> Unit) {
    val systemDensity = LocalDensity.current
    val windowPx = LocalWindowInfo.current.containerSize
    val shortPx = minOf(windowPx.width, windowPx.height).toFloat()

    val density = when {
        shortPx <= 0f -> systemDensity.density
        shortPx / systemDensity.density >= TABLET_SHORT_SIDE_DP -> systemDensity.density
        else -> (shortPx / DESIGN_SHORT_SIDE_DP)
            .coerceIn(systemDensity.density, systemDensity.density * MAX_SCALE)
    }
    val designSize = DpSize((windowPx.width / density).dp, (windowPx.height / density).dp)

    CompositionLocalProvider(
        LocalDensity provides Density(density, systemDensity.fontScale),
        LocalDesignScreenSize provides designSize,
        content = content
    )
}
