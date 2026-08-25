package com.example.focusflight.ui.screens.account

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Green
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.ContinentStats

private val continentNames = mapOf(
    "AF" to "Africa",
    "AN" to "Antarctica",
    "AS" to "Asia",
    "EU" to "Europe",
    "NA" to "N. America",
    "OC" to "Oceania",
    "SA" to "S. America"
)

private val continentColors = mapOf(
    "AF" to Color(0xFFF59E0B),
    "AN" to Color(0xFF93C5FD),
    "AS" to Color(0xFFEC4899),
    "EU" to Color(0xFF34D399),
    "NA" to Color(0xFF818CF8),
    "OC" to Color(0xFFFB923C),
    "SA" to Color(0xFF22D3EE)
)

@Composable
internal fun ContinentProgressCard(stats: List<ContinentStats>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        stats.forEach { stat ->
            ContinentProgressRow(stat)
        }
    }
}

@Composable
internal fun ContinentProgressRow(stat: ContinentStats) {
    val progress = if (stat.totalCountries > 0)
        stat.visitedCountries.size.toFloat() / stat.totalCountries.toFloat()
    else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progress_${stat.continentCode}"
    )

    val color = continentColors[stat.continentCode] ?: Amber
    val name = continentNames[stat.continentCode] ?: stat.continentCode

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Color dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = OffWhite
                )
                if (stat.isCompleted) {
                    Box(
                        modifier = Modifier
                            .background(Green.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "DONE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Green,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            Text(
                text = "${stat.visitedCountries.size} / ${stat.totalCountries}",
                style = MaterialTheme.typography.labelMedium,
                color = Haze
            )
        }

        // Custom progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Slate)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (stat.isCompleted)
                            Brush.horizontalGradient(listOf(Green, Green.copy(alpha = 0.7f)))
                        else
                            Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f)))
                    )
            )
        }
    }
}
