package com.silas270.blocktime.ui.screens.inflight

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lets the debug CONTROL broadcast (see CesiumGameActivity) switch the in-flight HUD into and out
 * of scenic mode, so tools/capture_all_screens.sh can capture both without touching the screen.
 * Only the debug receiver ever emits; in release builds nothing does.
 */
internal object InFlightDebugControl {
    private val _scenicMode = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val scenicMode: SharedFlow<Boolean> = _scenicMode.asSharedFlow()

    fun setScenicMode(enabled: Boolean) {
        _scenicMode.tryEmit(enabled)
    }
}
