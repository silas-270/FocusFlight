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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.focusflight.ui.Screen
import com.example.focusflight.ui.screens.ArrivalCelebrationScreen
import com.example.focusflight.ui.screens.CheckInScreen
import com.example.focusflight.ui.viewmodel.CheckInViewModel
import com.example.focusflight.ui.viewmodel.CheckInViewModelFactory
import com.google.androidgamesdk.GameActivity

class CesiumGameActivity : GameActivity() {

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

        val composeView = ComposeView(this).apply {
            setContent {
                FocusFlightTheme {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.CheckIn.createRoute(destIata)
                    ) {
                        composable(
                            route = Screen.CheckIn.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("destIata") { type = androidx.navigation.NavType.StringType }
                            )
                        ) {
                            val viewModel = ViewModelProvider(
                                this@CesiumGameActivity,
                                CheckInViewModelFactory(databaseHelper, preferencesRepository, destIata, flightNo)
                            )[CheckInViewModel::class.java]

                            CheckInScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    setResult(android.app.Activity.RESULT_CANCELED)
                                    finish()
                                },
                                onStartFlight = { _, _, _ ->
                                    navController.navigate(Screen.InFlight.createRoute(flightNo, destIata, durationMin)) {
                                        popUpTo(Screen.CheckIn.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = Screen.InFlight.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("flightNo") { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("destIata") { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("durationMin") { type = androidx.navigation.NavType.IntType }
                            )
                        ) {
                            val viewModel = ViewModelProvider(
                                this@CesiumGameActivity,
                                InFlightViewModelFactory(databaseHelper, preferencesRepository, cacheDir, flightNo, destIata, durationMin)
                            )[InFlightViewModel::class.java]

                            InFlightScreen(
                                viewModel = viewModel,
                                onLandingCelebration = { rank ->
                                    navController.navigate(Screen.ArrivalCelebration.createRoute(flightNo, destIata, durationMin, rank)) {
                                        popUpTo(Screen.InFlight.route) { inclusive = true }
                                    }
                                },
                                onExitFlight = {
                                    setResult(android.app.Activity.RESULT_CANCELED)
                                    finish()
                                }
                            )
                        }

                        composable(
                            route = Screen.ArrivalCelebration.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("flightNo") { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("destIata") { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("durationMin") { type = androidx.navigation.NavType.IntType },
                                androidx.navigation.navArgument("rank") { type = androidx.navigation.NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val rank = backStackEntry.arguments?.getString("rank") ?: ""

                            ArrivalCelebrationScreen(
                                flightNo = flightNo,
                                destIata = destIata,
                                durationMin = durationMin,
                                rank = rank,
                                onContinue = {
                                    val resultIntent = android.content.Intent().apply {
                                        putExtra("rank", rank)
                                        putExtra("flightNo", flightNo)
                                        putExtra("destIata", destIata)
                                        putExtra("durationMin", durationMin)
                                    }
                                    setResult(android.app.Activity.RESULT_OK, resultIntent)
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
        
        addContentView(composeView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }
}
