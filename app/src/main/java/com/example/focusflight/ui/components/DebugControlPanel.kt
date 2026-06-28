package com.example.focusflight.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.viewmodel.FlightViewModel

// ── Color Constants ──────────────────────────────────────────────────────
private val PanelBg = Color(0xDD1A1A2E)
private val ActiveColor = Color(0xFF00D4FF)
private val InactiveColor = Color(0xFF2A2A4A)
private val TextColor = Color(0xFFE0E0E0)
private val LabelColor = Color(0xFF8888AA)
private val DividerColor = Color(0xFF333355)
private val ToggleBg = Color(0xFF0A0A1A)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugControlPanel(
    viewModel: FlightViewModel,
    modifier: Modifier = Modifier
) {
    val cameraMode by viewModel.cameraMode.collectAsState()
    val mapStyle by viewModel.mapStyle.collectAsState()
    val sunsetMode by viewModel.sunsetMode.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val speedMultiplier by viewModel.speedMultiplier.collectAsState()
    val fpsMode by viewModel.fpsMode.collectAsState()

    var isPanelOpen by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.End
    ) {
        // ── Panel Content ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isPanelOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(PanelBg, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Header ───────────────────────────────────────────────
                Text(
                    "DEBUG PANEL",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = ActiveColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                PanelDivider()

                // ── Section: Kamera ──────────────────────────────────────
                SectionLabel("KAMERA")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("FREE", "TRACKING", "COCKPIT").forEach { mode ->
                        ToggleChip(
                            label = mode,
                            isActive = cameraMode == mode,
                            onClick = { viewModel.setCameraMode(mode) }
                        )
                    }
                }

                PanelDivider()

                // ── Section: Karte ───────────────────────────────────────
                SectionLabel("KARTE")
                
                val isDarkMode = mapStyle == "NIGHTLIGHTS" || mapStyle == "MINIMALIST_DARK"
                val isMinimalist = mapStyle == "MINIMALIST" || mapStyle == "MINIMALIST_DARK"

                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToggleChip(
                        label = "☀️ LIGHT",
                        isActive = !isDarkMode,
                        onClick = { 
                            if (isMinimalist) viewModel.setMapStyle("MINIMALIST") 
                            else viewModel.setMapStyle("SATELLITE")
                        }
                    )
                    ToggleChip(
                        label = "🌙 DARK",
                        isActive = isDarkMode,
                        onClick = { 
                            if (isMinimalist) viewModel.setMapStyle("MINIMALIST_DARK") 
                            else viewModel.setMapStyle("NIGHTLIGHTS")
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip(
                        label = "SATELLIT",
                        isActive = !isMinimalist,
                        onClick = { 
                            if (isDarkMode) viewModel.setMapStyle("NIGHTLIGHTS") 
                            else viewModel.setMapStyle("SATELLITE")
                        }
                    )
                    ToggleChip(
                        label = "MINIMALIST",
                        isActive = isMinimalist,
                        onClick = { 
                            if (isDarkMode) viewModel.setMapStyle("MINIMALIST_DARK") 
                            else viewModel.setMapStyle("MINIMALIST")
                        }
                    )
                }

                PanelDivider()

                // ── Section: Sunset Mode ─────────────────────────────────
                SectionLabel("SUNSET")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip(
                        label = "CUSTOM",
                        isActive = sunsetMode == "CUSTOM",
                        onClick = { viewModel.setSunsetMode("CUSTOM") }
                    )
                    ToggleChip(
                        label = "DEFAULT",
                        isActive = sunsetMode == "DEFAULT",
                        onClick = { viewModel.setSunsetMode("DEFAULT") }
                    )
                }

                PanelDivider()

                // ── Section: Playback ────────────────────────────────────
                SectionLabel("PLAYBACK")

                // Play / Pause toggle
                Button(
                    onClick = { viewModel.togglePlayback() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color(0xFF2E7D32) else Color(0xFFE65100)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        if (isPlaying) "⏸ Pause" else "▶ Play",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Speed buttons
                Text("Geschwindigkeit:", fontSize = 10.sp, color = LabelColor)
                Spacer(Modifier.height(2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(1, 2, 5, 10, 60).forEach { speed ->
                        ToggleChip(
                            label = "${speed}×",
                            isActive = speedMultiplier == speed,
                            onClick = { viewModel.setSpeedMultiplier(speed) }
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Seek slider
                Text("Seek:", fontSize = 10.sp, color = LabelColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = seekValue,
                        onValueChange = { seekValue = it },
                        onValueChangeFinished = { viewModel.seekTo(seekValue) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = ActiveColor,
                            activeTrackColor = ActiveColor,
                            inactiveTrackColor = InactiveColor
                        )
                    )
                    Text(
                        "%.0f%%".format(seekValue * 100),
                        fontSize = 10.sp,
                        color = LabelColor,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(36.dp)
                    )
                }

                PanelDivider()

                // ── Section: Performance ─────────────────────────────────
                SectionLabel("PERFORMANCE")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("AUTO", "PERFORMANCE", "QUALITY").forEach { mode ->
                        ToggleChip(
                            label = mode,
                            isActive = fpsMode == mode,
                            onClick = { viewModel.setFpsMode(mode) }
                        )
                    }
                }

                PanelDivider()

                // ── Section: Status (read-only) ──────────────────────────
                SectionLabel("STATUS")
                StatusLine("Modus", cameraMode)
                StatusLine("Map", mapStyle)
                StatusLine("Speed", "${speedMultiplier}×")
                StatusLine("FPS", fpsMode)
                StatusLine("Playing", if (isPlaying) "YES" else "NO")
            }
        }

        // ── Toggle Button ────────────────────────────────────────────────
        Button(
            onClick = { isPanelOpen = !isPanelOpen },
            modifier = Modifier
                .padding(end = 8.dp, bottom = 16.dp)
                .size(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPanelOpen) ActiveColor else ToggleBg.copy(alpha = 0.9f)
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Text(
                "⚙",
                fontSize = 20.sp,
                color = if (isPanelOpen) Color.Black else TextColor
            )
        }
    }
}

// ── Reusable Components ──────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = LabelColor,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) ActiveColor else InactiveColor
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.height(32.dp),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = if (isActive) Color.Black else TextColor
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            "$label: ",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = LabelColor
        )
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = ActiveColor
        )
    }
}

@Composable
private fun PanelDivider() {
    HorizontalDivider(
        color = DividerColor,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}
