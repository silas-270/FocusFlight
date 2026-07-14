package com.example.focusflight

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import com.example.focusflight.engine.CesiumEngineManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.focusflight.data.repository.FlightDatabaseHelper
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.engine.CesiumBridge
import com.example.focusflight.ui.Screen
import com.example.focusflight.ui.screens.ArrivalCelebrationScreen
import com.example.focusflight.ui.screens.CheckInScreen
import com.example.focusflight.ui.screens.FlightSearchScreen
import com.example.focusflight.ui.screens.InFlightScreen
import com.example.focusflight.ui.screens.OnboardingScreen
import com.example.focusflight.ui.theme.FocusFlightTheme
import com.example.focusflight.ui.viewmodel.CheckInViewModel
import com.example.focusflight.ui.viewmodel.CheckInViewModelFactory
import com.example.focusflight.ui.viewmodel.FlightSearchViewModel
import com.example.focusflight.ui.viewmodel.FlightSearchViewModelFactory
import com.example.focusflight.ui.viewmodel.HubViewModel
import com.example.focusflight.ui.viewmodel.HubViewModelFactory
import com.example.focusflight.ui.viewmodel.InFlightViewModel
import com.example.focusflight.ui.viewmodel.InFlightViewModelFactory
import com.example.focusflight.ui.viewmodel.OnboardingViewModel
import com.example.focusflight.ui.viewmodel.OnboardingViewModelFactory
import com.google.androidgamesdk.GameActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CesiumGameActivity : GameActivity() {

    private lateinit var databaseHelper: FlightDatabaseHelper
    private lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during flight
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize database helper and preferences repository (absorbed from CesiumActivity)
        databaseHelper = FlightDatabaseHelper(applicationContext)
        preferencesRepository = PreferencesRepository(applicationContext)

        // Copy database asset on first run
        databaseHelper.ensureDatabaseCopied()

        // Attach the lifecycle observer ONCE before Compose content is set.
        // This is not re-triggered on recomposition because it targets the Activity lifecycle,
        // not the Compose recomposition lifecycle.
        lifecycle.addObserver(CesiumEngineManager())

        val composeView = ComposeView(this).apply {
            setContent {
                FocusFlightTheme {
                    val navController = rememberNavController()
                    val startDestination = if (preferencesRepository.isOnboardingCompleted()) {
                        Screen.Hub.route
                    } else {
                        Screen.Onboarding.route
                    }

                    // ── Rendering toggle: enable wgpu rendering on flight screens only ──
                    val currentEntry = navController.currentBackStackEntryAsState().value
                    val currentRoute = currentEntry?.destination?.route
                    val shouldRender = currentRoute?.startsWith("check_in/") == true
                            || currentRoute?.startsWith("in_flight/") == true

                    // Rendering enable/disable is route-scoped (only on flight screens).
                    // Suspend/resume (winit sleep/wake) is lifecycle-scoped via CesiumEngineManager.
                    LaunchedEffect(shouldRender) {
                        CesiumBridge.nativeSetRenderingEnabled(shouldRender)
                    }

                    val bgColor = if (shouldRender) {
                        androidx.compose.ui.graphics.Color.Transparent
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.background
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxSize(),
                        color = bgColor
                    ) {
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
                            // ── Onboarding ──
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

                            // ── Hub ──
                            composable(Screen.Hub.route) {
                                val viewModel: HubViewModel = viewModel(
                                    factory = HubViewModelFactory(databaseHelper, preferencesRepository, cacheDir)
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

                            // ── Flight Search ──
                            composable(Screen.FlightSearch.route) {
                                val viewModel: FlightSearchViewModel = viewModel(
                                    factory = FlightSearchViewModelFactory(databaseHelper, preferencesRepository)
                                )
                                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

                                FlightSearchScreen(
                                    viewModel = viewModel,
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onRouteConfirm = { route ->
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                val originIata = preferencesRepository.getCurrentAirport() ?: "STR"
                                                val origin = databaseHelper.getAirportByIata(originIata)
                                                val dest = databaseHelper.getAirportByIata(route.destIata)

                                                val flightNo = "FF-${kotlin.math.abs(route.destIata.hashCode()) % 1000 + 100}"
                                                val durationMin = route.flightTimeMin

                                                if (origin != null && dest != null) {
                                                    val originRunway = databaseHelper.getRunwaysForAirport(origin.id).maxByOrNull { it.lengthFt }
                                                    val destRunway = databaseHelper.getRunwaysForAirport(dest.id).maxByOrNull { it.lengthFt }
                                                    val allRunways = listOfNotNull(originRunway, destRunway)

                                                    val airportIds = allRunways.map { it.airportId }.toIntArray()
                                                    val lengthFt = allRunways.map { it.lengthFt }.toFloatArray()
                                                    val widthFt = allRunways.map { it.widthFt }.toFloatArray()
                                                    val leHeading = allRunways.map { it.leHeading }.toFloatArray()
                                                    val leLat = allRunways.map { it.leLat }.toDoubleArray()
                                                    val leLon = allRunways.map { it.leLon }.toDoubleArray()
                                                    val heHeading = allRunways.map { it.heHeading }.toFloatArray()
                                                    val heLat = allRunways.map { it.heLat }.toDoubleArray()
                                                    val heLon = allRunways.map { it.heLon }.toDoubleArray()

                                                    CesiumBridge.nativeSetRunways(
                                                        airportIds, lengthFt, widthFt, leHeading, leLat, leLon,
                                                        heHeading, heLat, heLon
                                                    )

                                                    CesiumBridge.nativeSetPendingFlight(
                                                        origin.lon, origin.lat, dest.lon, dest.lat, (durationMin * 60 * 1000).toLong()
                                                    )
                                                    CesiumBridge.nativeLoadPendingFlight()
                                                }

                                                withContext(Dispatchers.Main) {
                                                    navController.navigate(Screen.CheckIn.createRoute(flightNo, route.destIata, durationMin))
                                                }
                                            }
                                        }
                                    }
                                )
                            }

                            // ── Check-In ──
                            composable(
                                route = Screen.CheckIn.route,
                                arguments = listOf(
                                    navArgument("flightNo") { type = NavType.StringType },
                                    navArgument("destIata") { type = NavType.StringType },
                                    navArgument("durationMin") { type = NavType.IntType }
                                )
                            ) { backStackEntry ->
                                val flightNo = backStackEntry.arguments?.getString("flightNo") ?: ""
                                val destIata = backStackEntry.arguments?.getString("destIata") ?: ""
                                val durationMin = backStackEntry.arguments?.getInt("durationMin") ?: 0

                                val viewModel: CheckInViewModel = viewModel(
                                    factory = CheckInViewModelFactory(databaseHelper, preferencesRepository, destIata, flightNo)
                                )

                                CheckInScreen(
                                    viewModel = viewModel,
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onStartFlight = { fn, di, dm ->
                                        navController.navigate(Screen.InFlight.createRoute(fn, di, dm)) {
                                            popUpTo(Screen.CheckIn.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── In-Flight ──
                            composable(
                                route = Screen.InFlight.route,
                                arguments = listOf(
                                    navArgument("flightNo") { type = NavType.StringType },
                                    navArgument("destIata") { type = NavType.StringType },
                                    navArgument("durationMin") { type = NavType.IntType }
                                )
                            ) { backStackEntry ->
                                val flightNo = backStackEntry.arguments?.getString("flightNo") ?: ""
                                val destIata = backStackEntry.arguments?.getString("destIata") ?: ""
                                val durationMin = backStackEntry.arguments?.getInt("durationMin") ?: 0

                                val viewModel: InFlightViewModel = viewModel(
                                    factory = InFlightViewModelFactory(databaseHelper, preferencesRepository, cacheDir, flightNo, destIata, durationMin)
                                )

                                InFlightScreen(
                                    viewModel = viewModel,
                                    onLandingCelebration = { rank ->
                                        navController.navigate(Screen.ArrivalCelebration.createRoute(flightNo, destIata, durationMin, rank)) {
                                            popUpTo(Screen.InFlight.route) { inclusive = true }
                                        }
                                    },
                                    onExitFlight = {
                                        navController.navigate(Screen.Hub.route) {
                                            popUpTo(Screen.Hub.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── Arrival Celebration ──
                            composable(
                                route = Screen.ArrivalCelebration.route,
                                arguments = listOf(
                                    navArgument("flightNo") { type = NavType.StringType },
                                    navArgument("destIata") { type = NavType.StringType },
                                    navArgument("durationMin") { type = NavType.IntType },
                                    navArgument("rank") { type = NavType.StringType }
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

        addContentView(composeView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }
}
