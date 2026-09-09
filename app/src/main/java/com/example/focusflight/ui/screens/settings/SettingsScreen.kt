package com.example.focusflight.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.ThemeMode
import com.example.focusflight.ui.components.BackTopAppBar
import com.example.focusflight.ui.components.SectionHeader
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.account.AccountViewModel
import com.example.focusflight.ui.viewmodel.account.HomeBaseActionResult
import java.util.Locale

/**
 * Settings: theme preference and the Story Mode home-base actions (docs/modes.md). Split out of
 * the Passport (`AccountScreen`) so that screen can stay a read-only trophy case/logbook, and
 * anything that changes app state or app-wide preferences lives here instead - the same
 * [AccountViewModel] backs both (it already owns theme mode and the home-base cooldown/search
 * state), just via its own instance scoped to this destination's back-stack entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AccountViewModel,
    onBackClick: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()
    val isLightMode = when (themeMode) {
        ThemeMode.LIGHT -> true
        ThemeMode.DARK -> false
        ThemeMode.SYSTEM -> !systemInDarkTheme
    }

    var showReturnHomeModal by remember { mutableStateOf(false) }
    var showReturningHomeModal by remember { mutableStateOf(false) }
    var showChangeHomeBase by remember { mutableStateOf(false) }
    var showWelcomeHome by remember { mutableStateOf(false) }
    var homeBaseSetAirport by remember { mutableStateOf<Airport?>(null) }
    val homeBaseSearchQuery by viewModel.homeBaseSearchQuery.collectAsState()
    val homeBaseSearchResults by viewModel.homeBaseSearchResults.collectAsState()
    val homeBaseSuggestions by viewModel.homeBaseSuggestions.collectAsState()
    val homeBaseActionResult by viewModel.homeBaseActionResult.collectAsState()

    // Same reasoning as the Passport's former copy of this effect: the work runs in
    // viewModelScope, so the result arrives asynchronously and may just as well say the cooldown
    // re-check refused the action, in which case the right behaviour is to show nothing at all.
    LaunchedEffect(homeBaseActionResult) {
        when (val result = homeBaseActionResult) {
            null -> Unit
            is HomeBaseActionResult.ReturnedHome -> {
                showWelcomeHome = true
                viewModel.consumeHomeBaseActionResult()
            }
            is HomeBaseActionResult.HomeBaseSet -> {
                homeBaseSetAirport = result.airport
                viewModel.consumeHomeBaseActionResult()
            }
            HomeBaseActionResult.Ineligible, HomeBaseActionResult.Failed -> {
                viewModel.consumeHomeBaseActionResult()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BackTopAppBar(title = "SETTINGS", onBackClick = onBackClick)
            },
            containerColor = Midnight
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(
                    start = Spacing.Medium,
                    end = Spacing.Medium,
                    top = Spacing.Small,
                    bottom = Spacing.ExtraLarge
                )
            ) {
                item { SectionHeader(title = "PREFERENCES") }
                item {
                    ThemePreferenceRow(
                        isLightMode = isLightMode,
                        onToggle = { checked ->
                            viewModel.setThemeMode(if (checked) ThemeMode.LIGHT else ThemeMode.DARK)
                        }
                    )
                }


                item { SectionHeader(title = "HOME BASE") }
                item {
                    HomeBaseActions(
                        state = uiState,
                        onReturnHomeClick = { showReturnHomeModal = true },
                        onChangeHomeBaseClick = { showChangeHomeBase = true }
                    )
                }
            }
        }

        if (showReturnHomeModal) {
            ReturnHomeConfirmModal(
                homeAirportIata = uiState.homeAirportIata,
                currentAirportIata = uiState.currentAirportIata,
                onConfirm = {
                    showReturnHomeModal = false
                    showReturningHomeModal = true
                },
                onDismiss = { showReturnHomeModal = false }
            )
        }

        // The teleport's outcome is settled the moment this modal appears - it cannot be
        // dismissed and it ends by attempting the teleport - so the globe the Hub will want is
        // rendered during the animation instead of after it. Keyed on Unit inside the `if` so it
        // fires once per showing.
        if (showReturningHomeModal) {
            LaunchedEffect(Unit) { viewModel.prepareReturnHome() }
            ReturningHomeModal(
                onComplete = {
                    viewModel.returnHome()
                    showReturningHomeModal = false
                }
            )
        }

        // Drawn last so it's the topmost sibling, fully covering everything above it.
        if (showChangeHomeBase) {
            ChangeHomeBaseScreen(
                query = homeBaseSearchQuery,
                onQueryChange = { viewModel.onHomeBaseSearchQueryChanged(it) },
                results = homeBaseSearchResults,
                suggestions = homeBaseSuggestions,
                // Close the picker on select, but leave the celebration to the published result -
                // changeHomeBase re-checks the 30-day cooldown and may write nothing at all.
                onAirportSelect = { airport ->
                    viewModel.changeHomeBase(airport)
                    showChangeHomeBase = false
                },
                onBackClick = { showChangeHomeBase = false }
            )
        }

        // ── Celebrations ─────────────────────────────────────────────────
        // Topmost of all: either can open over the picker above, and both end the flow by handing
        // the pilot back to the Hub rather than returning them here.
        if (showWelcomeHome) {
            val home = uiState.homeAirport
            HomeBaseCelebrationScreen(
                eyebrow = "WELCOME BACK",
                iata = uiState.homeAirportIata,
                airportName = home?.name.orEmpty(),
                locationLine = locationLine(home?.municipality, home?.isoCountry),
                onContinue = {
                    showWelcomeHome = false
                    onNavigateHome()
                },
                inlineHero = { HomeBaseSetHero() }
            )
        }

        homeBaseSetAirport?.let { airport ->
            HomeBaseCelebrationScreen(
                eyebrow = "HOME BASE SET",
                iata = airport.iataCode,
                airportName = airport.name,
                locationLine = locationLine(airport.municipality, airport.isoCountry),
                onContinue = {
                    homeBaseSetAirport = null
                    onNavigateHome()
                },
                inlineHero = { HomeBaseSetHero() }
            )
        }
    }
}


/** "MUNICH · GERMANY". [isoCountry] is a 2-letter code, and the app has no code-to-name table -
 *  `Locale` already ships one, and falls back to blank for anything it doesn't recognise. */
private fun locationLine(city: String?, isoCountry: String?): String {
    val country = isoCountry
        ?.takeIf { it.isNotBlank() }
        ?.let { Locale("", it).getDisplayCountry(Locale.US) }
        ?.takeIf { it.isNotBlank() }
    return listOfNotNull(city?.takeIf { it.isNotBlank() }, country)
        .joinToString(" · ")
        .uppercase(Locale.US)
}
