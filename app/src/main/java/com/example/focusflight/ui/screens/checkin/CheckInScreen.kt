package com.example.focusflight.ui.screens.checkin

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
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material3.*
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.components.BackTopAppBar
import com.example.focusflight.ui.components.BadgeSize
import com.example.focusflight.ui.components.BadgeStyle
import com.example.focusflight.ui.components.BadgeVariant
import com.example.focusflight.ui.components.CardVariant
import com.example.focusflight.ui.components.FocusBadge
import com.example.focusflight.ui.components.FocusButton
import com.example.focusflight.ui.components.FocusCard
import com.example.focusflight.ui.components.FocusInfoRow
import com.example.focusflight.ui.components.SectionHeader
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.viewmodel.checkin.CheckInViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    viewModel: CheckInViewModel,
    onBackClick: () -> Unit,
    onStartFlight: (flightNumber: String, destIata: String, durationMin: Int) -> Unit
) {
    val originAirport by viewModel.originAirport.collectAsState()
    val destAirport by viewModel.destAirport.collectAsState()
    val routeDetails by viewModel.routeDetails.collectAsState()
    val routeMissing by viewModel.routeMissing.collectAsState()
    val pilotName by viewModel.pilotName.collectAsState()

    Scaffold(
        topBar = {
            BackTopAppBar(title = "CHECK-IN", onBackClick = onBackClick)
        },
        containerColor = Midnight
    ) { paddingValues ->
        // ── Main Content Column ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(start = Spacing.Large, top = Spacing.Medium, end = Spacing.Large, bottom = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Boarding Pass Ticket Card
            FocusCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                variant = CardVariant.Elevated,
                contentPadding = PaddingValues(vertical = Spacing.Large)
            ) {
                // Ticket Header
                SectionHeader(
                    title = "FOCUSFLIGHT",
                    modifier = Modifier.padding(horizontal = Spacing.Large),
                    trailingAction = {
                        FocusBadge(
                            text = "BOARDING PASS",
                            variant = BadgeVariant.Neutral,
                            style = BadgeStyle.Translucent,
                            size = BadgeSize.Compact
                        )
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.Large))
                DashedDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.Large))

                // Pilot Info - the pilot's own name rather than a fixed rank (ranks are earned
                // per flight on arrival). Labelled PILOT, not PASSENGER: the app's player flies.
                FocusInfoRow(
                    label = "PILOT",
                    value = pilotName ?: "Pilot",
                    modifier = Modifier.padding(horizontal = Spacing.Large)
                )

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

                // DURATION / DISTANCE Rows
                val durationMin = routeDetails?.durationMin ?: 0
                val hours = durationMin / 60
                val minutes = durationMin % 60
                val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                val distanceKm = routeDetails?.distanceKm ?: 0.0

                FocusInfoRow(
                    label = "DURATION",
                    value = durationText,
                    modifier = Modifier.padding(horizontal = Spacing.Large)
                )
                Spacer(modifier = Modifier.height(Spacing.Small))
                FocusInfoRow(
                    label = "DISTANCE",
                    value = com.example.focusflight.util.formatMiles(distanceKm),
                    modifier = Modifier.padding(horizontal = Spacing.Large)
                )

                Spacer(modifier = Modifier.height(Spacing.Medium))
                DashedDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.Large))

                // FLIGHT / DATE Rows
                FocusInfoRow(
                    label = "FLIGHT NO.",
                    value = viewModel.flightNumber,
                    modifier = Modifier.padding(horizontal = Spacing.Large)
                )
                Spacer(modifier = Modifier.height(Spacing.Small))
                FocusInfoRow(
                    label = "DATE",
                    value = viewModel.currentDate,
                    modifier = Modifier.padding(horizontal = Spacing.Large)
                )

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

            if (routeMissing) {
                Text(
                    text = "This route couldn't be loaded. Go back and pick your flight again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.Small)
                )
            }

            // Action: Start Flight
            FocusButton(
                text = "START FLIGHT",
                onClick = {
                    val durationMin = routeDetails?.durationMin ?: 0
                    if (durationMin > 0) {
                        onStartFlight(viewModel.flightNumber, viewModel.destIata, durationMin)
                    }
                },
                enabled = routeDetails != null,
                icon = Icons.Outlined.FlightTakeoff,
                modifier = Modifier.fillMaxWidth()
            )

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
            // In dp, not raw px, so the dashes are the same size on every screen density. 5dp and
            // 3.33dp are what the original 15px/10px came to on the S23 (density 3.0).
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), (10f / 3f).dp.toPx()), 0f)
        )
    }
}

/** How many bar units [BoardingPassBarcode] pre-generates - far wider than any portrait screen
 *  needs, so the pattern always reaches the full width. */
private const val BarcodeUnits = 2000

@Composable
fun BoardingPassBarcode(
    color: Color = OffWhite,
    modifier: Modifier = Modifier
) {
    // Bar and gap widths in units of 1dp, from a fixed seed so the pattern never jitters. These
    // used to be raw pixels with generation stopping at 600px - on the S23 that filled only about
    // three quarters of the ticket, and on any other density it was a different size again.
    // Measured in dp, the bars are the same widths the S23 always showed (3px = 1dp there), and
    // the pattern is drawn to the full width on every screen.
    val barConfig = remember {
        val random = java.util.Random(12345)
        val bars = mutableListOf<Pair<Int, Int>>() // Pair of (bar width, gap), in dp
        var total = 0
        while (total < BarcodeUnits) {
            val barWidth = random.nextInt(3) + 1
            val gap = random.nextInt(2) + 1
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
        val unit = 1.dp.toPx()
        val width = size.width
        val height = size.height

        // Take as many bars as fit, then centre them, so both ends of the barcode sit an equal
        // (sub-bar-width) distance from the edges instead of a ragged gap on the right only.
        var used = 0f
        var count = 0
        while (count < barConfig.size) {
            val barEnd = used + barConfig[count].first * unit
            if (barEnd > width) break
            used = barEnd + barConfig[count].second * unit
            count++
        }
        val lastGap = if (count > 0) barConfig[count - 1].second * unit else 0f
        var x = (width - (used - lastGap)) / 2f

        for (index in 0 until count) {
            val (barWidth, gap) = barConfig[index]
            drawRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(barWidth * unit, height)
            )
            x += (barWidth + gap) * unit
        }
    }
}
