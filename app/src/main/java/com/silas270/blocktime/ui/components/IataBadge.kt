package com.silas270.blocktime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The small bordered IATA-code box used in airport result rows and suggestion tiles - previously
 * three near/byte-identical reimplementations across onboarding and flight search. Colors and text
 * style are parameterized since call sites use two different accent schemes.
 *
 * [width]/[height] are minimums: at font scale 1.0 a three-letter code fits well inside them, so
 * the badge is exactly that size, but at large font scales the text used to spill out of a fixed
 * box. Now the badge grows around it instead.
 */
@Composable
fun IataBadge(
    code: String,
    borderColor: Color,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold
    ),
    width: Dp = 46.dp,
    height: Dp = 28.dp
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = width, minHeight = height)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(6.dp))
            .background(color = backgroundColor, shape = RoundedCornerShape(6.dp))
            // Only reached once scaled text outgrows the minimum size - keeps it off the border.
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = code, style = textStyle, color = textColor, maxLines = 1)
    }
}
