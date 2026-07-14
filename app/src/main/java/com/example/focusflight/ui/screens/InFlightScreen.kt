package com.example.focusflight.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.draw.alpha
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

@OptIn(ExperimentalMaterial3Api::class)
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

    // Intercept back button during flight
    BackHandler {
        // Do nothing for now to prevent accidental flight abort.
        // TODO: Show a modal asking if they really want to quit.
    }

    var showSettings by remember { mutableStateOf(false) }
    var sheetExpanded by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(false) }
    var selectedCamera by rememberSaveable { mutableStateOf(1) } // Default to CHASE

    LaunchedEffect(selectedCamera) {
        com.example.focusflight.engine.CesiumBridge.nativeSetCameraMode(selectedCamera)
    }

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

    val scaffoldState = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContainerColor = DeepNavy,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetDragHandle = {
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 16.dp)
                    .width(80.dp)
                    .height(4.dp)
                    .background(Border, RoundedCornerShape(2.dp))
            )
        },
        sheetPeekHeight = 104.dp,
        containerColor = Color.Transparent, // Restored so globe is visible
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .padding(bottom = 30.dp)
            ) {
                // --- Collapsed Info Summary (Always Visible in peek mode) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Ground Speed
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(text = "GROUND SPEED", style = MaterialTheme.typography.labelSmall, color = Haze)
                        Text(
                            text = "${uiState.speedKmh} km/h",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = OffWhite
                        )
                    }

                    // Center: Time Remaining (Stronger Visual)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "REMAINING", style = MaterialTheme.typography.labelSmall, color = Haze)
                        Text(
                            text = formatRemainingTime(uiState.timeRemainingSeconds),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            ),
                            color = Amber
                        )
                    }

                    // Right: Altitude
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(text = "ALTITUDE", style = MaterialTheme.typography.labelSmall, color = Haze)
                        Text(
                            text = "${uiState.altitudeMeters} m",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = OffWhite
                        )
                    }
                }

                // --- Expanded Info Panel ---
                Spacer(modifier = Modifier.height(30.dp))
                HorizontalDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(30.dp))

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

            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Transparent) // Make background transparent to see engine
        ) {
            // --- Layer 1: Touch Receiver for the Engine ---
            // Placed at the bottom of the Box (z-index 0) so it only receives touches
            // that were NOT consumed by foreground HUD controls like the Skip button.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        val activity = context as? android.app.Activity
                        activity?.onTouchEvent(event) ?: false
                    }
            )

            // --- Layer 2: Background 3D Engine Placeholder / Tactical Radar ---
            // (Removed 2D canvas, the Rust wgpu engine renders underneath this Compose layer)

            // --- Layer 2: Cockpit HUD Top Bar controls ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                    // Settings gear button
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.size(40.dp)
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
        }

    // --- Layer 2 Settings modal Dialog overlay ---
    if (showSettings) {
        // Full screen transparent click catcher to close modal
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { showSettings = false }
        )

        // Modal content positioned below top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp)
                .padding(horizontal = Spacing.Large)
                .background(DeepNavy, RoundedCornerShape(16.dp))
                .border(1.dp, Border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val cameraViews = listOf(
                    Triple("FREE", 0, Icons.Outlined.Explore),
                    Triple("CHASE", 1, Icons.Outlined.Visibility),
                    Triple("COCKPIT", 2, Icons.Outlined.Flight)
                )

                cameraViews.forEach { (camName, camMode, icon) ->
                    val isActive = selectedCamera == camMode
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isActive) DeepNavy else Slate, RoundedCornerShape(12.dp))
                            .border(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) Amber else Border,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedCamera = camMode
                                showSettings = false
                            }
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = camName,
                            tint = if (isActive) Amber else OffWhite,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = camName,
                            color = if (isActive) Amber else OffWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
    }

    // --- Movie Style Countdown Overlay ---
    if (uiState.timeElapsedMs < 0) {
        MovieCountdown(uiState.timeElapsedMs)
    }
}

@Composable
fun MovieCountdown(timeElapsedMs: Long) {
    if (timeElapsedMs >= 0) return
    
    // timeElapsedMs goes from -3000 to 0.
    val totalHoldMs = 3000L
    // seconds remaining: 3, 2, 1
    val secondsRemaining = kotlin.math.ceil(kotlin.math.abs(timeElapsedMs) / 1000.0).toInt()
    // fraction of the current second (0.0 to 1.0)
    val fraction = (kotlin.math.abs(timeElapsedMs) % 1000) / 1000f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        // Draw the movie-style circle and sweeping line
        Canvas(modifier = Modifier.size(200.dp)) {
            val strokeWidth = 8.dp.toPx()
            
            // Background circle
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = size.width / 2,
                style = Stroke(width = strokeWidth)
            )
            
            // Sweeping circle (like old film countdowns)
            // It sweeps 360 degrees every second
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Inner crosshairs (movie style)
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx()
            )
        }
        
        Text(
            text = secondsRemaining.toString(),
            color = Color.White,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 100.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        )
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
    return if (h > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
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
