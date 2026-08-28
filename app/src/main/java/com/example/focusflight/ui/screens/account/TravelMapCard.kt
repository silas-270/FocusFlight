package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.components.InteractiveWorldMap
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.account.AccountUiState

@Composable
internal fun TravelMapCard(
    state: AccountUiState,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
            .clickable(onClick = onClick)
            .padding(Spacing.Medium)
    ) {
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

@Composable
internal fun TravelMapDetailModal(
    visitedCountriesCount: Int,
    totalCountriesCount: Int,
    onDismiss: () -> Unit
) {
    ScrimCardModal(onScrimTap = onDismiss) {
        Text(
            text = "WORLD EXPLORATION",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = Haze,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.Medium))

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = visitedCountriesCount.toString(),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = Amber
            )
            Text(
                text = " / $totalCountriesCount",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = Haze,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        val pct = if (totalCountriesCount > 0) (visitedCountriesCount.toFloat() / totalCountriesCount * 100) else 0f
        Text(
            text = "${"%.1f".format(pct)}% of all countries visited",
            style = MaterialTheme.typography.bodyMedium,
            color = OffWhite,
            textAlign = TextAlign.Center
        )
    }
}
