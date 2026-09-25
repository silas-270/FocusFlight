package com.silas270.blocktime.ui.screens.challenges

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.data.model.Challenge
import com.silas270.blocktime.data.model.ChallengeType
import com.silas270.blocktime.data.model.PausedFlight
import com.silas270.blocktime.data.model.PredefinedRoute
import com.silas270.blocktime.data.model.predefinedRoute
import com.silas270.blocktime.data.model.resolveSetMemberProgress
import com.silas270.blocktime.data.model.CuratedChallengeCatalog
import com.silas270.blocktime.data.model.progressFraction
import androidx.compose.foundation.layout.PaddingValues
import com.silas270.blocktime.ui.components.BadgeSize
import com.silas270.blocktime.ui.components.BadgeStyle
import com.silas270.blocktime.ui.components.BadgeVariant
import com.silas270.blocktime.ui.components.ButtonSize
import com.silas270.blocktime.ui.components.ButtonStyle
import com.silas270.blocktime.ui.components.ButtonVariant
import com.silas270.blocktime.ui.components.CardVariant
import com.silas270.blocktime.ui.components.FocusBadge
import com.silas270.blocktime.ui.components.FocusButton
import com.silas270.blocktime.ui.components.FocusCard
import com.silas270.blocktime.ui.components.FocusInfoRow
import com.silas270.blocktime.ui.components.ChallengeProgressBar
import com.silas270.blocktime.ui.components.DestructiveActionButton
import com.silas270.blocktime.ui.components.ModalTitle
import com.silas270.blocktime.ui.components.PrimaryActionButton
import com.silas270.blocktime.ui.components.SecondaryActionButton
import com.silas270.blocktime.ui.components.ScrimCardModal
import com.silas270.blocktime.ui.components.SetMemberChecklist
import com.silas270.blocktime.ui.components.challengeTypeDescription
import com.silas270.blocktime.ui.components.challengeTypeIcon
import com.silas270.blocktime.ui.components.challengeTypeLabel
import com.silas270.blocktime.ui.components.icon
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.Border
import com.silas270.blocktime.ui.theme.DeepNavy
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.OffWhite
import com.silas270.blocktime.ui.theme.Slate
import com.silas270.blocktime.ui.theme.Spacing

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.input.KeyboardType
import com.silas270.blocktime.data.model.Airport
import com.silas270.blocktime.ui.screens.flightsearch.OriginSearchPanel
import com.silas270.blocktime.ui.viewmodel.challenges.ChallengesViewModel
import com.silas270.blocktime.ui.viewmodel.challenges.formatKm

private sealed interface PickerStep {
    object TypeGrid : PickerStep
    data class CuratedList(val type: ChallengeType) : PickerStep
    data class CreateCustom(val type: ChallengeType) : PickerStep
}

/**
 * Opened by tapping an empty slot:
 * 1. Shows a 2x2 grid of challenge types.
 * 2. Selecting a type lists the curated challenges for that type, with a "Create your own" button
 *    at the bottom (for Route, Distance, and Streak).
 * 3. Tapping "Create your own" transitions directly within the modal to that type's dedicated
 *    custom creation form.
 */
@Composable
internal fun ChallengePickerModal(
    viewModel: ChallengesViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf<PickerStep>(PickerStep.TypeGrid) }

    // One level up the wizard - what the in-card back arrows do, and (below) what system back does.
    fun stepBack() {
        when (val current = step) {
            PickerStep.TypeGrid -> Unit
            is PickerStep.CuratedList -> step = PickerStep.TypeGrid
            is PickerStep.CreateCustom -> {
                viewModel.clearRouteSearch()
                step = PickerStep.CuratedList(current.type)
            }
        }
    }

    // A scrim tap still closes the whole picker, the same as every other ScrimCardModal: tapping
    // outside the card is an explicit "not now". System back is different - it has to agree with the
    // back arrow on screen, which steps back one level, so it only closes from the first step.
    ScrimCardModal(onScrimTap = {
        viewModel.clearRouteSearch()
        onDismiss()
    }) {
        // Composed after ScrimCardModal's own BackHandler, so it takes priority while enabled.
        BackHandler(enabled = step != PickerStep.TypeGrid) { stepBack() }

        when (val current = step) {
            PickerStep.TypeGrid -> {
                ModalTitle("START A CHALLENGE")
                Spacer(modifier = Modifier.height(Spacing.Small))
                Text(
                    text = "Select a challenge type to explore:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze
                )
                Spacer(modifier = Modifier.height(Spacing.Medium))

                // 2x2 Grid of Challenge Types
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChallengeTypeCard(
                            type = ChallengeType.ROUTE,
                            modifier = Modifier.weight(1f),
                            onClick = { step = PickerStep.CuratedList(ChallengeType.ROUTE) }
                        )
                        ChallengeTypeCard(
                            type = ChallengeType.SET_COMPLETION,
                            modifier = Modifier.weight(1f),
                            onClick = { step = PickerStep.CuratedList(ChallengeType.SET_COMPLETION) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChallengeTypeCard(
                            type = ChallengeType.DISTANCE,
                            modifier = Modifier.weight(1f),
                            onClick = { step = PickerStep.CuratedList(ChallengeType.DISTANCE) }
                        )
                        ChallengeTypeCard(
                            type = ChallengeType.STREAK,
                            modifier = Modifier.weight(1f),
                            onClick = { step = PickerStep.CuratedList(ChallengeType.STREAK) }
                        )
                    }
                }
            }

            is PickerStep.CuratedList -> {
                val currentType = current.type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PickerBackButton(contentDescription = "Back to types", onClick = { stepBack() })
                    ModalTitle(challengeTypeLabel(currentType))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = challengeTypeDescription(currentType),
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze
                )
                Spacer(modifier = Modifier.height(Spacing.Medium))

                // Curated challenges for this type
                val templates = CuratedChallengeCatalog.ALL.filter { it.type == currentType }
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (templates.isEmpty()) {
                        Text(
                            text = "No curated challenges available for this type.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Haze,
                            modifier = Modifier.padding(vertical = Spacing.Medium)
                        )
                    } else {
                        templates.forEach { template ->
                            FocusCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = CardVariant.Secondary,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(Spacing.Medium),
                                onClick = { viewModel.startCurated(template.catalogId) }
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
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
                }

                // Create your own button (only for non-set-completion challenges)
                if (currentType != ChallengeType.SET_COMPLETION) {
                    Spacer(modifier = Modifier.height(Spacing.Medium))
                    HorizontalDivider(color = Border.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(Spacing.Small))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { step = PickerStep.CreateCustom(currentType) }
                            .padding(vertical = Spacing.Small, horizontal = Spacing.Small),
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
                            text = "Create your own…",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Amber
                        )
                    }
                }
            }

            is PickerStep.CreateCustom -> {
                val currentType = current.type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PickerBackButton(contentDescription = "Back to list", onClick = { stepBack() })
                    ModalTitle("CUSTOM ${challengeTypeLabel(currentType)}")
                }
                Spacer(modifier = Modifier.height(Spacing.Medium))

                when (currentType) {
                    ChallengeType.ROUTE -> CustomRouteModalForm(
                        viewModel = viewModel,
                        onCreate = viewModel::startCustomRoute
                    )
                    ChallengeType.DISTANCE -> CustomDistanceModalForm(
                        onCreate = viewModel::startCustomDistance
                    )
                    ChallengeType.STREAK -> CustomStreakModalForm(
                        onCreate = viewModel::startCustomStreak
                    )
                    ChallengeType.SET_COMPLETION -> Unit
                }
            }
        }
    }
}

/**
 * The picker's in-card back arrow. 48dp to touch, but the pressed highlight stays the old 32dp
 * rounded square. The 48dp box is pulled 8dp left (into the card's padding) and reports 8dp less
 * width, so the arrow and the title after it sit exactly where they did with the old 32dp box and
 * its 8dp spacer.
 */
@Composable
private fun PickerBackButton(contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val pullPx = 8.dp.roundToPx()
                layout(placeable.width - pullPx, placeable.height) { placeable.place(-pullPx, 0) }
            }
            .size(48.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .indication(interactionSource, LocalIndication.current),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = contentDescription,
                tint = Amber,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ChallengeTypeCard(
    type: ChallengeType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FocusCard(
        modifier = modifier,
        variant = CardVariant.Secondary,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        onClick = onClick,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FocusBadge(
            text = challengeTypeLabel(type),
            variant = BadgeVariant.Primary,
            style = BadgeStyle.Translucent,
            size = BadgeSize.Compact
        )
        Spacer(modifier = Modifier.height(10.dp))
        Icon(
            imageVector = challengeTypeIcon(type),
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = challengeTypeDescription(type),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            color = Haze,
            textAlign = TextAlign.Center,
            minLines = 3
        )
    }
}

@Composable
private fun CustomRouteModalForm(
    viewModel: ChallengesViewModel,
    onCreate: (Airport, Airport) -> Unit
) {
    val originQuery by viewModel.originQuery.collectAsState()
    val originResults by viewModel.originResults.collectAsState()
    val destQuery by viewModel.destQuery.collectAsState()
    val destResults by viewModel.destResults.collectAsState()

    var pickedOrigin by remember { mutableStateOf<Airport?>(null) }
    var pickedDest by remember { mutableStateOf<Airport?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            pickedOrigin == null -> {
                Text(
                    text = "Pick a departure airport.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze
                )
                Spacer(modifier = Modifier.height(Spacing.Small))
                Box(modifier = Modifier.height(280.dp)) {
                    OriginSearchPanel(
                        query = originQuery,
                        onQueryChange = viewModel::onOriginQueryChanged,
                        results = originResults,
                        onAirportSelect = { pickedOrigin = it },
                        caption = "DEPARTURE AIRPORT",
                        placeholder = "Search origin airport…"
                    )
                }
            }

            pickedDest == null -> {
                Text(
                    text = "Departing ${pickedOrigin!!.iataCode} (${pickedOrigin!!.municipality}) - now pick a destination.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze
                )
                Spacer(modifier = Modifier.height(Spacing.Small))
                Box(modifier = Modifier.height(280.dp)) {
                    OriginSearchPanel(
                        query = destQuery,
                        onQueryChange = viewModel::onDestQueryChanged,
                        results = destResults,
                        onAirportSelect = { pickedDest = it },
                        caption = "DESTINATION AIRPORT",
                        placeholder = "Search destination airport…"
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
                PrimaryActionButton(
                    text = "PICK A DIFFERENT DESTINATION",
                    size = ButtonSize.Compact
                ) { pickedDest = null }
            }

            else -> {
                val origin = pickedOrigin!!
                val dest = pickedDest!!
                FocusInfoRow(
                    label = "ORIGIN",
                    value = "${origin.municipality} (${origin.iataCode})"
                )
                FocusInfoRow(
                    label = "DESTINATION",
                    value = "${dest.municipality} (${dest.iataCode})"
                )
                Spacer(modifier = Modifier.height(Spacing.Medium))
                PrimaryActionButton(
                    text = "START CHALLENGE",
                    size = ButtonSize.Compact
                ) { onCreate(origin, dest) }
                Spacer(modifier = Modifier.height(Spacing.Small))
                // Styled like "Create your own…" - bold Amber is this picker's inline-link look.
                // In Haze it read as one more caption, not something you could tap.
                Text(
                    text = "Change destination",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Amber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { pickedDest = null }
                        .padding(vertical = Spacing.Small)
                )
            }
        }
    }
}

@Composable
private fun CustomDistanceModalForm(onCreate: (Double) -> Unit) {
    var customText by remember { mutableStateOf("") }
    val target = customText.toDoubleOrNull() ?: 0.0

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Enter a custom target distance to fly in miles.",
            style = MaterialTheme.typography.bodySmall,
            color = Haze
        )
        Spacer(modifier = Modifier.height(Spacing.Medium))
        OutlinedTextField(
            value = customText,
            onValueChange = { customText = it.filter(Char::isDigit) },
            label = { Text("Target distance (mi)") },
            placeholder = { Text("e.g. 10000", color = Haze.copy(alpha = 0.5f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
        PrimaryActionButton(
            text = if (target > 0) "START CHALLENGE (${String.format(java.util.Locale.US, "%,.0f mi", target)})" else "START CHALLENGE",
            size = ButtonSize.Compact,
            enabled = target > 0
        ) {
            // Typed in miles, stored in km like every other distance in the database.
            onCreate(com.silas270.blocktime.util.milesToKm(target))
        }
    }
}

@Composable
private fun CustomStreakModalForm(onCreate: (Int) -> Unit) {
    var customText by remember { mutableStateOf("") }
    val target = customText.toIntOrNull() ?: 0

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Enter the number of consecutive days you want to fly. Miss a day and the streak resets.",
            style = MaterialTheme.typography.bodySmall,
            color = Haze
        )
        Spacer(modifier = Modifier.height(Spacing.Medium))
        OutlinedTextField(
            value = customText,
            onValueChange = { customText = it.filter(Char::isDigit) },
            label = { Text("Target days") },
            placeholder = { Text("e.g. 7", color = Haze.copy(alpha = 0.5f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
        PrimaryActionButton(
            text = if (target > 0) "START CHALLENGE ($target DAYS)" else "START CHALLENGE",
            size = ButtonSize.Compact,
            enabled = target > 0
        ) {
            onCreate(target)
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
        Text(
            text = challenge.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = OffWhite
        )
        if (challenge.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = challenge.description,
                style = MaterialTheme.typography.bodySmall,
                color = Haze
            )
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))
        ChallengeProgressBar(progress = challenge.progressFraction(), height = 12.dp)
        Spacer(modifier = Modifier.height(Spacing.Medium))

        FocusInfoRow(
            label = "PROGRESS",
            value = "${(challenge.progressFraction() * 100).toInt()}%"
        )
        FocusInfoRow(
            label = "STATUS",
            value = challengeSubtitle(challenge)
        )

        val setMembers = challenge.resolveSetMemberProgress()
        if (!setMembers.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.Medium))
            SetMemberChecklist(members = setMembers)
        }

        challenge.predefinedRoute()?.let { route ->
            if (route.hasDistances) {
                FocusInfoRow(
                    label = "DISTANCE FLOWN",
                    value = "${formatKm(route.distanceFlownKm(challenge.legIndex))} of ${formatKm(route.totalDistanceKm)}"
                )
            }
            Spacer(modifier = Modifier.height(Spacing.Medium))
            RouteLegList(route = route, legIndex = challenge.legIndex)
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))
        if (challenge.type == ChallengeType.ROUTE) {
            // onContinue resumes the paused leg when there is one and books a fresh leg otherwise,
            // so the label says which of the two is about to happen.
            FocusButton(
                text = when {
                    isFocused -> "PAUSE CHALLENGE"
                    challenge.pausedFlight != null -> "RESUME CHALLENGE"
                    else -> "CONTINUE CHALLENGE"
                },
                onClick = if (isFocused) onPause else onContinue,
                variant = ButtonVariant.Primary,
                style = ButtonStyle.Filled,
                size = ButtonSize.Compact
            )
            Spacer(modifier = Modifier.height(Spacing.Small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                FocusButton(
                    text = "ABANDON",
                    onClick = onAbandon,
                    variant = ButtonVariant.Danger,
                    style = ButtonStyle.Filled,
                    size = ButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                    fillMaxWidth = false
                )
                FocusButton(
                    text = "CLOSE",
                    onClick = onDismiss,
                    variant = ButtonVariant.Secondary,
                    style = ButtonStyle.Filled,
                    size = ButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                    fillMaxWidth = false
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                FocusButton(
                    text = "ABANDON",
                    onClick = onAbandon,
                    variant = ButtonVariant.Danger,
                    style = ButtonStyle.Filled,
                    size = ButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                    fillMaxWidth = false
                )
                FocusButton(
                    text = "CLOSE",
                    onClick = onDismiss,
                    variant = ButtonVariant.Primary,
                    style = ButtonStyle.Filled,
                    size = ButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                    fillMaxWidth = false
                )
            }
        }
    }
}

/**
 * A predefined itinerary's legs in flying order - flown ones checked off, the next one lit, the
 * rest waiting.
 *
 * Deliberately *not* [SetMemberChecklist], even though both are "a list with some items done". A
 * set has no order and no repeats, so that component is free to reorder visited-first and filter to
 * MISSING. An itinerary is nothing but its order, and a circuit visits the same airport twice - so
 * reordering it would destroy the only thing it says, and "which are missing" is never the question
 * (they all are, in a fixed sequence, and only one of them is next).
 */
@Composable
private fun RouteLegList(route: PredefinedRoute, legIndex: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (leg in 0 until route.legCount) {
            val flown = leg < legIndex
            val isNext = leg == legIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isNext) Slate.copy(alpha = 0.55f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    if (flown) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (isNext) {
                        Icon(
                            imageVector = Icons.Outlined.FlightTakeoff,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "${leg + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Haze
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${route.originOf(leg)} → ${route.destOf(leg)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (flown || isNext) OffWhite else Haze
                )
                if (isNext) {
                    Spacer(modifier = Modifier.width(10.dp))
                    FocusBadge(
                        text = "NEXT",
                        variant = BadgeVariant.Primary,
                        style = BadgeStyle.Translucent,
                        size = BadgeSize.Compact
                    )
                }
                // Right-aligned so the distances form a column - this is what says which legs are
                // the expensive ones before you commit to the challenge.
                route.legDistancesKm.getOrNull(leg)?.let { km ->
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatKm(km),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (flown || isNext) Haze else Haze.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
