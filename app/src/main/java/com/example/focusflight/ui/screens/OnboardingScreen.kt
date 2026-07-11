package com.example.focusflight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focusflight.data.model.Airport
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.OnboardingViewModel

// Dimension tokens to avoid inline hardcoding
private val CornerRadiusLarge = 24.dp
private val CornerRadiusMedium = 16.dp
private val LogoSize = 64.dp
private val ButtonHeight = 56.dp
private val StrokeWidthThin = 1.dp
private val StrokeWidthMedium = 2.dp
private val DotRadiusNormal = 6.dp
private val DotRadiusGlow = 12.dp
private val IconSizeNormal = 24.dp
private val MaxResultsHeight = 220.dp
private val TickLength = 10.dp
private val TickSpacing = 20.dp

// Opacity and float tokens
private const val AlphaCardBg = 0.85f
private const val AlphaTextMuted = 0.6f
private const val AlphaBackgroundRadar = 0.15f
private const val AlphaFlightPathSecondary = 0.3f
private const val AlphaWaypointGlow = 0.4f
private const val AlphaSweep = 0.4f
private const val AlphaTicks = 0.3f
private const val AlphaSurfaceVariantBg = 0.3f
private const val AlphaListBg = 0.5f

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
        // Layer 1: Flight Radar Canvas Background
        FlightRadarBackground(modifier = Modifier.fillMaxSize())

        // Layer 2 & 3: Card and Interactive UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(300)),
                shape = RoundedCornerShape(CornerRadiusLarge),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = AlphaCardBg)
                ),
                border = BorderStroke(
                    width = StrokeWidthThin,
                    color = MaterialTheme.colorScheme.outline
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.Large),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cockpit flight icon
                    Icon(
                        imageVector = Icons.Rounded.FlightTakeoff,
                        contentDescription = "Flight Icon",
                        modifier = Modifier.size(LogoSize),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    Text(
                        text = "FOCUSFLIGHT",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Earn your miles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.Large))

                    Text(
                        text = "Select Home Airport",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(Spacing.Small))

                    Text(
                        text = "Choose your home base to configure your cockpit instrumentation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    // Dynamic text field for airport lookup
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
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaSurfaceVariantBg),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaSurfaceVariantBg),
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
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaListBg))
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

                    // Onboarding Action Trigger
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
                }
            }
        }
    }
}

@Composable
private fun FlightRadarBackground(modifier: Modifier = Modifier) {
    // Collect theme colors to avoid drawing raw hexadecimal colors
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = AlphaBackgroundRadar)
    val pathPrimaryColor = MaterialTheme.colorScheme.primary.copy(alpha = AlphaBackgroundRadar)
    val waypointColor = MaterialTheme.colorScheme.primary
    val waypointGlowColor = MaterialTheme.colorScheme.primary.copy(alpha = AlphaWaypointGlow)
    val sweepColor = MaterialTheme.colorScheme.primary
    val borderTickColor = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTicks)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)

        // Draw concentric circular sweep lines
        val maxRadius = minOf(width, height) * 0.6f
        val circleCount = 4
        for (i in 1..circleCount) {
            val radius = maxRadius * (i.toFloat() / circleCount)
            drawCircle(
                color = gridColor,
                radius = radius,
                style = Stroke(width = StrokeWidthThin.toPx())
            )
        }

        // Draw intersecting radar lines (axes)
        drawLine(
            color = gridColor,
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = StrokeWidthThin.toPx()
        )
        drawLine(
            color = gridColor,
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = StrokeWidthThin.toPx()
        )

        // Draw curved dashed flight paths representing flight network routes
        val path1 = Path().apply {
            moveTo(0f, height * 0.2f)
            quadraticTo(width * 0.4f, height * 0.3f, width, height * 0.8f)
        }
        drawPath(
            path = path1,
            color = pathPrimaryColor,
            style = Stroke(
                width = StrokeWidthMedium.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )
        )

        val path2 = Path().apply {
            moveTo(0f, height * 0.9f)
            cubicTo(width * 0.3f, height * 0.6f, width * 0.7f, height * 0.4f, width, height * 0.1f)
        }
        drawPath(
            path = path2,
            color = gridColor.copy(alpha = AlphaFlightPathSecondary),
            style = Stroke(
                width = StrokeWidthThin.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        )

        // Draw glowing waypoints (nodes)
        drawCircle(
            color = waypointGlowColor,
            radius = DotRadiusGlow.toPx(),
            center = Offset(width * 0.5f, height * 0.45f)
        )
        drawCircle(
            color = waypointColor,
            radius = DotRadiusNormal.toPx(),
            center = Offset(width * 0.5f, height * 0.45f)
        )

        drawCircle(
            color = waypointGlowColor,
            radius = DotRadiusGlow.toPx(),
            center = Offset(width * 0.3f, height * 0.6f)
        )
        drawCircle(
            color = waypointColor,
            radius = DotRadiusNormal.toPx(),
            center = Offset(width * 0.3f, height * 0.6f)
        )

        // Static glowing radar sweep line gradient
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(sweepColor.copy(alpha = AlphaSweep), Color.Transparent),
                start = center,
                end = Offset(center.x + maxRadius * 0.7f, center.y - maxRadius * 0.7f)
            ),
            start = center,
            end = Offset(center.x + maxRadius * 0.7f, center.y - maxRadius * 0.7f),
            strokeWidth = StrokeWidthMedium.toPx()
        )

        // Draw compass/heading tick marks along top and bottom edges
        val tickLengthPx = TickLength.toPx()
        val tickSpacingPx = TickSpacing.toPx()

        var x = 0f
        while (x < width) {
            drawLine(
                color = borderTickColor,
                start = Offset(x, 0f),
                end = Offset(x, tickLengthPx),
                strokeWidth = StrokeWidthThin.toPx()
            )
            x += tickSpacingPx
        }

        x = 0f
        while (x < width) {
            drawLine(
                color = borderTickColor,
                start = Offset(x, height),
                end = Offset(x, height - tickLengthPx),
                strokeWidth = StrokeWidthThin.toPx()
            )
            x += tickSpacingPx
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
