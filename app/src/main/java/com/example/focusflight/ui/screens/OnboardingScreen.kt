package com.example.focusflight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.Airport
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Dim
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.SoftAmber
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.OnboardingViewModel


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

            // ── Branding ────────────────────────────────────────────
            Icon(
                imageVector = Icons.Outlined.AirplanemodeActive,
                contentDescription = "FocusFlight logo",
                tint = Amber,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(Spacing.Medium))

            Text(
                text = "FOCUSFLIGHT",
                style = MaterialTheme.typography.headlineMedium,
                color = OffWhite,
                letterSpacing = 4.0.sp
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            Text(
                text = "Earn your miles.",
                style = MaterialTheme.typography.bodyLarge,
                color = Haze
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Prompt ──────────────────────────────────────────────
            Text(
                text = "Where do you fly from?",
                style = MaterialTheme.typography.headlineSmall,
                color = OffWhite
            )

            Spacer(modifier = Modifier.height(Spacing.Large))

            // ── Search field ────────────────────────────────────────
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.onQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search airport or city\u2026",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Dim
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = Haze,
                        modifier = Modifier.size(24.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Slate,
                    unfocusedContainerColor = DeepNavy,
                    cursorColor = Amber,
                    focusedIndicatorColor = Amber,
                    unfocusedIndicatorColor = Border,
                    focusedTextColor = OffWhite,
                    unfocusedTextColor = OffWhite,
                    focusedLeadingIconColor = Amber,
                    unfocusedLeadingIconColor = Haze
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            // ── Search results ──────────────────────────────────────
            AnimatedVisibility(
                visible = searchResults.isNotEmpty(),
                enter = expandVertically() + fadeIn(tween(200)),
                exit = shrinkVertically() + fadeOut(tween(150))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            color = DeepNavy,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = Spacing.Small)
                ) {
                    items(searchResults, key = { it.id }) { airport ->
                        AirportResultRow(
                            airport = airport,
                            isSelected = selectedAirport?.id == airport.id,
                            onClick = { viewModel.selectAirport(airport) }
                        )
                    }
                }
            }

            // ── Selected airport indicator ──────────────────────────
            AnimatedVisibility(
                visible = selectedAirport != null && searchResults.isEmpty(),
                enter = expandVertically() + fadeIn(tween(200)),
                exit = shrinkVertically() + fadeOut(tween(150))
            ) {
                selectedAirport?.let { airport ->
                    SelectedAirportCard(airport = airport)
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
                shape = RoundedCornerShape(24.dp),
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

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
// Background layer — animated flight arcs and subtle latitude grid
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun OnboardingBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")

    // Slow pulsating glow intensity
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.03f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Slow sweep for the moving dot on arc
    val dotProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dot"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // ── Subtle latitude/longitude grid lines ────────────────
        val gridColor = Color(0xFF1E2636).copy(alpha = pulseAlpha * 3f)
        val gridCount = 8
        for (i in 1 until gridCount) {
            val yFraction = i.toFloat() / gridCount
            // Horizontal lines
            drawLine(
                color = gridColor,
                start = Offset(0f, h * yFraction),
                end = Offset(w, h * yFraction),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f))
            )
            // Vertical lines
            drawLine(
                color = gridColor,
                start = Offset(w * yFraction, 0f),
                end = Offset(w * yFraction, h),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f))
            )
        }

        // ── Decorative flight arcs ──────────────────────────────
        val arcColor = Amber.copy(alpha = pulseAlpha * 1.5f)
        val arcStroke = Stroke(
            width = 1.5f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
        )

        // Arc 1 — sweeping from bottom-left to upper-right
        val arc1 = Path().apply {
            moveTo(w * 0.05f, h * 0.85f)
            quadraticTo(
                w * 0.35f, h * 0.45f,
                w * 0.80f, h * 0.20f
            )
        }
        drawPath(arc1, color = arcColor, style = arcStroke)

        // Arc 2 — sweeping from left-center to right
        val arc2 = Path().apply {
            moveTo(w * 0.10f, h * 0.55f)
            quadraticTo(
                w * 0.55f, h * 0.30f,
                w * 0.95f, h * 0.50f
            )
        }
        drawPath(arc2, color = arcColor.copy(alpha = arcColor.alpha * 0.7f), style = arcStroke)

        // Arc 3 — small one in bottom half
        val arc3 = Path().apply {
            moveTo(w * 0.30f, h * 0.92f)
            quadraticTo(
                w * 0.60f, h * 0.70f,
                w * 0.90f, h * 0.78f
            )
        }
        drawPath(arc3, color = arcColor.copy(alpha = arcColor.alpha * 0.5f), style = arcStroke)

        // ── Animated dot traveling along arc 1 ──────────────────
        val dotT = dotProgress
        val dotX = (1 - dotT) * (1 - dotT) * (w * 0.05f) +
                2 * (1 - dotT) * dotT * (w * 0.35f) +
                dotT * dotT * (w * 0.80f)
        val dotY = (1 - dotT) * (1 - dotT) * (h * 0.85f) +
                2 * (1 - dotT) * dotT * (h * 0.45f) +
                dotT * dotT * (h * 0.20f)

        drawCircle(
            color = Amber.copy(alpha = 0.6f),
            radius = 6f,
            center = Offset(dotX, dotY)
        )
        // Outer glow
        drawCircle(
            color = Amber.copy(alpha = 0.15f),
            radius = 16f,
            center = Offset(dotX, dotY)
        )

        // ── Small static dots at arc endpoints (like cities) ────
        val endpoints = listOf(
            Offset(w * 0.05f, h * 0.85f),
            Offset(w * 0.80f, h * 0.20f),
            Offset(w * 0.10f, h * 0.55f),
            Offset(w * 0.95f, h * 0.50f),
            Offset(w * 0.30f, h * 0.92f),
            Offset(w * 0.90f, h * 0.78f)
        )
        endpoints.forEach { point ->
            drawCircle(
                color = Haze.copy(alpha = 0.4f),
                radius = 3f,
                center = point
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
// Airport search result row
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun AirportResultRow(
    airport: Airport,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) SoftAmber else Color.Transparent
    val accentColor = if (isSelected) Amber else Haze

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(Spacing.Small))

            // IATA code — monospace
            Text(
                text = airport.iataCode,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Amber else OffWhite,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(Spacing.Small))

            // City + airport name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = airport.municipality,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OffWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = airport.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Country code badge
            Text(
                text = airport.isoCountry,
                style = MaterialTheme.typography.labelMedium,
                color = Dim
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = Spacing.Small),
            color = Border,
            thickness = 0.5.dp
        )
    }
}

// ═════════════════════════════════════════════════════════════════════
// Selected airport confirmation card
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun SelectedAirportCard(airport: Airport) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = DeepNavy,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(Spacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.FlightTakeoff,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(Spacing.Medium))

                Column {
                    Text(
                        text = airport.iataCode,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Amber
                    )
                    Text(
                        text = airport.municipality,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OffWhite
                    )
                    Text(
                        text = airport.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Haze
                    )
                }
            }

            // Dashed flight path decoration
            Canvas(
                modifier = Modifier
                    .width(48.dp)
                    .height(2.dp)
            ) {
                drawLine(
                    color = Amber.copy(alpha = 0.4f),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            }
        }
    }
}
