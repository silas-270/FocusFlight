package com.example.focusflight.ui.screens.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.repository.MAX_ACTIVE_CHALLENGES
import com.example.focusflight.data.repository.StartChallengeResult
import com.example.focusflight.ui.components.AchievementProgressRow
import com.example.focusflight.ui.screens.account.ChallengeCompletionEntry
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.challenges.ChallengesViewModel

private enum class ChallengesTab { CHALLENGES, ACHIEVEMENTS }

/**
 * The Challenges screen - a full destination rather than the bottom sheet this used to live in,
 * which needed its own inner scroll and swapped five view states inside a card.
 *
 * Layout, top to bottom: the Free Mode entry, a two-tab switcher, then either the three challenge
 * slots plus the completed-challenges log, or the still-unearned achievements list.
 *
 * Achievements share this screen because they are the same kind of thing as a challenge - a goal
 * you have not finished yet. Once earned they leave here entirely and become badges on the
 * Passport, so this list only ever shrinks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesScreen(
    viewModel: ChallengesViewModel,
    onBackClick: () -> Unit,
    onFreeModeClick: () -> Unit,
    onContinueRouteChallenge: (challengeId: Int) -> Unit,
    onCreateCustomClick: () -> Unit
) {
    val activeChallenges by viewModel.activeChallenges.collectAsState()
    val completedChallenges by viewModel.completedChallenges.collectAsState()
    val unfinishedAchievements by viewModel.unfinishedAchievements.collectAsState()
    val startResult by viewModel.startResult.collectAsState()

    var tab by rememberSaveable { mutableStateOf(ChallengesTab.CHALLENGES) }
    var showPicker by remember { mutableStateOf(false) }
    var infoChallenge by remember { mutableStateOf<Challenge?>(null) }
    var pendingRouteConfirm by remember { mutableStateOf<Challenge?>(null) }
    var pendingAbandon by remember { mutableStateOf<Challenge?>(null) }

    // A successful start just fills a slot - close the picker and let the slot row update.
    LaunchedEffect(startResult) {
        if (startResult is StartChallengeResult.Started) {
            showPicker = false
            viewModel.clearStartResult()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Midnight,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "CHALLENGES",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = OffWhite
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = OffWhite
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.Medium,
                    end = Spacing.Medium,
                    top = Spacing.Small,
                    bottom = Spacing.ExtraLarge
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                item { FreeModeRow(onClick = onFreeModeClick) }

                item {
                    TabSwitcher(
                        selected = tab,
                        onSelect = { tab = it }
                    )
                }

                when (tab) {
                    ChallengesTab.CHALLENGES -> {
                        item {
                            SectionLabel(
                                text = "ACTIVE",
                                trailing = "${activeChallenges.size}/$MAX_ACTIVE_CHALLENGES"
                            )
                        }

                        item {
                            ChallengeSlotRow(
                                challenges = activeChallenges,
                                onEmptySlotClick = { showPicker = true },
                                onChallengeClick = { infoChallenge = it }
                            )
                        }

                        item { SectionLabel(text = "COMPLETED") }

                        if (completedChallenges.isEmpty()) {
                            item { EmptyLine("No challenges completed yet.") }
                        } else {
                            itemsIndexed(
                                items = completedChallenges,
                                key = { _, challenge -> "completed_${challenge.id}" }
                            ) { index, challenge ->
                                ChallengeCompletionEntry(
                                    challenge = challenge,
                                    entryNumber = completedChallenges.size - index
                                )
                            }
                        }
                    }

                    ChallengesTab.ACHIEVEMENTS -> {
                        if (unfinishedAchievements.isEmpty()) {
                            item { EmptyLine("Every achievement earned. Check your Passport.") }
                        } else {
                            itemsIndexed(
                                items = unfinishedAchievements,
                                key = { _, achievement -> achievement.id }
                            ) { _, achievement ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(DeepNavy)
                                        .padding(Spacing.Medium)
                                ) {
                                    // Everything on this tab is unearned by construction, so the
                                    // "DONE" pill and locked/unlocked dot would be dead pixels.
                                    AchievementProgressRow(
                                        achievement = achievement,
                                        showUnlockedCues = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPicker) {
            ChallengePickerModal(
                onStartCurated = viewModel::startCurated,
                onCreateCustomClick = {
                    showPicker = false
                    onCreateCustomClick()
                },
                onDismiss = { showPicker = false }
            )
        }

        infoChallenge?.let { challenge ->
            ChallengeInfoModal(
                challenge = challenge,
                onContinue = {
                    infoChallenge = null
                    pendingRouteConfirm = challenge
                },
                onAbandon = {
                    infoChallenge = null
                    pendingAbandon = challenge
                },
                onDismiss = { infoChallenge = null }
            )
        }

        pendingRouteConfirm?.let { challenge ->
            RouteContinueConfirmModal(
                challenge = challenge,
                onConfirm = {
                    pendingRouteConfirm = null
                    onContinueRouteChallenge(challenge.id)
                },
                onDismiss = { pendingRouteConfirm = null }
            )
        }

        pendingAbandon?.let { challenge ->
            AbandonConfirmModal(
                challenge = challenge,
                onConfirm = {
                    viewModel.abandon(challenge.id)
                    pendingAbandon = null
                },
                onDismiss = { pendingAbandon = null }
            )
        }

        // Structurally hard to reach now that the picker only opens from an empty slot, but a
        // start can still lose a race against a concurrently-started challenge - so it is
        // surfaced rather than swallowed.
        (startResult as? StartChallengeResult.CapReached)?.let {
            InfoModal(
                title = "CHALLENGE SLOTS FULL",
                message = "You already have $MAX_ACTIVE_CHALLENGES active challenges - the most you can run at once. Abandon one before starting another.",
                onDismiss = { viewModel.clearStartResult() }
            )
        }

        (startResult as? StartChallengeResult.UnknownTemplate)?.let {
            // Shouldn't happen from UI-driven catalogIds - defensive only.
            LaunchedEffect(it) { viewModel.clearStartResult() }
        }
    }
}

@Composable
private fun FreeModeRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeepNavy)
            .clickable(onClick = onClick)
            .padding(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.FlightTakeoff,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.Small))
        Text(
            text = "Free Mode",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = OffWhite,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun TabSwitcher(selected: ChallengesTab, onSelect: (ChallengesTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ChallengesTab.entries.forEach { entry ->
            val active = entry == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(entry) }
                    .padding(vertical = Spacing.Small),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = if (active) Amber else Haze
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (active) Amber else Border.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Haze
        )
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.labelSmall, color = Haze)
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Haze,
        modifier = Modifier.padding(vertical = Spacing.Small)
    )
}
