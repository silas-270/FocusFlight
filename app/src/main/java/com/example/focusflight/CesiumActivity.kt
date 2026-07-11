package com.example.focusflight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import com.example.focusflight.ui.theme.FocusFlightTheme
import com.example.focusflight.ui.viewmodel.OnboardingViewModel
import com.example.focusflight.ui.viewmodel.OnboardingViewModelFactory

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
                        startDestination = startDestination
                    ) {
                        composable(Screen.Onboarding.route) {
                            val viewModel: OnboardingViewModel by viewModels {
                                OnboardingViewModelFactory(databaseHelper, preferencesRepository)
                            }
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
                            val viewModel: com.example.focusflight.ui.viewmodel.HubViewModel by viewModels {
                                com.example.focusflight.ui.viewmodel.HubViewModelFactory(databaseHelper, preferencesRepository, cacheDir)
                            }
                            com.example.focusflight.ui.screens.HubScreen(
                                viewModel = viewModel,
                                onBookFlightClick = {
                                    // TODO: Navigate to Flight Search
                                },
                                onPassportClick = {
                                    // TODO: Navigate to Passport
                                },
                                onSettingsClick = {
                                    // TODO: Navigate to Settings
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
