package com.example.focusflight

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import com.example.focusflight.engine.live.CesiumEngineManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.focusflight.data.local.AppDatabase
import com.example.focusflight.data.local.airport.AirportRouteSqliteDataSource
import com.example.focusflight.data.model.FlightMode
import com.example.focusflight.data.repository.AchievementsRepository
import com.example.focusflight.data.repository.AirportRepository
import com.example.focusflight.data.repository.ChallengeRepository
import com.example.focusflight.data.repository.LocalAchievementsRepository
import com.example.focusflight.data.repository.LandingResult
import com.example.focusflight.data.repository.LandingResultChannel
import com.example.focusflight.data.repository.LocalAirportRepository
import com.example.focusflight.data.repository.LocalChallengeRepository
import com.example.focusflight.data.repository.FlightLogRepository
import com.example.focusflight.data.repository.LegacyFlightLogMigrator
import com.example.focusflight.data.repository.LocalFlightLogRepository
import com.example.focusflight.data.repository.LocalUserRepository
import com.example.focusflight.data.repository.PreferencesRepository
import com.example.focusflight.data.repository.UserRepository
import com.example.focusflight.engine.live.CesiumLiveJniBridge
import com.example.focusflight.engine.live.PendingFlightLoader
import com.example.focusflight.ui.Screen
import com.example.focusflight.ui.screens.arrival.ArrivalCelebrationScreen
import com.example.focusflight.ui.screens.challenge.ChallengeCompletionScreen
import com.example.focusflight.ui.screens.challenge.ChallengeProgressScreen
import com.example.focusflight.ui.screens.checkin.CheckInScreen
import com.example.focusflight.ui.screens.flightsearch.FlightSearchScreen
import com.example.focusflight.ui.screens.inflight.InFlightScreen
import com.example.focusflight.ui.screens.onboarding.OnboardingScreen
import com.example.focusflight.ui.theme.FocusFlightTheme
import com.example.focusflight.ui.viewmodel.challenges.ChallengesViewModel
import com.example.focusflight.ui.viewmodel.challenges.ChallengesViewModelFactory
import com.example.focusflight.ui.viewmodel.checkin.CheckInViewModel
import com.example.focusflight.ui.viewmodel.checkin.CheckInViewModelFactory
import com.example.focusflight.ui.viewmodel.flightsearch.FlightSearchViewModel
import com.example.focusflight.ui.viewmodel.flightsearch.FlightSearchViewModelFactory
import com.example.focusflight.ui.viewmodel.hub.HubViewModel
import com.example.focusflight.ui.viewmodel.hub.HubViewModelFactory
import com.example.focusflight.ui.viewmodel.account.AccountViewModel
import com.example.focusflight.ui.viewmodel.account.AccountViewModelFactory
import com.example.focusflight.ui.viewmodel.inflight.InFlightViewModel
import com.example.focusflight.ui.viewmodel.inflight.InFlightViewModelFactory
import com.example.focusflight.ui.viewmodel.onboarding.OnboardingViewModel
import com.example.focusflight.ui.viewmodel.onboarding.OnboardingViewModelFactory
import com.google.androidgamesdk.GameActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class CesiumGameActivity : GameActivity() {

    private lateinit var airportRepository: AirportRepository
    private lateinit var pendingFlightLoader: PendingFlightLoader
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var userRepository: UserRepository
    private lateinit var flightLogRepository: FlightLogRepository
    private lateinit var challengeRepository: ChallengeRepository
    private lateinit var achievementsRepository: AchievementsRepository

    // Bridges the post-landing challenge-check result across the InFlight -> ArrivalCelebration
    // -> (tick-up | completion) navigation hop (Phase 3b - see LandingResultChannel's doc). Needs
    // no Activity/context dependency, so - unlike the repositories above - it's constructed
    // directly here rather than in onCreate.
    private val landingResultChannel = LandingResultChannel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during flight
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Initialize database helper and preferences repository
        airportRepository = LocalAirportRepository(AirportRouteSqliteDataSource(applicationContext))
        pendingFlightLoader = PendingFlightLoader(airportRepository)
        preferencesRepository = PreferencesRepository(applicationContext)

        // Initialize Room database and repositories
        val appDatabase = AppDatabase.getInstance(applicationContext)
        userRepository = LocalUserRepository(appDatabase.userProfileDao())
        flightLogRepository = LocalFlightLogRepository(appDatabase.flightLogDao(), appDatabase.userProfileDao())
        challengeRepository = LocalChallengeRepository(appDatabase.challengeDao(), appDatabase.userProfileDao(), airportRepository)
        achievementsRepository = LocalAchievementsRepository(
            appDatabase.achievementUnlockDao(),
            appDatabase.userProfileDao(),
            airportRepository,
            flightLogRepository
        )

        // Copy reference database asset on first run
        airportRepository.ensureDatabaseCopied()

        // Migrate SharedPreferences flight logs to Room (one-time)
        migrateFlightLogsIfNeeded()

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
                        CesiumLiveJniBridge.nativeSetRenderingEnabled(shouldRender)
                    }

                    // Portrait everywhere except InFlight, which allows free rotation.
                    val isInFlight = currentRoute?.startsWith("in_flight/") == true
                    LaunchedEffect(isInFlight) {
                        requestedOrientation = if (isInFlight) {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } else {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
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
                                    factory = OnboardingViewModelFactory(airportRepository, preferencesRepository, userRepository, cacheDir)
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
                                    factory = HubViewModelFactory(airportRepository, preferencesRepository, flightLogRepository, cacheDir)
                                )
                                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                                com.example.focusflight.ui.screens.hub.HubScreen(
                                    viewModel = viewModel,
                                    onBookFlightClick = {
                                        navController.navigate(Screen.FlightSearch.createRoute())
                                    },
                                    onChallengesClick = {
                                        navController.navigate(Screen.Challenges.route)
                                    },
                                    onResumeFlightClick = { context ->
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                pendingFlightLoader.loadPendingFlight(context.originIata, context.destIata, context.durationMin)
                                            }
                                            // Resumes with the mode/origin/challenge scoping the flight was actually
                                            // booked under - previously this hardcoded FlightMode.STORY, which would
                                            // have silently mis-tagged a resumed Free Mode flight (see
                                            // docs/design/codebase-map.md). context.challengeId carries a resumed
                                            // Route-challenge session's scoping the same way (Phase 3).
                                            navController.navigate(
                                                Screen.InFlight.createRoute(context.originIata, context.flightNumber, context.destIata, context.durationMin, context.mode, context.challengeId)
                                            )
                                        }
                                    },
                                    onPassportClick = {
                                        navController.navigate(Screen.Account.route)
                                    }
                                )
                            }

                            // ── Challenges (modes/goals surface) ──
                            composable(Screen.Challenges.route) {
                                val challengesViewModel: ChallengesViewModel = viewModel(
                                    factory = ChallengesViewModelFactory(challengeRepository, airportRepository, achievementsRepository)
                                )
                                com.example.focusflight.ui.screens.challenges.ChallengesScreen(
                                    viewModel = challengesViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onFreeModeClick = {
                                        navController.navigate(Screen.FlightSearch.createRoute(FlightMode.FREE))
                                    },
                                    onContinueRouteChallenge = { challengeId ->
                                        // Same booking flow Story Mode's "Book a flight" uses, just tagged
                                        // CHALLENGE and scoped to this challenge's own position pointer. See
                                        // docs/design/challenges.md#persistence--route-scoping.
                                        navController.navigate(Screen.FlightSearch.createRoute(FlightMode.CHALLENGE, challengeId))
                                    },
                                    onCreateCustomClick = {
                                        navController.navigate(Screen.CreateChallenge.route)
                                    }
                                )
                            }

                            // ── Custom challenge creation ──
                            composable(Screen.CreateChallenge.route) {
                                val challengesViewModel: ChallengesViewModel = viewModel(
                                    factory = ChallengesViewModelFactory(challengeRepository, airportRepository, achievementsRepository)
                                )
                                com.example.focusflight.ui.screens.challenges.CreateChallengeScreen(
                                    viewModel = challengesViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    // The new challenge is already in a slot by the time we land back:
                                    // activeChallenges is a Flow off the same table the insert wrote to.
                                    onCreated = { navController.popBackStack() }
                                )
                            }

                            // ── Flight Search ──
                            composable(
                                route = Screen.FlightSearch.route,
                                arguments = listOf(
                                    navArgument("mode") {
                                        type = NavType.StringType
                                        defaultValue = FlightMode.STORY.name
                                    },
                                    navArgument("challengeId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    }
                                )
                            ) { backStackEntry ->
                                val mode = backStackEntry.arguments?.getString("mode")
                                    ?.let { runCatching { FlightMode.valueOf(it) }.getOrDefault(FlightMode.STORY) }
                                    ?: FlightMode.STORY
                                val challengeId = backStackEntry.arguments?.getInt("challengeId")
                                    ?.takeIf { it >= 0 }

                                val viewModel: FlightSearchViewModel = viewModel(
                                    factory = FlightSearchViewModelFactory(applicationContext, airportRepository, preferencesRepository, userRepository, flightLogRepository, challengeRepository, mode, challengeId)
                                )
                                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

                                FlightSearchScreen(
                                    viewModel = viewModel,
                                    mode = mode,
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onRouteConfirm = { route ->
                                        coroutineScope.launch {
                                            // `route.originIata` is whatever origin FlightSearchViewModel resolved
                                            // for this session - `currentAirport` for STORY (unchanged), the
                                            // player's picked airport for FREE, that Route challenge's own position
                                            // pointer for CHALLENGE (Phase 3). Reading it off the route (rather
                                            // than re-reading currentAirport here) is what lets both Free Mode's
                                            // and a Route challenge's origin actually reach booking instead of
                                            // being silently overridden.
                                            val originIata = route.originIata
                                            val flightNo = "FF-${kotlin.math.abs(route.destIata.hashCode()) % 1000 + 100}"
                                            val durationMin = route.flightTimeMin
                                            withContext(Dispatchers.IO) {
                                                pendingFlightLoader.loadPendingFlight(originIata, route.destIata, durationMin)
                                            }
                                            navController.navigate(Screen.CheckIn.createRoute(originIata, flightNo, route.destIata, durationMin, mode, challengeId))
                                        }
                                    }
                                )
                            }

                            // ── Check-In ──
                            composable(
                                route = Screen.CheckIn.route,
                                arguments = listOf(
                                    navArgument("originIata") { type = NavType.StringType },
                                    navArgument("flightNo") { type = NavType.StringType },
                                    navArgument("destIata") { type = NavType.StringType },
                                    navArgument("durationMin") { type = NavType.IntType },
                                    navArgument("mode") { type = NavType.StringType },
                                    navArgument("challengeId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    }
                                )
                            ) { backStackEntry ->
                                val originIata = backStackEntry.arguments?.getString("originIata") ?: ""
                                val flightNo = backStackEntry.arguments?.getString("flightNo") ?: ""
                                val destIata = backStackEntry.arguments?.getString("destIata") ?: ""
                                val durationMin = backStackEntry.arguments?.getInt("durationMin") ?: 0
                                val mode = backStackEntry.arguments?.getString("mode")
                                    ?.let { runCatching { FlightMode.valueOf(it) }.getOrDefault(FlightMode.STORY) }
                                    ?: FlightMode.STORY
                                // Pure passthrough here (CheckInViewModel doesn't need it) - carried forward to
                                // InFlight, which is where a CHALLENGE session's scoping is actually consumed.
                                val challengeId = backStackEntry.arguments?.getInt("challengeId")
                                    ?.takeIf { it >= 0 }

                                val viewModel: CheckInViewModel = viewModel(
                                    factory = CheckInViewModelFactory(airportRepository, originIata, destIata, flightNo)
                                )

                                CheckInScreen(
                                    viewModel = viewModel,
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onStartFlight = { fn, di, dm ->
                                        preferencesRepository.clearActiveFlightProgress(fn)
                                        preferencesRepository.saveActiveFlightContext(fn, originIata, di, dm, mode, challengeId)
                                        navController.navigate(Screen.InFlight.createRoute(originIata, fn, di, dm, mode, challengeId)) {
                                            popUpTo(Screen.CheckIn.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── In-Flight ──
                            composable(
                                route = Screen.InFlight.route,
                                arguments = listOf(
                                    navArgument("originIata") { type = NavType.StringType },
                                    navArgument("flightNo") { type = NavType.StringType },
                                    navArgument("destIata") { type = NavType.StringType },
                                    navArgument("durationMin") { type = NavType.IntType },
                                    navArgument("mode") { type = NavType.StringType },
                                    navArgument("challengeId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    }
                                )
                            ) { backStackEntry ->
                                val originIata = backStackEntry.arguments?.getString("originIata") ?: ""
                                val flightNo = backStackEntry.arguments?.getString("flightNo") ?: ""
                                val destIata = backStackEntry.arguments?.getString("destIata") ?: ""
                                val durationMin = backStackEntry.arguments?.getInt("durationMin") ?: 0
                                val mode = backStackEntry.arguments?.getString("mode")
                                    ?.let { runCatching { FlightMode.valueOf(it) }.getOrDefault(FlightMode.STORY) }
                                    ?: FlightMode.STORY
                                // Consumed by InFlightViewModel.checkAchievementsAndChallenges() on landing - see
                                // docs/design/challenges.md#persistence--route-scoping.
                                val challengeId = backStackEntry.arguments?.getInt("challengeId")
                                    ?.takeIf { it >= 0 }

                                val viewModel: InFlightViewModel = viewModel(
                                    factory = InFlightViewModelFactory(airportRepository, preferencesRepository, flightLogRepository, challengeRepository, landingResultChannel, cacheDir, flightNo, originIata, destIata, durationMin, mode, challengeId)
                                )

                                InFlightScreen(
                                    viewModel = viewModel,
                                    onLandingCelebration = { rank ->
                                        preferencesRepository.clearActiveFlightContext()
                                        navController.navigate(Screen.ArrivalCelebration.createRoute(flightNo, destIata, durationMin, rank, mode)) {
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
                                    navArgument("rank") { type = NavType.StringType },
                                    navArgument("mode") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val flightNo = backStackEntry.arguments?.getString("flightNo") ?: ""
                                val destIata = backStackEntry.arguments?.getString("destIata") ?: ""
                                val durationMin = backStackEntry.arguments?.getInt("durationMin") ?: 0
                                val rank = backStackEntry.arguments?.getString("rank") ?: ""
                                // Not consumed by this screen itself - mechanics.md's post-landing
                                // pipeline step 5 branches purely on `landingResultChannel`, not on mode.
                                @Suppress("UNUSED_VARIABLE")
                                val mode = backStackEntry.arguments?.getString("mode")
                                    ?.let { runCatching { FlightMode.valueOf(it) }.getOrDefault(FlightMode.STORY) }
                                    ?: FlightMode.STORY
                                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

                                ArrivalCelebrationScreen(
                                    flightNo = flightNo,
                                    destIata = destIata,
                                    durationMin = durationMin,
                                    rank = rank,
                                    onContinue = {
                                        // mechanics.md's post-landing pipeline step 5: the rank stamp above
                                        // always shows first, unchanged - this is the "always sequenced,
                                        // never replaced" hand-off into whatever step 4's challenge check
                                        // found (Phase 3b). `first { it != Pending }` awaits a resolved value
                                        // rather than racing InFlightViewModel's IO-dispatched check - see
                                        // LandingResultChannel's doc for why Pending is never itself acted on.
                                        coroutineScope.launch {
                                            when (landingResultChannel.result.first { it != LandingResult.Pending }) {
                                                is LandingResult.ChallengeCompleted ->
                                                    navController.navigate(Screen.ChallengeCompletion.route) {
                                                        popUpTo(Screen.ArrivalCelebration.route) { inclusive = true }
                                                    }
                                                is LandingResult.ChallengeAdvanced ->
                                                    navController.navigate(Screen.ChallengeProgress.route) {
                                                        popUpTo(Screen.ArrivalCelebration.route) { inclusive = true }
                                                    }
                                                LandingResult.None, LandingResult.Pending ->
                                                    navController.navigate(Screen.Hub.route) {
                                                        popUpTo(Screen.Hub.route) { inclusive = true }
                                                    }
                                            }
                                        }
                                    }
                                )
                            }

                            // ── Challenge per-leg tick-up (Phase 3b) ──
                            composable(Screen.ChallengeProgress.route) {
                                val outcome = landingResultChannel.result.value as? LandingResult.ChallengeAdvanced
                                ChallengeProgressScreen(
                                    challengeName = outcome?.name ?: "",
                                    challengeType = outcome?.type ?: com.example.focusflight.data.model.ChallengeType.DISTANCE,
                                    oldProgress = outcome?.oldProgress ?: 0f,
                                    newProgress = outcome?.newProgress ?: 0f,
                                    onContinue = {
                                        navController.navigate(Screen.Hub.route) {
                                            popUpTo(Screen.Hub.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── Challenge completion celebration (Phase 3b) ──
                            composable(Screen.ChallengeCompletion.route) {
                                val outcome = landingResultChannel.result.value as? LandingResult.ChallengeCompleted
                                ChallengeCompletionScreen(
                                    challengeName = outcome?.name ?: "",
                                    challengeType = outcome?.type ?: com.example.focusflight.data.model.ChallengeType.DISTANCE,
                                    onContinue = {
                                        navController.navigate(Screen.Hub.route) {
                                            popUpTo(Screen.Hub.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── Account / Passport ──
                            composable(Screen.Account.route) {
                                val viewModel: AccountViewModel = viewModel(
                                    factory = AccountViewModelFactory(applicationContext, userRepository, flightLogRepository, airportRepository, preferencesRepository, achievementsRepository)
                                )
                                
                                com.example.focusflight.ui.screens.account.AccountScreen(
                                    viewModel = viewModel,
                                    onBackClick = { navController.popBackStack() }
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

    private fun migrateFlightLogsIfNeeded() {
        val prefs = getSharedPreferences("focus_flight_prefs", MODE_PRIVATE)
        val migrator = LegacyFlightLogMigrator(
            prefs, userRepository, flightLogRepository, airportRepository,
            fallbackHomeAirportIata = { preferencesRepository.getHomeAirport() }
        )
        runBlocking(Dispatchers.IO) {
            migrator.migrateIfNeeded()
        }
    }
}
