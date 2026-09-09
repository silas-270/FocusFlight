package com.example.focusflight.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.ui.theme.Accent
import com.example.focusflight.ui.theme.Border
import com.example.focusflight.ui.theme.Container
import com.example.focusflight.ui.theme.Danger
import com.example.focusflight.ui.theme.DarkPalette
import com.example.focusflight.ui.theme.Radius
import com.example.focusflight.ui.theme.Spacing
import com.example.focusflight.ui.theme.Success
import com.example.focusflight.ui.theme.TextPrimary
import com.example.focusflight.ui.theme.TextSecondary

enum class ButtonVariant {
    Primary,     // Accent
    Secondary,   // Container neutral
    Danger,      // Danger destructive
    Success      // Success emerald accent
}

enum class ButtonStyle {
    Filled,
    Outlined
}

enum class ButtonSize {
    Standard,    // 54.dp height, main CTA
    Compact      // 42.dp height, modals and compact actions
}

/**
 * The app's unified, standardized button component.
 *
 * Enforces consistent sizing (54dp standard / 42dp compact), 14dp rounded corners,
 * bold monospace typography, and WCAG-contrast-safe text colors across both Dark
 * and Light ("Sky") themes.
 */
@Composable
fun FocusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    style: ButtonStyle = ButtonStyle.Filled,
    size: ButtonSize = ButtonSize.Standard,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true
) {
    val height = when (size) {
        ButtonSize.Standard -> 54.dp
        ButtonSize.Compact -> 42.dp
    }
    val shape = RoundedCornerShape(Radius.Medium)

    val containerColor = when {
        !enabled -> if (style == ButtonStyle.Filled) Container.copy(alpha = 0.5f) else Color.Transparent
        style == ButtonStyle.Outlined -> Color.Transparent
        variant == ButtonVariant.Primary -> Accent
        variant == ButtonVariant.Secondary -> Container
        variant == ButtonVariant.Danger -> Danger
        variant == ButtonVariant.Success -> Success
        else -> Accent
    }

    val contentColor = when {
        !enabled -> TextSecondary
        style == ButtonStyle.Outlined -> when (variant) {
            ButtonVariant.Primary -> Accent
            ButtonVariant.Secondary -> TextPrimary
            ButtonVariant.Danger -> Danger
            ButtonVariant.Success -> Success
        }
        variant == ButtonVariant.Primary -> DarkPalette.background
        variant == ButtonVariant.Secondary -> TextPrimary
        variant == ButtonVariant.Danger -> TextPrimary
        variant == ButtonVariant.Success -> DarkPalette.background
        else -> DarkPalette.background
    }

    val borderStroke = when {
        style == ButtonStyle.Filled -> null
        !enabled -> BorderStroke(1.dp, Border.copy(alpha = 0.5f))
        variant == ButtonVariant.Primary -> BorderStroke(1.dp, Accent)
        variant == ButtonVariant.Secondary -> BorderStroke(1.dp, Border)
        variant == ButtonVariant.Danger -> BorderStroke(1.dp, Danger)
        variant == ButtonVariant.Success -> BorderStroke(1.dp, Success)
        else -> null
    }

    val borderModifier = if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier

    Box(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .clip(shape)
            .then(borderModifier)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = Spacing.Medium)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.Small))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = contentColor
            )
        }
    }
}

/**
 * Backward-compatible wrapper for primary CTA button.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Standard,
    onClick: () -> Unit
) {
    FocusButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Primary,
        style = ButtonStyle.Filled,
        size = size,
        icon = icon,
        enabled = enabled
    )
}

/**
 * Backward-compatible wrapper for secondary neutral action button.
 */
@Composable
fun SecondaryActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FocusButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Secondary,
        style = ButtonStyle.Filled,
        size = ButtonSize.Standard,
        enabled = enabled
    )
}

/**
 * Backward-compatible wrapper for destructive action button.
 */
@Composable
fun DestructiveActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FocusButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Danger,
        style = ButtonStyle.Filled,
        size = ButtonSize.Standard,
        enabled = enabled
    )
}

@Composable
fun ModalButtonRow(
    dismissText: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusButton(
            text = dismissText,
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            variant = ButtonVariant.Secondary,
            style = ButtonStyle.Filled,
            size = ButtonSize.Compact,
            fillMaxWidth = false
        )
        FocusButton(
            text = confirmText,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            variant = if (isDestructive) ButtonVariant.Danger else ButtonVariant.Primary,
            style = ButtonStyle.Filled,
            size = ButtonSize.Compact,
            fillMaxWidth = false
        )
    }
}

@Composable
fun ModalTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = TextPrimary
    )
}
