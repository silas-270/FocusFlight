package com.silas270.blocktime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.Slate

private val CloseSquareSize = 32.dp
private val CloseTouchTarget = 48.dp

/**
 * The app's close ✕ for panels and modals: a 32dp square drawn inside a 48dp touch target. The
 * target is shifted right by the invisible margin, so the visible square's right edge lines up
 * with the content edge of whatever it sits in.
 */
@Composable
fun CloseSquareButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .offset(x = (CloseTouchTarget - CloseSquareSize) / 2)
            .size(CloseTouchTarget)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(CloseSquareSize)
                .clip(RoundedCornerShape(10.dp))
                .background(Slate)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = contentDescription,
                tint = Haze,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
