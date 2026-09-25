package com.example.focusflight.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Spacing

/**
 * The app's one modal convention (see docs/architecture.md): a full-screen scrim [Box] and
 * a sibling centered card [Box] - never `AlertDialog`/`Dialog`. Originated in
 * `InFlightScreen.kt`'s exit/pause confirmation (scrim and card are siblings, not nested, so a tap
 * on the card can't also fall through to the scrim's dismiss handler underneath it - nesting was
 * tried and rejected per commit `fc160b0`). Pulled out here so Phase 3b's Route-continue
 * confirmation and abandon confirmation don't reinvent it a third and fourth time, per
 * codebase-map.md's explicit recommendation to extract and reuse this pattern.
 *
 * [onScrimTap] is usually "dismiss", but callers that pause something while the modal is up (the
 * original exit-confirm resumes the flight timer on scrim-tap) can do that here too. System back
 * is routed to it as well: without that the back gesture passes straight through an open modal and
 * pops the whole nav destination underneath it, which on the Challenges screen meant tapping back
 * to close the challenge picker instead threw you out to the Hub.
 *
 * [onClose] adds a visible ✕ in the card's top-right corner. Info-only modals (no action
 * buttons) pass it, since scrim tap and back are invisible ways out; modals whose buttons
 * already close them leave it null.
 */
@Composable
fun ScrimCardModal(
    onScrimTap: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    BackHandler(onBack = onScrimTap)
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
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                // Centre the card in the space above the keyboard, not the whole screen - the
                // window is edge-to-edge, so the IME overlays rather than resizes it, and a modal
                // with a text field (Create Challenge's search and distance forms) otherwise had
                // its field and buttons under the keyboard. A no-op for every modal without one.
                .imePadding()
                .padding(horizontal = Spacing.Large)
                .fillMaxWidth()
                // clip (not just a rounded background) so content that draws or translates past
                // its own bounds - e.g. ReturningHomeModal's speed-line canvas - is cut off at the
                // card's rounded corners instead of spilling out over the scrim.
                .clip(RoundedCornerShape(20.dp))
                .background(DeepNavy)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
            if (onClose != null) {
                CloseSquareButton(
                    onClick = onClose,
                    contentDescription = "Close",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 12.dp)
                )
            }
        }
    }
}
