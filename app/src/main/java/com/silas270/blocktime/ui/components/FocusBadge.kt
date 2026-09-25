package com.silas270.blocktime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.ui.theme.Accent
import com.silas270.blocktime.ui.theme.AccentSubtle
import com.silas270.blocktime.ui.theme.Border
import com.silas270.blocktime.ui.theme.Container
import com.silas270.blocktime.ui.theme.Danger
import com.silas270.blocktime.ui.theme.DangerSubtle
import com.silas270.blocktime.ui.theme.DarkPalette
import com.silas270.blocktime.ui.theme.Success
import com.silas270.blocktime.ui.theme.SuccessSubtle
import com.silas270.blocktime.ui.theme.TextPrimary
import com.silas270.blocktime.ui.theme.TextSecondary

enum class BadgeVariant {
    Primary,   // Amber / Gold
    Success,   // Emerald Green
    Danger,    // Crimson Red
    Neutral,   // Slate / OffWhite
    Muted      // Haze / Dim
}

enum class BadgeStyle {
    Filled,       // Solid opaque fill
    Translucent,  // Tinted translucent fill (e.g. SoftAmber, Green 20%)
    Outlined,     // Transparent fill with colored border
    Stamp         // Inked rubber stamp: square corners, slight tilt, faded ink - reads as printed, not pressable
}

enum class BadgeSize {
    Compact,   // Micro badge: 4dp corners, 10sp monospace, 6x2dp padding
    Standard   // Normal badge: 6dp corners, 11sp monospace, 8x4dp padding
}

/**
 * The app's unified badge/tag/pill component.
 *
 * Replaces ad-hoc Box+clip+background badges across challenges, logbook, ranks, and status chips.
 */
@Composable
fun FocusBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Primary,
    style: BadgeStyle = BadgeStyle.Translucent,
    size: BadgeSize = BadgeSize.Standard,
    icon: ImageVector? = null,
    shape: Shape = when (size) {
        BadgeSize.Compact -> RoundedCornerShape(4.dp)
        BadgeSize.Standard -> RoundedCornerShape(6.dp)
    }
) {
    val containerColor = when (style) {
        BadgeStyle.Filled -> when (variant) {
            BadgeVariant.Primary -> Accent
            BadgeVariant.Success -> Success
            BadgeVariant.Danger -> Danger
            BadgeVariant.Neutral -> Container
            BadgeVariant.Muted -> Container.copy(alpha = 0.5f)
        }
        BadgeStyle.Translucent -> when (variant) {
            BadgeVariant.Primary -> AccentSubtle
            BadgeVariant.Success -> SuccessSubtle
            BadgeVariant.Danger -> DangerSubtle
            BadgeVariant.Neutral -> Container.copy(alpha = 0.7f)
            BadgeVariant.Muted -> TextSecondary.copy(alpha = 0.15f)
        }
        BadgeStyle.Outlined, BadgeStyle.Stamp -> Color.Transparent
    }

    val contentColor = when (style) {
        BadgeStyle.Filled -> when (variant) {
            BadgeVariant.Primary -> DarkPalette.background
            BadgeVariant.Success -> DarkPalette.background
            BadgeVariant.Danger -> TextPrimary
            BadgeVariant.Neutral -> TextPrimary
            BadgeVariant.Muted -> TextSecondary
        }
        BadgeStyle.Translucent, BadgeStyle.Outlined, BadgeStyle.Stamp -> when (variant) {
            BadgeVariant.Primary -> Accent
            BadgeVariant.Success -> Success
            BadgeVariant.Danger -> Danger
            BadgeVariant.Neutral -> TextPrimary
            BadgeVariant.Muted -> TextSecondary
        }
    }.let { if (style == BadgeStyle.Stamp) it.copy(alpha = StampInkAlpha) else it }

    val borderStroke = when (style) {
        BadgeStyle.Filled -> null
        BadgeStyle.Translucent -> null
        BadgeStyle.Stamp -> BorderStroke(1.dp, contentColor)
        BadgeStyle.Outlined -> when (variant) {
            BadgeVariant.Primary -> BorderStroke(1.dp, Accent)
            BadgeVariant.Success -> BorderStroke(1.dp, Success)
            BadgeVariant.Danger -> BorderStroke(1.dp, Danger)
            BadgeVariant.Neutral -> BorderStroke(1.dp, Border)
            BadgeVariant.Muted -> BorderStroke(1.dp, Border.copy(alpha = 0.5f))
        }
    }

    val paddingH = if (size == BadgeSize.Compact) 6.dp else 8.dp
    val paddingV = if (size == BadgeSize.Compact) 2.dp else 4.dp
    val fontSize = if (size == BadgeSize.Compact) 10.sp else 11.sp
    val iconSize = if (size == BadgeSize.Compact) 12.dp else 14.dp

    // A stamp ignores the caller's shape: its square corners are part of what sets it apart from
    // the rounded, pressable-looking badges.
    val badgeShape = if (style == BadgeStyle.Stamp) RoundedCornerShape(2.dp) else shape
    val borderModifier = if (borderStroke != null) Modifier.border(borderStroke, badgeShape) else Modifier

    Box(
        modifier = modifier
            .then(if (style == BadgeStyle.Stamp) Modifier.rotate(StampTiltDegrees) else Modifier)
            .clip(badgeShape)
            .then(borderModifier)
            .background(containerColor)
            .padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize,
                    letterSpacing = 0.5.sp
                ),
                color = contentColor
            )
        }
    }
}

private const val StampInkAlpha = 0.85f
private const val StampTiltDegrees = -4f
