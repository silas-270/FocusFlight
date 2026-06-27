package com.example.focusflight.bridge

import android.webkit.JavascriptInterface
import android.util.Log
import com.example.focusflight.ui.viewmodel.FlightViewModel

class CesiumBridge(private val viewModel: FlightViewModel) {
    private val TAG = "CesiumBridge"

    @JavascriptInterface
    fun onEngineInitialized() {
        Log.d(TAG, "Cesium Engine Initialized signal received from JS")
        viewModel.setEngineReady()
    }

    @JavascriptInterface
    fun onFlightStarted() {
        // Wird später mit ViewModel-Logik befüllt
    }

    @JavascriptInterface
    fun onCameraModeChanged(modeStr: String) {
        Log.d(TAG, "Camera mode changed from JS to: $modeStr")
        viewModel.onCameraModeChangedFromBridge(modeStr)
    }

    @JavascriptInterface
    fun onMapStyleChanged(style: String) {
        Log.d(TAG, "Map style changed from JS to: $style")
        viewModel.onMapStyleChangedFromBridge(style)
    }

    @JavascriptInterface
    fun onPlaybackStateChanged(playing: Boolean) {
        Log.d(TAG, "Playback state changed from JS to: $playing")
        viewModel.onPlaybackStateChangedFromBridge(playing)
    }

    @JavascriptInterface
    fun onSpeedChanged(speed: Int) {
        Log.d(TAG, "Speed changed from JS to: $speed")
        viewModel.onSpeedChangedFromBridge(speed)
    }

    @JavascriptInterface
    fun onFpsModeChanged(mode: String) {
        Log.d(TAG, "FPS mode changed from JS to: $mode")
        viewModel.onFpsModeChangedFromBridge(mode)
    }
}
