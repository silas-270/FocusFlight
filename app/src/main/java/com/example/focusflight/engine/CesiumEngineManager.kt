package com.example.focusflight.engine

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Binds the Cesium/wgpu Rust engine to the Android Activity lifecycle.
 *
 * This replaces all ad-hoc LaunchedEffect-based suspend/resume calls that were
 * previously scattered throughout CesiumGameActivity's Compose tree. The engine
 * now strictly follows the Activity lifecycle: it renders only when the Activity
 * is resumed and in the foreground.
 *
 * Attach exactly once in CesiumGameActivity.onCreate() before setContent().
 * Because it is attached to the Activity's lifecycle (not the Compose scope),
 * it is not re-created on recomposition.
 */
class CesiumEngineManager : DefaultLifecycleObserver {

    /**
     * Activity is becoming visible. Wake the winit event loop from ControlFlow::Wait
     * so it stops blocking and is ready to render when it next navigates to a flight screen.
     * Does NOT enable rendering — that is route-scoped via nativeSetRenderingEnabled,
     * which is driven by the shouldRender flag in Compose.
     */
    override fun onStart(owner: LifecycleOwner) {
        CesiumBridge.nativeSetSuspended(false)
    }

    /**
     * Activity is no longer visible. Put winit into ControlFlow::Wait to stop
     * burning CPU/GPU cycles in the background.
     */
    override fun onStop(owner: LifecycleOwner) {
        CesiumBridge.nativeSetSuspended(true)
    }

    /**
     * Activity is permanently destroyed (not a config change).
     * Sends EngineEvent::Destroy to the winit event loop, causing it to exit cleanly
     * and drop WgpuState, which releases all Vulkan resources.
     *
     * SAFETY: Guarded by isChangingConfigurations to prevent engine teardown during
     * rotation or other config-change-triggered Activity recreation. Without this guard,
     * a device rotation mid-flight would destroy the Vulkan device while the user
     * expects the flight to continue.
     *
     * VERIFICATION STATUS: Implemented, unverified — needs runtime test on a physical
     * device to confirm that the winit event loop cleanly exits and Vulkan memory is
     * released before the process terminates.
     */
    override fun onDestroy(owner: LifecycleOwner) {
        val activity = owner as? Activity ?: return
        if (!activity.isChangingConfigurations) {
            CesiumBridge.nativeDestroyEngine()
        }
    }
}
