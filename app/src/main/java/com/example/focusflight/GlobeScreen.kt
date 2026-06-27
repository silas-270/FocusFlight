package com.example.focusflight

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.focusflight.ui.components.CesiumGlobeView
import com.example.focusflight.ui.components.DebugControlPanel
import com.example.focusflight.ui.viewmodel.FlightViewModel

@Composable
fun GlobeScreen() {
    val viewModel: FlightViewModel = viewModel()

    Box(modifier = Modifier.fillMaxSize()) {
        CesiumGlobeView(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
            onWebViewReady = { webView ->
                viewModel.setWebView(webView)
            }
        )

        // Debug overlay — collapsible panel with ⚙ toggle button
        DebugControlPanel(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxSize()
        )
    }
}
