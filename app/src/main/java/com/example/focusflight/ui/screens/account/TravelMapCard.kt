package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.components.InteractiveWorldMap
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.account.AccountUiState

@Composable
internal fun TravelMapCard(state: AccountUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
            .padding(Spacing.Medium)
    ) {
        SectionHeader(icon = Icons.Outlined.Public, title = "TRAVEL MAP")
        Spacer(Modifier.height(Spacing.Small))
        InteractiveWorldMap(
            mapPaths = state.mapPaths,
            visitedCountries = state.allVisitedCountries,
            countryToContinent = state.countryToContinent,
            completedContinents = state.completedContinents,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        )
    }
}
