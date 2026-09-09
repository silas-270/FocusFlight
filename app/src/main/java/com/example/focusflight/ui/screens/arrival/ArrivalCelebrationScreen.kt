package com.example.focusflight.ui.screens.arrival

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.components.FocusButton
import com.example.focusflight.ui.components.FocusInfoRow
import com.example.focusflight.ui.theme.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.example.focusflight.R

@Composable
fun ArrivalCelebrationScreen(
    flightNo: String,
    destIata: String,
    durationMin: Int,
    rank: String,
    /** Prefetched by `InFlightViewModel` during the flight and handed off via
     *  `DestinationPhotoChannel` - null means "not resolved, no match, or fetch failed", all of
     *  which look identical here: fall back to the flat [Midnight] background below, silently. */
    destPhotoUrl: String? = null,
    onContinue: () -> Unit
) {
    BackHandler {
        onContinue()
    }
    
    val view = androidx.compose.ui.platform.LocalView.current
    val textMeasurer = rememberTextMeasurer()

    var landed by remember { mutableStateOf(false) }
    val slam by animateFloatAsState(
        targetValue = if (landed) 1f else 4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "celebration_slam"
    )
    val fade by animateFloatAsState(
        targetValue = if (landed) 1f else 0f,
        animationSpec = tween(300),
        label = "celebration_fade"
    )

    // Compute rank-specific ink accent color on parchment
    val stampInkColor = when (rank) {
        "GLOBETROTTER" -> LogbookInkDark
        "COMMANDER" -> Color(0xFF6E2814) // Rich deep mahogany / wax red
        "CAPTAIN" -> Color(0xFF144D30)    // Deep forest passport green
        else -> LogbookInkDark           // Classic dark sepia ink
    }

    val currentDateStr = remember {
        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(java.util.Date()).uppercase()
    }

    LaunchedEffect(Unit) {
        delay(120)
        landed = true
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) {
            // Haptics are a flourish, never a requirement
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Midnight),
        contentAlignment = Alignment.Center
    ) {
        // Fullscreen Destination Landmark Photo (bleeds edge-to-edge behind system bars)
        if (destPhotoUrl != null) {
            AsyncImage(
                model = destPhotoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            // Atmospheric top and bottom dark gradient scrims for contrast
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Midnight.copy(alpha = 0.70f),
                                Midnight.copy(alpha = 0.25f),
                                Midnight.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(vertical = Spacing.Large, horizontal = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // 1. Eyebrow Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = fade }
            ) {
                Text(
                    text = "TOUCHDOWN",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    ),
                    color = Amber
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Welcome to $destIata",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = OffWhite.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 2. Torn Paper Piece (Static) with Slamming Stamp Overlay
            Box(
                modifier = Modifier
                    .size(310.dp)
                    .graphicsLayer { alpha = fade },
                contentAlignment = Alignment.Center
            ) {
                // Static torn paper piece lying on the screen
                Image(
                    painter = painterResource(R.drawable.bg_torn_paper),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Animated Stamp that slams down onto the paper piece
                Box(
                    modifier = Modifier
                        .size(228.dp)
                        .graphicsLayer {
                            scaleX = slam
                            scaleY = slam
                            alpha = fade
                            rotationZ = -4f // Authentic angled stamp tilt
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val w = size.width
                        val h = size.height
                        val center = Offset(w / 2f, h / 2f)

                        // 3. Draw Outer Vintage Stamp Boundary based on Rank (pressed onto paper)
                        val strokeWidth = 3.5.dp.toPx()
                        val ink = stampInkColor.copy(alpha = 0.90f)

                        when (rank) {
                            "CO-PILOT" -> {
                                drawCircle(
                                    color = ink,
                                    radius = w * 0.46f,
                                    style = Stroke(width = strokeWidth)
                                )
                                drawCircle(
                                    color = ink.copy(alpha = 0.7f),
                                    radius = w * 0.41f,
                                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))
                                )
                            }
                            "CAPTAIN" -> {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    val r = w * 0.46f
                                    val angles = listOf(0, 45, 90, 135, 180, 225, 270, 315)
                                    angles.forEachIndexed { idx, a ->
                                        val rad = Math.toRadians(a.toDouble())
                                        val x = center.x + r * kotlin.math.cos(rad).toFloat()
                                        val y = center.y + r * kotlin.math.sin(rad).toFloat()
                                        if (idx == 0) moveTo(x, y) else lineTo(x, y)
                                    }
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    color = ink,
                                    style = Stroke(width = strokeWidth)
                                )
                                drawCircle(
                                    color = ink.copy(alpha = 0.6f),
                                    radius = w * 0.41f,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                            "COMMANDER" -> {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(w * 0.5f, h * 0.08f)
                                    lineTo(w * 0.92f, h * 0.08f)
                                    lineTo(w * 0.92f, h * 0.52f)
                                    cubicTo(w * 0.92f, h * 0.76f, w * 0.5f, h * 0.92f, w * 0.5f, h * 0.92f)
                                    cubicTo(w * 0.5f, h * 0.92f, w * 0.08f, h * 0.76f, w * 0.08f, h * 0.52f)
                                    lineTo(w * 0.08f, h * 0.08f)
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    color = ink,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                            else -> { // GLOBETROTTER
                                drawCircle(
                                    color = ink,
                                    radius = w * 0.48f,
                                    style = Stroke(width = strokeWidth)
                                )
                                drawCircle(
                                    color = ink.copy(alpha = 0.75f),
                                    radius = w * 0.43f,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                                // Surrounding dots
                                val count = 24
                                for (i in 0 until count) {
                                    val rad = Math.toRadians((i * (360.0 / count)))
                                    val dotPt = Offset(
                                        center.x + (w * 0.38f) * kotlin.math.cos(rad).toFloat(),
                                        center.y + (w * 0.38f) * kotlin.math.sin(rad).toFloat()
                                    )
                                    drawCircle(
                                        color = ink,
                                        radius = 2.dp.toPx(),
                                        center = dotPt
                                    )
                                }
                            }
                        }

                        // 4. Draw Date above IATA
                        val dateResult = textMeasurer.measure(
                            text = currentDateStr,
                            style = TextStyle(
                                color = ink.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.5.sp
                            )
                        )
                        drawText(
                            textLayoutResult = dateResult,
                            topLeft = Offset(
                                center.x - dateResult.size.width / 2f,
                                center.y - 52.dp.toPx()
                            )
                        )

                        // 5. Draw Large Centered Destination IATA
                        val iataResult = textMeasurer.measure(
                            text = destIata,
                            style = TextStyle(
                                color = ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 44.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.sp
                            )
                        )
                        drawText(
                            textLayoutResult = iataResult,
                            topLeft = Offset(
                                center.x - iataResult.size.width / 2f,
                                center.y - iataResult.size.height / 2f
                            )
                        )

                        // 6. Draw Earned Rank below IATA
                        val rankResult = textMeasurer.measure(
                            text = rank,
                            style = TextStyle(
                                color = ink.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 2.sp
                            )
                        )
                        drawText(
                            textLayoutResult = rankResult,
                            topLeft = Offset(
                                center.x - rankResult.size.width / 2f,
                                center.y + 40.dp.toPx()
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 3. Flight Duration: Clean minimal telemetry presentation (positioned between stamp and button)
            val hours = durationMin / 60
            val mins = durationMin % 60
            val timeString = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

            Text(
                text = timeString,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                ),
                color = OffWhite,
                modifier = Modifier.graphicsLayer { alpha = fade }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Exit / Continue Button
            FocusButton(
                text = "ENTER HUB",
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = fade }
            )
        }
    }
}

/**
 * Backward compatibility alias for the centralized [com.example.focusflight.ui.components.FocusInfoRow].
 */
@Deprecated("Use FocusInfoRow instead", ReplaceWith("FocusInfoRow(label = label, value = value)", "com.example.focusflight.ui.components.FocusInfoRow"))
@Composable
fun CelebrationRow(label: String, value: String) {
    FocusInfoRow(
        label = label,
        value = value,
        labelColor = Haze.copy(alpha = 0.6f),
        valueColor = Haze
    )
}
