package com.example.focusflight.ui.components

import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Midnight
import com.example.focusflight.ui.theme.OffWhite

/**
 * The back-button title bar shared by every full-screen destination (Check-In, Flight Search,
 * Create/Challenges, Account): a bold letter-spaced title, a back arrow, Midnight background.
 * Previously reimplemented per-screen in two color/spacing variants - [accentColor]/[letterSpacing]
 * cover both without forcing every screen's title to look identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Amber,
    letterSpacing: TextUnit = 3.sp,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
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
            IconButton(onClick = onBackClick) {
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
