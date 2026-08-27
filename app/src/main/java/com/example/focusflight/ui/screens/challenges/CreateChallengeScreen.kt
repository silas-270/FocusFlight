package com.example.focusflight.ui.screens.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.repository.StartChallengeResult
import com.example.focusflight.ui.screens.flightsearch.OriginSearchPanel
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.challenges.ChallengesViewModel
import com.example.focusflight.ui.viewmodel.challenges.formatKm

private enum class CustomKind { ROUTE, DISTANCE }

private val DistancePresetsKm = listOf(5_000.0, 10_000.0, 20_000.0)

/**
 * Building a custom challenge, as its own destination. Picking two airports needs two search
 * panels and a confirmation step - far more room than the slot picker's modal can give it, which
 * is exactly why this is a screen and the curated list is not.
 *
 * On a successful start the caller pops back to the Challenges screen, where the new challenge is
 * already in a slot: `activeChallenges` is a Flow off the same table the insert wrote to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChallengeScreen(
    viewModel: ChallengesViewModel,
    onBackClick: () -> Unit,
    onCreated: () -> Unit
) {
    var kind by remember { mutableStateOf(CustomKind.ROUTE) }
    val startResult by viewModel.startResult.collectAsState()

    LaunchedEffect(startResult) {
        if (startResult is StartChallengeResult.Started) {
            viewModel.clearStartResult()
            onCreated()
        }
    }

    // Leaving mid-pick shouldn't strand a half-typed query for the next visit.
    DisposableEffect(Unit) {
        onDispose { viewModel.clearRouteSearch() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Midnight,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "CREATE CHALLENGE",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
            ) {
                KindSwitcher(selected = kind, onSelect = { kind = it })
                Spacer(modifier = Modifier.height(Spacing.Large))

                when (kind) {
                    CustomKind.ROUTE -> CustomRouteForm(
                        viewModel = viewModel,
                        onCreate = viewModel::startCustomRoute
                    )
                    CustomKind.DISTANCE -> CustomDistanceForm(
                        onCreate = viewModel::startCustomDistance
                    )
                }
            }
        }

        (startResult as? StartChallengeResult.CapReached)?.let {
            InfoModal(
                title = "CHALLENGE SLOTS FULL",
                message = "You already have the maximum number of active challenges. Abandon one before starting another.",
                onDismiss = { viewModel.clearStartResult() }
            )
        }
    }
}

@Composable
private fun KindSwitcher(selected: CustomKind, onSelect: (CustomKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CustomKind.entries.forEach { entry ->
            val active = entry == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Amber.copy(alpha = 0.2f) else DeepNavy)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = if (active) Amber else Haze
                )
            }
        }
    }
}

@Composable
private fun CustomRouteForm(
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
                Box(modifier = Modifier.height(360.dp)) {
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
                Box(modifier = Modifier.height(360.dp)) {
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

@Composable
private fun CustomDistanceForm(onCreate: (Double) -> Unit) {
    var selectedPreset by remember { mutableStateOf(DistancePresetsKm[1]) }
    var customText by remember { mutableStateOf("") }

    val customValue = customText.toDoubleOrNull()
    val target = customValue ?: selectedPreset

    Column(modifier = Modifier.fillMaxWidth()) {
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
            text = "START CHALLENGE (${formatKm(target)})",
            enabled = target > 0
        ) {
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
