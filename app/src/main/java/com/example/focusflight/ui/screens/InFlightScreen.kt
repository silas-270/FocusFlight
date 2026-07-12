package com.example.focusflight.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focusflight.data.model.Airport
import kotlinx.coroutines.delay
import com.example.focusflight.ui.theme.*
import com.example.focusflight.ui.viewmodel.InFlightViewModel
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.ui.graphics.asAndroidPath
import kotlin.math.roundToInt
import kotlin.math.sin

// --- Helper class for programmatically generated low-frequency cabin noise ---
class EngineSoundManager {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    fun start() {
        if (isPlaying) return
        isPlaying = true
        Thread {
            val sampleRate = 44100
            val numSamples = 44100
            val generatedSnd = ByteArray(2 * numSamples)
            
            // Mix 80Hz + 40Hz sub-carrier waves with white noise for realistic cabin rumble
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val angle1 = 2.0 * Math.PI * 80.0 * t
                val angle2 = 2.0 * Math.PI * 40.0 * t
                val sampleValue = (sin(angle1) * 0.7 + sin(angle2) * 0.2 + (Math.random() - 0.5) * 0.1)
                
                val valInt = (sampleValue * 32767).toInt().toShort()
                generatedSnd[2 * i] = (valInt.toInt() and 0x00ff).toByte()
                generatedSnd[2 * i + 1] = ((valInt.toInt() and 0xff00) ushr 8).toByte()
            }

            try {
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    generatedSnd.size,
                    AudioTrack.MODE_STATIC
                )
                audioTrack?.write(generatedSnd, 0, generatedSnd.size)
                audioTrack?.setLoopPoints(0, numSamples, -1)
                audioTrack?.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }
}

@Composable
fun InFlightScreen(
    viewModel: InFlightViewModel,
    onLandingCompleted: (destination: String) -> Unit,
    onExitFlight: () -> Unit
) {
    val originAirport by viewModel.originAirport.collectAsState()
    val destAirport by viewModel.destAirport.collectAsState()
    val routeDetails by viewModel.routeDetails.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var sheetExpanded by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(false) }

    val soundManager = remember { EngineSoundManager() }

    // --- Audio Hum Control ---
    DisposableEffect(soundEnabled) {
        if (soundEnabled) {
            soundManager.start()
        } else {
            soundManager.stop()
        }
        onDispose {
            soundManager.stop()
        }
    }

    // --- Screen Wake Lock ---
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // --- Lifecycle Focus Observer ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.pauseTimer()
            } else if (event == Lifecycle.Event.ON_START) {
                viewModel.startTimer()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Midnight)
    ) {
        // --- Layer 1: Background 3D Engine Placeholder / Tactical Radar ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Border.copy(alpha = 0.25f)
            val strokeWidth = 1.dp.toPx()

            // Draw circular radar sweep lines
            val center = Offset(size.width / 2f, size.height / 2.2f)
            for (r in 1..4) {
                drawCircle(
                    color = gridColor,
                    radius = (size.width / 5f) * r,
                    center = center,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )
            }

            // Draw intersecting angular cross-hairs
            drawLine(
                color = gridColor,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = gridColor,
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = strokeWidth
            )

            // Draw a minimal great-circle flight path arc
            val startOffset = Offset(size.width * 0.2f, center.y * 1.3f)
            val endOffset = Offset(size.width * 0.8f, center.y * 0.7f)
            val controlOffset = Offset(size.width * 0.5f, center.y * 0.5f)

            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(startOffset.x, startOffset.y)
                quadraticTo(controlOffset.x, controlOffset.y, endOffset.x, endOffset.y)
            }

            // Muted total route line
            drawPath(
                path = path,
                color = Border,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Active path flown (drawn as solid Amber curve up to current progress)
            val pathMeasure = android.graphics.PathMeasure(path.asAndroidPath(), false)
            val activePath = androidx.compose.ui.graphics.Path()
            if (pathMeasure.length > 0f) {
                pathMeasure.getSegment(0f, pathMeasure.length * uiState.progress, activePath.asAndroidPath(), true)
                drawPath(
                    path = activePath,
                    color = Amber,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw current airplane coordinate position dot
                val pos = FloatArray(2)
                pathMeasure.getPosTan(pathMeasure.length * uiState.progress, pos, null)
                drawCircle(
                    color = Amber,
                    radius = 5.dp.toPx(),
                    center = Offset(pos[0], pos[1])
                )
                drawCircle(
                    color = Amber.copy(alpha = 0.3f),
                    radius = 12.dp.toPx(),
                    center = Offset(pos[0], pos[1])
                )
            }
        }

        // --- Live Cockpit Coordinates Overlay ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp, top = 100.dp)
        ) {
            TelemetryText("G-SPD", "${uiState.speedKmh} KM/H")
            Spacer(modifier = Modifier.height(16.dp))
            TelemetryText("ALT", "${uiState.altitudeMeters} M")
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp, top = 100.dp),
            horizontalAlignment = Alignment.End
        ) {
            val formattedLat = "%.4f° %s".format(kotlin.math.abs(uiState.currentLat), if (uiState.currentLat >= 0) "N" else "S")
            val formattedLon = "%.4f° %s".format(kotlin.math.abs(uiState.currentLon), if (uiState.currentLon >= 0) "E" else "W")
            TelemetryText("LAT", formattedLat)
            Spacer(modifier = Modifier.height(16.dp))
            TelemetryText("LON", formattedLon)
        }

        // --- Layer 2: Cockpit HUD Top Bar controls ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exit Flight Button
            IconButton(
                onClick = onExitFlight,
                modifier = Modifier
                    .size(40.dp)
                    .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Abort Flight",
                    tint = OffWhite
                )
            }

            // Right-side controls row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip Flight Button (for debugging)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                        .clickable { viewModel.skipFlight() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ">>",
                        color = Amber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Pause / Resume Focus Button
                IconButton(
                    onClick = {
                        if (uiState.isRunning) {
                            viewModel.pauseTimer()
                        } else {
                            viewModel.startTimer()
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = if (uiState.isRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = "Pause / Resume Flight",
                        tint = if (uiState.isRunning) OffWhite else Amber
                    )
                }

                // Settings gear button
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Midnight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Flight Instruments",
                        tint = OffWhite
                    )
                }
            }
        }

        // --- Flight Completed Overlay ---
        if (uiState.isCompleted) {
            val textMeasurer = rememberTextMeasurer()
            var stampLanded by remember { mutableStateOf(false) }
            val stampScale by animateFloatAsState(
                targetValue = if (stampLanded) 1f else 3f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            val stampAlpha by animateFloatAsState(
                targetValue = if (stampLanded) 1f else 0f,
                animationSpec = tween(300)
            )

            // Compute rank and styling details based on focus duration
            val durationHours = viewModel.durationMin / 60.0
            val (rank, inkColor) = when {
                durationHours >= 8.0 -> Pair("GLOBETROTTER", Amber) // Gold/Amber
                durationHours >= 4.0 -> Pair("COMMANDER", Color(0xFF2563EB)) // Cobalt Blue
                durationHours >= 2.0 -> Pair("CAPTAIN", Color(0xFF10B981)) // Emerald Green
                else -> Pair("CO-PILOT", Color(0xFF94A3B8)) // Slate Grey
            }

            val currentDateStr = remember {
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(java.util.Date()).uppercase()
            }

            LaunchedEffect(Unit) {
                delay(300)
                stampLanded = true
                // Haptic touchdown feedback
                try {
                    val view = context as? android.app.Activity
                    view?.window?.decorView?.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Particle sweep simulator
            val particles = remember { mutableStateListOf<CelebrationParticle>() }
            val maxParticles = when (rank) {
                "GLOBETROTTER" -> 100
                "COMMANDER" -> 60
                "CAPTAIN" -> 30
                else -> 12
            }

            LaunchedEffect(Unit) {
                val random = java.util.Random()
                while (true) {
                    withFrameMillis {
                        val iterator = particles.iterator()
                        while (iterator.hasNext()) {
                            val p = iterator.next()
                            p.x += p.vx
                            p.y += p.vy
                            p.alpha -= p.decay
                            if (p.alpha <= 0f) {
                                iterator.remove()
                            }
                        }

                        if (particles.size < maxParticles) {
                            val angle = random.nextDouble() * 2.0 * Math.PI
                            val speed = 1f + random.nextFloat() * 3f
                            particles.add(
                                CelebrationParticle(
                                    x = 0f,
                                    y = 0f,
                                    vx = (kotlin.math.cos(angle) * speed).toFloat(),
                                    vy = (kotlin.math.sin(angle) * speed).toFloat(),
                                    size = 3f + random.nextFloat() * 6f,
                                    color = inkColor,
                                    alpha = 1f,
                                    decay = 0.006f + random.nextFloat() * 0.008f
                                )
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Midnight.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                // Background Dynamic Canvas Particle VFX
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerOffset = Offset(size.width / 2f, size.height / 2.3f)
                    particles.forEach { p ->
                        drawCircle(
                            color = p.color.copy(alpha = p.alpha),
                            radius = p.size,
                            center = Offset(centerOffset.x + p.x, centerOffset.y + p.y)
                        )
                    }
                }

                // Celebration Card Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .background(DeepNavy, RoundedCornerShape(20.dp))
                        .border(1.dp, Border, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "FLIGHT CONCLUDED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = Haze
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 1. Vintage Passport Stamp Canvas
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = stampScale,
                                    scaleY = stampScale,
                                    alpha = stampAlpha
                                )
                        ) {
                            val strokeWidth = 3.dp.toPx()
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
                                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
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
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                    // Surrounding dots
                                    val count = 16
                                    for (i in 0 until count) {
                                        val rad = Math.toRadians((i * (360.0 / count)))
                                        val dotPt = Offset(
                                            center.x + (w * 0.38f) * kotlin.math.cos(rad).toFloat(),
                                            center.y + (w * 0.38f) * kotlin.math.sin(rad).toFloat()
                                        )
                                        drawCircle(
                                            color = inkColor.copy(alpha = 0.8f),
                                            radius = 2.dp.toPx(),
                                            center = dotPt
                                        )
                                    }
                                }
                            }

                            // Draw Large Destination IATA in Center
                            val iataResult = textMeasurer.measure(
                                text = viewModel.destIata,
                                style = TextStyle(
                                    color = inkColor.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            drawText(
                                textLayoutResult = iataResult,
                                topLeft = Offset(
                                    center.x - iataResult.size.width / 2f,
                                    center.y - iataResult.size.height / 2f - 10.dp.toPx()
                                )
                            )

                            // Draw Stamp Date above IATA
                            val dateResult = textMeasurer.measure(
                                text = currentDateStr,
                                style = TextStyle(
                                    color = inkColor.copy(alpha = 0.6f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            drawText(
                                textLayoutResult = dateResult,
                                topLeft = Offset(
                                    center.x - dateResult.size.width / 2f,
                                    center.y - 28.dp.toPx()
                                )
                            )

                            // Draw Earned Rank below IATA
                            val rankResult = textMeasurer.measure(
                                text = rank,
                                style = TextStyle(
                                    color = inkColor.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            )
                            drawText(
                                textLayoutResult = rankResult,
                                topLeft = Offset(
                                    center.x - rankResult.size.width / 2f,
                                    center.y + 16.dp.toPx()
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. Monospaced Flight Telemetry Receipt Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Midnight.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "OFFICIAL FLIGHT LOG",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Haze,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        TelemetryRow("FLIGHT NO.", viewModel.flightNumber)
                        TelemetryRow("ORIGIN", "${originAirport?.municipality ?: "DEP"} (${originAirport?.iataCode ?: "---"})")
                        TelemetryRow("DESTINATION", "${destAirport?.municipality ?: "ARR"} (${viewModel.destIata})")
                        TelemetryRow("FOCUS MINUTES", "${viewModel.durationMin} MINS")
                        TelemetryRow("CREW RANK", rank)
                        TelemetryRow("STATUS", "ARRIVED / STAMP SECURED")
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Complete Flight Action Button
                    Button(
                        onClick = { onLandingCompleted(viewModel.destIata) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = inkColor, contentColor = Midnight)
                    ) {
                        Text(
                            text = "CONFIRM ARRIVAL",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }
        }

        // --- Layer 3: Draggable / Swipeable Bottom Sheet ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .animateContentSize()
                .background(
                    color = Midnight,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .border(
                    width = 1.dp,
                    color = Border,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .clickable { sheetExpanded = !sheetExpanded }
                .padding(horizontal = Spacing.Large, vertical = 16.dp)
        ) {
            // Drag handle representation
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Border, RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- Collapsed Info Summary (Always Visible) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "TIME REMAINING", style = MaterialTheme.typography.labelSmall, color = Haze)
                    Text(
                        text = formatRemainingTime(uiState.timeRemainingSeconds),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = OffWhite
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "SPD", style = MaterialTheme.typography.labelSmall, color = Haze)
                    Text(
                        text = "${uiState.speedKmh} km/h",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = OffWhite
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "PROGRESS", style = MaterialTheme.typography.labelSmall, color = Haze)
                    Text(
                        text = "${(uiState.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Amber
                    )
                }
            }

            // --- Expanded Info Panel (Visible on Expand) ---
            if (sheetExpanded) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(24.dp))

                // Route Details Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${originAirport?.iataCode ?: "---"} ──✈── ${destAirport?.iataCode ?: "---"}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = OffWhite
                    )
                    Text(
                        text = viewModel.flightNumber,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Amber
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Linear flight progress track bar
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Amber,
                    trackColor = Border,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Flight telemetry statistics block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryBlock("ELAPSED", formatRemainingTime(uiState.timeElapsedSeconds))
                    TelemetryBlock("TOTAL", "${viewModel.durationMin} MIN")
                    TelemetryBlock("ALTITUDE", "${uiState.altitudeMeters} M")
                }

                Spacer(modifier = Modifier.height(20.dp))

                val distanceLeft = routeDetails?.distanceKm?.let { dist ->
                    (dist * (1f - uiState.progress)).roundToInt()
                } ?: 0

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryBlock("DIST LEFT", "$distanceLeft KM")
                    val formattedLat = "%.4f° %s".format(kotlin.math.abs(uiState.currentLat), if (uiState.currentLat >= 0) "N" else "S")
                    val formattedLon = "%.4f° %s".format(kotlin.math.abs(uiState.currentLon), if (uiState.currentLon >= 0) "E" else "W")
                    TelemetryBlock("LATITUDE", formattedLat)
                    TelemetryBlock("LONGITUDE", formattedLon)
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(24.dp))

                // Tactical mini progress map
                Text(
                    text = "TACTICAL FLIGHT SEGMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Haze,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Slate, RoundedCornerShape(16.dp))
                        .border(1.dp, Border, RoundedCornerShape(16.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        val trackColor = Border.copy(alpha = 0.5f)
                        val startPt = Offset(size.width * 0.15f, size.height * 0.5f)
                        val endPt = Offset(size.width * 0.85f, size.height * 0.5f)

                        // Path trajectory
                        drawLine(
                            color = trackColor,
                            start = startPt,
                            end = endPt,
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        // Departure node indicator
                        drawCircle(color = Haze, radius = 6.dp.toPx(), center = startPt)
                        // Destination node indicator
                        drawCircle(color = Haze, radius = 6.dp.toPx(), center = endPt)

                        // Current airplane progress locator dot
                        val progressOffset = Offset(
                            startPt.x + (endPt.x - startPt.x) * uiState.progress,
                            startPt.y
                        )
                        drawCircle(color = Amber, radius = 8.dp.toPx(), center = progressOffset)
                        drawCircle(
                            color = Amber.copy(alpha = 0.25f),
                            radius = 16.dp.toPx(),
                            center = progressOffset
                        )
                    }
                    Text(
                        text = originAirport?.iataCode ?: "DEP",
                        color = OffWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp)
                    )
                    Text(
                        text = destAirport?.iataCode ?: "ARR",
                        color = OffWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)
                    )
                }
            }
        }
    }

    // --- Layer 2 Settings modal Dialog overlay ---
    if (showSettings) {
        Dialog(onDismissRequest = { showSettings = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(DeepNavy, RoundedCornerShape(20.dp))
                    .border(1.dp, Border, RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "FLIGHT INSTRUMENTS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Amber
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Cabin Hum volume sound toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Cabin Engine Hum", color = OffWhite, style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Midnight,
                            checkedTrackColor = Amber,
                            uncheckedThumbColor = Haze,
                            uncheckedTrackColor = Border
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Camera Views selectors (Mocked)
                Text(
                    text = "CAMERA MODE (MOCKED)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Haze,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cameraViews = listOf("CHASE", "COCKPIT", "FREE")
                    var selectedCamera by remember { mutableStateOf("CHASE") }
                    cameraViews.forEach { cam ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selectedCamera == cam) Amber else Slate, RoundedCornerShape(8.dp))
                                .clickable { selectedCamera = cam }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cam,
                                color = if (selectedCamera == cam) Midnight else OffWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { showSettings = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Midnight)
                ) {
                    Text(
                        text = "CLOSE",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryText(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Haze)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = OffWhite
        )
    }
}

@Composable
private fun TelemetryBlock(label: String, value: String) {
    Column(modifier = Modifier.width(96.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Haze)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = OffWhite
        )
    }
}

private fun formatRemainingTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = Haze
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = OffWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class CelebrationParticle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val color: Color,
    var alpha: Float,
    val decay: Float
)
