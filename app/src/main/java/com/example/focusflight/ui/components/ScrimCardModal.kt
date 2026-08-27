package com.example.focusflight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Spacing

/**
 * The app's one modal convention (see docs/design/codebase-map.md): a full-screen scrim [Box] and
 * a sibling centered card [Box] - never `AlertDialog`/`Dialog`. Originated in
 * `InFlightScreen.kt`'s exit/pause confirmation (scrim and card are siblings, not nested, so a tap
 * on the card can't also fall through to the scrim's dismiss handler underneath it - nesting was
 * tried and rejected per commit `fc160b0`). Pulled out here so Phase 3b's Route-continue
 * confirmation and abandon confirmation don't reinvent it a third and fourth time, per
 * codebase-map.md's explicit recommendation to extract and reuse this pattern.
 *
 * [onScrimTap] is usually "dismiss", but callers that pause something while the modal is up (the
 * original exit-confirm resumes the flight timer on scrim-tap) can do that here too.
 */
@Composable
fun ScrimCardModal(
    onScrimTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onScrimTap
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = Spacing.Large)
                .fillMaxWidth()
                .background(DeepNavy, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}
