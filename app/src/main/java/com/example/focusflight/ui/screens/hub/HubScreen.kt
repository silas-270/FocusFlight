package com.example.focusflight.ui.screens.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.PausedFlight
import com.example.focusflight.data.model.progressFraction
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import com.example.focusflight.ui.components.ButtonStyle
import com.example.focusflight.ui.components.ButtonVariant
import com.example.focusflight.ui.components.CardVariant
import com.example.focusflight.ui.components.ChallengeProgressBar
import com.example.focusflight.ui.components.DiscardFlightConfirmModal
import com.example.focusflight.ui.components.FocusButton
import com.example.focusflight.ui.components.FocusCard
import com.example.focusflight.ui.components.FocusStatItem
import com.example.focusflight.ui.components.PrimaryActionButton
import com.example.focusflight.ui.components.challengeTypeLabel
import com.example.focusflight.ui.components.icon
import com.example.focusflight.ui.screens.challenges.challengeSubtitle
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.hub.HubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    viewModel: HubViewModel,
    onBookFlightClick: () -> Unit,
    onResumeFlightClick: (flight: PausedFlight) -> Unit,
    onPassportClick: () -> Unit,
    onChallengesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onContinueChallengeClick: (challengeId: Int) -> Unit
) {
    val currentAirport by viewModel.currentAirport.collectAsState()
    val focusedChallenge by viewModel.focusedChallenge.collectAsState()
    val stats by viewModel.flightStats.collectAsState()
    val recentFlights by viewModel.recentFlights.collectAsState()
    val routeMapPath by viewModel.routeMapPath.collectAsState()

    // Hub's ViewModel (and its data) is loaded once in init, but the Hub's own back-stack entry
    // survives a popBackStack() from Account/Passport - so returning from there after using
    // story-mode.md's return-home teleport (a direct currentAirport write, no flight/session)
    // needs an explicit refresh, or the globe/greeting would keep showing the pre-teleport
    // airport until the next full Hub recreation. ON_START (not just first composition) fires
    // both on initial entry and on returning to this screen - same lifecycle-observer pattern
    // InFlightScreen already uses for its own pause/resume handling.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState()
    val isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded

    val pausedFlight by viewModel.pausedFlight.collectAsState()
    var showDiscardFlightConfirm by remember { mutableStateOf(false) }

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
              Column {
                // The obvious cue that the Hub is in Route-challenge focus mode: a full card
                // (same style as the Achievements-tab cards) with a progress bar and an explicit
                // exit, stacked above the normal Resume/Book button rather than replacing it.
                focusedChallenge?.let { challenge ->
                    FocusedChallengeCard(
                        challenge = challenge,
                        onExit = { viewModel.exitFocusedChallenge() },
                        modifier = Modifier.padding(bottom = Spacing.Large)
                    )
                }
                if (pausedFlight != null) {
                    val flight = pausedFlight!!
                    PrimaryActionButton(
                        text = "RESUME FLIGHT",
                        modifier = Modifier.height(56.dp),
                        icon = Icons.Outlined.FlightTakeoff,
                        onClick = { onResumeFlightClick(flight) }
                    )
                } else {
                    val focused = focusedChallenge
                    PrimaryActionButton(
                        text = "BOOK A FLIGHT",
                        modifier = Modifier.height(56.dp),
                        icon = Icons.Outlined.FlightTakeoff,
                        onClick = { if (focused != null) onContinueChallengeClick(focused.id) else onBookFlightClick() }
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
                    .padding(bottom = Spacing.Small)
            ) {
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

                Spacer(modifier = Modifier.height(30.dp))
                HorizontalDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(30.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FocusStatItem(value = stats.totalFlights.toString(), label = "FLIGHTS")
                    val hoursInt = stats.totalMinutes / 60
                    val minutesInt = stats.totalMinutes % 60
                    FocusStatItem(value = String.format(java.util.Locale.US, "%02d:%02d", hoursInt, minutesInt), label = "HOURS")
                    FocusStatItem(value = stats.airportsVisited.toString(), label = "AIRPORTS")
                }

                // Secondary Button (when any active flight exists, in story mode or route challenge)
                if (pausedFlight != null) {
                    Spacer(modifier = Modifier.height(30.dp))
                    FocusButton(
                        text = "BOOK NEW FLIGHT",
                        onClick = {
                            if (pausedFlight != null) {
                                showDiscardFlightConfirm = true
                            } else if (focusedChallenge != null) {
                                onContinueChallengeClick(focusedChallenge!!.id)
                            } else {
                                onBookFlightClick()
                            }
                        },
                        variant = ButtonVariant.Primary,
                        style = ButtonStyle.Outlined,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Midnight)
        ) {
            // Background Globe image. Sized off the screen's own height, not this Box's
            // immediate parent - that parent shrinks whenever the outer Scaffold's bottomBar
            // grows (e.g. the focused-challenge card adds a row above Resume/Book), and sizing
            // off a shrinking container recentered the crop of this fixed-aspect image, reading
            // as the globe getting cut off at the top the moment a challenge is focused.
            val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeightDp * 0.775f)
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
                            FocusCard(
                                variant = CardVariant.Surface,
                                onClick = { viewModel.retryRenderMap() },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                contentPadding = PaddingValues(Spacing.Large)
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

            // Top Header & Navigation (outside solid container so globe is behind). Settings sits
            // alone on the left so it doesn't compete with the Challenges/Account pair - those two
            // are the frequent, session-shaped destinations; Settings is the occasional one.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = Spacing.Large, end = Spacing.Large, top = Spacing.Large, bottom = Spacing.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings: theme preference and the Story Mode home-base actions.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepNavy)
                        .clickable { onSettingsClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = OffWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Challenges: Free Mode entry, the challenge slots, and achievements
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeepNavy)
                            .clickable { onChallengesClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Explore,
                            contentDescription = "Challenges",
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
    }

    if (showDiscardFlightConfirm && pausedFlight != null) {
        val flight = pausedFlight!!
        val focused = focusedChallenge
        DiscardFlightConfirmModal(
            flight = flight,
            onConfirm = {
                showDiscardFlightConfirm = false
                if (focused != null) onContinueChallengeClick(focused.id) else onBookFlightClick()
            },
            onDismiss = { showDiscardFlightConfirm = false }
        )
    }
}

/**
 * The Hub's cue for Route-challenge focus mode - same card language as the Achievements-tab cards
 * (rounded corners, a colored container, progress bar) but in [Slate] rather than [DeepNavy] so
 * it reads as a distinct, temporary mode rather than just another list item. Purely informational
 * (like an achievement card) aside from the exit "x" - continuing/resuming lives on the Resume/
 * Book button below it instead of on the card itself.
 */
@Composable
private fun FocusedChallengeCard(challenge: Challenge, onExit: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.Secondary,
        border = BorderStroke(1.dp, Border.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(horizontal = Spacing.Medium, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = challenge.icon(),
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = challenge.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = OffWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(Spacing.Small))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(DeepNavy.copy(alpha = 0.7f))
                    .clickable(onClick = onExit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Exit challenge",
                    tint = Haze,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        ChallengeProgressBar(
            progress = challenge.progressFraction(),
            trackColor = DeepNavy,
            fillColor = Amber,
            modifier = Modifier.fillMaxWidth(),
            height = 6.dp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = challengeSubtitle(challenge),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Haze
            )
            val pct = (challenge.progressFraction() * 100).toInt()
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = if (pct > 0) Amber else Haze.copy(alpha = 0.6f)
            )
        }
    }
}

@Deprecated("Use FocusStatItem instead", ReplaceWith("FocusStatItem(value, label)", "com.example.focusflight.ui.components.FocusStatItem"))
@Composable
private fun StatItem(value: String, label: String) {
    FocusStatItem(value = value, label = label)
}
