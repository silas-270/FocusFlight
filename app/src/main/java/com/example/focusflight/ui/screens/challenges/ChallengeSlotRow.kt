package com.example.focusflight.ui.screens.challenges

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.progressFraction
import com.example.focusflight.ui.components.RingProgress
import com.example.focusflight.ui.components.challengeTypeIcon
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.data.repository.MAX_ACTIVE_CHALLENGES

/**
 * The three challenge slots, side by side - the screen's primary affordance.
 *
 * Always renders exactly [MAX_ACTIVE_CHALLENGES] squares regardless of how many challenges are
 * active, so the row never reflows and the cap is legible at a glance rather than being an error
 * message you hit later. An empty slot *is* the "start a challenge" button; there is no separate
 * one.
 *
 * Slot identity is positional, not stable per challenge: when one completes, the list compacts and
 * the remaining challenges shift left. That is intentional - "when a challenge completes it's
 * removed from the layout" - and needs no bookkeeping beyond `getOrNull`.
 */
@Composable
internal fun ChallengeSlotRow(
    challenges: List<Challenge>,
    onEmptySlotClick: () -> Unit,
    onChallengeClick: (Challenge) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        for (index in 0 until MAX_ACTIVE_CHALLENGES) {
            val challenge = challenges.getOrNull(index)
            if (challenge != null) {
                FilledSlot(challenge = challenge, onClick = { onChallengeClick(challenge) })
            } else {
                EmptySlot(onClick = onEmptySlotClick)
            }
        }
    }
}

@Composable
private fun RowScope.EmptySlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            // Borderless, so flat Slate has to carry the slot on its own. It can't be a dimmed
            // DeepNavy "recess" any more - without the outline that read as nothing at all
            // against Midnight - so an open slot goes one step *brighter* than the filled
            // slot's DeepNavy instead. Slate is the palette's secondary-container tone and is
            // already the empty/neutral surface elsewhere (modal buttons, picker rows).
            .background(Slate)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "Start a challenge",
            tint = Haze,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun RowScope.FilledSlot(challenge: Challenge, onClick: () -> Unit) {
    val animatedProgress by animateFloatAsState(
        targetValue = challenge.progressFraction(),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "challenge_slot_${challenge.id}"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(DeepNavy)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Type icon sits behind the ring, dimmed - it identifies the challenge kind without
        // competing with the number, which is the thing you actually read.
        Icon(
            imageVector = challengeTypeIcon(challenge.type),
            contentDescription = null,
            tint = Haze.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxSize().padding(Spacing.Large)
        )
        RingProgress(
            progress = animatedProgress,
            modifier = Modifier.fillMaxSize().padding(Spacing.Small),
            strokeWidth = 5.dp,
            fillColor = Amber
        ) {
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = OffWhite
            )
        }
    }
}
