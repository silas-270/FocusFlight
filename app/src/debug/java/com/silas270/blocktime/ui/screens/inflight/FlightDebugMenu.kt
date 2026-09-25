package com.silas270.blocktime.ui.screens.inflight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.ui.theme.Spacing
import com.silas270.blocktime.ui.viewmodel.inflight.InFlightViewModel
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

// Log-scale speed slider range: 0.1x .. 500x. The number field takes any value >= 0.
private const val MIN_SLIDER_SCALE = 0.1
private const val MAX_SLIDER_SCALE = 500.0

private fun scaleToSlider(scale: Double): Float =
    (ln(scale.coerceIn(MIN_SLIDER_SCALE, MAX_SLIDER_SCALE) / MIN_SLIDER_SCALE) /
        ln(MAX_SLIDER_SCALE / MIN_SLIDER_SCALE)).toFloat()

private fun sliderToScale(t: Float): Double =
    MIN_SLIDER_SCALE * (MAX_SLIDER_SCALE / MIN_SLIDER_SCALE).pow(t.toDouble())

private val DebugText = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

/**
 * Debug builds only (src/release has an empty stand-in): the in-flight debug menu, modelled on
 * CesiumRS's desktop flight panel - progress, flight speed, pause/resume, and skip to landing.
 *
 * Collapsed to a "DBG" chip in the top-left corner, clear of the top-bar buttons on the right;
 * the panel opens below the top bar ([panelTop] under it), never over it. Seeking moves the
 * flight clock itself (see [InFlightViewModel.debugSeek]), so it doesn't pause anything: a
 * running flight carries on from the new point, a paused one resumes from it.
 */
@Composable
internal fun FlightDebugMenu(viewModel: InFlightViewModel, panelTop: Dp, onResume: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
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
            style = DebugText.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clip(chipShape)
                .background(Color.Black.copy(alpha = if (expanded) 0.85f else 0.5f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 12.dp)
        )
    }

    if (!expanded) return

    // Collected here rather than by the caller, so the 30 Hz readout only recomposes this overlay.
    val state by viewModel.uiState.collectAsState()
    val power by viewModel.enginePower.collectAsState()
    val timeScale by viewModel.debugTimeScale.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = topBarTop + panelTop)
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
                text = String.format(
                    Locale.US, "progress=%.3f  alt=%dm  spd=%dkm/h  N1=%.2f",
                    state.progress, state.altitudeMeters, state.speedKmh, power
                ),
                style = DebugText
            )

            // --- Progress ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "PROGRESS", style = DebugText, modifier = Modifier.weight(1f))
                DebugNumberField(
                    liveText = String.format(Locale.US, "%.2f", state.progress * 100f),
                    suffix = "%",
                    onCommit = { viewModel.debugSeek((it / 100.0).toFloat()) }
                )
            }
            Slider(
                value = state.progress,
                onValueChange = viewModel::debugSeek,
                valueRange = 0f..1f
            )

            // --- Speed ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                val realSeconds = if (timeScale > 0.0) state.totalDurationSeconds / timeScale else null
                Text(
                    text = "SPEED  " + (realSeconds?.let {
                        "${state.totalDurationSeconds / 60} min → ${formatRealTime(it)}"
                    } ?: "held"),
                    style = DebugText,
                    modifier = Modifier.weight(1f)
                )
                DebugNumberField(
                    liveText = formatScale(timeScale),
                    suffix = "×",
                    onCommit = { viewModel.setDebugTimeScale(it) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = scaleToSlider(timeScale),
                    onValueChange = { viewModel.setDebugTimeScale(sliderToScale(it)) },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.setDebugTimeScale(1.0) }) {
                    Text(text = "1×", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }

            // --- Pause/resume + skip ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { if (state.isRunning) viewModel.pauseTimer() else onResume() }) {
                    Text(
                        text = if (state.isRunning) "PAUSE" else "RESUME",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = viewModel::skipFlight) {
                    Text(text = "SKIP TO LANDING", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Number field for precise values. Shows [liveText] while not being edited; once focused it
 * keeps what's typed, and applies it on IME Done or when focus leaves. Unparseable input is
 * dropped and the field snaps back to the live value.
 */
@Composable
private fun DebugNumberField(liveText: String, suffix: String, onCommit: (Double) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(liveText) }
    val focusManager = LocalFocusManager.current

    fun commit() {
        draft.replace(',', '.').toDoubleOrNull()?.let(onCommit)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = if (focused) draft else liveText,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = DebugText.copy(fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .width(72.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .onFocusChanged {
                    if (it.isFocused && !focused) draft = liveText
                    if (!it.isFocused && focused) commit()
                    focused = it.isFocused
                }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = suffix, style = DebugText)
    }
}

private fun formatScale(scale: Double): String =
    if (scale >= 10.0) String.format(Locale.US, "%.0f", scale) else String.format(Locale.US, "%.2f", scale)

private fun formatRealTime(seconds: Double): String {
    val s = seconds.toLong()
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}
