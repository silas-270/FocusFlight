package com.example.focusflight.ui.components.airportpicker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.model.Airport
import com.example.focusflight.ui.components.IataBadge
import com.example.focusflight.ui.components.SearchTextField
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Spacing
import kotlinx.coroutines.delay

/** The results list's height with no keyboard up - the size the layout was designed around. */
private val ResultsListHeight = 240.dp

/** The smallest the results list shrinks to while the keyboard is up (about two rows), so a very
 *  crowded caller still shows something rather than collapsing the list to nothing. */
private val ResultsListMinHeight = 112.dp

/** How long a query must sit with no results before "no match" is shown - longer than
 *  AirportSearchController's 300ms debounce plus the query itself, so it doesn't flash up for the
 *  stale empty result of the previous, too-short query while the pilot is still typing. */
private const val NoMatchesDelayMs = 600L

/** AirportSearchController only searches from two characters on; below that an empty result
 *  means "not searched yet", not "no match". */
private const val MinSearchLength = 2

/** One pre-search suggestion tile's content plus its tap action, resolved by the caller (e.g. an
 *  [Airport] looked up by IATA code) so this package doesn't need to know how each screen sources
 *  its suggestions. */
data class AirportSuggestion(
    val code: String,
    val city: String,
    val name: String,
    val onSelect: () -> Unit
)

/**
 * The "search an airport" step of the app's two-step airport picker: a headline, a search field,
 * a results list once the user types, and a pre-search 2x2 suggestion grid. Extracted from
 * Onboarding's home-airport picker (previously private to that screen) so Free Flight's origin
 * picker and Change Home Base can reuse the exact same layout instead of re-implementing it -
 * only [headline]/[placeholder]/[suggestions] differ per caller.
 */
@Composable
fun AirportSearchStep(
    headline: String,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    searchResults: List<Airport>,
    onAirportSelected: (Airport) -> Unit,
    suggestions: List<AirportSuggestion>,
    modifier: Modifier = Modifier
) {
    // Keyboard awareness. The window is edge-to-edge, so the IME overlays this screen instead of
    // resizing it (see CesiumGameActivity) - and on the S23 it used to hide the bottom half of the
    // results list. Rather than every caller having to re-flow its whole layout around the
    // keyboard, the list itself shrinks to whatever room is left between its top edge and the
    // top of the keyboard, capped at its normal height. With no keyboard up this is exactly
    // [ResultsListHeight], so the resting layout is unchanged.
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    var resultsTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var rootHeightPx by remember { mutableIntStateOf(0) }
    val resultsHeight = if (imeBottomPx > 0 && !resultsTopPx.isNaN() && rootHeightPx > 0) {
        with(density) { (rootHeightPx - imeBottomPx - resultsTopPx).toDp() - Spacing.Small }
            .coerceIn(ResultsListMinHeight, ResultsListHeight)
    } else {
        ResultsListHeight
    }

    // Same "no match" feedback AirportSearchPanel/OriginSearchPanel give - without it a search
    // that matched nothing just left an empty screen (results list and suggestion grid both gone).
    val trimmedLength = searchQuery.trim().length
    val searchedWithNoResults = trimmedLength >= MinSearchLength && searchResults.isEmpty()
    var showNoMatches by remember { mutableStateOf(false) }
    LaunchedEffect(searchQuery, searchedWithNoResults) {
        showNoMatches = false
        if (searchedWithNoResults) {
            delay(NoMatchesDelayMs)
            showNoMatches = true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            color = OffWhite
        )

        Spacer(modifier = Modifier.height(Spacing.Large))

        SearchTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = placeholder
        )

        Spacer(
            modifier = Modifier
                .height(Spacing.Small)
                .onGloballyPositioned { coordinates ->
                    resultsTopPx = coordinates.positionInRoot().y + coordinates.size.height
                    rootHeightPx = coordinates.findRootCoordinates().size.height
                }
        )

        AnimatedVisibility(
            visible = searchResults.isNotEmpty(),
            enter = expandVertically() + fadeIn(tween(200)),
            exit = shrinkVertically() + fadeOut(tween(150))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resultsHeight)
                    .background(
                        color = DeepNavy,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = Spacing.Small)
            ) {
                items(searchResults, key = { it.id }) { airport ->
                    AirportResultRow(
                        airport = airport,
                        onClick = { onAirportSelected(airport) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showNoMatches && searchedWithNoResults,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            Text(
                text = "No airports match that search.",
                color = Haze,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.Large)
            )
        }

        // ── Pre-Search Suggestion Grid ──────────────────────────
        AnimatedVisibility(
            visible = searchQuery.isEmpty() && suggestions.isNotEmpty(),
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(Spacing.Large))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    suggestions.chunked(2).forEach { rowSuggestions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            rowSuggestions.forEach { suggestion ->
                                Box(modifier = Modifier.weight(1f)) {
                                    SuggestionTile(
                                        code = suggestion.code,
                                        city = suggestion.city,
                                        name = suggestion.name,
                                        onClick = suggestion.onSelect
                                    )
                                }
                            }
                            if (rowSuggestions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
// Airport search result row
// ═════════════════════════════════════════════════════════════════════

@Composable
fun AirportResultRow(
    airport: Airport,
    onClick: () -> Unit
) {
    val backgroundColor = Color.Transparent
    val accentColor = Haze

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
            IataBadge(
                code = airport.iataCode,
                borderColor = accentColor.copy(alpha = 0.5f),
                backgroundColor = accentColor.copy(alpha = 0.15f),
                textColor = accentColor,
                textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.width(Spacing.Medium))

            // City and Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = airport.municipality,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = Spacing.Small),
            color = Border,
            thickness = 0.5.dp
        )
    }
}

@Composable
fun SuggestionTile(
    code: String,
    city: String,
    name: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeepNavy)
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
        IataBadge(code = code, borderColor = Border, backgroundColor = Midnight, textColor = OffWhite)

        Spacer(modifier = Modifier.height(Spacing.Medium))

        // Bottom-side text
        Text(
            text = city,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = OffWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = Haze,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
