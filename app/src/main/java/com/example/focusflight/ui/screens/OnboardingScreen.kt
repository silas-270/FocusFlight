package com.example.focusflight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.focusflight.data.model.Airport
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.OnboardingViewModel

// Dimension tokens to avoid inline hardcoding
private val CornerRadiusMedium = 16.dp
private val ButtonHeight = 56.dp
private val StrokeWidthThin = 1.dp
private val IconSizeNormal = 24.dp
private val MaxResultsHeight = 220.dp

// Opacity and float tokens
private const val AlphaTextMuted = 0.5f
private const val ScrimAlphaTop = 0.4f
private const val AlphaFieldBg = 0.7f

// Premium aircraft/flight photo from Unsplash
private const val BackgroundImageUrl = "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?q=80&w=1200"

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedAirport by viewModel.selectedAirport.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Layer 1: Dark high-fidelity aircraft background loaded from the internet
        AsyncImage(
            model = BackgroundImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Subtle dark gradient scrim to maximize readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = ScrimAlphaTop),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // Layer 3: Minimalist controls overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraLarge)
                .animateContentSize(animationSpec = tween(300)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Branding (Logo icon and subtitles removed as requested)
            Text(
                text = "Focusflight",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            Text(
                text = "Select Home Airport",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))

            // Search input field
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search airport (e.g. STR)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTextMuted)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search Icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = AlphaFieldBg),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = AlphaFieldBg),
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(CornerRadiusMedium),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Airport Search Results List
            AnimatedVisibility(
                visible = results.isNotEmpty() && selectedAirport == null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = MaxResultsHeight)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerRadiusMedium))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = AlphaFieldBg))
                        .border(
                            width = StrokeWidthThin,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(CornerRadiusMedium)
                        ),
                    contentPadding = PaddingValues(vertical = Spacing.Small)
                ) {
                    items(results) { airport ->
                        AirportResultRow(
                            airport = airport,
                            onClick = { viewModel.selectAirport(airport) }
                        )
                    }
                }
            }

            // Onboarding Action Trigger Button
            AnimatedVisibility(
                visible = selectedAirport != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    Button(
                        onClick = {
                            if (viewModel.saveHomeAirport()) {
                                onOnboardingComplete()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonHeight),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(CornerRadiusMedium),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = Spacing.ExtraSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Confirm",
                            modifier = Modifier.size(IconSizeNormal)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Small))
                        Text(
                            text = "INITIALIZE COCKPIT",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AirportResultRow(
    airport: Airport,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = "Location",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSizeNormal)
        )
        Spacer(modifier = Modifier.width(Spacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${airport.municipality} (${airport.iataCode})",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = airport.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
