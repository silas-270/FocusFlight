package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing

/**
 * The expanded form of a Passport stack: every earned tier in the family, best first.
 *
 * Collapsing a ladder on the Passport is what stops four plaques all saying "you have flown a lot
 * of km" - but the tiers are still individually earned, and dated, and worth seeing. This is where
 * they come back, as a plain scrolling list of the same card each one would show on its own.
 *
 * Inside the app's standard [ScrimCardModal] like every other modal here. The tier cards then need
 * a background of their own that is *not* the wrapper's DeepNavy - a DeepNavy card on a DeepNavy
 * card is invisible - so they take a lifted Slate instead, which is what keeps them reading as
 * separate cards rather than one long column.
 */
@Composable
internal fun AchievementStackModal(stack: AchievementStack, onDismiss: () -> Unit) {
    ScrimCardModal(onScrimTap = onDismiss, onClose = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A family is four entries today. The cap keeps the modal on screen if one grows,
                // and the scroll is what makes a stack of any depth viewable.
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            stack.earned.forEach { achievement ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate.copy(alpha = 0.25f))
                        .padding(Spacing.Medium),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AchievementCard(achievement = achievement)
                }
            }
        }
    }
}
