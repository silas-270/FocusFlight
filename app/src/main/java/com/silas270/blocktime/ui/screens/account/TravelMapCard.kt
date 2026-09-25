package com.silas270.blocktime.ui.screens.account

import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.remember
import com.silas270.blocktime.ui.theme.Border
import java.util.Locale
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
import com.silas270.blocktime.ui.components.InteractiveWorldMap
import com.silas270.blocktime.ui.components.ScrimCardModal
import com.silas270.blocktime.ui.theme.Amber
import com.silas270.blocktime.ui.theme.DeepNavy
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.OffWhite
import com.silas270.blocktime.ui.theme.Slate
import com.silas270.blocktime.ui.theme.Spacing
import com.silas270.blocktime.ui.viewmodel.account.AccountUiState

@Composable
internal fun TravelMapCard(
    state: AccountUiState,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
            .clickable(onClickLabel = "Show visited countries", onClick = onClick)
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
        // Says the card opens something, and what: the map alone looked exactly like the
        // non-tappable one on Flight Search.
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val visited = state.allVisitedCountries.size
            Text(
                text = "$visited ${if (visited == 1) "country" else "countries"} visited",
                style = MaterialTheme.typography.labelMedium,
                color = Haze,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "DETAILS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = Amber
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Opened by tapping [TravelMapCard]. The card already *is* the map (a static drawing - no pan or
 * zoom), so this is the part the map cannot say: how many countries, and which ones by name. It
 * used to stop at the count, which made the tap feel like it had opened nothing.
 *
 * [visitedCountryCodes] are ISO 3166 alpha-2 codes; names come from [Locale], as the Settings
 * screen's location line does, and a code it does not know falls back to itself.
 */
@Composable
internal fun TravelMapDetailModal(
    visitedCountryCodes: Set<String>,
    totalCountriesCount: Int,
    onDismiss: () -> Unit
) {
    val visitedCountriesCount = visitedCountryCodes.size
    val countryNames = remember(visitedCountryCodes) {
        visitedCountryCodes
            .map { code -> Locale("", code).getDisplayCountry(Locale.US).ifBlank { code } }
            .sorted()
    }

    ScrimCardModal(onScrimTap = onDismiss, onClose = onDismiss) {
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
            text = "${String.format(Locale.US, "%.1f", pct)}% of all countries visited",
            style = MaterialTheme.typography.bodyMedium,
            color = OffWhite,
            textAlign = TextAlign.Center
        )

        if (countryNames.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.Medium))
            HorizontalDivider(color = Border.copy(alpha = 0.4f))
            Spacer(Modifier.height(Spacing.Small))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                countryNames.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OffWhite
                    )
                }
            }
        }
    }
}
