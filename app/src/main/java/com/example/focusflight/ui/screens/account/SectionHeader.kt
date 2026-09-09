package com.example.focusflight.ui.screens.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Backward compatibility alias for the centralized [com.example.focusflight.ui.components.SectionHeader].
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    com.example.focusflight.ui.components.SectionHeader(title = title, modifier = modifier)
}
