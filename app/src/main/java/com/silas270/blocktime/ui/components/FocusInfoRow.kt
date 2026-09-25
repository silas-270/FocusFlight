package com.silas270.blocktime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silas270.blocktime.ui.theme.Border
import com.silas270.blocktime.ui.theme.TextPrimary
import com.silas270.blocktime.ui.theme.TextSecondary

/**
 * Standardized key-value data row.
 *
 * Used across Arrival Celebration, Confirmation Modals, Check-In, Logbook, and Hub summaries.
 */
@Composable
fun FocusInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColor: Color = TextSecondary,
    valueColor: Color = TextPrimary,
    valueMonospace: Boolean = true,
    valueBold: Boolean = true,
    icon: ImageVector? = null,
    showDivider: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = labelColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    ),
                    color = labelColor
                )
            }

            if (trailingContent != null) {
                trailingContent()
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = if (valueMonospace) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = valueColor
                )
            }
        }

        if (showDivider) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
