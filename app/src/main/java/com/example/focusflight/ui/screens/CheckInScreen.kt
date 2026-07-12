package com.example.focusflight.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.CheckInViewModel

@Composable
fun CheckInScreen(
    viewModel: CheckInViewModel,
    onBackClick: () -> Unit,
    onStartFlight: (flightNumber: String, destIata: String, durationMin: Int) -> Unit
) {
    val originAirport by viewModel.originAirport.collectAsState()
    val destAirport by viewModel.destAirport.collectAsState()
    val routeDetails by viewModel.routeDetails.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Midnight)
    ) {
        // ── Background Layer: Latitude/Longitude Grid Drawing ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeColor = Border.copy(alpha = 0.4f)
            val strokeWidth = 1.dp.toPx()
            
            // Draw horizontal lat lines
            val latSpacing = size.height / 8
            for (i in 1..7) {
                val y = latSpacing * i
                drawLine(
                    color = strokeColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                )
            }
            
            // Draw vertical lon lines
            val lonSpacing = size.width / 6
            for (i in 1..5) {
                val x = lonSpacing * i
                drawLine(
                    color = strokeColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidth
                )
            }
        }

        // ── Main Content Column ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = OffWhite
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.Medium))
                Text(
                    text = "CHECK-IN",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = OffWhite,
                        fontFamily = FontFamily.SansSerif
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Boarding Pass Ticket Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(DeepNavy, RoundedCornerShape(16.dp))
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
                    .padding(vertical = Spacing.Large)
            ) {
                // Ticket Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FOCUSFLIGHT",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        ),
                        color = Amber
                    )
                    Text(
                        text = "BOARDING PASS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Haze
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.Large))
                DashedDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.Large))

                // Passenger Info
                Column(modifier = Modifier.padding(horizontal = Spacing.Large)) {
                    Text(
                        text = "PASSENGER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Haze,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Captain",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = OffWhite
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))
                DashedDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.Large))

                // FROM / TO Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FROM",
                            style = MaterialTheme.typography.labelSmall,
                            color = Haze,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = originAirport?.iataCode ?: "---",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 36.sp
                            ),
                            color = OffWhite
                        )
                        Text(
                            text = originAirport?.municipality ?: "Loading...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Haze,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.FlightTakeoff,
                        contentDescription = "Flight to",
                        tint = Amber,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(horizontal = Spacing.ExtraSmall)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "TO",
                            style = MaterialTheme.typography.labelSmall,
                            color = Haze,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = destAirport?.iataCode ?: "---",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 36.sp
                            ),
                            color = OffWhite
                        )
                        Text(
                            text = destAirport?.municipality ?: "Loading...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Haze,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Large))
                DashedDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.Large))

                // DURATION / DISTANCE Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "DURATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = Haze,
                            letterSpacing = 1.sp
                        )
                        val durationMin = routeDetails?.flightTimeMin ?: 0
                        val hours = durationMin / 60
                        val minutes = durationMin % 60
                        val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = OffWhite
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DISTANCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Haze,
                            letterSpacing = 1.sp
                        )
                        val distanceKm = routeDetails?.distanceKm ?: 0.0
                        Text(
                            text = "%,d km".format(distanceKm.toInt()),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = OffWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))
                DashedDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.Large))

                // FLIGHT / DATE Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "FLIGHT NO.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Haze,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = viewModel.flightNumber,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = OffWhite
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Haze,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = viewModel.currentDate,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = OffWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                DashedDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.Large))

                // Custom Barcode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large),
                    contentAlignment = Alignment.Center
                ) {
                    BoardingPassBarcode(color = OffWhite.copy(alpha = 0.8f))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Large))

            // Action: Start Flight
            Button(
                onClick = {
                    val durationMin = routeDetails?.flightTimeMin ?: 0
                    if (durationMin > 0) {
                        onStartFlight(viewModel.flightNumber, viewModel.destIata, durationMin)
                    }
                },
                enabled = routeDetails != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor = Midnight,
                    disabledContainerColor = Slate,
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
                    text = "START FLIGHT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                )
            }

        }
    }
}

@Composable
fun DashedDivider(
    color: Color = Border,
    thickness: Dp = 1.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = thickness.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
        )
    }
}

@Composable
fun BoardingPassBarcode(
    color: Color = OffWhite,
    modifier: Modifier = Modifier
) {
    // Generate barcode bars with a stable seed to prevent jitter
    val barConfig = remember {
        val random = java.util.Random(12345)
        val bars = mutableListOf<Pair<Float, Float>>() // Pair of (bar width, gap)
        var total = 0f
        while (total < 600f) {
            val barWidth = (random.nextInt(3) + 1) * 3f
            val gap = (random.nextInt(2) + 1) * 3f
            bars.add(Pair(barWidth, gap))
            total += barWidth + gap
        }
        bars
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val width = size.width
        val height = size.height
        var x = 0f
        var index = 0
        while (x < width && index < barConfig.size) {
            val config = barConfig[index]
            val barWidth = config.first
            val gap = config.second
            if (x + barWidth <= width) {
                drawRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, height)
                )
            }
            x += barWidth + gap
            index++
        }
    }
}
