package com.example.focusflight.ui.screens.inflight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.Route
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focusflight.R
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.ui.components.CaptionLabel
import com.example.focusflight.ui.components.BadgeStyle
import com.example.focusflight.ui.components.BadgeVariant
import com.example.focusflight.ui.components.FocusBadge
import com.example.focusflight.ui.components.ButtonSize
import com.example.focusflight.ui.components.ButtonVariant
import com.example.focusflight.ui.components.FocusButton
import com.example.focusflight.ui.components.ModalTitle
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.components.SpeedMotionLayer
import com.example.focusflight.data.network.NetworkMode
import com.example.focusflight.domain.NetworkNotice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.focusflight.ui.theme.*
import com.example.focusflight.ui.viewmodel.inflight.InFlightState
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
import androidx.compose.ui.graphics.asAndroidPath
import com.example.focusflight.audio.EngineSoundEngine
import com.example.focusflight.engine.live.CesiumLiveJniBridge
import kotlin.math.roundToInt

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
    val engineSoundEnabled by viewModel.isEngineSoundEnabled.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    // Scenic mode: clears the HUD down to a glass settings button and a timer-only
    // pill, so the view isn't cluttered by the full flight-info sheet.
    var scenicMode by remember { mutableStateOf(false) }

    // Measured live from the standard-mode peek timer's actual on-screen position
    // (see its onGloballyPositioned below) so the scenic-mode pill can reproduce the
    // exact same gap to the bottom edge and the timer never jumps when toggling modes.
    var timerBottomInsetFromScreen by remember { mutableStateOf(ScenicPillBottomPadding) }

    // True from the moment the timer reads 00:00 until the arrival screen takes over: the
    // ~3 s end hold the ViewModel plays before it lands the flight (and the instant after a
    // skip). Leaving in that window would exit without logging a flight that has already
    // been flown, so the HUD shows "LANDING…" and leave/settings stand down. Collected as its
    // own flag, like CountdownOverlayHost, so this scope doesn't recompose on every tick.
    val landing by remember(viewModel) {
        viewModel.uiState.map { it.isLanding() }.distinctUntilChanged()
    }.collectAsState(initial = viewModel.uiState.value.isLanding())

    // Intercept back button during flight - pause and ask for confirmation instead
    // of silently doing nothing or aborting the flight outright. Swallowed while landing
    // (see [landing]), since the only way out then is the arrival screen.
    BackHandler {
        if (landing) return@BackHandler
        viewModel.pauseTimer()
        showExitConfirm = true
    }

    LaunchedEffect(landing) {
        if (landing) showSettings = false
    }

    // Defaults to Chase for a brand-new flight; synced to the restored mode when resuming
    // one that was previously saved with a camera pose (see InFlightViewModel.init).
    var selectedCamera by rememberSaveable { mutableStateOf(uiState.restoredCameraMode ?: 1) }
    // The restored mode can arrive after the first composition (a CHALLENGE session reads its
    // paused flight from Room), so seeding the picker once isn't enough: it would show CHASE
    // while the engine flies the restored view, and tapping CHASE would then do nothing. Synced
    // when it lands, unless the pilot has already picked a view themselves in the meantime.
    var cameraPickedByUser by rememberSaveable { mutableStateOf(false) }
    val restoredCameraMode by remember(viewModel) {
        viewModel.uiState.map { it.restoredCameraMode }.distinctUntilChanged()
    }.collectAsState(initial = viewModel.uiState.value.restoredCameraMode)
    LaunchedEffect(restoredCameraMode) {
        val restored = restoredCameraMode
        if (restored != null && !cameraPickedByUser) selectedCamera = restored
    }

    // Wall-clock moment this session effectively departed: now minus the elapsed time the
    // flight started (or resumed) with. Captured once the ViewModel has loaded the session -
    // before that the state still reads 0 elapsed, even for a resumed flight - and held at
    // screen level so hiding the sheet in scenic mode doesn't drop it. Only the departure
    // clock uses it; the arrival clock is re-derived from the remaining time (see the sheet).
    var departureEpochMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(viewModel) {
        val loaded = viewModel.uiState.first { it.totalDurationSeconds > 0 }
        departureEpochMs = System.currentTimeMillis() - loaded.timeElapsedMs.coerceAtLeast(0)
    }
    val selectedMapStyle by viewModel.mapStyle.collectAsState()
    // What the globe really shows: the pilot's choice, or the offline map while offline.
    val effectiveMapStyle by viewModel.effectiveMapStyle.collectAsState()
    val networkMode by viewModel.networkMode.collectAsState()
    val networkNotice by viewModel.networkNotice.collectAsState()

    LaunchedEffect(selectedCamera) {
        com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetCameraMode(selectedCamera)
    }

    LaunchedEffect(effectiveMapStyle) {
        com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetMapStyle(effectiveMapStyle)
    }

    val routeLineMode by viewModel.routeLineMode.collectAsState()

    LaunchedEffect(routeLineMode) {
        com.example.focusflight.engine.live.CesiumLiveJniBridge.nativeSetRouteLineMode(
            mode = routeLineMode,
            behindNm = com.example.focusflight.engine.live.CesiumLiveJniBridge.DEFAULT_ROUTE_LINE_BEHIND_NM,
            aheadNm = com.example.focusflight.engine.live.CesiumLiveJniBridge.DEFAULT_ROUTE_LINE_AHEAD_NM
        )
    }

    val engineSoundEngine = remember { EngineSoundEngine() }

    // --- Engine Sound Control ---
    // Foreground-only, like the wake lock below: stopped/restarted by the lifecycle observer on
    // ON_STOP/ON_START, not kept alive in the background (no foreground Service exists for that).
    DisposableEffect(engineSoundEnabled) {
        if (engineSoundEnabled) {
            engineSoundEngine.start()
        } else {
            engineSoundEngine.stop()
        }
        onDispose {
            engineSoundEngine.stop()
        }
    }

    // Collected rather than read as state: engine power changes on every 33 ms tick, so keying a
    // LaunchedEffect on it would start a fresh coroutine 30 times a second, and collecting it into
    // Compose state would drag the whole screen into recomposing at that rate for a value nothing
    // on screen draws. Handing it straight to the audio thread costs neither.
    LaunchedEffect(engineSoundEngine) {
        viewModel.enginePower.collect { engineSoundEngine.setEnginePower(it) }
    }

    // Screen wake lock: owned by CesiumGameActivity, scoped to the flight-session routes.
    val context = LocalContext.current


    // --- Lifecycle Focus Observer ---
    val lifecycleOwner = LocalLifecycleOwner.current
    // rememberUpdatedState so the observer below (created once per lifecycleOwner, not per
    // recomposition) always reads the latest toggle instead of whatever it was when first attached.
    val currentEngineSoundEnabled by rememberUpdatedState(engineSoundEnabled)
    // The leave-flight modal pauses the timer on purpose; returning from the background must not
    // silently restart it behind that modal. Only RESUME ends a user-initiated pause.
    val currentShowExitConfirm by rememberUpdatedState(showExitConfirm)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.pauseTimer()
                viewModel.saveCameraState()
                engineSoundEngine.stop()
            } else if (event == Lifecycle.Event.ON_START) {
                if (!currentShowExitConfirm) viewModel.startTimer()
                if (currentEngineSoundEnabled) {
                    engineSoundEngine.start()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState()

    // Scenic mode force-collapses the sheet before it's hidden (sheetPeekHeight
    // drops to 0 below), in case the user toggles it while the sheet is expanded.
    LaunchedEffect(scenicMode) {
        if (scenicMode) {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContainerColor = DeepNavy,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetMaxWidth = SheetMaxWidth,
        sheetSwipeEnabled = !scenicMode,
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
        sheetPeekHeight = if (scenicMode) 0.dp else 104.dp,
        containerColor = Color.Transparent, // Restored so globe is visible
        sheetContent = sheetContent@{
            if (scenicMode) return@sheetContent
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            // The instrument cluster lives in the expanded panel, entirely below the peek
            // window. Its wind-streak animation is frame-driven, so leaving it running
            // while the sheet is collapsed burned a full display-refresh redraw of the
            // whole overlay to animate pixels nobody can see. targetValue is included so
            // the effect is already alive by the time the panel finishes sliding open.
            val instrumentsVisible =
                scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded ||
                    scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
            val altitudeFt = metersToFeet(uiState.altitudeMeters.toDouble()).roundToInt()
            val speedMph = kmhToMph(uiState.speedKmh)
            val distanceLeftKm = routeDetails?.distanceKm?.let { it * (1f - uiState.progress) } ?: 0.0

            // Wall-clock departure/arrival in each airport's local time (see util/FlightClock.kt).
            // Arrival is now + time remaining rather than departure + total, so a pause (the
            // app backgrounded, the leave dialog open) pushes it later the way it really does.
            // Keyed to the minute the label shows, so the 30 Hz recomposition of this sheet
            // doesn't redo the time-zone work on every tick.
            val departureMs = departureEpochMs
            val remainingMs = uiState.totalDurationSeconds * 1000L - uiState.timeElapsedMs.coerceAtLeast(0)
            val arrivalMinute = (System.currentTimeMillis() + remainingMs.coerceAtLeast(0)) / 60_000L
            val departureClock = remember(departureMs, originAirport) {
                val origin = originAirport
                if (departureMs == null || origin == null) return@remember null
                val departureDate = localDateOf(departureMs, origin.lon, origin.lat, origin.isoCountry)
                airportClock(departureMs, origin.lon, origin.lat, origin.isoCountry, departureDate)
            }
            val arrivalClock = remember(arrivalMinute, departureMs, originAirport, destAirport) {
                val dest = destAirport
                if (departureMs == null || dest == null) return@remember null
                val arrivalEpochMs = arrivalMinute * 60_000L
                val departureDate = originAirport?.let { localDateOf(departureMs, it.lon, it.lat, it.isoCountry) }
                    ?: localDateOf(departureMs, dest.lon, dest.lat, dest.isoCountry)
                airportClock(arrivalEpochMs, dest.lon, dest.lat, dest.isoCountry, departureDate)
            }

            // Landscape: the expanded panel is roughly as tall as the whole screen, and M3 lets
            // the sheet grow to its content's height - so uncapped it slid up over the top-bar
            // buttons (the sheet and the buttons share the same centered 600dp span). Capped to
            // end just below them, scrolling when the content doesn't fit.
            val landscapeSheetMaxHeight = (
                LocalConfiguration.current.screenHeightDp.dp -
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() -
                    HudTopBarReservedHeight
                ).coerceAtLeast(104.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isLandscape) {
                            Modifier
                                .heightIn(max = landscapeSheetMaxHeight)
                                .verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }
                    )
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

                // Feeds the scenic-mode pill's bottom margin (see timerBottomInsetFromScreen
                // above): measures the timer's actual on-screen bottom edge here so the pill
                // can reproduce the identical gap and the timer never jumps between modes.
                val density = LocalDensity.current
                val rootView = LocalView.current
                val measureTimerBottomInset: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = { coords ->
                    val bottomPx = coords.positionInWindow().y + coords.size.height
                    val gapPx = rootView.height - bottomPx
                    if (gapPx >= 0f) {
                        timerBottomInsetFromScreen = with(density) { gapPx.toDp() }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(peekContentHeight),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLandscape) {
                        // Speed and altitude flank the timer only while the sheet is collapsed:
                        // expanded, the instruments below show the same two numbers, so they
                        // fade out here rather than appear twice.
                        val peekReadoutAlpha by animateFloatAsState(
                            targetValue = if (instrumentsVisible) 0f else 1f,
                            label = "peekReadoutAlpha"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Ground Speed
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer { alpha = peekReadoutAlpha },
                                horizontalAlignment = Alignment.Start
                            ) {
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
                                text = if (landing) LandingLabel else formatRemainingTime(uiState.timeRemainingSeconds),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                ),
                                color = Amber,
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned(measureTimerBottomInset),
                                textAlign = TextAlign.Center
                            )

                            // Right: Altitude
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer { alpha = peekReadoutAlpha },
                                horizontalAlignment = Alignment.End
                            ) {
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
                            text = if (landing) LandingLabel else formatRemainingTime(uiState.timeRemainingSeconds),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            ),
                            color = Amber,
                            modifier = Modifier.onGloballyPositioned(measureTimerBottomInset)
                        )
                    }
                }

                // --- Expanded Info Panel ---
                HorizontalDivider(color = Border, thickness = 1.dp)
                // Tighter in landscape, where height is what keeps the sheet below the top bar.
                Spacer(modifier = Modifier.height(if (isLandscape) Spacing.Medium else 30.dp))

                if (isLandscape) {
                    // Wide screen: route hero + flight-time readout on the left, instrument
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
                                progress = { uiState.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlightTimeReadout(
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
                            SpeedInstrument(speedMph = speedMph, animate = instrumentsVisible, faceHeight = LandscapeInstrumentFaceHeight, modifier = Modifier.weight(1f))
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
                            progress = { uiState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            AltitudeGauge(altitudeFt = altitudeFt, modifier = Modifier.weight(1f))
                            SpeedInstrument(speedMph = speedMph, animate = instrumentsVisible, modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        FlightTimeReadout(
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
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            // On wide landscape screens the bottom sheet is centered and capped at
            // SheetMaxWidth, so its right border sits inset from the screen edge by
            // this same margin. Match it so the button's right edge lines up with
            // the card's right border instead of the physical screen edge.
            val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
            val sheetSideMargin = ((screenWidthDp - SheetMaxWidth) / 2).coerceAtLeast(0.dp)
            // Each 40dp button sits in a 48dp touch target (see HudButton), so the padding and
            // gaps here are each HudTouchInset smaller than the visual spacing they produce.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(
                        start = Spacing.Large - HudTouchInset,
                        end = (if (isLandscape) sheetSideMargin else Spacing.Large) - HudTouchInset,
                        top = Spacing.Large - HudTouchInset,
                        bottom = Spacing.Medium - HudTouchInset
                    ),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp - HudTouchInset * 2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Only while offline: a status pill, not a button. Scenic mode keeps just
                    // the icon, so the cleared-down HUD stays uncluttered.
                    if (networkMode.isOffline) {
                        HudOfflineStatus(
                            mode = networkMode,
                            iconOnly = scenicMode,
                            modifier = Modifier.padding(end = HudTouchInset)
                        )
                    }

                    // Debug: jump straight to landing. Still scaffolding - see
                    // DEV_FEATURES_TO_REVERT.md. Restored because the challenge
                    // advance/completion beats are otherwise only reachable by sitting
                    // through a real 30-minute-plus session.
                    SkipFlightDebugButton(viewModel)

                    // Scenic-mode toggle: clears the HUD down to a glass settings
                    // button and a timer-only pill. Drawn on the same surface as the
                    // settings button beside it - bare, its icon vanished against the
                    // globe in the light theme, where OffWhite is navy.
                    HudButton(
                        onClick = { scenicMode = !scenicMode },
                        surface = hudButtonSurface(scenicMode)
                    ) {
                        Icon(
                            imageVector = if (scenicMode) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (scenicMode) "Show flight HUD" else "Hide flight HUD",
                            tint = if (scenicMode) Silver else OffWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Flight settings button (camera view + leave slider).
                    // Restyled to a grey glass look in scenic mode. Stands down while
                    // landing, since everything behind it is moot by then.
                    HudButton(
                        onClick = { showSettings = true },
                        surface = hudButtonSurface(scenicMode),
                        enabled = !landing
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AirplanemodeActive,
                            contentDescription = "Flight Settings",
                            tint = if (scenicMode) Silver else OffWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // --- Network notice: says why the map just switched on its own ---
            NetworkNoticePill(
                notice = networkNotice,
                onShown = viewModel::consumeNetworkNotice
            )

            // --- Scenic mode: timer-only glass pill replacing the bottom sheet ---
            if (scenicMode) {
                // Pill vertical padding (10dp) sits between the pill's own bottom edge and
                // the text inside it, so back that out of the measured gap to land the text
                // itself at the same screen-relative spot it occupied in standard mode.
                val pillTextVerticalPadding = 10.dp
                val pillBottomPadding =
                    (timerBottomInsetFromScreen - pillTextVerticalPadding).coerceAtLeast(0.dp)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = pillBottomPadding),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .glassSurface(RoundedCornerShape(50))
                            .padding(horizontal = 24.dp, vertical = pillTextVerticalPadding)
                    ) {
                        Text(
                            text = if (landing) LandingLabel else formatRemainingTime(uiState.timeRemainingSeconds),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            ),
                            color = Silver
                        )
                    }
                }
            }

            // --- Flight Completion Navigation Trigger ---
            // Collected inside the effect rather than read from composition: reading any
            // uiState field here would subscribe this scope to the whole state object,
            // which changes every tick, so this scope would recompose 30x/second just to
            // re-check a flag that flips once.
            LaunchedEffect(Unit) {
                viewModel.uiState
                    .map { it.isCompleted }
                    .distinctUntilChanged()
                    .collect { completed ->
                        if (completed) {
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
        }

    }

    // Overlays below are drawn in this order, each above the last: countdown, debug
    // scrubber, settings, leave dialog. The countdown used to come last, so backing out
    // during it left a frozen giant digit painted over the leave dialog.

    // --- Movie Style Countdown Overlay ---
    // Reading uiState.timeElapsedMs directly here would subscribe this whole screen to a
    // field that changes every 33 ms tick, forcing everything above — scaffold, buttons,
    // camera controls — to recompose 30x/second. Scoped into its own composable instead,
    // gated on a boolean that only flips once, so after the countdown nothing recomposes.
    CountdownOverlayHost(viewModel)

    // --- TEMPORARY debug-only engine sound scrubber ---
    // Lets you drag through the whole flight instantly to audition enginePower/altitude/speed
    // at any point without waiting real time. BuildConfig.DEBUG-gated so it can never ship, and
    // meant to be deleted once the engine sound tuning is done.
    if (com.example.focusflight.BuildConfig.DEBUG) {
        EngineSoundDebugScrubber(
            viewModel = viewModel,
            onResume = { if (!showExitConfirm) viewModel.startTimer() }
        )
    }

    // --- Flight settings overlay ---
    // Lives as a sibling of the scaffold rather than inside its content lambda: the
    // content lambda is drawn *underneath* the bottom sheet, which is what let the
    // old panel slide behind the peek card. As a sibling it composes on top of both.
    FlightSettingsOverlay(
        visible = showSettings,
        selectedCamera = selectedCamera,
        mapStyle = MapStylePickerState(
            preferred = selectedMapStyle,
            effective = effectiveMapStyle,
            networkMode = networkMode
        ),
        selectedRouteLineMode = routeLineMode,
        engineSoundEnabled = engineSoundEnabled,
        onCameraSelected = {
            cameraPickedByUser = true
            selectedCamera = it
            showSettings = false
        },
        onMapStyleSelected = {
            viewModel.setMapStyle(it)
            showSettings = false
        },
        onRouteLineModeSelected = {
            viewModel.setRouteLineMode(it)
            showSettings = false
        },
        onEngineSoundToggled = viewModel::setEngineSoundEnabled,
        onDismiss = { showSettings = false },
        onLeaveRequested = {
            showSettings = false
            // The overlay closes itself the moment the landing starts, but a slide
            // released in that same instant must still not open the leave dialog.
            if (!landing) {
                viewModel.pauseTimer()
                showExitConfirm = true
            }
        }
    )

    // --- Layer 2 Exit/Pause confirmation Dialog overlay ---
    if (showExitConfirm) {
        val resume = {
            showExitConfirm = false
            viewModel.startTimer()
        }
        ScrimCardModal(onScrimTap = resume) {
            ModalTitle("LEAVE FLIGHT?")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = leaveFlightMessage(viewModel.mode),
                style = MaterialTheme.typography.bodyMedium,
                color = Haze,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            // RESUME is the primary action here - staying is the answer this dialog hopes
            // for - so it gets the accent fill and the trailing slot, with LEAVE as the
            // quieter secondary button. Laid out locally because the shared ModalButtonRow
            // always puts the accent on its confirm button.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton(
                    text = "LEAVE",
                    onClick = {
                        showExitConfirm = false
                        onExitFlight()
                    },
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Compact,
                    fillMaxWidth = false
                )
                FocusButton(
                    text = "RESUME",
                    onClick = resume,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Compact,
                    fillMaxWidth = false
                )
            }
        }
    }
}

/** Where a flight left now can be picked up again, which depends on the slot it is saved to
 *  (see PausedFlightStore): Story flights resume from the Hub; Free flights from the Free Mode
 *  row on Challenges; a challenge leg from that challenge on Challenges, and from the Hub too
 *  while it is the focused challenge - which it is when one of its legs is being flown. */
private fun leaveFlightMessage(mode: FlightMode): String = "Your progress is saved. " + when (mode) {
    FlightMode.STORY -> "You can resume this flight later from the Hub."
    FlightMode.FREE -> "You can resume it later from Free Mode on the Challenges screen."
    FlightMode.CHALLENGE -> "You can resume it later from the Hub, or from this challenge on the Challenges screen."
}

/** See `landing` in [InFlightScreen]: the timer has run out and the flight is about to land. */
private fun InFlightState.isLanding(): Boolean =
    totalDurationSeconds > 0 && timeElapsedMs >= totalDurationSeconds * 1000L

/** Stands in for the 00:00 timer while the flight lands. Same width as an hh:mm:ss timer. */
private const val LandingLabel = "LANDING…"

@Composable
private fun CountdownOverlayHost(viewModel: InFlightViewModel) {
    val showCountdown by remember(viewModel) {
        viewModel.uiState.map { it.timeElapsedMs < 0 }.distinctUntilChanged()
    }.collectAsState(initial = viewModel.uiState.value.timeElapsedMs < 0)

    if (showCountdown) {
        // Only while the countdown is on screen does anything here track the 30 Hz value.
        val elapsedMs by remember(viewModel) {
            viewModel.uiState.map { it.timeElapsedMs }.distinctUntilChanged()
        }.collectAsState(initial = viewModel.uiState.value.timeElapsedMs)
        MovieCountdown(elapsedMs)
    }
}

// ============================================================================
//  Flight settings overlay
//
//  Portrait and landscape get genuinely different layouts rather than one layout
//  stretched to fit. Portrait has height to spare, so it keeps the stacked card
//  hanging under the top bar. Landscape has almost none - the stacked card is
//  taller than the whole viewport there - so landscape gets a right-anchored
//  drawer that spends the axis it actually has: two side-by-side columns of
//  compact option rows, with the slide-to-leave control across the foot.
// ============================================================================

private data class CameraOption(val label: String, val mode: Int, val icon: ImageVector)

private fun cameraViewOptions(): List<CameraOption> = listOf(
    CameraOption("FREE", 0, Icons.Outlined.Explore),
    CameraOption("CHASE", 1, Icons.Outlined.Visibility),
    CameraOption("COCKPIT", 2, Icons.Outlined.Flight)
)

private data class RouteLineOption(val label: String, val mode: Int, val icon: ImageVector)

private fun routeLineOptions(): List<RouteLineOption> = listOf(
    RouteLineOption("FULL", 0, Icons.Outlined.Route),
    RouteLineOption("WINDOW", 1, Icons.Outlined.LinearScale),
    RouteLineOption("HIDDEN", 2, Icons.Outlined.VisibilityOff)
)

// Label to the map-style id understood by nativeSetMapStyle(): the CARTO dark basemap,
// Esri imagery on 3D relief, and the bundled vector map that needs no network.
private val MapStyleOptions = listOf(
    "OFFLINE" to CesiumLiveJniBridge.MAP_STYLE_OFFLINE,
    "MAP" to CesiumLiveJniBridge.MAP_STYLE_STANDARD,
    "SAT+3D" to CesiumLiveJniBridge.MAP_STYLE_SATELLITE_TERRAIN
)

private fun mapStyleLabel(style: Int): String =
    MapStyleOptions.firstOrNull { it.second == style }?.first ?: "MAP"

// How dimmed a settings option is while it can't be picked (network map styles offline).
private const val DisabledOptionAlpha = 0.4f

/** What the map-style picker shows. [effective] is highlighted, since that is what the globe
 *  really renders. While offline only the offline map can be picked; [preferred] is the
 *  pilot's own choice that returns once the app is online again. */
private data class MapStylePickerState(
    val preferred: Int,
    val effective: Int,
    val networkMode: NetworkMode
) {
    fun isActive(style: Int): Boolean = effective == style

    fun isAvailable(style: Int): Boolean =
        !networkMode.isOffline || style == CesiumLiveJniBridge.MAP_STYLE_OFFLINE

    /** One line under the picker explaining the lock, or null when online. */
    val offlineHint: String?
        get() = when (networkMode) {
            NetworkMode.ONLINE -> null
            NetworkMode.OFFLINE_DATA_SAVER -> "Offline maps is on in Settings"
            NetworkMode.OFFLINE_NO_CONNECTION ->
                if (preferred == CesiumLiveJniBridge.MAP_STYLE_OFFLINE) "No connection"
                else "No connection · ${mapStyleLabel(preferred)} returns when back online"
        }
}

@Composable
private fun MapStyleOfflineHint(text: String) {
    Text(
        text = text,
        color = Haze,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
}

/**
 * The HUD's OFFLINE marker. Deliberately not shaped like the 40dp square buttons beside it (as
 * the shared [com.example.focusflight.ui.components.OfflineBadge] is, to sit in the Hub header):
 * a short, fully rounded pill with no click handler, so it reads as status rather than as a
 * control that does nothing. [iconOnly] is scenic mode's version - just the icon, on glass.
 */
@Composable
private fun HudOfflineStatus(mode: NetworkMode, iconOnly: Boolean, modifier: Modifier = Modifier) {
    val dataSaver = mode == NetworkMode.OFFLINE_DATA_SAVER
    val icon = if (dataSaver) Icons.Outlined.DataSaverOn else Icons.Outlined.CloudOff
    val description = if (dataSaver) "Offline: data saver on" else "Offline: no connection"
    val pill = RoundedCornerShape(50)
    if (iconOnly) {
        Box(
            modifier = modifier
                .glassSurface(pill)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = description, tint = Silver, modifier = Modifier.size(16.dp))
        }
    } else {
        FocusBadge(
            text = "OFFLINE",
            modifier = modifier.semantics { contentDescription = description },
            variant = BadgeVariant.Neutral,
            style = BadgeStyle.Translucent,
            icon = icon,
            shape = pill
        )
    }
}

/** The look of a top-bar button: solid in the full HUD, glass in scenic mode. */
private fun hudButtonSurface(scenicMode: Boolean): Modifier =
    if (scenicMode) {
        Modifier.glassSurface(RoundedCornerShape(12.dp))
    } else {
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DeepNavy)
    }

/**
 * A 40dp top-bar button inside a 48dp touch target. The extra 4dp on each side is invisible and
 * tappable, and the ripple is still drawn on the 40dp [surface] only, so the button looks exactly
 * as it did while being easier to hit. Callers shrink their padding by [HudTouchInset] to keep the
 * same visual spacing.
 */
@Composable
private fun HudButton(
    onClick: () -> Unit,
    surface: Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(HudTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(HudButtonSize)
                .alpha(if (enabled) 1f else DisabledOptionAlpha)
                .then(surface)
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

private const val NetworkNoticeDurationMs = 3_000L

/** A short glass pill under the HUD top bar saying the map just switched on its own. It shows
 *  for [NetworkNoticeDurationMs] and then calls [onShown]. */
@Composable
private fun NetworkNoticePill(notice: NetworkNotice?, onShown: () -> Unit) {
    // Kept after [notice] is consumed so the text doesn't vanish mid-fade.
    var lastNotice by remember { mutableStateOf<NetworkNotice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            lastNotice = notice
            delay(NetworkNoticeDurationMs)
            onShown()
        }
    }
    val shown = notice ?: lastNotice ?: return
    val text = when (shown) {
        is NetworkNotice.SwitchedToOffline ->
            if (shown.dataSaver) "Data saver on · offline map" else "No connection · offline map"
        is NetworkNotice.RestoredMap -> "Back online · ${mapStyleLabel(shown.restoredStyle)} restored"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            // Clears the 40dp top-bar buttons and their padding.
            .padding(top = Spacing.Large + 40.dp + Spacing.Medium),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(240))
        ) {
            Row(
                modifier = Modifier
                    .glassSurface(RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (shown is NetworkNotice.RestoredMap) Icons.Outlined.Cloud else Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = Silver,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = text,
                    color = Silver,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// --- TEMPORARY: engine sound debug scrubber, see its call site above. Delete this whole
// composable along with the call site once engine sound tuning is done. ---
//
// Collapsed to a small "DBG" chip in the top-left corner, clear of the top-bar buttons on the
// right; the panel opens *below* the top bar, never over it. (Pinned open across the top, its
// slider used to catch taps meant for the buttons and silently pause the flight.) Scrubbing
// pauses the timer so the two don't fight, and the panel says so and offers RESUME; closing
// the panel after a scrub resumes the timer too, via [onResume].
@Composable
private fun EngineSoundDebugScrubber(viewModel: InFlightViewModel, onResume: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var scrubbed by remember { mutableStateOf(false) }
    val chipShape = RoundedCornerShape(8.dp)
    val topBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Spacing.Large

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = Spacing.Large, top = topBarTop)
    ) {
        Text(
            text = "DBG",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(chipShape)
                .background(Color.Black.copy(alpha = if (expanded) 0.85f else 0.5f))
                .clickable {
                    if (expanded && scrubbed) {
                        scrubbed = false
                        onResume()
                    }
                    expanded = !expanded
                }
                .padding(horizontal = 10.dp, vertical = 12.dp)
        )
    }

    if (!expanded) return

    // Collected here rather than by the caller, so the 30 Hz readout only recomposes this overlay.
    val state by viewModel.uiState.collectAsState()
    val power by viewModel.enginePower.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            // Below the top bar's 40dp buttons, the same line NetworkNoticePill starts at.
            .padding(top = topBarTop + HudButtonSize + Spacing.Medium)
            .padding(horizontal = Spacing.Large),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "DEBUG progress=${"%.3f".format(state.progress)}  alt=${state.altitudeMeters}m  " +
                    "spd=${state.speedKmh}km/h  N1=${"%.2f".format(power)}",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Slider(
                value = state.progress,
                onValueChange = { progress ->
                    scrubbed = true
                    viewModel.pauseTimer()
                    viewModel.scrubProgress(progress)
                },
                valueRange = 0f..1f
            )
            if (!state.isRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TIMER PAUSED",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        scrubbed = false
                        onResume()
                    }) {
                        Text(text = "RESUME", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightSettingsOverlay(
    visible: Boolean,
    selectedCamera: Int,
    mapStyle: MapStylePickerState,
    selectedRouteLineMode: Int,
    engineSoundEnabled: Boolean,
    onCameraSelected: (Int) -> Unit,
    onMapStyleSelected: (Int) -> Unit,
    onRouteLineModeSelected: (Int) -> Unit,
    onEngineSoundToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onLeaveRequested: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Back closes the panel first. Registered after the screen's own BackHandler, so while
    // the panel is open it takes precedence over "pause and ask to leave".
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150))
        ) {
            // Full screen scrim: dims the live scene behind the panel and closes on tap outside
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onDismiss() }
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(if (isLandscape) Alignment.CenterEnd else Alignment.TopCenter),
            enter = if (isLandscape) {
                slideInHorizontally(tween(220)) { it } + fadeIn(tween(160))
            } else {
                fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180))
            },
            exit = if (isLandscape) {
                slideOutHorizontally(tween(160)) { it } + fadeOut(tween(140))
            } else {
                fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120))
            }
        ) {
            if (isLandscape) {
                LandscapeFlightSettingsPanel(
                    selectedCamera = selectedCamera,
                    mapStyle = mapStyle,
                    selectedRouteLineMode = selectedRouteLineMode,
                    engineSoundEnabled = engineSoundEnabled,
                    onCameraSelected = onCameraSelected,
                    onMapStyleSelected = onMapStyleSelected,
                    onRouteLineModeSelected = onRouteLineModeSelected,
                    onEngineSoundToggled = onEngineSoundToggled,
                    onDismiss = onDismiss,
                    onLeaveRequested = onLeaveRequested
                )
            } else {
                PortraitFlightSettingsCard(
                    selectedCamera = selectedCamera,
                    mapStyle = mapStyle,
                    selectedRouteLineMode = selectedRouteLineMode,
                    engineSoundEnabled = engineSoundEnabled,
                    onCameraSelected = onCameraSelected,
                    onMapStyleSelected = onMapStyleSelected,
                    onRouteLineModeSelected = onRouteLineModeSelected,
                    onEngineSoundToggled = onEngineSoundToggled,
                    onLeaveRequested = onLeaveRequested
                )
            }
        }
    }
}

// --- Portrait: a compact floating card below the top bar, options stacked as
// icon-over-label tiles. ---
@Composable
private fun PortraitFlightSettingsCard(
    selectedCamera: Int,
    mapStyle: MapStylePickerState,
    selectedRouteLineMode: Int,
    engineSoundEnabled: Boolean,
    onCameraSelected: (Int) -> Unit,
    onMapStyleSelected: (Int) -> Unit,
    onRouteLineModeSelected: (Int) -> Unit,
    onEngineSoundToggled: (Boolean) -> Unit,
    onLeaveRequested: () -> Unit
) {
    Column(
        modifier = Modifier
            // widthIn before fillMaxWidth so the cap actually wins: fillMaxWidth pins
            // the width to the parent's, and a widthIn placed after it cannot shrink
            // that back down again.
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp)
            .padding(horizontal = Spacing.Large)
            .background(DeepNavy, RoundedCornerShape(20.dp))
            .border(1.dp, Border, RoundedCornerShape(20.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CaptionLabel("CAMERA VIEW")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            cameraViewOptions().forEach { option ->
                val isActive = selectedCamera == option.mode
                SettingsTile(
                    isActive = isActive,
                    onClick = { onCameraSelected(option.mode) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.label,
                        tint = if (isActive) Amber else OffWhite,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = option.label,
                        color = if (isActive) Amber else OffWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 1.dp)

        CaptionLabel("ROUTE LINE")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            routeLineOptions().forEach { option ->
                val isActive = selectedRouteLineMode == option.mode
                SettingsTile(
                    isActive = isActive,
                    onClick = { onRouteLineModeSelected(option.mode) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.label,
                        tint = if (isActive) Amber else OffWhite,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = option.label,
                        color = if (isActive) Amber else OffWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 1.dp)

        CaptionLabel("MAP STYLE")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MapStyleOptions.forEach { (styleName, styleMode) ->
                val isActive = mapStyle.isActive(styleMode)
                val available = mapStyle.isAvailable(styleMode)
                SettingsTile(
                    isActive = isActive,
                    onClick = { onMapStyleSelected(styleMode) },
                    enabled = available,
                    chrome = TileChrome.None,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    MapStyleThumbnail(style = styleMode, isActive = isActive, unavailable = !available)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = styleName,
                        color = if (isActive) Amber else OffWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        mapStyle.offlineHint?.let { MapStyleOfflineHint(it) }

        HorizontalDivider(color = Border, thickness = 1.dp)

        CaptionLabel("ENGINE SOUND")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Jet engine noise",
                color = OffWhite,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Switch(
                checked = engineSoundEnabled,
                onCheckedChange = onEngineSoundToggled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Amber,
                    checkedTrackColor = Amber.copy(alpha = 0.4f)
                )
            )
        }

        SlideToLeaveControl(onSlideCompleted = onLeaveRequested)
    }
}

// --- Landscape: a right-anchored, full-height drawer. Every option is a single
// short row (leading visual + label) so a section costs ~40dp of height instead of
// the ~90dp a stacked tile costs, and the two sections sit beside each other rather
// than one under the other. Anchoring right also puts the panel under the thumb
// that just tapped the top-bar button. ---
@Composable
private fun LandscapeFlightSettingsPanel(
    selectedCamera: Int,
    mapStyle: MapStylePickerState,
    selectedRouteLineMode: Int,
    engineSoundEnabled: Boolean,
    onCameraSelected: (Int) -> Unit,
    onMapStyleSelected: (Int) -> Unit,
    onRouteLineModeSelected: (Int) -> Unit,
    onEngineSoundToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onLeaveRequested: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val panelWidth = (screenWidth * 0.52f).coerceIn(360.dp, 500.dp)
    val panelShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(panelWidth)
            .clip(panelShape)
            .background(DeepNavy)
            .border(1.dp, Border, panelShape)
            // Insets inside the surface so the drawer's fill still runs edge to edge
            // behind the status and navigation bars. safeDrawing rather than systemBars:
            // the bars are hidden in flight, so systemBars is zero, while a punch-hole
            // camera on this side still needs clearing. Start is left out - the drawer's
            // left edge is mid-screen, so a cutout on the far side isn't its concern.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
            // Height is the scarce axis here: scroll rather than clip if a device's
            // usable height is shorter than the content.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FLIGHT SETTINGS",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                ),
                color = OffWhite,
                modifier = Modifier.weight(1f)
            )
            // 32dp square drawn inside a 48dp touch target; the offset keeps the visible
            // square's right edge where it was, flush with the column below.
            val closeInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .offset(x = (HudTouchTarget - 32.dp) / 2)
                    .size(HudTouchTarget)
                    .clickable(
                        interactionSource = closeInteraction,
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate)
                        .indication(closeInteraction, ripple()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close flight settings",
                        tint = Haze,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Row(
            // Min intrinsic height so the divider between the columns spans exactly
            // the taller of the two, with no fixed height to keep in sync.
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionLabel("CAMERA VIEW")
                cameraViewOptions().forEach { option ->
                    SettingsOptionRow(
                        label = option.label,
                        isActive = selectedCamera == option.mode,
                        onClick = { onCameraSelected(option.mode) }
                    ) { isActive ->
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = if (isActive) Amber else OffWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Border)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionLabel("ROUTE LINE")
                routeLineOptions().forEach { option ->
                    SettingsOptionRow(
                        label = option.label,
                        isActive = selectedRouteLineMode == option.mode,
                        onClick = { onRouteLineModeSelected(option.mode) }
                    ) { isActive ->
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = if (isActive) Amber else OffWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionLabel("MAP STYLE")
                MapStyleOptions.forEach { (styleName, styleMode) ->
                    val available = mapStyle.isAvailable(styleMode)
                    SettingsOptionRow(
                        label = styleName,
                        isActive = mapStyle.isActive(styleMode),
                        onClick = { onMapStyleSelected(styleMode) },
                        enabled = available,
                        contentPadding = PaddingValues(6.dp),
                        spacing = 10.dp
                    ) { isActive ->
                        MapStyleThumbnail(
                            style = styleMode,
                            isActive = isActive,
                            unavailable = !available,
                            width = 64.dp,
                            height = 42.dp
                        )
                    }
                }
                mapStyle.offlineHint?.let { MapStyleOfflineHint(it) }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Border)
            )

            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionLabel("ENGINE SOUND")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "NOISE",
                        color = OffWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Switch(
                        checked = engineSoundEnabled,
                        onCheckedChange = onEngineSoundToggled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Amber,
                            checkedTrackColor = Amber.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 1.dp)

        SlideToLeaveControl(onSlideCompleted = onLeaveRequested, trackHeight = 48.dp)
    }
}

// --- Compact horizontal option row used by the landscape drawer: a leading visual
// (camera icon or map preview) beside its label. The active row is tinted amber and
// ringed, matching the affordance the portrait tiles use. ---
@Composable
private fun SettingsOptionRow(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    spacing: Dp = 12.dp,
    leading: @Composable (isActive: Boolean) -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        targetValue = if (isActive) Amber.copy(alpha = 0.15f) else Slate,
        label = "settingsRowBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) Amber else Color.Transparent,
        label = "settingsRowBorder"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else DisabledOptionAlpha)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        leading(isActive)
        Text(
            text = label,
            color = if (isActive) Amber else OffWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- Shared chrome for the settings-modal option tiles (camera view / map style).
// Fill draws the classic amber-tint-vs-slate background (camera row); None draws no
// chrome of its own, for tiles (map style) whose content manages its own active
// affordance (a border around the thumbnail, not the whole tile). ---
private enum class TileChrome { Fill, None }

@Composable
private fun SettingsTile(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    chrome: TileChrome = TileChrome.Fill,
    contentPadding: PaddingValues = PaddingValues(vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "settingsTileScale"
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (chrome == TileChrome.Fill) {
                    Modifier.background(
                        if (isActive) Amber.copy(alpha = 0.15f) else Slate,
                        RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else DisabledOptionAlpha)
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

// --- Map style preview: a real crop of each basemap the engine actually renders,
// all over Hong Kong so the thumbnails read as one place in three styles. The artwork
// is a static crop of the same sources the globe draws at runtime - CARTO dark_nolabels
// (Standard), Esri World Imagery (Satellite + Terrain) and CesiumRS's bundled
// world_vector_dark.svg (Offline, drawn at a wider zoom since it is 1:10m data) - so the
// preview cannot drift from what selecting it gives you. The active thumbnail gets an amber ring; the inactive one keeps a neutral
// hairline so both read as framed previews rather than one floating image. ---
@Composable
private fun MapStyleThumbnail(
    style: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    // Marks a network style that can't be picked while offline.
    unavailable: Boolean = false,
    // Unspecified fills the available width at the artwork's own 400x264 aspect ratio.
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified
) {
    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 1.dp,
        label = "mapThumbnailBorder"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) Amber else Border,
        label = "mapThumbnailBorderColor"
    )
    val thumbnailShape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .then(
                if (width == Dp.Unspecified) {
                    Modifier.fillMaxWidth().aspectRatio(400f / 264f)
                } else {
                    Modifier.size(width = width, height = height)
                }
            )
            .clip(thumbnailShape)
            .background(Midnight)
            .border(borderWidth, borderColor, thumbnailShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(
                id = when (style) {
                    CesiumLiveJniBridge.MAP_STYLE_SATELLITE_TERRAIN -> R.drawable.map_preview_satellite
                    CesiumLiveJniBridge.MAP_STYLE_OFFLINE -> R.drawable.map_preview_offline
                    else -> R.drawable.map_preview_standard
                }
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (unavailable) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = "Needs a connection",
                tint = OffWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SlideToLeaveControl(onSlideCompleted: () -> Unit, trackHeight: Dp = 56.dp) {
    val thumbInsetDp = 4.dp
    val thumbSizeDp = trackHeight - thumbInsetDp * 2
    val trackHeightDp = trackHeight
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
        // The thumb sits inside a 4dp inset on either side, so its full footprint is
        // one track-height square - travel has to stop that far short of the end or
        // the thumb runs past the track and gets clipped.
        val maxOffsetPx = (trackWidthPx - with(density) { trackHeightDp.toPx() }).coerceAtLeast(0f)

        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        var committed by remember { mutableStateOf(false) }
        val animatedOffsetPx by animateFloatAsState(
            targetValue = dragOffsetPx,
            label = "slideToLeaveOffset"
        )
        val thumbScale by animateFloatAsState(
            targetValue = if (dragging) 1.08f else 1f,
            label = "slideToLeaveThumbScale"
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
            text = if (armed) "RELEASE TO LEAVE" else "SLIDE TO LEAVE",
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
                .padding(thumbInsetDp)
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
                contentDescription = "Slide to leave the flight",
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

// --- Route hero: just the two airport codes bridged by a progress track, styled as
// the same kind of widget as the instrument cards below it. The travelling plane
// tilts nose-up on departure, levels off for cruise, and noses down on approach. ---
@Composable
private fun FlightRouteHero(
    originIata: String,
    destIata: String,
    departureClock: AirportClock?,
    arrivalClock: AirportClock?,
    progress: () -> Float,
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
// `progress` arrives as a lambda so that a value changing 30x/second never has to be
// read during composition — the draw and layout scopes below read it directly, which
// keeps this track (and the route hero that hosts it) out of the recomposition path.
@Composable
private fun RouteProgressTrack(progress: () -> Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.height(28.dp)) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val clampedProgress = progress().coerceIn(0f, 1f)
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
                pathEffect = dashEffect
            )
        }

        // Phase is a three-step function of progress, so derivedStateOf means the pitch
        // animation is only retargeted at the two phase changes, not on every tick.
        val planePitch by remember(progress) {
            derivedStateOf {
                when {
                    progress().coerceIn(0f, 1f) < 0.08f -> 45f  // climbing out, nose up
                    progress().coerceIn(0f, 1f) > 0.9f -> 135f  // on approach, nose down
                    else -> 90f                                  // level cruise
                }
            }
        }
        val animatedPitch = animateFloatAsState(targetValue = planePitch, label = "planePitch")
        val iconInsetPx = with(density) { 12.dp.toPx() }
        Icon(
            imageVector = Icons.Outlined.Flight,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    // Layout-scope read: the plane slides without recomposing anything.
                    val x = (trackWidthPx * progress().coerceIn(0f, 1f)) - iconInsetPx
                    IntOffset(x.coerceAtLeast(0f).roundToInt(), 0)
                }
                .size(24.dp)
                .graphicsLayer { rotationZ = animatedPitch.value }
        )
    }
}

// Shared frame for the two instrument cards so they always match in size: a face
// that fills the card's width (so its margin matches the card's own padding on
// every side) at a given height, a spacer, then a label caption underneath.
private val InstrumentFaceHeight = 108.dp
// Tuned to the landscape left column (route hero + flight-time readout) so both sides of
// the expanded panel end level.
private val LandscapeInstrumentFaceHeight = 100.dp

// The bottom sheet is centered and capped at this width on wide landscape screens
// (Material3's own default is 640.dp; kept explicit here so the HUD top bar can
// compute the same side margin and line the settings button up with the sheet's
// actual right border).
private val SheetMaxWidth = 600.dp

// Top-bar buttons: drawn at HudButtonSize, tappable across HudTouchTarget (the 48dp minimum),
// which leaves HudTouchInset of invisible touch area on each side. See HudButton.
private val HudButtonSize = 40.dp
private val HudTouchTarget = 48.dp
private val HudTouchInset = (HudTouchTarget - HudButtonSize) / 2

// How far down from the top edge (below any status-bar inset) the top-bar buttons reach, plus
// a small gap - the landscape bottom sheet is capped to stop here so it can't cover them.
private val HudTopBarReservedHeight = Spacing.Large + HudButtonSize + 4.dp

// How far the scenic-mode timer pill sits above the physical bottom edge. Chosen to
// approximate where the timer already sits in the normal collapsed sheet peek.
private val ScenicPillBottomPadding = 36.dp

// Faked glassmorphism (translucent fill + grey border) used by the settings button
// and timer pill in scenic mode. A real backdrop blur isn't possible here: the 3D
// engine renders into a SurfaceView, composited by SurfaceFlinger outside the
// Compose/View draw pass, so no Compose-based blur can see it.
private fun Modifier.glassSurface(shape: Shape): Modifier = this
    .clip(shape)
    .background(Dim.copy(alpha = 0.35f), shape)
    .border(1.dp, Haze.copy(alpha = 0.4f), shape)

// Debug affordance, wired into the HUD top bar. Jumps straight to the landing pipeline so the
// post-flight beats (rank stamp, challenge tick-up, challenge completion) can be exercised
// without sitting through a real session. Must go before shipping - see DEV_FEATURES_TO_REVERT.md.
@Composable
private fun SkipFlightDebugButton(viewModel: InFlightViewModel) {
    // HudButton for the same 48dp touch target and spacing as its neighbours in the top bar.
    HudButton(
        onClick = { viewModel.skipFlight() },
        surface = Modifier
            .background(DeepNavy.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        Text(
            text = ">>",
            color = Amber,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun InstrumentCard(
    label: String,
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
            color = Haze
        )
    }
}

// --- Altimeter tape: a vertical scrolling ruler of altitude values, like a real
// primary flight display. The current reading sits fixed at the centre, enlarged,
// amber, with a caret pointing at it; the rest of the tape scrolls past behind it. ---
@Composable
private fun AltitudeGauge(altitudeFt: Int, modifier: Modifier = Modifier, faceHeight: Dp = InstrumentFaceHeight) {
    val stepFt = 500
    val exactIndex = altitudeFt / stepFt.toFloat()
    val animatedIndexState = animateFloatAsState(targetValue = exactIndex, label = "altitudeTape")
    val density = LocalDensity.current
    val rowHeightDp = 22.dp
    val rowHeightPx = with(density) { rowHeightDp.toPx() }

    // Which rows exist only changes when the tape crosses a 500 ft boundary. Reading
    // the raw animated value in composition (as `floor(animatedIndex)` used to) forced
    // this whole gauge — seven Text nodes, each re-laid-out — to recompose on every
    // single animation frame. derivedStateOf collapses that to the rare boundary cross.
    // Rounded, not floored: this is the row currently closest to the centre line, so the
    // `offset == 0` row below is the one that genuinely sits under the caret and earns
    // the amber "ft" readout.
    val baseIdx by remember { derivedStateOf { animatedIndexState.value.roundToInt() } }

    InstrumentCard(label = "ALTITUDE", modifier = modifier, faceHeight = faceHeight) {
        val centerIdx = baseIdx
        for (offset in -3..3) {
            val idx = centerIdx + offset
            val isCenter = offset == 0
            Text(
                text = if (isCenter) {
                    String.format(java.util.Locale.US, "%,d ft", altitudeFt)
                } else {
                    String.format(java.util.Locale.US, "%,d", idx * stepFt)
                },
                style = MaterialTheme.typography.labelSmall,
                fontSize = 19.sp,
                fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                color = if (isCenter) Amber else Haze,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    // Position, scale and fade are all applied in layout/draw scopes, so
                    // the tape slides without re-measuring any text. Scaling replaces the
                    // old per-frame fontSize change, which invalidated text layout.
                    .offset {
                        IntOffset(0, ((animatedIndexState.value - idx) * rowHeightPx).roundToInt())
                    }
                    .graphicsLayer {
                        val distanceFromCenter = kotlin.math.abs(animatedIndexState.value - idx)
                        val sizeFraction = (distanceFromCenter / 3f).coerceIn(0f, 1f)
                        val scale = 1f - 0.42f * sizeFraction
                        scaleX = scale
                        scaleY = scale
                        alpha = if (distanceFromCenter < 0.5f) 1f
                                else (1f - distanceFromCenter / 2.4f).coerceIn(0f, 1f)
                    }
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
private fun SpeedInstrument(
    speedMph: Int,
    animate: Boolean,
    modifier: Modifier = Modifier,
    faceHeight: Dp = InstrumentFaceHeight
) {
    val maxSpeedMph = 600f
    val intensity = (speedMph / maxSpeedMph).coerceIn(0f, 1f)
    val moving = animate && intensity > 0.02f

    InstrumentCard(label = "GROUND SPEED", modifier = modifier, faceHeight = faceHeight) {
        if (moving) {
            // The animated layer only exists while the aircraft is moving *and* the panel
            // is on screen. Leaving composition disposes its infinite transition, so a
            // parked aircraft — or a collapsed sheet — costs zero per-frame animation
            // callbacks; this previously ran at full display refresh unconditionally.
            SpeedMotionLayer(intensity = { intensity })
        } else {
            Icon(
                imageVector = Icons.Outlined.Flight,
                contentDescription = null,
                tint = OffWhite,
                modifier = Modifier
                    .align(BiasAlignment(0f, -0.44f))
                    .size(28.dp)
                    .graphicsLayer { rotationZ = 90f }
            )
        }

        // fontWeight/fontFamily passed as parameters rather than folded into a copied
        // TextStyle: `style.copy(...)` allocates a new instance on every recomposition,
        // so Text never sees equal parameters and re-measures on each 30 Hz tick even
        // when the string is unchanged. Same reasoning everywhere else in this file.
        Text(
            text = "$speedMph mph",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Amber,
            modifier = Modifier.align(BiasAlignment(0f, 0.47f))
        )
    }
}

// --- Flight time as elapsed / total; tap it to swap the readout for miles left / total
// miles, with a swap glyph hinting it's interactive. It used to carry a fill bar too, but
// the route track's travelling plane above already shows progress, so the bar was a third
// copy of the same fact. ---
@Composable
private fun FlightTimeReadout(
    elapsedSec: Long,
    totalSec: Long,
    distanceLeftKm: Double,
    totalDistanceKm: Double,
    modifier: Modifier = Modifier
) {
    var showDistance by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Slate.copy(alpha = 0.5f))
            .clickable(onClickLabel = if (showDistance) "Show flight time" else "Show distance") {
                showDistance = !showDistance
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (showDistance) "DISTANCE" else "FLIGHT TIME",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                color = Haze
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = null,
                tint = Haze,
                modifier = Modifier.size(14.dp)
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
