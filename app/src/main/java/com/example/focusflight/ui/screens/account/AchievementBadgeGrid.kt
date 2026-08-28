package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.AchievementStatus
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

private val unlockedDateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)

/** Every badge is exactly this size, one- or two-line label alike - see [AchievementBadge]. */
private val BadgeWidth = 100.dp
private val BadgeHeight = 114.dp
private val BadgeShape = RoundedCornerShape(16.dp)

/**
 * Earned achievements, displayed in a single horizontally scrollable row.
 *
 * Side padding and hidden overflow are preserved so items clip cleanly at the bounds.
 */
@Composable
internal fun AchievementBadgeGrid(
    achievements: List<AchievementStatus>,
    onBadgeClick: (AchievementStatus) -> Unit
) {
    if (achievements.isEmpty()) {
        Text(
            text = "No achievements earned yet - see the Challenges screen for what's in reach.",
            style = MaterialTheme.typography.bodySmall,
            color = Haze
        )
        return
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = achievements,
            key = { it.id }
        ) { achievement ->
            AchievementBadge(achievement = achievement, onClick = { onBadgeClick(achievement) })
        }
    }
}

/**
 * One badge: a diagonally-striped plaque in its difficulty's metal, a large bare icon, and the
 * name.
 *
 * Fixed [BadgeWidth] x [BadgeHeight] rather than wrapping its content, and the label is pinned to
 * exactly two lines (`minLines = 2`) whether or not the name needs them. Sizing to content made a
 * "Globetrotter" badge visibly shorter than a "Cleared for Long-Haul" one sitting next to it,
 * which broke the grid's rows into a ragged edge - a trophy case reads as a set or it reads as
 * nothing. Two lines is the natural size for the longest names, so pinning to two costs no space
 * that wasn't already being reserved by the tallest badge in the row.
 *
 * No circular icon chip and no border: the plaque is cast from the tier's metal itself - a deep
 * base, struck with shiny diagonal bands and a large bare icon in the same metal's highlight. A
 * gold badge is gold all over rather than a navy card wearing gold trim, so difficulty is legible
 * from across the grid without a label saying "gold".
 */
@Composable
private fun AchievementBadge(achievement: AchievementStatus, onClick: () -> Unit) {
    val tier = achievementTier(achievement)

    Box(
        modifier = Modifier
            .width(BadgeWidth)
            .height(BadgeHeight)
            .clip(BadgeShape)
            .background(tier.base)
            .clickable(onClick = onClick)
    ) {
        DiagonalStripes(shine = tier.shine, modifier = Modifier.fillMaxSize(), band = 14.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = achievementIcon(achievement.id, achievement.category),
                contentDescription = null,
                tint = tier.iconTint,
                modifier = Modifier.size(46.dp)
            )
            Spacer(Modifier.height(Spacing.Small))
            Text(
                text = achievement.displayName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = OffWhite,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                lineHeight = 12.sp
            )
        }
    }
}

/**
 * The tier texture: thick, bold bands running bottom-left to top-right at 45 degrees.
 *
 * Drawn as stroked lines with the stroke width set to [band]. The lines extend well beyond the
 * card's outer bounds so stroke ends/caps are completely outside the card, with hidden overflow
 * clipped by [BadgeShape].
 *
 * The bands are rendered with a bright metallic sheen gradient that catches light like a polished medal.
 */
@Composable
private fun DiagonalStripes(
    shine: Color,
    modifier: Modifier = Modifier,
    band: Dp = 14.dp
) {
    Canvas(modifier = modifier) {
        val bandPx = band.toPx()
        val step = bandPx * 2f * sqrt(2f)
        // Runs perpendicular to the stripes (top-left to bottom-right), so the highlight crosses
        // every band instead of running along one of them.
        val sheen = Brush.linearGradient(
            colorStops = arrayOf(
                0f to shine.copy(alpha = 0.60f),
                0.45f to shine.copy(alpha = 0.28f),
                0.7f to shine.copy(alpha = 0.52f),
                1f to shine.copy(alpha = 0.24f)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
        // Extend lines beyond card bounds so ends/caps are never visible (hidden overflow clipped by card shape)
        val extension = size.height + bandPx * 4f
        var x = -size.height - extension
        while (x <= size.width + size.height + extension) {
            drawLine(
                brush = sheen,
                start = Offset(x - extension, size.height + extension),
                end = Offset(x + size.height + extension, -extension),
                strokeWidth = bandPx
            )
            x += step
        }
    }
}

@Composable
internal fun AchievementBadgeModal(achievement: AchievementStatus, onDismiss: () -> Unit) {
    val tier = achievementTier(achievement)

    ScrimCardModal(onScrimTap = onDismiss) {
        // Same plaque as the grid badge, scaled up - tapping a badge should open that badge, not
        // a differently-dressed dialog about it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(BadgeShape)
                .background(tier.base),
            contentAlignment = Alignment.Center
        ) {
            DiagonalStripes(shine = tier.shine, modifier = Modifier.fillMaxSize(), band = 18.dp)
            Icon(
                imageVector = achievementIcon(achievement.id, achievement.category),
                contentDescription = null,
                tint = tier.iconTint,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(Modifier.height(Spacing.Medium))
        Text(
            text = achievement.displayName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = OffWhite,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = achievement.description,
            style = MaterialTheme.typography.bodySmall,
            color = Haze,
            textAlign = TextAlign.Center
        )
        achievement.unlockedAt?.let { millis ->
            Spacer(Modifier.height(Spacing.Medium))
            Text(
                text = "Earned ${unlockedDateFormat.format(Date(millis))}",
                style = MaterialTheme.typography.labelSmall,
                color = tier.iconTint,
                letterSpacing = 1.sp
            )
        }
    }
}
