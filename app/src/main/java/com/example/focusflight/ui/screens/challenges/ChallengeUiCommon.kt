package com.example.focusflight.ui.screens.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeType
import com.example.focusflight.data.model.progressFraction
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.viewmodel.challenges.formatKm

/**
 * Bits shared by the Challenges screen and the custom-creation screen - buttons, the confirm
 * modals, and the one-line progress summary. Salvaged from the old Hub quest-log sheet, which
 * kept them all `private` in a single 800-line file; they are `internal` here so both screens can
 * use one copy.
 */

/** "STR → PEK · 47%" / "3/7 visited · 43%" / "4,200 km / 10,000 km", by type. */
internal fun challengeSubtitle(challenge: Challenge): String {
    val pct = (challenge.progressFraction() * 100).toInt()
    return when (challenge.type) {
        ChallengeType.ROUTE -> "${challenge.positionIata ?: "?"} → ${challenge.destIata ?: "?"} · $pct%"
        ChallengeType.SET_COMPLETION -> "${challenge.visitedSetMembers.size}/${challenge.setTotalMembers} visited · $pct%"
        ChallengeType.DISTANCE -> {
            val target = challenge.targetDistanceKm ?: 0.0
            "${formatKm(challenge.cumulativeDistanceKm)} / ${formatKm(target)}"
        }
    }
}

@Composable
internal fun PrimaryActionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Amber else Slate)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = if (enabled) DeepNavy else Haze
        )
    }
}

@Composable
internal fun DestructiveActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Slate)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = OffWhite
        )
    }
}

@Composable
internal fun ModalButtonRow(
    dismissText: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Slate)
                .clickable(onClick = onDismiss)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dismissText,
                color = OffWhite,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Amber)
                .clickable(onClick = onConfirm)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = confirmText,
                color = DeepNavy,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
internal fun ModalTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = OffWhite
    )
}

/**
 * Shown before a Free Mode flight, since the flight looks exactly like a Story Mode one from the
 * cockpit, but none of it is recorded against
 * your progress. Free Mode is filtered out of achievement evaluation (see
 * `AchievementProgress`, which keeps to STORY-tagged flights), is a no-op for challenge crediting
 * (`processLandingForChallenges`), and never moves your position - so landing somewhere new does
 * not become your home airport. Better said here than discovered after an hour in the air.
 */
@Composable
internal fun FreeModeNoticeModal(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ScrimCardModal(onScrimTap = onDismiss) {
        ModalTitle("FREE MODE")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This flight won't be counted. It earns no progress toward challenges or " +
                "achievements, and where you land won't become your home airport.",
            style = MaterialTheme.typography.bodyMedium,
            color = Haze
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModalButtonRow(
            dismissText = "CANCEL",
            confirmText = "FLY",
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }
}

@Composable
internal fun AbandonConfirmModal(challenge: Challenge, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ScrimCardModal(onScrimTap = onDismiss) {
        ModalTitle("ABANDON CHALLENGE?")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "\"${challenge.name}\" will be removed entirely, freeing up a challenge slot. " +
                "This can't be undone - starting it again later begins from zero.",
            style = MaterialTheme.typography.bodyMedium,
            color = Haze
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModalButtonRow(
            dismissText = "KEEP IT",
            confirmText = "ABANDON",
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }
}

@Composable
internal fun InfoModal(title: String, message: String, onDismiss: () -> Unit) {
    ScrimCardModal(onScrimTap = onDismiss) {
        ModalTitle(title)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = Haze)
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryActionButton(text = "OK", onClick = onDismiss)
    }
}
