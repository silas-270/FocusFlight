package com.example.focusflight.engine.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug-build-only automation hook for on-device performance testing (see
 * tools/run_perf_scenario.sh at the repo root). Registered only via
 * `app/src/debug/AndroidManifest.xml`, so it's never present in a release APK.
 *
 * Triggered with:
 * `adb shell am broadcast -a com.example.focusflight.PERF_SCENARIO --ei scenario_id <N>`
 *
 * Forwards straight to [CesiumLiveJniBridge.nativeRunPerfScenario], which is
 * itself only exported by a `-Pcesium.profile=profiling` native build — on a
 * normal debug build (no profiling `.so`) this throws UnsatisfiedLinkError,
 * which is caught and logged rather than crashing the app.
 */
class PerfScenarioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scenarioId = intent.getIntExtra("scenario_id", -1)
        if (scenarioId < 0) {
            Log.w(TAG, "Ignoring PERF_SCENARIO broadcast with no/invalid scenario_id extra")
            return
        }
        try {
            CesiumLiveJniBridge.nativeRunPerfScenario(scenarioId)
            Log.i(TAG, "Ran perf scenario $scenarioId")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "nativeRunPerfScenario unavailable — rebuild with -Pcesium.profile=profiling", e)
        }
    }

    private companion object {
        const val TAG = "PerfScenarioReceiver"
    }
}
