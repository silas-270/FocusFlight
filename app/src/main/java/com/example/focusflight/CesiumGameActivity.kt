package com.example.focusflight

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.ui.screens.InFlightScreen
import com.example.focusflight.ui.theme.FocusFlightTheme
import com.example.focusflight.ui.viewmodel.InFlightViewModel
import com.example.focusflight.ui.viewmodel.InFlightViewModelFactory
import com.google.androidgamesdk.GameActivity

class CesiumGameActivity : GameActivity() {

    private lateinit var viewModel: InFlightViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during flight
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Extract intent extras
        val flightNo = intent.getStringExtra("flightNo") ?: ""
        val destIata = intent.getStringExtra("destIata") ?: ""
        val durationMin = intent.getIntExtra("durationMin", 0)

        val databaseHelper = FlightDatabaseHelper(applicationContext)
        val preferencesRepository = PreferencesRepository(applicationContext)

        viewModel = ViewModelProvider(
            this,
            InFlightViewModelFactory(
                databaseHelper,
                preferencesRepository,
                cacheDir,
                flightNo,
                destIata,
                durationMin
            )
        )[InFlightViewModel::class.java]

        val composeView = ComposeView(this).apply {
            setContent {
                FocusFlightTheme {
                    InFlightScreen(
                        viewModel = viewModel,
                        onLandingCelebration = { rank ->
                            val resultIntent = android.content.Intent().apply {
                                putExtra("rank", rank)
                                putExtra("flightNo", flightNo)
                                putExtra("destIata", destIata)
                                putExtra("durationMin", durationMin)
                            }
                            setResult(android.app.Activity.RESULT_OK, resultIntent)
                            finish()
                        },
                        onExitFlight = {
                            setResult(android.app.Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
        
        addContentView(composeView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }
}
