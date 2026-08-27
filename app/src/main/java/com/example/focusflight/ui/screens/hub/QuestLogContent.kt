package com.example.focusflight.ui.screens.hub

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeType
import com.example.focusflight.data.model.CuratedChallengeCatalog
import com.example.focusflight.data.model.progressFraction
import com.example.focusflight.data.repository.MAX_ACTIVE_CHALLENGES
import com.example.focusflight.data.repository.StartChallengeResult
import com.example.focusflight.ui.components.ChallengeProgressBar
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.components.challengeTypeIcon
import com.example.focusflight.ui.components.challengeTypeLabel
import com.example.focusflight.ui.screens.flightsearch.OriginSearchPanel
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.challenges.ChallengesViewModel
import com.example.focusflight.ui.viewmodel.challenges.formatKm

/**
 * The quest log (docs/design/challenges.md#entry--management-surface): active-challenges list,
 * start-new (curated browse + custom Route/Distance creation), and abandon. Lives inside the
 * Hub's existing mode-select sheet (see `HubScreen.kt`'s `ModeSelectMenuContent`), above the Free
 * Mode entry.
 *
 * Rather than a second stacked `ModalBottomSheet` for the browse/create sub-flows (codebase-map.md
 * flags that a second sheet on top of the Hub's already-present one risks getting visually
 * awkward), this swaps its own content in place via a small internal view-state enum - the sheet
 * itself never nests, it just shows different things.
 */
@Composable
fun QuestLogSection(
    viewModel: ChallengesViewModel,
    onContinueRouteChallenge: (challengeId: Int) -> Unit
) {
    val challenges by viewModel.activeChallenges.collectAsState()
    val startResult by viewModel.startResult.collectAsState()

    var view by rememberSaveable { mutableStateOf(QuestLogView.LIST) }
    var detailChallengeId by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingRouteConfirm by remember { mutableStateOf<Challenge?>(null) }
    var pendingAbandon by remember { mutableStateOf<Challenge?>(null) }

    // A successful start returns to the list automatically; CapReached/UnknownTemplate are
    // surfaced via the InfoModal below instead and cleared once the player dismisses it.
    LaunchedEffect(startResult) {
        if (startResult is StartChallengeResult.Started) {
            view = QuestLogView.LIST
            viewModel.clearStartResult()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CHALLENGES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = Haze
            )
            Text(
                text = "${challenges.size}/$MAX_ACTIVE_CHALLENGES",
                style = MaterialTheme.typography.labelSmall,
                color = Haze
            )
        }
        Spacer(modifier = Modifier.height(Spacing.Small))

        when (view) {
            QuestLogView.LIST -> {
                if (challenges.isEmpty()) {
                    Text(
                        text = "No active challenges yet - start one below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Haze,
                        modifier = Modifier.padding(vertical = Spacing.Small)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        challenges.forEach { challenge ->
                            ChallengeCard(
                                challenge = challenge,
                                onPrimaryClick = {
                                    if (challenge.type == ChallengeType.ROUTE) {
                                        pendingRouteConfirm = challenge
                                    } else {
                                        detailChallengeId = challenge.id
                                        view = QuestLogView.DETAIL
                                    }
                                },
                                onAbandonClick = { pendingAbandon = challenge }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.Medium))
                StartNewChallengeButton(onClick = { view = QuestLogView.BROWSE })
            }

            QuestLogView.DETAIL -> {
                val challenge = challenges.find { it.id == detailChallengeId }
                if (challenge != null) {
                    ChallengeDetailContent(
                        challenge = challenge,
                        onBack = { view = QuestLogView.LIST; detailChallengeId = null },
                        onAbandon = { pendingAbandon = challenge }
                    )
                } else {
                    // The challenge finished/vanished (e.g. completed) while detail was open.
                    view = QuestLogView.LIST
                }
            }

            QuestLogView.BROWSE -> {
                BrowseChallengesContent(
                    onBack = { view = QuestLogView.LIST },
                    onStartCurated = viewModel::startCurated,
                    onCustomRouteClick = { view = QuestLogView.CREATE_ROUTE },
                    onCustomDistanceClick = { view = QuestLogView.CREATE_DISTANCE }
                )
            }

            QuestLogView.CREATE_ROUTE -> {
                CreateRouteChallengeContent(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.clearRouteSearch()
                        view = QuestLogView.BROWSE
                    },
                    onCreate = viewModel::startCustomRoute
                )
            }

            QuestLogView.CREATE_DISTANCE -> {
                CreateDistanceChallengeContent(
                    onBack = { view = QuestLogView.BROWSE },
                    onCreate = viewModel::startCustomDistance
                )
            }
        }
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
                if (detailChallengeId == challenge.id) {
                    detailChallengeId = null
                    view = QuestLogView.LIST
                }
            },
            onDismiss = { pendingAbandon = null }
        )
    }

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

private enum class QuestLogView { LIST, DETAIL, BROWSE, CREATE_ROUTE, CREATE_DISTANCE }

// ── Active challenge card ────────────────────────────────────────────────────────────────

@Composable
private fun ChallengeCard(
    challenge: Challenge,
    onPrimaryClick: () -> Unit,
    onAbandonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Slate.copy(alpha = 0.4f))
            .clickable(onClick = onPrimaryClick)
            .padding(Spacing.Medium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = challengeTypeIcon(challenge.type),
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = challenge.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = OffWhite
                )
                Text(
                    text = challengeSubtitle(challenge),
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze
                )
            }
            if (challenge.type == ChallengeType.ROUTE) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Continue",
                    tint = Amber,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.Small))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAbandonClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Abandon challenge",
                    tint = Haze,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.Small))
        ChallengeProgressBar(progress = challenge.progressFraction())
    }
}

private fun challengeSubtitle(challenge: Challenge): String {
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

// ── Detail view (Distance / Set-completion: progress + abandon, no "continue") ──────────

@Composable
private fun ChallengeDetailContent(
    challenge: Challenge,
    onBack: () -> Unit,
    onAbandon: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BackRow(title = challengeTypeLabel(challenge.type), onBack = onBack)
        Spacer(modifier = Modifier.height(Spacing.Medium))
        Text(
            text = challenge.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = OffWhite
        )
        if (challenge.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = challenge.description, style = MaterialTheme.typography.bodySmall, color = Haze)
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
        DestructiveActionButton(text = "ABANDON CHALLENGE", onClick = onAbandon)
    }
}

// ── Start-new: curated browse + entry points into custom creation ───────────────────────

@Composable
private fun BrowseChallengesContent(
    onBack: () -> Unit,
    onStartCurated: (String) -> Unit,
    onCustomRouteClick: () -> Unit,
    onCustomDistanceClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BackRow(title = "START A NEW CHALLENGE", onBack = onBack)
        Spacer(modifier = Modifier.height(Spacing.Medium))

        Text(
            text = "CURATED",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = Haze
        )
        Spacer(modifier = Modifier.height(Spacing.Small))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text(text = template.description, style = MaterialTheme.typography.bodySmall, color = Haze)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Large))
        Text(
            text = "CUSTOM",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = Haze
        )
        Spacer(modifier = Modifier.height(Spacing.Small))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomChallengeEntryRow(
                icon = Icons.Outlined.FlightTakeoff,
                title = "Custom Route",
                subtitle = "Pick any two airports.",
                onClick = onCustomRouteClick
            )
            CustomChallengeEntryRow(
                icon = Icons.Outlined.Straighten,
                title = "Custom Distance",
                subtitle = "Pick your own target distance.",
                onClick = onCustomDistanceClick
            )
        }
    }
}

@Composable
private fun CustomChallengeEntryRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeepNavy)
            .clickable(onClick = onClick)
            .padding(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(Spacing.Small))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = OffWhite)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Haze)
        }
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "Create",
            tint = Amber,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Custom Route creation: pick origin, then destination ────────────────────────────────

@Composable
private fun CreateRouteChallengeContent(
    viewModel: ChallengesViewModel,
    onBack: () -> Unit,
    onCreate: (Airport, Airport) -> Unit
) {
    val originQuery by viewModel.originQuery.collectAsState()
    val originResults by viewModel.originResults.collectAsState()
    val destQuery by viewModel.destQuery.collectAsState()
    val destResults by viewModel.destResults.collectAsState()

    var pickedOrigin by remember { mutableStateOf<Airport?>(null) }
    var pickedDest by remember { mutableStateOf<Airport?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BackRow(title = "CUSTOM ROUTE CHALLENGE", onBack = onBack)
        Spacer(modifier = Modifier.height(Spacing.Medium))

        when {
            pickedOrigin == null -> {
                Text(text = "Pick a departure airport.", style = MaterialTheme.typography.bodySmall, color = Haze)
                Spacer(modifier = Modifier.height(Spacing.Small))
                Box(modifier = Modifier.height(280.dp)) {
                    OriginSearchPanel(
                        query = originQuery,
                        onQueryChange = viewModel::onOriginQueryChanged,
                        results = originResults,
                        onAirportSelect = { pickedOrigin = it }
                    )
                }
            }
            pickedDest == null -> {
                Text(
                    text = "Departing ${pickedOrigin!!.iataCode} - now pick a destination.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze
                )
                Spacer(modifier = Modifier.height(Spacing.Small))
                Box(modifier = Modifier.height(280.dp)) {
                    OriginSearchPanel(
                        query = destQuery,
                        onQueryChange = viewModel::onDestQueryChanged,
                        results = destResults,
                        onAirportSelect = { pickedDest = it }
                    )
                }
            }
            pickedOrigin!!.iataCode == pickedDest!!.iataCode -> {
                Text(
                    text = "Origin and destination can't be the same airport.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Haze
                )
                Spacer(modifier = Modifier.height(Spacing.Medium))
                PrimaryActionButton(text = "PICK A DIFFERENT DESTINATION") { pickedDest = null }
            }
            else -> {
                val origin = pickedOrigin!!
                val dest = pickedDest!!
                Text(
                    text = "${origin.municipality} → ${dest.municipality}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = OffWhite
                )
                Text(
                    text = "${origin.iataCode} → ${dest.iataCode}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = Haze
                )
                Spacer(modifier = Modifier.height(Spacing.Medium))
                PrimaryActionButton(text = "START CHALLENGE") { onCreate(origin, dest) }
                Spacer(modifier = Modifier.height(Spacing.Small))
                Text(
                    text = "Change destination",
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze,
                    modifier = Modifier.clickable { pickedDest = null }
                )
            }
        }
    }
}

// ── Custom Distance creation: presets or a free-entry target ────────────────────────────

private val DistancePresetsKm = listOf(5_000.0, 10_000.0, 20_000.0)

@Composable
private fun CreateDistanceChallengeContent(onBack: () -> Unit, onCreate: (Double) -> Unit) {
    var selectedPreset by remember { mutableStateOf(DistancePresetsKm[1]) }
    var customText by remember { mutableStateOf("") }

    val customValue = customText.toDoubleOrNull()
    val target = customValue ?: selectedPreset

    Column(modifier = Modifier.fillMaxWidth()) {
        BackRow(title = "CUSTOM DISTANCE CHALLENGE", onBack = onBack)
        Spacer(modifier = Modifier.height(Spacing.Medium))
        Text(
            text = "Pick a total distance to fly, or set your own target.",
            style = MaterialTheme.typography.bodySmall,
            color = Haze
        )
        Spacer(modifier = Modifier.height(Spacing.Medium))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DistancePresetsKm.forEach { preset ->
                val active = customValue == null && selectedPreset == preset
                DistanceChip(
                    label = formatKm(preset),
                    active = active,
                    onClick = { selectedPreset = preset; customText = "" }
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.Medium))
        OutlinedTextField(
            value = customText,
            onValueChange = { customText = it.filter(Char::isDigit) },
            label = { Text("Custom target (km)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Slate,
                unfocusedContainerColor = DeepNavy,
                cursorColor = Amber,
                focusedBorderColor = Amber,
                unfocusedBorderColor = Border.copy(alpha = 0.3f),
                focusedTextColor = OffWhite,
                unfocusedTextColor = OffWhite
            )
        )
        Spacer(modifier = Modifier.height(Spacing.Large))
        PrimaryActionButton(text = "START CHALLENGE (${formatKm(target)})", enabled = target > 0) {
            onCreate(target)
        }
    }
}

@Composable
private fun DistanceChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Amber.copy(alpha = 0.2f) else Slate)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (active) Amber else OffWhite
        )
    }
}

// ── Shared bits ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StartNewChallengeButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeepNavy)
            .clickable(onClick = onClick)
            .padding(Spacing.Medium),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Outlined.Add, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(Spacing.Small))
        Text(
            text = "START NEW CHALLENGE",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = Amber
        )
    }
}

@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Slate)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = OffWhite,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(Spacing.Small))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = Haze
        )
    }
}

@Composable
private fun PrimaryActionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
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
private fun DestructiveActionButton(text: String, onClick: () -> Unit) {
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

// ── Modals (scrim + card, per docs/design/codebase-map.md) ──────────────────────────────

@Composable
private fun RouteContinueConfirmModal(challenge: Challenge, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ScrimCardModal(onScrimTap = onDismiss) {
        Text(
            text = "SCOPED FLIGHT",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = OffWhite
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This flight is scoped to \"${challenge.name}\" - it won't move your main Story Mode position. " +
                "You're continuing from ${challenge.positionIata ?: "your current position on this challenge"}.",
            style = MaterialTheme.typography.bodyMedium,
            color = Haze
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModalButtonRow(
            dismissText = "CANCEL",
            confirmText = "CONTINUE",
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }
}

@Composable
private fun AbandonConfirmModal(challenge: Challenge, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ScrimCardModal(onScrimTap = onDismiss) {
        Text(
            text = "ABANDON CHALLENGE?",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = OffWhite
        )
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
private fun InfoModal(title: String, message: String, onDismiss: () -> Unit) {
    ScrimCardModal(onScrimTap = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = OffWhite
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = Haze)
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryActionButton(text = "OK", onClick = onDismiss)
    }
}

@Composable
private fun ModalButtonRow(
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
