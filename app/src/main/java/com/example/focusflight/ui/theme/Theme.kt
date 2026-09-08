package com.example.focusflight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.model.ThemeMode

// The cockpit dark theme - FocusFlight's original and default look.
private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.amber,
    onPrimary = DarkPalette.midnight,
    secondary = DarkPalette.haze,
    onSecondary = DarkPalette.offWhite,
    background = DarkPalette.midnight,
    onBackground = DarkPalette.offWhite,
    surface = DarkPalette.deepNavy,
    onSurface = DarkPalette.offWhite,
    surfaceVariant = DarkPalette.slate,
    onSurfaceVariant = DarkPalette.offWhite,
    outline = DarkPalette.border,
    error = DarkPalette.amber // reuse amber/warm coral if we had it, but amber is primary
)

// The "Sky" light theme - clouds and open sky in place of leather and brass.
private val LightColorScheme = lightColorScheme(
    primary = LightSkyPalette.amber,
    onPrimary = LightSkyPalette.midnight,
    secondary = LightSkyPalette.haze,
    onSecondary = LightSkyPalette.offWhite,
    background = LightSkyPalette.midnight,
    onBackground = LightSkyPalette.offWhite,
    surface = LightSkyPalette.deepNavy,
    onSurface = LightSkyPalette.offWhite,
    surfaceVariant = LightSkyPalette.slate,
    onSurfaceVariant = LightSkyPalette.offWhite,
    outline = LightSkyPalette.border,
    error = LightSkyPalette.amber // matches the dark scheme's choice above
)

object Spacing {
    val None: Dp = 0.dp
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 24.dp
    val ExtraLarge: Dp = 32.dp
}

object Radius {
    val Small: Dp = 12.dp
    val Medium: Dp = 14.dp
    val Large: Dp = 16.dp
    val ExtraLarge: Dp = 20.dp
}

@Composable
fun FocusFlightTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (useDarkTheme) DarkPalette else LightSkyPalette
    // Runs after a successful composition, before the next frame draws - every top-level color
    // token in Color.kt (Amber, OffWhite, ...) reads through ActivePalette.current, so this one
    // write is what actually repaints the whole app when the mode changes.
    SideEffect {
        ActivePalette.current = palette
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
