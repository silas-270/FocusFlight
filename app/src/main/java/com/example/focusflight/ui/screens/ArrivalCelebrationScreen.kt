package com.example.focusflight.ui.screens

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
import com.example.focusflight.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ArrivalCelebrationScreen(
    flightNo: String,
    destIata: String,
    durationMin: Int,
    rank: String,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Screen height in pixels for the airplane animation
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var stampLanded by remember { mutableStateOf(false) }
    val stampScale by animateFloatAsState(
        targetValue = if (stampLanded) 1f else 5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    val stampAlpha by animateFloatAsState(
        targetValue = if (stampLanded) 1f else 0f,
        animationSpec = tween(300)
    )

    // Airplane animation states: start just off-screen bottom
    val planeOffsetY = remember { Animatable(screenHeightPx * 0.7f) }

    // Compute styling details based on rank
    val inkColor = when (rank) {
        "GLOBETROTTER" -> Amber // Gold/Amber
        "COMMANDER" -> Color(0xFF2563EB) // Cobalt Blue
        "CAPTAIN" -> Color(0xFF10B981) // Emerald Green
        else -> Color(0xFF94A3B8) // Slate Grey
    }

    val currentDateStr = remember {
        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(java.util.Date()).uppercase()
    }

    LaunchedEffect(Unit) {
        // Launch airplane animation: from off-screen bottom to off-screen top
        launch {
            planeOffsetY.animateTo(
                targetValue = -screenHeightPx * 0.7f,
                animationSpec = tween(
                    durationMillis = 1500,
                    easing = CubicBezierEasing(0.15f, 0.55f, 0.3f, 1.0f) // Slower starting speed, smoother deceleration
                )
            )
        }

        delay(1500) // Wait for plane to exit (1.5 seconds)
        stampLanded = true
        // Massive Haptic touchdown feedback
        try {
            val view = context as? android.app.Activity
            view?.window?.decorView?.performHapticFeedback(
                android.view.HapticFeedbackConstants.LONG_PRESS
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Midnight),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = Spacing.Large, horizontal = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Shrunk font size from displayMedium to headlineLarge so it doesn't wrap
                Text(
                    text = "TOUCHDOWN",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    ),
                    color = Haze,
                    modifier = Modifier.graphicsLayer { alpha = stampAlpha }
                )
                
                Text(
                    text = "Welcome to $destIata",
                    style = MaterialTheme.typography.titleMedium,
                    color = inkColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp).graphicsLayer { alpha = stampAlpha }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Massive Vintage Passport Stamp Canvas
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = stampScale,
                                scaleY = stampScale,
                                alpha = stampAlpha,
                                rotationZ = -5f // Slight tilt for organic stamp look
                            )
                    ) {
                        val strokeWidth = 5.dp.toPx()
                        val w = size.width
                        val h = size.height
                        val center = Offset(w / 2f, h / 2f)

                        // Draw Stamp Boundary
                        when (rank) {
                            "CO-PILOT" -> {
                                drawCircle(
                                    color = inkColor.copy(alpha = 0.8f),
                                    radius = w * 0.46f,
                                    style = Stroke(width = strokeWidth)
                                )
                                drawCircle(
                                    color = inkColor.copy(alpha = 0.8f),
                                    radius = w * 0.41f,
                                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))
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
                                    color = inkColor.copy(alpha = 0.8f),
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                            "COMMANDER" -> {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(w * 0.5f, h * 0.06f)
                                    lineTo(w * 0.92f, h * 0.06f)
                                    lineTo(w * 0.92f, h * 0.52f)
                                    cubicTo(w * 0.92f, h * 0.78f, w * 0.5f, h * 0.94f, w * 0.5f, h * 0.94f)
                                    cubicTo(w * 0.5f, h * 0.94f, w * 0.08f, h * 0.78f, w * 0.08f, h * 0.52f)
                                    lineTo(w * 0.08f, h * 0.06f)
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    color = inkColor.copy(alpha = 0.8f),
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                            else -> { // GLOBETROTTER (Foil Golden Double Ring)
                                drawCircle(
                                    color = inkColor.copy(alpha = 0.9f),
                                    radius = w * 0.48f,
                                    style = Stroke(width = strokeWidth)
                                )
                                drawCircle(
                                    color = inkColor.copy(alpha = 0.9f),
                                    radius = w * 0.43f,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                // Surrounding dots
                                val count = 20
                                for (i in 0 until count) {
                                    val rad = Math.toRadians((i * (360.0 / count)))
                                    val dotPt = Offset(
                                        center.x + (w * 0.38f) * kotlin.math.cos(rad).toFloat(),
                                        center.y + (w * 0.38f) * kotlin.math.sin(rad).toFloat()
                                    )
                                    drawCircle(
                                        color = inkColor.copy(alpha = 0.8f),
                                        radius = 3.dp.toPx(),
                                        center = dotPt
                                    )
                                }
                            }
                        }

                        // Draw Large Destination IATA in Center (Exactly Centered)
                        val iataResult = textMeasurer.measure(
                            text = destIata,
                            style = TextStyle(
                                color = inkColor.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 48.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        drawText(
                            textLayoutResult = iataResult,
                            topLeft = Offset(
                                center.x - iataResult.size.width / 2f,
                                center.y - iataResult.size.height / 2f
                            )
                        )

                        // Draw Stamp Date above IATA (Pushed up for spacing)
                        val dateResult = textMeasurer.measure(
                            text = currentDateStr,
                            style = TextStyle(
                                color = inkColor.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        drawText(
                            textLayoutResult = dateResult,
                            topLeft = Offset(
                                center.x - dateResult.size.width / 2f,
                                center.y - 56.dp.toPx()
                            )
                        )

                        // Draw Earned Rank below IATA (Symmetrically spaced)
                        val rankResult = textMeasurer.measure(
                            text = rank,
                            style = TextStyle(
                                color = inkColor.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 2.sp
                            )
                        )
                        drawText(
                            textLayoutResult = rankResult,
                            topLeft = Offset(
                                center.x - rankResult.size.width / 2f,
                                center.y + 44.dp.toPx()
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Monospaced Flight Telemetry Receipt Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepNavy.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .border(1.dp, inkColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(vertical = 28.dp, horizontal = 16.dp)
                        .graphicsLayer { alpha = stampAlpha },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val hours = durationMin / 60
                    val mins = durationMin % 60
                    val timeString = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        ),
                        color = Haze
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Large))

            // Exit / Continue Button
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .graphicsLayer { alpha = stampAlpha },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = inkColor, contentColor = Midnight)
            ) {
                Text(
                    text = "ENTER HUB",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                )
            }
        }

        // Massive Airplane Flyover - drawn above everything
        Icon(
            imageVector = Icons.Default.Flight,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
                .graphicsLayer {
                    translationY = planeOffsetY.value
                }
        )
    }
}

@Composable
fun CelebrationRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Haze.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = Haze
        )
    }
}
