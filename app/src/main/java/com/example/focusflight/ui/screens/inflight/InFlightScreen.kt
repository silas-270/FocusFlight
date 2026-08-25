package com.example.focusflight.ui.screens.inflight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focusflight.data.model.Airport
import kotlinx.coroutines.delay
import com.example.focusflight.ui.theme.*
import com.example.focusflight.ui.viewmodel.inflight.InFlightViewModel
import com.example.focusflight.util.AirportClock
import com.example.focusflight.util.airportClock
import com.example.focusflight.util.formatFeet
import com.example.focusflight.util.formatMiles
import com.example.focusflight.util.formatMph
import com.example.focusflight.util.kmhToMph
import com.example.focusflight.util.localDateOf
import com.example.focusflight.util.metersToFeet
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import android.content.res.Configuration
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

    var showSettings by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // Intercept back button during flight - pause and ask for confirmation instead
    // of silently doing nothing or aborting the flight outright.
    BackHandler {
        viewModel.pauseTimer()
        showExitConfirm = true
    }

    var sheetExpanded by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(false) }
    var selectedCamera by rememberSaveable { mutableStateOf(1) } // Default to CHASE

    LaunchedEffect(selectedCamera) {
        com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetCameraMode(selectedCamera)
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
                    .padding(top = 12.dp)
                    .width(80.dp)
                    .height(4.dp)
                    .background(Border, RoundedCornerShape(2.dp))
            )
        },
        sheetPeekHeight = 104.dp,
        containerColor = Color.Transparent, // Restored so globe is visible
        sheetContent = {
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val altitudeFt = metersToFeet(uiState.altitudeMeters.toDouble()).roundToInt()
            val speedMph = kmhToMph(uiState.speedKmh)
            val distanceLeftKm = routeDetails?.distanceKm?.let { it * (1f - uiState.progress) } ?: 0.0

            // Wall-clock departure/arrival, approximated per-airport from longitude
            // since no real timezone database is bundled. Captured once so it stays
            // stable across recompositions instead of drifting with "now".
            val departureEpochMs = remember { System.currentTimeMillis() - uiState.timeElapsedMs.coerceAtLeast(0) }
            val arrivalEpochMs = departureEpochMs + uiState.totalDurationSeconds * 1000
            val departureClock = originAirport?.let { origin ->
                val departureDate = localDateOf(departureEpochMs, origin.lon, origin.lat, origin.isoCountry)
                airportClock(departureEpochMs, origin.lon, origin.lat, origin.isoCountry, departureDate)
            }
            val arrivalClock = destAirport?.let { dest ->
                val departureDate = originAirport?.let { localDateOf(departureEpochMs, it.lon, it.lat, it.isoCountry) }
                    ?: localDateOf(departureEpochMs, dest.lon, dest.lat, dest.isoCountry)
                airportClock(arrivalEpochMs, dest.lon, dest.lat, dest.isoCountry, departureDate)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .padding(bottom = 30.dp)
            ) {
                // --- Collapsed Info Summary (Always Visible in peek mode) ---
                // Both branches share the same fixed-height, center-aligned Box so the
                // peek content is vertically centered in the sheet's peek window
                // identically in either orientation. The nav-bar inset is subtracted
                // since on gesture-nav devices it eats into the bottom of the nominal
                // peekHeight (104dp) without actually being visible/usable space. The
                // drag handle above this Box only has top padding now (see
                // sheetDragHandle), so simply centering within the remaining height
                // below it already centers within the whole peek card.
                val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val peekDragHandleHeight = 16.dp
                val peekContentHeight = (104.dp - peekDragHandleHeight - navBarInset).coerceAtLeast(24.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(peekContentHeight),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Ground Speed
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(text = "GROUND SPEED", style = MaterialTheme.typography.labelSmall, color = Haze)
                                Text(
                                    text = formatMph(uiState.speedKmh),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = OffWhite
                                )
                            }

                            // Center: Time Remaining (Stronger Visual)
                            Text(
                                text = formatRemainingTime(uiState.timeRemainingSeconds),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                ),
                                color = Amber,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )

                            // Right: Altitude
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "ALTITUDE", style = MaterialTheme.typography.labelSmall, color = Haze)
                                Text(
                                    text = formatFeet(uiState.altitudeMeters),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = OffWhite
                                )
                            }
                        }
                    } else {
                        // Portrait: speed/altitude move into the expanded panel below, so the
                        // always-visible peek is just the one number that matters at a glance.
                        Text(
                            text = formatRemainingTime(uiState.timeRemainingSeconds),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            ),
                            color = Amber
                        )
                    }
                }

                // --- Expanded Info Panel ---
                HorizontalDivider(color = Border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(30.dp))

                if (isLandscape) {
                    // Wide screen: route hero + flight-time bar on the left, instrument
                    // cluster on the right. Rather than stretching either side to match
                    // the other (which just inserts gaps), the instrument faces are
                    // sized to a fixed height tuned to equal the left column's height.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Large)
                    ) {
                        Column(
                            modifier = Modifier.weight(1.2f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FlightRouteHero(
                                originIata = originAirport?.iataCode ?: "---",
                                destIata = destAirport?.iataCode ?: "---",
                                departureClock = departureClock,
                                arrivalClock = arrivalClock,
                                progress = uiState.progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlightTimeBar(
                                elapsedSec = uiState.timeElapsedSeconds,
                                totalSec = uiState.totalDurationSeconds,
                                distanceLeftKm = distanceLeftKm,
                                totalDistanceKm = routeDetails?.distanceKm ?: 0.0,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Border)
                        )

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AltitudeGauge(altitudeFt = altitudeFt, faceHeight = LandscapeInstrumentFaceHeight, modifier = Modifier.weight(1f))
                            SpeedInstrument(speedMph = speedMph, faceHeight = LandscapeInstrumentFaceHeight, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    // Portrait: everything stacked, speed/altitude reclaimed here as instruments.
                    Column {
                        FlightRouteHero(
                            originIata = originAirport?.iataCode ?: "---",
                            destIata = destAirport?.iataCode ?: "---",
                            departureClock = departureClock,
                            arrivalClock = arrivalClock,
                            progress = uiState.progress,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            AltitudeGauge(altitudeFt = altitudeFt, modifier = Modifier.weight(1f))
                            SpeedInstrument(speedMph = speedMph, modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        FlightTimeBar(
                            elapsedSec = uiState.timeElapsedSeconds,
                            totalSec = uiState.totalDurationSeconds,
                            distanceLeftKm = distanceLeftKm,
                            totalDistanceKm = routeDetails?.distanceKm ?: 0.0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                    .padding(start = Spacing.Large, end = Spacing.Large, top = Spacing.Large, bottom = Spacing.Medium),
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

                    // Flight settings button (camera view + pause/leave slider)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeepNavy)
                            .clickable { showSettings = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AirplanemodeActive,
                            contentDescription = "Flight Settings",
                            tint = OffWhite,
                            modifier = Modifier.size(20.dp)
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp)
                .padding(horizontal = Spacing.Large)
                .background(DeepNavy, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            .background(
                                if (isActive) Amber.copy(alpha = 0.15f) else Slate,
                                RoundedCornerShape(12.dp)
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

            SlideToPauseControl(
                onSlideCompleted = {
                    showSettings = false
                    viewModel.pauseTimer()
                    showExitConfirm = true
                }
            )
        }
    }
    }

    // --- Layer 2 Exit/Pause confirmation Dialog overlay ---
    if (showExitConfirm) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim and card are siblings (not nested) so a tap on the card can't
            // also fall through to the scrim's dismiss handler underneath it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        showExitConfirm = false
                        viewModel.startTimer()
                    }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = Spacing.Large)
                    .fillMaxWidth()
                    .background(DeepNavy, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LEAVE FLIGHT?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = OffWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your progress is saved. You can resume this flight later from the Hub.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Haze,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Slate, RoundedCornerShape(12.dp))
                            .clickable {
                                showExitConfirm = false
                                viewModel.startTimer()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "RESUME",
                            color = OffWhite,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Amber, RoundedCornerShape(12.dp))
                            .clickable {
                                showExitConfirm = false
                                onExitFlight()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LEAVE",
                            color = Midnight,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
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
private fun SlideToPauseControl(onSlideCompleted: () -> Unit) {
    val thumbSizeDp = 48.dp
    val trackHeightDp = 56.dp
    val trackShape = RoundedCornerShape(12.dp)
    val commitThreshold = 0.6f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeightDp)
            .clip(trackShape)
            .background(Slate)
    ) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbSizePx = with(density) { thumbSizeDp.toPx() }
        val maxOffsetPx = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)

        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        var committed by remember { mutableStateOf(false) }
        val animatedOffsetPx by animateFloatAsState(
            targetValue = dragOffsetPx,
            label = "slideToPauseOffset"
        )
        val thumbScale by animateFloatAsState(
            targetValue = if (dragging) 1.08f else 1f,
            label = "slideToPauseThumbScale"
        )
        val progress = if (maxOffsetPx > 0f) (animatedOffsetPx / maxOffsetPx).coerceIn(0f, 1f) else 0f
        val armed = progress >= commitThreshold

        // Trailing fill behind the thumb, growing with drag progress.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { (animatedOffsetPx + thumbSizePx / 2f).toDp() })
                .clip(trackShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Amber.copy(alpha = 0.28f), Amber.copy(alpha = 0.12f))
                    )
                )
        )

        Text(
            text = if (armed) "RELEASE TO PAUSE" else "SLIDE TO PAUSE",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Haze.copy(alpha = 1f - progress * 0.85f),
            modifier = Modifier.align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                .padding(4.dp)
                .size(thumbSizeDp)
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .clip(RoundedCornerShape(10.dp))
                .background(if (armed) Amber else OffWhite)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            if (dragOffsetPx > maxOffsetPx * commitThreshold) {
                                dragOffsetPx = maxOffsetPx
                                if (!committed) {
                                    committed = true
                                    onSlideCompleted()
                                }
                            } else {
                                dragOffsetPx = 0f
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            dragOffsetPx = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, maxOffsetPx)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (armed) {
                    Icons.Outlined.Pause
                } else {
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight
                },
                contentDescription = "Slide to pause",
                tint = Midnight,
                modifier = Modifier.size(if (armed) 20.dp else 24.dp)
            )
        }
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

// --- Route hero: just the two airport codes bridged by a progress track, styled as
// the same kind of widget as the instrument cards below it. The travelling plane
// tilts nose-up on departure, levels off for cruise, and noses down on approach. ---
@Composable
private fun FlightRouteHero(
    originIata: String,
    destIata: String,
    departureClock: AirportClock?,
    arrivalClock: AirportClock?,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Slate.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = originIata,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                ),
                color = OffWhite
            )
            if (departureClock != null) {
                Text(
                    text = departureClock.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Haze
                )
            }
        }

        RouteProgressTrack(
            progress = progress,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = destIata,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                ),
                color = OffWhite
            )
            if (arrivalClock != null) {
                Text(
                    text = if (arrivalClock.dayOffset != 0) {
                        "${arrivalClock.label} ${if (arrivalClock.dayOffset > 0) "+" else ""}${arrivalClock.dayOffset}"
                    } else {
                        arrivalClock.label
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Haze
                )
            }
        }
    }
}

// --- Dashed route line with a plane travelling along it, amber ahead of the
// aircraft (flown distance), dashed grey behind (remaining). The plane's pitch
// reflects the flight phase: nose-up climbing out, level at cruise, nose-down
// on approach. ---
@Composable
private fun RouteProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.height(28.dp)) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val clampedProgress = progress.coerceIn(0f, 1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height / 2
            val flownEndX = size.width * clampedProgress
            drawLine(
                color = Amber,
                start = Offset(0f, y),
                end = Offset(flownEndX, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Border,
                start = Offset(flownEndX, y),
                end = Offset(size.width, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )
        }

        val planeOffsetDp = with(density) { (trackWidthPx * clampedProgress).toDp() }
        val planePitch = when {
            clampedProgress < 0.08f -> 45f  // climbing out, nose up
            clampedProgress > 0.9f -> 135f  // on approach, nose down
            else -> 90f                     // level cruise
        }
        val animatedPitch by animateFloatAsState(targetValue = planePitch, label = "planePitch")
        Icon(
            imageVector = Icons.Outlined.Flight,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (planeOffsetDp - 12.dp).coerceAtLeast(0.dp))
                .size(24.dp)
                .graphicsLayer { rotationZ = animatedPitch }
        )
    }
}

// Shared frame for the two instrument cards so they always match in size: a face
// that fills the card's width (so its margin matches the card's own padding on
// every side) at a given height, a spacer, then an icon+label caption underneath.
private val InstrumentFaceHeight = 108.dp
private val LandscapeInstrumentFaceHeight = 124.dp

@Composable
private fun InstrumentCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    faceHeight: Dp = InstrumentFaceHeight,
    face: @Composable BoxScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(Slate.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(faceHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(Midnight.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
            content = face
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = Amber, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                color = Haze
            )
        }
    }
}

// --- Altimeter tape: a vertical scrolling ruler of altitude values, like a real
// primary flight display. The current reading sits fixed at the centre, enlarged,
// amber, with a caret pointing at it; the rest of the tape scrolls past behind it. ---
@Composable
private fun AltitudeGauge(altitudeFt: Int, modifier: Modifier = Modifier, faceHeight: Dp = InstrumentFaceHeight) {
    val stepFt = 500
    val exactIndex = altitudeFt / stepFt.toFloat()
    val animatedIndex by animateFloatAsState(targetValue = exactIndex, label = "altitudeTape")
    val density = LocalDensity.current
    val rowHeightDp = 22.dp
    val rowHeightPx = with(density) { rowHeightDp.toPx() }

    InstrumentCard(label = "ALTITUDE", icon = Icons.Outlined.Height, modifier = modifier, faceHeight = faceHeight) {
        val baseIdx = kotlin.math.floor(animatedIndex).toInt()
        for (offset in -3..3) {
            val idx = baseIdx + offset
            val offsetSteps = animatedIndex - idx
            val isCenter = kotlin.math.abs(offsetSteps) < 0.5f
            val distanceFromCenter = kotlin.math.abs(offsetSteps)
            val sizeFraction = (distanceFromCenter / 3f).coerceIn(0f, 1f)
            val fontSizeSp = 19f - 8f * sizeFraction
            val rowAlpha = (1f - distanceFromCenter / 2.4f).coerceIn(0f, 1f)

            Text(
                text = if (isCenter) {
                    String.format(java.util.Locale.US, "%,d ft", altitudeFt)
                } else {
                    String.format(java.util.Locale.US, "%,d", idx * stepFt)
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = fontSizeSp.sp,
                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                ),
                color = if (isCenter) Amber else Haze.copy(alpha = rowAlpha),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, (offsetSteps * rowHeightPx).roundToInt()) }
            )
        }

        // Fade the tape toward the top/bottom edges, like a picker wheel losing focus.
        // Uses the face's own background color so the fade blends in rather than
        // reading as a separate dark shadow.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Midnight.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Transparent,
                            Midnight.copy(alpha = 0.35f)
                        )
                    )
                )
        )

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(16.dp)
        )
    }
}

// --- Speedometer: a plane that vibrates as speed climbs, with amber wind streaks
// racing past behind it, instead of a static "SPEED" figure. ---
@Composable
private fun SpeedInstrument(speedMph: Int, modifier: Modifier = Modifier, faceHeight: Dp = InstrumentFaceHeight) {
    val maxSpeedMph = 600f
    val intensity = (speedMph / maxSpeedMph).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "speedFx")
    val streamPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1400 - (intensity * 900)).toInt().coerceAtLeast(300),
                easing = LinearEasing
            )
        ),
        label = "streamPhase"
    )
    val jitter by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jitter"
    )
    val laneSeeds = remember { List(5) { kotlin.random.Random(it * 91 + 7).nextFloat() } }

    InstrumentCard(label = "SPEED", icon = Icons.Outlined.Speed, modifier = modifier, faceHeight = faceHeight) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (intensity > 0.02f) {
                laneSeeds.forEachIndexed { index, seed ->
                    val laneY = size.height * (0.2f + 0.6f * (index / (laneSeeds.size - 1f)))
                    val phase = (streamPhase + seed) % 1f
                    val x = size.width * (1f - phase)
                    val len = 10.dp.toPx() + 16.dp.toPx() * intensity
                    drawLine(
                        color = Amber.copy(alpha = 0.25f + 0.35f * intensity),
                        start = Offset(x, laneY),
                        end = Offset((x - len).coerceAtLeast(0f), laneY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Outlined.Flight,
            contentDescription = null,
            tint = OffWhite,
            modifier = Modifier
                .align(BiasAlignment(0f, -0.44f))
                .size(28.dp)
                .graphicsLayer {
                    rotationZ = 90f
                    translationX = jitter * 5f * intensity
                    translationY = jitter * 3f * intensity
                }
        )

        Text(
            text = "$speedMph MPH",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = Amber,
            modifier = Modifier.align(BiasAlignment(0f, 0.47f))
        )
    }
}

// --- Flight time as a fuel-gauge-style fill bar; tap it to swap the readout for
// miles left / total miles, with a small swap glyph hinting it's interactive. ---
@Composable
private fun FlightTimeBar(
    elapsedSec: Long,
    totalSec: Long,
    distanceLeftKm: Double,
    totalDistanceKm: Double,
    modifier: Modifier = Modifier
) {
    var showDistance by remember { mutableStateOf(false) }
    val fraction = if (totalSec > 0) (elapsedSec.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "flightTimeBar")

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Slate.copy(alpha = 0.5f))
            .clickable { showDistance = !showDistance }
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (showDistance) Icons.Outlined.Straighten else Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showDistance) "DISTANCE" else "FLIGHT TIME",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                    color = Haze
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.SwapHoriz,
                    contentDescription = "Tap to switch units",
                    tint = Haze.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = if (showDistance) {
                    "${formatMiles(distanceLeftKm)} / ${formatMiles(totalDistanceKm)}"
                } else {
                    "${formatRemainingTime(elapsedSec)} / ${formatRemainingTime(totalSec)}"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = OffWhite
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Midnight.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Brush.horizontalGradient(listOf(Amber.copy(alpha = 0.7f), Amber)))
            )
        }
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
