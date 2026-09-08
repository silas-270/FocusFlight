package com.example.focusflight.data.model

/** Persisted via `PreferencesRepository.getThemeMode()`/`setThemeMode()`. [SYSTEM] is the
 *  default - the app follows the device's light/dark setting until the pilot explicitly toggles
 *  "Sky mode" on the Account screen, at which point the explicit choice sticks. */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}
