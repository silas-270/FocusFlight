package com.example.focusflight.ui.screens.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.example.focusflight.data.model.CuratedChallengeCatalog
import com.example.focusflight.data.model.progressFraction
import com.example.focusflight.ui.components.ChallengeProgressBar
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.components.challengeTypeIcon
import com.example.focusflight.ui.components.challengeTypeLabel
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing

/**
 * Opened by tapping an empty slot: pick one of the curated challenges, or head off to build a
 * custom one. Custom creation is a full screen rather than another modal step, since picking two
 * airports needs far more room than a card affords.
 */
@Composable
internal fun ChallengePickerModal(
    onStartCurated: (catalogId: String) -> Unit,
    onCreateCustomClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ScrimCardModal(onScrimTap = onDismiss) {
        ModalTitle("START A CHALLENGE")
        Spacer(modifier = Modifier.height(Spacing.Medium))

        Column(
            modifier = Modifier
                // Six curated entries plus the custom row overflow a short screen; cap the list
                // and let it scroll rather than pushing the custom row off the bottom.
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CuratedChallengeCatalog.ALL.forEach { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Slate.copy(alpha = 0.4f))
                        .clickable { onStartCurated(template.catalogId) }
                        .padding(Spacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = challengeTypeIcon(template.type),
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.Small))
                    Column {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = OffWhite
                        )
                        Text(
                            text = template.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Haze
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))
        HorizontalDivider(color = Border.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(Spacing.Medium))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onCreateCustomClick)
                .padding(vertical = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.Small))
            Text(
                text = "Create custom…",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Amber
            )
        }
    }
}

/**
 * Opened by tapping a filled slot. Route challenges get a "continue" action (they need an actual
 * scoped flight); Distance and Set-completion credit passively from any eligible flight, so for
 * those there is nothing to continue - only progress to read and the option to abandon.
 */
@Composable
internal fun ChallengeInfoModal(
    challenge: Challenge,
    isFocused: Boolean,
    onContinue: () -> Unit,
    onPause: () -> Unit,
    onAbandon: () -> Unit,
    onDismiss: () -> Unit
) {
    ScrimCardModal(onScrimTap = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = challengeTypeIcon(challenge.type),
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.Small))
            Text(
                text = challengeTypeLabel(challenge.type),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Haze
            )
        }
        Spacer(modifier = Modifier.height(Spacing.Small))

        Text(
            text = challenge.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = OffWhite
        )
        if (challenge.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = challenge.description,
                style = MaterialTheme.typography.bodySmall,
                color = Haze
            )
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))
        ChallengeProgressBar(progress = challenge.progressFraction(), height = 12.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = challengeSubtitle(challenge),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = Haze
        )

        Spacer(modifier = Modifier.height(Spacing.Large))
        if (challenge.type == ChallengeType.ROUTE) {
            if (isFocused) {
                PrimaryActionButton(text = "PAUSE CHALLENGE", onClick = onPause)
            } else {
                PrimaryActionButton(text = "CONTINUE CHALLENGE", onClick = onContinue)
            }
            Spacer(modifier = Modifier.height(Spacing.Small))
        }
        DestructiveActionButton(text = "ABANDON CHALLENGE", onClick = onAbandon)
    }
}
