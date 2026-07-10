package com.example.focusflight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// We enforce dark theme only for the night/cockpit aesthetic of FocusFlight
private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Midnight,
    secondary = Haze,
    onSecondary = OffWhite,
    background = Midnight,
    onBackground = OffWhite,
    surface = DeepNavy,
    onSurface = OffWhite,
    surfaceVariant = Slate,
    onSurfaceVariant = OffWhite,
    outline = Border,
    error = Amber // reuse amber/warm coral if we had it, but amber is primary
)

object Spacing {
    val None: Dp = 0.dp
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 24.dp
    val ExtraLarge: Dp = 32.dp
}

@Composable
fun FocusFlightTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
