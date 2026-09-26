package com.silas270.blocktime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.silas270.blocktime.data.model.ThemeMode

/** App-scoped holder for the current [ThemeMode], mirroring how `MapImageCache.pinnedIatas` is
 *  used elsewhere as a plain observable singleton - there's no DI framework here, so this is the
 *  established way to share state above the per-screen ViewModel scope. `CesiumGameActivity`
 *  seeds [current] from `PreferencesRepository.getThemeMode()` on launch; the Settings
 *  toggle updates both this and the persisted value on change. `BlocktimeTheme` reads [current]
 *  directly, so a toggle repaints the whole app immediately. */
object ThemeModeHolder {
    var current by mutableStateOf(ThemeMode.SYSTEM)
}
