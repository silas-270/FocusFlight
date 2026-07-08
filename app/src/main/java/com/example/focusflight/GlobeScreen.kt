package com.example.focusflight

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.focusflight.ui.components.CesiumGlobeView
import com.example.focusflight.ui.components.DebugControlPanel
import com.example.focusflight.ui.viewmodel.FlightViewModel

@Composable
fun GlobeScreen() {
    val viewModel: FlightViewModel = viewModel()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        CesiumGlobeView(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // Debug overlay — collapsible panel with ⚙ toggle button
        DebugControlPanel(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxSize()
        )

        // Button to launch the native CesiumRS engine activity
        Button(
            onClick = {
                val intent = Intent(context, CesiumActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text("Launch Native CesiumRS Engine")
        }
    }
}

