package com.silas270.blocktime.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.silas270.blocktime.ui.components.FocusButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.silas270.blocktime.R
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.svg.SvgDecoder
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.ui.components.airportpicker.AirportMapConfirmCard
import com.silas270.blocktime.ui.components.airportpicker.AirportSearchStep
import com.silas270.blocktime.ui.components.airportpicker.AirportSuggestion
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.Danger
import com.silas270.blocktime.ui.theme.Dim
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.Midnight
import com.silas270.blocktime.ui.theme.OffWhite
import com.silas270.blocktime.ui.theme.Spacing
import com.silas270.blocktime.ui.viewmodel.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch


@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedAirport by viewModel.selectedAirport.collectAsState()
    val onboardingScope = rememberCoroutineScope()
    var saveFailed by remember { mutableStateOf(false) }

    // The window is edge-to-edge, so the keyboard overlays this screen rather than resizing it.
    // While it's up the hero (logo + wordmark) steps aside so the search results get the room -
    // on the S23 it otherwise pushed most of the results list under the keyboard.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ── Layer 1: Decorative background ──────────────────────────
        OnboardingBackground()

        // ── Layer 2 + 3: Content + Controls ─────────────────────────
        // Scrollable, but at least screen-tall so the weighted spacer still pins the CTA to the
        // bottom exactly as before: it only actually scrolls when the content outgrows the
        // screen - large font scales, where the CTA used to be pushed off-screen for good.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(Spacing.Large))

                AnimatedVisibility(
                    visible = !imeVisible,
                    enter = fadeIn(tween(200)) + expandVertically(),
                    exit = fadeOut(tween(150)) + shrinkVertically()
                ) {
                    OnboardingHero()
                }

                // ── Search & Selection State ────────────────────────────
                AnimatedContent(
                    targetState = selectedAirport,
                    transitionSpec = {
                        (fadeIn(tween(300)) + androidx.compose.animation.slideInVertically(tween(300)) { 20 })
                            .togetherWith(fadeOut(tween(150)) + androidx.compose.animation.slideOutVertically(tween(150)) { -20 })
                            .using(androidx.compose.animation.SizeTransform(clip = false))
                    },
                    label = "airport_state"
                ) { targetAirport ->
                    if (targetAirport == null) {
                        AirportSearchStep(
                            headline = "Where do you fly from?",
                            searchQuery = searchQuery,
                            onQueryChange = { viewModel.onQueryChanged(it) },
                            placeholder = "Search airport or city…",
                            searchResults = searchResults,
                            onAirportSelected = { viewModel.selectAirport(it) },
                            suggestions = listOf(
                                AirportSuggestion("FRA", "Frankfurt", "Frankfurt Airport") { viewModel.selectAirportByIata("FRA") },
                                AirportSuggestion("LHR", "London", "Heathrow Airport") { viewModel.selectAirportByIata("LHR") },
                                AirportSuggestion("BER", "Berlin", "Berlin Brandenburg") { viewModel.selectAirportByIata("BER") },
                                AirportSuggestion("MUC", "Munich", "Munich Airport") { viewModel.selectAirportByIata("MUC") }
                            )
                        )
                    } else {
                        AirportMapConfirmCard(
                            airport = targetAirport,
                            onChangeSelection = {
                                saveFailed = false
                                viewModel.clearSelection()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // A failed profile write used to leave the button silently doing nothing. Say
                // so - tapping again is the pilot's only move, and saveHomeAirport() is safe to
                // retry.
                if (saveFailed) {
                    Text(
                        text = "Couldn't save your home airport. Please try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.Small)
                    )
                } else if (selectedAirport == null) {
                    // Says why the button below is greyed out.
                    Text(
                        text = "Pick an airport to continue",
                        style = MaterialTheme.typography.bodySmall,
                        color = Haze,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.Small)
                    )
                }

                // ── CTA Button ──────────────────────────────────────────
                FocusButton(
                    text = "SET HOME AIRPORT",
                    onClick = {
                        onboardingScope.launch {
                            if (viewModel.saveHomeAirport()) {
                                onOnboardingComplete()
                            } else {
                                saveFailed = true
                            }
                        }
                    },
                    enabled = selectedAirport != null,
                    icon = Icons.Outlined.FlightTakeoff,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.Large))
            }
        }
    }
}

/** The A350 silhouette and BLOCK TIME wordmark above the search step. Its spacing adds up to
 *  the original 72dp from the top together with the Spacing.Large spacer that stays above it
 *  when this is hidden for the keyboard. */
@Composable
private fun OnboardingHero() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(48.dp))

        // ── Freestanding A350 Silhouette Hero Graphic ───────────
        Icon(
            painter = painterResource(R.drawable.ic_airbus_a350),
            contentDescription = "Blocktime logo",
            tint = Amber,
            modifier = Modifier
                .width(180.dp)
                .height(66.dp)
                .rotate(-8f) // Dynamic ascension angle
        )

        Spacer(modifier = Modifier.height(Spacing.Large))

        // Premium Mixed-Weight Monospace Title
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BLOCK",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                ),
                color = Amber
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TIME",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp
                ),
                color = OffWhite
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// ═════════════════════════════════════════════════════════════════════
// Background layer — animated flight arcs and subtle latitude grid
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun OnboardingBackground() {
    val context = LocalContext.current
    // Remembered: an ImageLoader sets up its own caches, and this used to build a fresh one on
    // every recomposition.
    val imageLoader = remember(context) {
        coil3.ImageLoader.Builder(context)
            .components { add(coil3.svg.SvgDecoder.Factory()) }
            .build()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(R.raw.globe)
                .allowHardware(false)
                .build(),
            imageLoader = imageLoader,
            contentDescription = "Globe background",
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .offset(x = 170.dp, y = -10.dp)
                .scale(2.8f)
                .rotate(20f),
            contentScale = ContentScale.Fit,
            alpha = 0.25f
        )
    }
}
