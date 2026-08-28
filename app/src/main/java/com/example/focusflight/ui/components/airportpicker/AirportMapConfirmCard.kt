package com.example.focusflight.ui.components.airportpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusflight.R
import com.example.focusflight.data.model.Airport
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Dim
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import java.util.Locale
import kotlin.math.abs

/**
 * The "confirm on the map" step of the app's two-step airport picker: IATA/name header, an
 * elevation/lat/lon fact row, and a world-map [Canvas] with a crosshair at the airport's
 * projected lat/lon. Extracted from Onboarding's `SelectedAirportCard` (previously private to
 * that screen) so Free Flight's origin picker and Change Home Base can show the same confirm
 * step instead of committing a selection immediately on tap.
 */
@Composable
fun AirportMapConfirmCard(
    airport: Airport,
    onChangeSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Amber
                )
                Text(
                    text = airport.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Haze,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onChangeSelection) {
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
    return String.format(Locale.US, "%.2f° %s", abs(value), dir)
}
