package com.example.focusflight.ui.viewmodel

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusflight.flight.FlightTelemetryGenerator
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CameraMode {
    FREE, TRACKING, COCKPIT
}

class FlightViewModel : ViewModel() {

    private val TAG = "FlightViewModel"
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── WebView reference ────────────────────────────────────────────────
    // Stored via setter, called from CesiumGlobeView's onWebViewReady.
    // Using a simple mutable property is the cleanest approach here because:
    // - The WebView is a View, not a state object — it shouldn't live in StateFlow
    // - The existing architecture already uses a callback (onWebViewReady) to deliver it
    // - No need for lifecycle complexity; the ViewModel outlives the WebView
    private var webView: WebView? = null

    fun setWebView(wv: WebView) {
        webView = wv
    }

    // ── State Flows ──────────────────────────────────────────────────────

    private val _cameraMode = MutableStateFlow("TRACKING")
    val cameraMode: StateFlow<String> = _cameraMode.asStateFlow()

    private val _mapStyle = MutableStateFlow("OSM")
    val mapStyle: StateFlow<String> = _mapStyle.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1)
    val speedMultiplier: StateFlow<Int> = _speedMultiplier.asStateFlow()

    private val _fpsMode = MutableStateFlow("AUTO")
    val fpsMode: StateFlow<String> = _fpsMode.asStateFlow()

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    // Legacy accessor kept for compatibility with existing code
    private val _currentMode = MutableStateFlow(CameraMode.FREE)
    val currentMode: StateFlow<CameraMode> = _currentMode.asStateFlow()

    // ── Engine Initialization ────────────────────────────────────────────

    fun setEngineReady() {
        _isEngineReady.value = true
        Log.d(TAG, "Engine ready — generating telemetry and sending to JS")

        viewModelScope.launch(Dispatchers.Default) {
            val telemetry = FlightTelemetryGenerator.generate(
                departureLon = 9.2220,
                departureLat = 48.6899,
                arrivalLon = 8.5706,
                arrivalLat = 50.0333,
                totalDurationMs = 1_800_000L, // 30 minutes
                depHeadingDeg = 252.0,
                arrHeadingDeg = 248.0
            )

            val jsonString = gson.toJson(telemetry)
            Log.d(TAG, "Generated ${telemetry.size} telemetry points, JSON length: ${jsonString.length}")

            mainHandler.post {
                webView?.let { wv ->
                    wv.evaluateJavascript("window.loadTelemetry($jsonString)", null)
                    Log.d(TAG, "loadTelemetry() called")

                    wv.evaluateJavascript("window.setMode('TRACKING')", null)
                    Log.d(TAG, "setMode('TRACKING') called")
                } ?: Log.e(TAG, "WebView is null — cannot send telemetry")
            }
        }
    }

    // ── Camera Mode ──────────────────────────────────────────────────────

    fun setMode(mode: CameraMode) {
        _currentMode.value = mode
        _cameraMode.value = mode.name
    }

    fun setCameraMode(modeStr: String) {
        _cameraMode.value = modeStr
        try {
            _currentMode.value = CameraMode.valueOf(modeStr)
        } catch (_: IllegalArgumentException) {
            // Unknown mode from JS — keep string state but can't map to enum
        }

        mainHandler.post {
            webView?.evaluateJavascript("window.setMode('$modeStr')", null)
        }
    }

    /** Called from Bridge only — updates state without calling back to JS */
    fun onCameraModeChangedFromBridge(modeStr: String) {
        _cameraMode.value = modeStr
        try {
            _currentMode.value = CameraMode.valueOf(modeStr)
        } catch (_: IllegalArgumentException) {}
    }

    // ── Map Style ────────────────────────────────────────────────────────

    fun setMapStyle(style: String) {
        _mapStyle.value = style
        mainHandler.post {
            webView?.evaluateJavascript("window.setMapStyle('$style')", null)
        }
    }

    /** Called from Bridge only */
    fun onMapStyleChangedFromBridge(style: String) {
        _mapStyle.value = style
    }

    // ── Playback ─────────────────────────────────────────────────────────

    fun play() {
        _isPlaying.value = true
        mainHandler.post {
            webView?.evaluateJavascript("window.play()", null)
        }
    }

    fun pause() {
        _isPlaying.value = false
        mainHandler.post {
            webView?.evaluateJavascript("window.pause()", null)
        }
    }

    fun togglePlayback() {
        if (_isPlaying.value) pause() else play()
    }

    /** Called from Bridge only */
    fun onPlaybackStateChangedFromBridge(playing: Boolean) {
        _isPlaying.value = playing
    }

    // ── Speed ────────────────────────────────────────────────────────────

    fun setSpeedMultiplier(speed: Int) {
        _speedMultiplier.value = speed
        mainHandler.post {
            webView?.evaluateJavascript("window.setSpeedMultiplier($speed)", null)
        }
    }

    /** Called from Bridge only */
    fun onSpeedChangedFromBridge(speed: Int) {
        _speedMultiplier.value = speed
    }

    // ── Seek ─────────────────────────────────────────────────────────────

    fun seekTo(fraction: Float) {
        mainHandler.post {
            webView?.evaluateJavascript("window.seekTo($fraction)", null)
        }
    }

    // ── FPS Mode ─────────────────────────────────────────────────────────

    fun setFpsMode(mode: String) {
        _fpsMode.value = mode
        mainHandler.post {
            webView?.evaluateJavascript("window.setFpsMode('$mode')", null)
        }
    }

    /** Called from Bridge only */
    fun onFpsModeChangedFromBridge(mode: String) {
        _fpsMode.value = mode
    }

    // ── Cleanup ──────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        webView = null
    }
}
