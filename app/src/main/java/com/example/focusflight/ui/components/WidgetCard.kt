package com.example.focusflight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.focusflight.ui.theme.Slate

/**
 * The app's small-widget card shell - a flat Slate surface with a hairline border, 20dp rounded
 * corners, 16dp padding. Pulled out of `FlightHighlightsRow`'s LONGEST FLIGHT card so every
 * stat/animation tile shares one background instead of each hand-rolling its own.
 *
 * Deliberately flat. This used to paint a Slate-to-DeepNavy gradient, which worked on the Passport
 * (a Midnight page) but made the card invisible inside a `ScrimCardModal`: that card's surface *is*
 * DeepNavy, so the gradient ended on exactly the background color and the widget dissolved into it.
 * A single fill that reads against both Midnight and DeepNavy, plus a border to give every card a
 * defined edge, is the fix.
 *
 * The hairline is an *accent* rather than the `Border` token on purpose: a 1dp line sits half on the
 * card's own fill, so it has to contrast with Slate as well as with the page behind it, and `Border`
 * is darker than Slate (about 1.09:1 against it - effectively invisible). Translucent Amber reads
 * against both, and matches the accent-hairline treatment `ArrivalCelebrationScreen` already uses.
 *
 * [containerColor] defaults to Slate, which is what a card inside a `ScrimCardModal` needs to stay
 * visible against that modal's DeepNavy surface. A card sitting directly on the Passport page can
 * pass DeepNavy instead, to match the hero card at the top of that screen.
 */
@Composable
fun WidgetCard(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    containerColor: Color = Slate,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .padding(16.dp),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}
