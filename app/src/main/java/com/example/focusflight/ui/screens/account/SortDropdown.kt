package com.example.focusflight.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflight.data.model.FlightSortOrder
import com.example.focusflight.ui.components.ScrimCardModal
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate
import com.example.focusflight.ui.theme.Spacing

@Composable
internal fun SortOrderButton(
    currentOrder: FlightSortOrder,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = DeepNavy,
            contentColor = OffWhite
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = currentOrder.displayName,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Amber
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = "Sort order options",
                tint = OffWhite,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Sort-order picker as a [ScrimCardModal] instead of an anchored `DropdownMenu` — this app's
 * one modal convention, matching `ReturnHomeConfirmModal`/`WelcomeHomeModal` in
 * HomeBaseSection.kt rather than reintroducing Material3's DropdownMenu.
 */
@Composable
internal fun SortOrderModal(
    currentOrder: FlightSortOrder,
    onOrderSelected: (FlightSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    ScrimCardModal(onScrimTap = onDismiss) {
        Text(
            text = "SORT BY",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = OffWhite
        )
        Spacer(modifier = Modifier.height(Spacing.Medium))
        Column(modifier = Modifier.fillMaxWidth()) {
            FlightSortOrder.entries.forEach { order ->
                val selected = order == currentOrder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Slate.copy(alpha = 0.4f) else Color.Transparent)
                        .clickable {
                            onOrderSelected(order)
                            onDismiss()
                        }
                        .padding(horizontal = Spacing.Medium, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = order.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) Amber else OffWhite
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
