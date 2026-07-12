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
    onLandingCelebration: (String) -> Unit,
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
            .background(Color.Transparent) // Make background transparent to see engine
    ) {
        // --- Layer 1: Background 3D Engine Placeholder / Tactical Radar ---
        // (Removed 2D canvas, the Rust wgpu engine renders underneath this Compose layer)

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
                    .background(DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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
                        .background(DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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
                        .background(DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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
                        .background(DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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

        // --- Flight Completion Navigation Trigger ---
        LaunchedEffect(uiState.isCompleted) {
            if (uiState.isCompleted) {
                val durationHours = viewModel.durationMin / 60.0
                val rank = when {
                    durationHours >= 8.0 -> "GLOBETROTTER"
                    durationHours >= 4.0 -> "COMMANDER"
                    durationHours >= 2.0 -> "CAPTAIN"
                    else -> "CO-PILOT"
                }
                onLandingCelebration(rank)
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

                // Camera Views selectors
                Text(
                    text = "CAMERA MODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Haze,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cameraViews = listOf("FREE" to 0, "CHASE" to 1, "COCKPIT" to 2)
                    var selectedCamera by remember { mutableStateOf(1) } // Default to CHASE
                    
                    cameraViews.forEach { (camName, camMode) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selectedCamera == camMode) Amber else Slate, RoundedCornerShape(8.dp))
                                .clickable { 
                                    selectedCamera = camMode 
                                    com.example.focusflight.engine.CesiumBridge.nativeSetCameraMode(camMode)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = camName,
                                color = if (selectedCamera == camMode) Midnight else OffWhite,
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
