package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeSource
import com.example.focusflight.ui.components.challengeTypeLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One row in the Achievements screen's "Challenges completed" log (docs/design/achievements.md -
 * a flat log of every completed challenge, curated or custom, duplicates included for repeat
 * completions - modeled on the flight logbook, not an "x/N" tally, since custom challenges and
 * repeats mean there's no fixed denominator). Reuses [LogPaperCard] - the same paper-card chrome
 * [LogbookEntry] draws its own rows with - and [LogbookDataCell], so this reads as the same
 * physical logbook rather than a second visual language.
 */
@Composable
internal fun ChallengeCompletionEntry(challenge: Challenge, entryNumber: Int) {
    // completedAt is always non-null for a COMPLETED-status row in practice (see
    // ChallengeStatus/LocalChallengeRepository's crediting paths, which always set it alongside
    // the status flip) - startedAt is only a defensive fallback so this never crashes on an
    // unexpected null.
    val dateStr = remember(challenge.completedAt, challenge.startedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(challenge.completedAt ?: challenge.startedAt))
    }

    val inkDark   = LogbookInkDark
    val inkMid    = LogbookInkMid
    val inkFaint  = LogbookInkFaint
    val marginRed = LogbookMarginRed

    LogPaperCard(entryNumber = entryNumber) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: DATE on left, challenge-type stamp on right (mirrors LogbookEntry's flight-
            // number stamp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = inkFaint
                )
                Box(
                    modifier = Modifier
                        .border(1.dp, marginRed.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = challengeTypeLabel(challenge.type),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = marginRed.copy(alpha = 0.75f)
                    )
                }
            }

            // Row 2: challenge name (in place of LogbookEntry's ORIGIN ✈ DEST route stamp - a
            // Set-completion/Distance challenge has no single origin/dest pair, so the name is
            // the one thing every challenge type has)
            Text(
                text = challenge.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                ),
                color = inkDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Row 3: SOURCE (curated/custom) | TYPE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LogbookDataCell(
                    label = "SOURCE",
                    value = if (challenge.source == ChallengeSource.CURATED) "CURATED" else "CUSTOM",
                    labelColor = inkFaint,
                    valueColor = inkMid
                )
                LogbookDataCell(
                    label = "TYPE",
                    value = challengeTypeLabel(challenge.type),
                    labelColor = inkFaint,
                    valueColor = inkMid
                )
            }
        }
    }
}
