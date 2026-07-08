package com.example.focusflight

import com.google.androidgamesdk.GameActivity
import android.os.Bundle

class CesiumActivity : GameActivity() {
    companion object {
        init {
            // Load the native wgpu/winit library compiled by Cargo NDK
            System.loadLibrary("cesium_rs")
        }
    }
}
