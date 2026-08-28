package com.example.focusflight.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.example.focusflight.R
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.components.airportpicker.AirportMapConfirmCard
import com.example.focusflight.ui.components.airportpicker.AirportSearchStep
import com.example.focusflight.ui.components.airportpicker.AirportSuggestion
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Dim
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.onboarding.OnboardingViewModel


@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedAirport by viewModel.selectedAirport.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ── Layer 1: Decorative background ──────────────────────────
        OnboardingBackground()

        // ── Layer 2 + 3: Content + Controls ─────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            // ── Freestanding A350 Silhouette Hero Graphic ───────────
            Icon(
                painter = painterResource(R.drawable.ic_airbus_a350),
                contentDescription = "FocusFlight logo",
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
                    text = "FOCUS",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    ),
                    color = Amber
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FLIGHT",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 2.sp
                    ),
                    color = OffWhite
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

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
                        onChangeSelection = { viewModel.clearSelection() }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── CTA Button ──────────────────────────────────────────
            Button(
                onClick = {
                    if (viewModel.saveHomeAirport()) {
                        onOnboardingComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = selectedAirport != null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor = Midnight,
                    disabledContainerColor = Dim,
                    disabledContentColor = Haze
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.FlightTakeoff,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text(
                    text = "SET HOME AIRPORT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Large))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
// Background layer — animated flight arcs and subtle latitude grid
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun OnboardingBackground() {
    val context = LocalContext.current
    val imageLoader = coil3.ImageLoader.Builder(context)
        .components { add(coil3.svg.SvgDecoder.Factory()) }
        .build()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(R.raw.globe)
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
