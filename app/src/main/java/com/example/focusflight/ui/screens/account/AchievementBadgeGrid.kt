package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.focusflight.ui.components.SetMemberChecklist
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

internal val unlockedDateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)

/** Every badge is exactly this size, one- or two-line label alike - see [AchievementBadge]. */
private val BadgeWidth = 100.dp
private val BadgeHeight = 114.dp
internal val BadgeShape = RoundedCornerShape(16.dp)

/** How far each card behind the top one peeks out, straight up. */
private val PeekStep = 10.dp

/** At most two cards ever show behind the top one, however deep the family goes - three visible
 *  edges reads as "a stack", and a fourth adds nothing but clutter. Tapping through shows the real
 *  depth, so nothing is lost by capping it here. */
private const val MaxPeek = 2

/** Room reserved above a badge for its peek cards, so the topmost one is not cut off by the row's
 *  own [clipToBounds]. */
private val PeekInset = PeekStep * MaxPeek

/** The tallest an item can ever be - a full stack. The row is pinned to this so its height never
 *  depends on which items happen to be composed. */
private val RowHeight = BadgeHeight + PeekInset

/**
 * Earned achievements, displayed in a single horizontally scrollable row.
 *
 * Each item is an [AchievementStack] rather than a bare achievement: a ladder family (the
 * cumulative-distance milestones) draws as one tile with its earned tiers peeking out behind it,
 * and everything else is a stack of one that looks exactly as it always did.
 *
 * Side padding and hidden overflow are preserved so items clip cleanly at the bounds.
 */
@Composable
internal fun AchievementBadgeGrid(
    stacks: List<AchievementStack>,
    onStackClick: (AchievementStack) -> Unit
) {
    if (stacks.isEmpty()) {
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
            // Pinned, not measured from the children, and that is load-bearing. A LazyRow sizes
            // itself to the tallest item it currently has composed, and only a stacked tile is
            // taller than a lone badge - so scrolling the one stack off-screen made the row shrink
            // by [PeekInset] and everything below it jump up the page. Fixing the height to the
            // tallest an item can ever be means the row measures the same whatever is scrolled
            // into view.
            .height(RowHeight)
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        // Top-aligned: a lone badge lines up with the top of a neighbouring stack's topmost peek
        // card, so the row reads as one flush edge across the top rather than as tiles at two
        // different heights.
        verticalAlignment = Alignment.Top
    ) {
        items(
            items = stacks,
            key = { it.key }
        ) { stack ->
            AchievementStackBadge(stack = stack, onClick = { onStackClick(stack) })
        }
    }
}

/**
 * A stack of plaques: up to [MaxPeek] cards fanning up and right, with the real badge for
 * [AchievementStack.top] on the front.
 *
 * Each card behind is cast from **its own** achievement's metal and struck with its own stripes, so
 * a gold badge sitting on a silver and a bronze one reads as the three separate awards it actually
 * is. Painting them all in the top card's colour made the ladder look like one achievement
 * duplicated, which is the opposite of what a stack is meant to say.
 *
 * They carry no icon and no label - those would be sliced off by the card in front - so the visible
 * strip of metal and stripe is the whole signal, which is exactly why it has to be the right metal.
 */
@Composable
private fun AchievementStackBadge(stack: AchievementStack, onClick: () -> Unit) {
    if (!stack.isStacked) {
        AchievementBadge(achievement = stack.top, onClick = onClick)
        return
    }

    // earned is best-first, so dropping the top leaves the cards that sit behind it in order.
    val behind = stack.earned.drop(1).take(MaxPeek)

    Box(modifier = Modifier.size(width = BadgeWidth, height = BadgeHeight + PeekInset)) {
        // Furthest card first so nearer ones paint over it. Straight up, no sideways drift.
        behind.asReversed().forEachIndexed { indexFromBack, achievement ->
            val depth = behind.size - indexFromBack
            val tier = achievementTier(achievement)
            Box(
                modifier = Modifier
                    .offset(y = PeekInset - PeekStep * depth)
                    .width(BadgeWidth)
                    .height(BadgeHeight)
                    .clip(BadgeShape)
                    .background(tier.base)
            ) {
                DiagonalStripes(shine = tier.shine, modifier = Modifier.fillMaxSize(), band = 14.dp)
            }
        }
        Box(modifier = Modifier.offset(y = PeekInset)) {
            AchievementBadge(achievement = stack.top, onClick = onClick)
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
 * which broke the row into a ragged edge - a trophy case reads as a set or it reads as nothing.
 * Two lines is the natural size for the longest names, so pinning to two costs no space that
 * wasn't already being reserved by the tallest badge in the row. The fixed size is also what lets
 * [AchievementStackBadge] lay its peek cards out with a plain offset.
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
internal fun DiagonalStripes(
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
    ScrimCardModal(onScrimTap = onDismiss) {
        AchievementCard(achievement = achievement)
    }
}

/**
 * The card for one earned achievement: its plaque scaled up, the name, the description, when it was
 * earned, and - for a set-shaped goal - the member checklist.
 *
 * Pulled out of [AchievementBadgeModal] so a stack can show the exact same card for each of its
 * tiers. Tapping a single badge and tapping a stack should not produce two different renderings of
 * the same achievement.
 */
@Composable
internal fun ColumnScope.AchievementCard(achievement: AchievementStatus) {
    val tier = achievementTier(achievement)

    // Same plaque as the grid badge, scaled up - tapping a badge should open that badge, not a
    // differently-dressed dialog about it.
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

    // Only while there is still something to chase. A checklist answers "what am I missing", and
    // once the set is complete the answer is "nothing" - so a finished Globetrotter shows the
    // trophy, not seven ticked rows restating it.
    if (!achievement.isUnlocked) {
        achievement.members?.takeIf { it.isNotEmpty() }?.let { members ->
            Spacer(Modifier.height(Spacing.Medium))
            SetMemberChecklist(members = members)
        }
    }
}
