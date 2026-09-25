package com.silas270.blocktime.ui.screens.inflight

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.silas270.blocktime.ui.viewmodel.inflight.InFlightViewModel

/** Release stand-in for the debug-only in-flight menu (the real one is in src/debug): draws nothing. */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun FlightDebugMenu(viewModel: InFlightViewModel, panelTop: Dp, onResume: () -> Unit) = Unit
