package com.example.focusflight.ui

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Hub : Screen("hub")
}
