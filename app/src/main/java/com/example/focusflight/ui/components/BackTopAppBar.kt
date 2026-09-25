package com.example.focusflight.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite

/**
 * The back-button title bar shared by every full-screen destination (Check-In, Flight Search,
 * Create/Challenges, Account): a bold letter-spaced title, a back arrow, Midnight background.
 * Previously reimplemented per-screen in two color/spacing variants - [accentColor]/[letterSpacing]
 * cover both without forcing every screen's title to look identical.
 *
 * [scrolled] fades in a hairline along the bar's bottom edge, so content scrolled up under the
 * bar has an edge to disappear behind instead of being cut off mid-card. Pass the list's
 * `canScrollBackward`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Amber,
    letterSpacing: TextUnit = 3.sp,
    scrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val hairlineAlpha by animateFloatAsState(if (scrolled) 1f else 0f, label = "topBarHairline")
    TopAppBar(
        // Drawn after the content: the bar paints its own background, which would cover a line
        // drawn behind it.
        modifier = modifier.drawWithContent {
            drawContent()
            if (hairlineAlpha > 0f) {
                val y = size.height - 0.5.dp.toPx()
                drawLine(Border.copy(alpha = hairlineAlpha), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = letterSpacing
                ),
                color = accentColor
            )
        },
        navigationIcon = {
            // Nudged so the arrow's tip lines up with the screens' ScreenGutter content edge.
            IconButton(onClick = onBackClick, modifier = Modifier.offset(x = 2.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = OffWhite
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight)
    )
}
