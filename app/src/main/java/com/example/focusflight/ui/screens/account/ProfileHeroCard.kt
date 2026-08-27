package com.example.focusflight.ui.screens.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.SoftAmber
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.account.AccountUiState

/**
 * The Passport's identity card, and the home for anything home-base related.
 *
 * Tapping it expands to reveal the return-home / change-home-base actions (see [HomeBaseActions]).
 * Those used to occupy a permanent section directly below this card, which badly overstated two
 * actions gated behind 7- and 30-day cooldowns. Folding them in here keeps them one tap away
 * without spending the top of the screen on them.
 *
 * Expansion state is hoisted to `AccountScreen` rather than remembered here, so it survives this
 * card scrolling out of the LazyColumn's viewport and back in.
 */
@Composable
internal fun ProfileHeroCard(
    state: AccountUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onReturnHomeClick: () -> Unit,
    onChangeHomeBaseClick: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "hero_card_chevron"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(DeepNavy, Slate)
                )
            )
            .clickable(onClick = onToggleExpanded)
            .padding(Spacing.Large)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Amber.copy(alpha = 0.3f), Midnight)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(Spacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.username.ifBlank { "Captain" },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = OffWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(SoftAmber, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#${state.userCode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.homeAirportIata.isNotBlank()) {
                        InfoChip(
                            icon = Icons.Outlined.FlightTakeoff,
                            label = state.homeAirportIata
                        )
                    }
                    if (state.joinDateFormatted.isNotBlank()) {
                        InfoChip(
                            icon = Icons.Outlined.Star,
                            label = state.joinDateFormatted.removePrefix("Joined ")
                        )
                    }
                }
            }

            // The card gives no other hint that it opens, so the chevron carries that entirely -
            // and doubles as the way back to collapsed.
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Home base options",
                tint = Haze,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(Spacing.Medium))
                HomeBaseActions(
                    state = state,
                    onReturnHomeClick = onReturnHomeClick,
                    onChangeHomeBaseClick = onChangeHomeBaseClick
                )
            }
        }
    }
}

@Composable
internal fun InfoChip(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Haze, modifier = Modifier.size(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Haze)
    }
}
