package com.example.focusflight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontFamily
import com.example.focusflight.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Where do you fly from?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OffWhite
                    )

                    Spacer(modifier = Modifier.height(Spacing.Large))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onQueryChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Search airport or city…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Dim,
                                modifier = Modifier.padding(start = 4.dp)
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
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate,
                            unfocusedContainerColor = DeepNavy,
                            cursorColor = Amber,
                            focusedBorderColor = Amber,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = OffWhite,
                            unfocusedTextColor = OffWhite,
                            focusedLeadingIconColor = Amber,
                            unfocusedLeadingIconColor = Haze
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(Spacing.Small))

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
                                    shape = RoundedCornerShape(16.dp)
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

                    // ── Pre-Search Suggestion Grid ──────────────────────────
                    AnimatedVisibility(
                        visible = searchQuery.isEmpty(),
                        enter = fadeIn(tween(200)) + expandVertically(),
                        exit = fadeOut(tween(150)) + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(Spacing.Large))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SuggestionTile(
                                            code = "FRA",
                                            city = "Frankfurt",
                                            name = "Frankfurt Airport",
                                            onClick = { viewModel.selectAirportByIata("FRA") }
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        SuggestionTile(
                                            code = "LHR",
                                            city = "London",
                                            name = "Heathrow Airport",
                                            onClick = { viewModel.selectAirportByIata("LHR") }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SuggestionTile(
                                            code = "BER",
                                            city = "Berlin",
                                            name = "Berlin Brandenburg",
                                            onClick = { viewModel.selectAirportByIata("BER") }
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        SuggestionTile(
                                            code = "MUC",
                                            city = "Munich",
                                            name = "Munich Airport",
                                            onClick = { viewModel.selectAirportByIata("MUC") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                SelectedAirportCard(
                    airport = targetAirport,
                    onClear = { viewModel.clearSelection() }
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

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))
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
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 28.dp)
                    .border(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .background(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = airport.iataCode,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.width(Spacing.Medium))

            // City and Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = airport.municipality,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = OffWhite,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = airport.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
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
private fun SelectedAirportCard(
    airport: Airport,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeepNavy)
    ) {
        // Top section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = airport.iataCode,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = Amber
                )
                Text(
                    text = airport.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear selection",
                    tint = Haze
                )
            }
        }

        HorizontalDivider(color = Border, thickness = 1.dp)

        // Facts section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FactItem("ELEV.", "${airport.elevationFt.toInt()} FT")
            VerticalDivider(modifier = Modifier.height(32.dp), color = Border)
            FactItem("LAT", formatCoord(airport.lat, true))
            VerticalDivider(modifier = Modifier.height(32.dp), color = Border)
            FactItem("LON", formatCoord(airport.lon, false))
        }

        // Map section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .background(Slate) // Backup color behind the map
        ) {
            Image(
                painter = painterResource(R.drawable.ic_world_map),
                contentDescription = "World Map",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * ((airport.lon + 180) / 360f).toFloat()
                val cy = size.height * ((90 - airport.lat) / 180f).toFloat()

                // Horizontal line
                drawLine(
                    color = Amber.copy(alpha = 0.8f),
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 2f
                )
                // Vertical line
                drawLine(
                    color = Amber.copy(alpha = 0.8f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 2f
                )
                
                // Center dot
                drawCircle(
                    color = Amber,
                    radius = 4f,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

@Composable
private fun FactItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Dim
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = OffWhite
        )
    }
}

private fun formatCoord(value: Double, isLat: Boolean): String {
    val dir = if (isLat) {
        if (value >= 0) "N" else "S"
    } else {
        if (value >= 0) "E" else "W"
    }
    return String.format(java.util.Locale.US, "%.2f° %s", kotlin.math.abs(value), dir)
}

@Composable
private fun SuggestionTile(
    code: String,
    city: String,
    name: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Midnight.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = Border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(Spacing.Medium),
        horizontalAlignment = Alignment.Start
    ) {
        // Top-side badge: dark background, thin muted white outline, white sans-serif IATA code
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 28.dp)
                .border(
                    width = 1.dp,
                    color = Border,
                    shape = RoundedCornerShape(6.dp)
                )
                .background(
                    color = Midnight,
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = OffWhite
            )
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))

        // Bottom-side text
        Text(
            text = city,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = OffWhite,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = Haze,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

