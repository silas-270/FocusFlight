package com.example.focusflight.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.theme.Haze

/**
 * The small-caps section caption style (bold, letter-spaced, Haze) used above lists and form
 * sections - e.g. "COMPLETED", "SUGGESTED DESTINATIONS", "FLY FROM ANYWHERE", "CAMERA VIEW".
 * Deliberately a different, quieter tier than `account/SectionHeader.kt` (Amber, larger, more
 * prominent) and `ModalTitle` (modal-only) - those stay separate on purpose.
 */
@Composable
fun CaptionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = Haze
    )
}
