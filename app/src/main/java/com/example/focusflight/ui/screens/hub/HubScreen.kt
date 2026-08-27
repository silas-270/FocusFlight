package com.example.focusflight.ui.screens.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.focusflight.data.repository.ActiveFlightContext
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.challenges.ChallengesViewModel
import com.example.focusflight.ui.viewmodel.hub.HubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    viewModel: HubViewModel,
    challengesViewModel: ChallengesViewModel,
    onBookFlightClick: () -> Unit,
    onResumeFlightClick: (context: ActiveFlightContext) -> Unit,
    onPassportClick: () -> Unit,
    onFreeModeClick: () -> Unit,
    onContinueRouteChallenge: (challengeId: Int) -> Unit
) {
    val currentAirport by viewModel.currentAirport.collectAsState()
    val stats by viewModel.flightStats.collectAsState()
    val recentFlights by viewModel.recentFlights.collectAsState()
    val routeMapPath by viewModel.routeMapPath.collectAsState()

    val scaffoldState = rememberBottomSheetScaffoldState()
    val isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded

    val activeFlightContext by viewModel.activeFlightContext.collectAsState()

    // Secondary "modes" menu (see docs/design/core-loop.md's "Mode-select addition") - a
    // ModalBottomSheet, distinct from the Hub's own always-present BottomSheetScaffold sheet
    // above, since this one is opened/dismissed on demand rather than permanently docked.
    var showModeMenu by remember { mutableStateOf(false) }

    androidx.compose.material3.Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepNavy)
                    .padding(
                        start = Spacing.Large,
                        end = Spacing.Large,
                        bottom = Spacing.Large,
                        top = Spacing.Medium
                    )
            ) {
                if (activeFlightContext != null) {
                    val context = activeFlightContext!!
                    Button(
                        onClick = { onResumeFlightClick(context) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Midnight
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FlightTakeoff,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Small))
                        Text(
                            text = "RESUME FLIGHT",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                } else {
                    Button(
                        onClick = onBookFlightClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber,
                            contentColor = Midnight
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FlightTakeoff,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Small))
                        Text(
                            text = "BOOK A FLIGHT",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        BottomSheetScaffold(
            modifier = Modifier.padding(innerPadding),
            scaffoldState = scaffoldState,
            sheetContainerColor = DeepNavy,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetDragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 16.dp)
                        .width(80.dp)
                        .height(4.dp)
                        .background(Border, RoundedCornerShape(2.dp))
                )
            },
            sheetPeekHeight = 160.dp,
            containerColor = Midnight,
            sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .padding(bottom = 30.dp)
            ) {
                // Header (Welcome back, Iata Code, Airport Name)
                Text(
                    text = "Welcome back, Captain",
                    style = MaterialTheme.typography.titleMedium,
                    color = Amber,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = currentAirport?.iataCode ?: "---",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 44.sp
                    ),
                    color = OffWhite
                )
                Text(
                    text = currentAirport?.name ?: "Loading base...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Haze
                )

                // Stats and Divider (below the fold when collapsed)
                Spacer(modifier = Modifier.height(30.dp))
                HorizontalDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(30.dp))

                // Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(value = stats.totalFlights.toString(), label = "FLIGHTS")
                    val hoursInt = stats.totalMinutes / 60
                    val minutesInt = stats.totalMinutes % 60
                    StatItem(value = String.format(java.util.Locale.US, "%02d:%02d", hoursInt, minutesInt), label = "HOURS")
                    StatItem(value = stats.airportsVisited.toString(), label = "AIRPORTS")
                }

                // Secondary Button (only if active flight exists)
                if (activeFlightContext != null) {
                    Spacer(modifier = Modifier.height(30.dp))
                    Button(
                        onClick = onBookFlightClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepNavy,
                            contentColor = Amber
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber)
                    ) {
                        Text(
                            text = "BOOK NEW FLIGHT",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Midnight)
        ) {
            // Background Globe image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.775f)
                    .padding(top = 48.dp)
                    .align(Alignment.TopCenter)
            ) {
                if (routeMapPath != null) {
                    AsyncImage(
                        model = routeMapPath,
                        contentDescription = "Decorative globe routes",
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )
                } else {
                    val mapRenderError by viewModel.mapRenderError.collectAsState()
                    if (mapRenderError != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .background(DeepNavy.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                    .padding(Spacing.Large)
                                    .clickable { viewModel.retryRenderMap() }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Error",
                                    tint = Amber,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = mapRenderError!!,
                                    color = OffWhite,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Tap to retry",
                                    color = Amber,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // Top Header & Navigation (outside solid container so globe is behind)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = Spacing.Large, end = Spacing.Large, top = Spacing.Large, bottom = Spacing.Medium),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Modes (Free Mode today; Challenges quest log joins this same menu in Phase 3)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepNavy)
                        .clickable { showModeMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = "Modes",
                        tint = OffWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.Small))

                // Account
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepNavy)
                        .clickable { onPassportClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Account",
                        tint = OffWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    }

    if (showModeMenu) {
        ModalBottomSheet(
            onDismissRequest = { showModeMenu = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = DeepNavy,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 16.dp)
                        .width(80.dp)
                        .height(4.dp)
                        .background(Border, RoundedCornerShape(2.dp))
                )
            }
        ) {
            ModeSelectMenuContent(
                challengesViewModel = challengesViewModel,
                onFreeModeClick = {
                    showModeMenu = false
                    onFreeModeClick()
                },
                onContinueRouteChallenge = { challengeId ->
                    showModeMenu = false
                    onContinueRouteChallenge(challengeId)
                }
            )
        }
    }
}

/**
 * Content of the Hub's secondary "modes" bottom sheet. Per docs/design/core-loop.md's
 * "Mode-select addition", this single menu covers both non-Story modes: the Challenges quest log
 * (Phase 3b - see [QuestLogSection]) above the Free Mode entry, a `Column` of discrete sections
 * rather than one hardcoded layout.
 */
@Composable
private fun ModeSelectMenuContent(
    challengesViewModel: ChallengesViewModel,
    onFreeModeClick: () -> Unit,
    onContinueRouteChallenge: (challengeId: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The quest log's browse/custom-creation sub-views can run longer than the sheet's
            // available height (especially the curated catalog list on a small device) - this
            // was a one-item static list before Phase 3b and never needed to scroll.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Large)
            .padding(bottom = 30.dp)
    ) {
        Text(
            text = "MODES",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Haze
        )
        Spacer(modifier = Modifier.height(Spacing.Medium))

        QuestLogSection(
            viewModel = challengesViewModel,
            onContinueRouteChallenge = onContinueRouteChallenge
        )

        Spacer(modifier = Modifier.height(Spacing.Large))
        HorizontalDivider(color = Border, thickness = 1.dp)
        Spacer(modifier = Modifier.height(Spacing.Large))

        ModeMenuItem(
            icon = Icons.Outlined.FlightTakeoff,
            title = "Free Mode",
            subtitle = "Any origin, any destination, any duration. Not tracked in Story Mode.",
            onClick = onFreeModeClick
        )
    }
}

@Composable
private fun ModeMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Slate.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.Medium))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = OffWhite
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Haze
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = OffWhite
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Haze,
            letterSpacing = 1.sp
        )
    }
}
