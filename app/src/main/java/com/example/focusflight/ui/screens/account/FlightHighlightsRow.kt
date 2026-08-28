package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.FlightHighlights
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Green
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import java.util.Locale
import kotlin.math.floor

private val equatorLapColors = listOf(Amber, Green)

@Composable
internal fun FlightHighlightsRow(highlights: FlightHighlights) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LongestFlightCard(
            flight = highlights.longestFlight,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        )
        EquatorProgressCard(
            ratio = highlights.equatorRatio,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        )
    }
}

@Composable
private fun LongestFlightCard(flight: FlightLog?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(colors = listOf(Slate, DeepNavy))
            )
            .padding(16.dp)
    ) {
        Text(
            text = "LONGEST FLIGHT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Haze
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (flight != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AutoResizingMilesText(
                        text = com.example.focusflight.util.formatMiles(flight.distanceKm)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = flight.originIata,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = OffWhite
                        )
                        Icon(
                            imageVector = Icons.Outlined.FlightTakeoff,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = flight.destIata,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = OffWhite
                        )
                    }
                }
            } else {
                Text(
                    text = "No flights yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Haze
                )
            }
        }
    }
}

@Composable
private fun AutoResizingMilesText(
    text: String,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(text) { mutableStateOf(26.sp) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        maxLines = 1,
        softWrap = false,
        color = Amber,
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        ),
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowWidth && fontSize > 12.sp) {
                fontSize = (fontSize.value - 1f).sp
            } else {
                readyToDraw = true
            }
        }
    )
}

@Composable
private fun EquatorProgressCard(ratio: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DeepNavy)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "EQUATOR PROGRESS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Haze
        )

        val safeRatio = ratio.coerceAtLeast(0.0)
        var lapIndex = floor(safeRatio).toInt()
        var fraction = (safeRatio - lapIndex).toFloat()
        if (fraction <= 0f && safeRatio > 0.0) {
            // Exact multiple of a lap (e.g. 1.0, 2.0) - show that lap fully completed.
            lapIndex -= 1
            fraction = 1f
        }
        val lapColor = equatorLapColors[lapIndex % equatorLapColors.size]
        val percentText = String.format(Locale.US, "%.1f%%", safeRatio * 100)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)

                    drawArc(
                        color = Slate,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    if (fraction > 0f) {
                        drawArc(
                            color = lapColor,
                            startAngle = -90f,
                            sweepAngle = 360f * fraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
                Text(
                    text = percentText,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = OffWhite
                )
            }
        }
    }
}
