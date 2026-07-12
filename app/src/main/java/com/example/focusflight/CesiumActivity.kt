package com.example.focusflight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.ui.Screen
import com.example.focusflight.ui.screens.OnboardingScreen
import com.example.focusflight.ui.screens.FlightSearchScreen
import com.example.focusflight.ui.screens.CheckInScreen
import com.example.focusflight.ui.screens.InFlightScreen
import com.example.focusflight.ui.screens.ArrivalCelebrationScreen
import com.example.focusflight.ui.theme.FocusFlightTheme
import com.example.focusflight.ui.viewmodel.OnboardingViewModel
import com.example.focusflight.ui.viewmodel.OnboardingViewModelFactory
import com.example.focusflight.ui.viewmodel.FlightSearchViewModel
import com.example.focusflight.ui.viewmodel.FlightSearchViewModelFactory
import com.example.focusflight.ui.viewmodel.CheckInViewModel
import com.example.focusflight.ui.viewmodel.CheckInViewModelFactory
import com.example.focusflight.ui.viewmodel.InFlightViewModel
import com.example.focusflight.ui.viewmodel.InFlightViewModelFactory

class CesiumActivity : ComponentActivity() {

    private lateinit var databaseHelper: FlightDatabaseHelper
    private lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database helper and preferences repository
        databaseHelper = FlightDatabaseHelper(applicationContext)
        preferencesRepository = PreferencesRepository(applicationContext)

        // Copy database asset on first run
        databaseHelper.ensureDatabaseCopied()

        setContent {
            FocusFlightTheme {
                val navController = rememberNavController()
                val startDestination = if (preferencesRepository.isOnboardingCompleted()) {
                    Screen.Hub.route
                } else {
                    Screen.Onboarding.route
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        enterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        composable(Screen.Onboarding.route) {
                            val viewModel: OnboardingViewModel = viewModel(
                                factory = OnboardingViewModelFactory(databaseHelper, preferencesRepository, cacheDir)
                            )
                            OnboardingScreen(
                                viewModel = viewModel,
                                onOnboardingComplete = {
                                    navController.navigate(Screen.Hub.route) {
                                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.Hub.route) {
                            val viewModel: com.example.focusflight.ui.viewmodel.HubViewModel = viewModel(
                                factory = com.example.focusflight.ui.viewmodel.HubViewModelFactory(databaseHelper, preferencesRepository, cacheDir)
                            )
                            com.example.focusflight.ui.screens.HubScreen(
                                viewModel = viewModel,
                                onBookFlightClick = {
                                    navController.navigate(Screen.FlightSearch.route)
                                },
                                onPassportClick = {
                                    // TODO: Navigate to Passport
                                },
                                onSettingsClick = {
                                    // TODO: Navigate to Settings
                                }
                            )
                        }

                        composable(Screen.FlightSearch.route) {
                            val viewModel: FlightSearchViewModel = viewModel(
                                factory = FlightSearchViewModelFactory(databaseHelper, preferencesRepository)
                            )
                            FlightSearchScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onRouteConfirm = { route ->
                                    navController.navigate(Screen.CheckIn.createRoute(route.destIata))
                                }
                            )
                        }

                        composable(
                            route = Screen.CheckIn.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("destIata") { type = androidx.navigation.NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val destIata = backStackEntry.arguments?.getString("destIata") ?: ""
                            val viewModel: CheckInViewModel = viewModel(
                                factory = CheckInViewModelFactory(databaseHelper, preferencesRepository, destIata)
                            )
                            CheckInScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onStartFlight = { flightNumber, destination, duration ->
                                    navController.navigate(Screen.InFlight.createRoute(flightNumber, destination, duration))
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
                        ) { backStackEntry ->
                            val flightNo = backStackEntry.arguments?.getString("flightNo") ?: ""
                            val destIata = backStackEntry.arguments?.getString("destIata") ?: ""
                            val durationMin = backStackEntry.arguments?.getInt("durationMin") ?: 0

                            val context = androidx.compose.ui.platform.LocalContext.current
                            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                            ) { result ->
                                if (result.resultCode == android.app.Activity.RESULT_OK) {
                                    val data = result.data
                                    val rank = data?.getStringExtra("rank") ?: "CO-PILOT"
                                    navController.navigate(Screen.ArrivalCelebration.createRoute(flightNo, destIata, durationMin, rank)) {
                                        popUpTo(Screen.Hub.route) { inclusive = false }
                                    }
                                } else {
                                    navController.popBackStack()
                                }
                            }

                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val originIata = preferencesRepository.getCurrentAirport() ?: "STR"
                                    val origin = databaseHelper.getAirportByIata(originIata)
                                    val dest = databaseHelper.getAirportByIata(destIata)
                                    
                                    if (origin != null && dest != null) {
                                        com.example.focusflight.engine.CesiumBridge.nativeSetPendingFlight(
                                            origin.lon, origin.lat, dest.lon, dest.lat, (durationMin * 60 * 1000).toLong()
                                        )
                                    }

                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        val intent = android.content.Intent(context, CesiumGameActivity::class.java).apply {
                                            putExtra("flightNo", flightNo)
                                            putExtra("destIata", destIata)
                                            putExtra("durationMin", durationMin)
                                        }
                                        launcher.launch(intent)
                                    }
                                }
                            }
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
                            val flightNo = backStackEntry.arguments?.getString("flightNo") ?: ""
                            val destIata = backStackEntry.arguments?.getString("destIata") ?: ""
                            val durationMin = backStackEntry.arguments?.getInt("durationMin") ?: 0
                            val rank = backStackEntry.arguments?.getString("rank") ?: ""

                            ArrivalCelebrationScreen(
                                flightNo = flightNo,
                                destIata = destIata,
                                durationMin = durationMin,
                                rank = rank,
                                onContinue = {
                                    navController.navigate(Screen.Hub.route) {
                                        popUpTo(Screen.Hub.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
