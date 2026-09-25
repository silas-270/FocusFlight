package com.silas270.blocktime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.silas270.blocktime.ui.theme.Border
import com.silas270.blocktime.ui.theme.Container
import com.silas270.blocktime.ui.theme.Radius
import com.silas270.blocktime.ui.theme.Spacing
import com.silas270.blocktime.ui.theme.Surface as ThemeSurface

enum class CardVariant {
    Surface,     // DeepNavy surface
    Secondary,   // Slate container
    Elevated,    // DeepNavy with hairline Border
    Outlined     // Transparent with Border
}

/**
 * Standardized card surface component for Blocktime.
 *
 * Enforces consistent corner radii (Radius.Large = 16dp by default, or Radius.ExtraLarge = 20dp for hero/modals),
 * padding, and theme-safe backgrounds.
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Surface,
    shape: Shape = RoundedCornerShape(Radius.Large),
    border: BorderStroke? = when (variant) {
        CardVariant.Elevated -> BorderStroke(1.dp, Border)
        CardVariant.Outlined -> BorderStroke(1.dp, Border)
        else -> null
    },
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.Medium),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = when (variant) {
        CardVariant.Surface, CardVariant.Elevated -> ThemeSurface
        CardVariant.Secondary -> Container
        CardVariant.Outlined -> Color.Transparent
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    val borderModifier = if (border != null) {
        Modifier.border(border, shape)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .then(borderModifier)
            .background(containerColor)
            .then(clickableModifier)
            .padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}
