package com.example.focusflight.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.DeepNavy
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Radius
import com.example.focusflight.ui.theme.Slate

/**
 * The app's "search an airport" text field: leading search icon, placeholder, single line,
 * [Radius.Large] rounded corners, Slate/DeepNavy/Amber colors. Shared by the onboarding home-
 * airport search, the flight-search airport panel, and the Free Mode origin picker - it does not
 * cover every `OutlinedTextField` in the app (e.g. Create Challenge's plain numeric distance
 * field is a different kind of input and keeps its own styling).
 */
@Composable
fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    unfocusedBorderColor: Color = Color.Transparent
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = Haze,
                modifier = Modifier.padding(start = 4.dp)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = Haze,
                modifier = Modifier.size(24.dp)
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(Radius.Large),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Slate,
            unfocusedContainerColor = DeepNavy,
            cursorColor = Amber,
            focusedBorderColor = Amber,
            unfocusedBorderColor = unfocusedBorderColor,
            focusedTextColor = OffWhite,
            unfocusedTextColor = OffWhite,
            focusedLeadingIconColor = Amber,
            unfocusedLeadingIconColor = Haze
        ),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}
