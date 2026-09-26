package com.silas270.blocktime.ui.screens.arrival

import com.silas270.blocktime.util.formatDuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.ui.components.FocusButton
import com.silas270.blocktime.ui.components.CaptionLabel
import com.silas270.blocktime.data.repository.DestinationPhoto
import androidx.compose.foundation.clickable
import com.silas270.blocktime.ui.theme.*
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.silas270.blocktime.R

@Composable
fun ArrivalCelebrationScreen(
    flightNo: String,
    destIata: String,
    durationMin: Int,
    rank: String,
    /** Prefetched by `InFlightViewModel` during the flight and handed off via
     *  `DestinationPhotoChannel` - null means "not resolved, no match, or fetch failed", all of
     *  which look identical here: fall back to the flat [Midnight] background below, silently.
     *  When present, its photographer credit is shown above CONTINUE (Pexels requires it). */
    destPhoto: DestinationPhoto? = null,
    /** The destination's city for "Welcome to …", looked up by the caller from [destIata] (it
     *  can't travel in the route string). Null until resolved, or if unknown: the IATA code
     *  stands in. */
    destCity: String? = null,
    onContinue: () -> Unit
) {
    // CONTINUE may wait up to LANDING_RESULT_TIMEOUT_MS on the landing result before it
    // navigates (see CesiumGameActivity), so after the first tap the button shows it's working
    // instead of looking dead, and further taps and back presses are ignored.
    var continuing by remember { mutableStateOf(false) }
    val continueOnce = {
        if (!continuing) {
            continuing = true
            onContinue()
        }
    }
    BackHandler {
        continueOnce()
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
        if (destPhoto != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(destPhoto.imageUrl)
                    .allowHardware(false)
                    .build(),
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

        // Scrolls when the content outgrows the screen (large font scales), while
        // heightIn(min = the viewport) keeps the weighted spacers spreading everything out
        // exactly as before whenever it does fit.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
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
                    text = "Welcome to ${destCity?.takeIf { it.isNotBlank() } ?: destIata}",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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

                        // The stamp's text is sized in dp (converted with toSp), not sp: it is
                        // artwork laid out at fixed offsets inside this fixed-size canvas, so
                        // following the system font scale only made the lines collide. At the
                        // default scale 1dp == 1sp, so nothing changes there.

                        // 4. Draw Date above IATA
                        val dateResult = textMeasurer.measure(
                            text = currentDateStr,
                            style = TextStyle(
                                color = ink.copy(alpha = 0.75f),
                                fontSize = 11.dp.toSp(),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.5.dp.toSp()
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
                                fontSize = 44.dp.toSp(),
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.dp.toSp()
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
                                fontSize = 12.dp.toSp(),
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 2.dp.toSp()
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
            val timeString = formatDuration(durationMin)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = fade }
            ) {
                // Says what the number is: the time spent focused on this flight.
                CaptionLabel(text = "FOCUSED FOR")
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    ),
                    color = OffWhite
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (destPhoto != null) {
                PhotoCredit(
                    photo = destPhoto,
                    // Same gate as CONTINUE: no stray tap opens a browser before it has faded in.
                    enabled = { fade >= 0.99f },
                    modifier = Modifier
                        .align(Alignment.Start)
                        .graphicsLayer { alpha = fade }
                )
                Spacer(modifier = Modifier.height(Spacing.Small))
            }

            // CONTINUE rather than "ENTER HUB": when the landing moved a challenge it leads to
            // the Challenge Outcome screen first. Taps only count once the button has faded in
            // - while it's still invisible a stray tap would skip the whole celebration.
            FocusButton(
                text = "CONTINUE",
                onClick = { if (fade >= 0.99f) continueOnce() },
                enabled = !continuing,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = fade }
            )
        }
        }
    }
}

/**
 * The photographer credit Pexels' API guidelines require, set like a gallery placard on the
 * photo's lower-left: one line of small serif - "Photo by *name* on Pexels", the name in the
 * italic of "Welcome to …". Deliberately sentence case and small so it
 * reads as part of the photo rather than as another all-caps label like "FOCUSED FOR". Tapping it
 * opens the photo's own Pexels page (or Pexels itself when the response had no page).
 */
@Composable
private fun PhotoCredit(photo: DestinationPhoto, enabled: () -> Boolean, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val link = photo.photoPageUrl ?: "https://www.pexels.com"
    val photographer = photo.photographer?.takeIf { it.isNotBlank() }
    val credit = buildAnnotatedString {
        if (photographer != null) {
            append("Photo by ")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = OffWhite.copy(alpha = 0.85f))) {
                append(photographer)
            }
            append(" on Pexels")
        } else {
            append("Photo from Pexels")
        }
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClickLabel = "Open photo on Pexels") {
                if (enabled()) runCatching { uriHandler.openUri(link) }
            }
            .padding(vertical = Spacing.Small),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = credit,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
            color = Haze,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
