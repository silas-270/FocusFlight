package com.example.focusflight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focusflight.data.model.SetMemberProgress
import com.example.focusflight.ui.theme.Amber
import com.example.focusflight.ui.theme.Haze
import com.example.focusflight.ui.theme.OffWhite
import com.example.focusflight.ui.theme.Slate

/** Which slice of a set the checklist is showing. */
enum class SetMemberFilter(val label: String) {
    ALL("ALL"),
    FINISHED("FINISHED"),
    MISSING("MISSING")
}

/**
 * A completable set's members, visited ones lit in Amber and the rest dimmed, over a
 * ALL / FINISHED / MISSING filter.
 *
 * The answer to "which ones am I still missing", which a bare `12 / 54` progress bar cannot give.
 * Shared by both surfaces that have one: a Set-completion challenge's info modal (where visited
 * state comes from the instance's credited members) and an achievement's card (where it comes from
 * `VisitedGeography`). Both hand over an already-ordered list - see
 * `List<SetMemberProgress>.visitedFirst()`.
 *
 * The filter matters most exactly where the list is longest: "visited-first" ordering answers
 * "what have I got" at a glance, but "what am I missing" then means scrolling past everything
 * earned - which for World Traveler is most of ~195 rows. MISSING makes that one tap.
 *
 * Scrolls internally under [maxHeight] rather than growing the card that contains it. That cap is
 * load-bearing, not cosmetic: the largest set a challenge can hold is seven continents, but the
 * "World Traveler" achievement's list is every country on Earth, and a modal sized to that would
 * run off both ends of the screen.
 */
@Composable
fun SetMemberChecklist(
    members: List<SetMemberProgress>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 190.dp
) {
    // Filter state is owned here rather than hoisted: it is a way of reading this one list, not
    // something any caller acts on, and every surface wants the identical three options. Resets
    // when the modal closes, which is the right default - a filter that persisted across
    // achievements would silently hide rows on the next one you opened.
    var filter by rememberSaveable { mutableStateOf(SetMemberFilter.ALL) }

    val visible = when (filter) {
        SetMemberFilter.ALL -> members
        SetMemberFilter.FINISHED -> members.filter { it.isVisited }
        SetMemberFilter.MISSING -> members.filterNot { it.isVisited }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SetMemberFilterBar(
            selected = filter,
            finishedCount = members.count { it.isVisited },
            totalCount = members.size,
            onSelect = { filter = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SetMemberRows(members = visible, height = height, filter = filter)
    }
}

/** The three-way ALL / FINISHED / MISSING switch, with a live count so the header answers
 *  "how many" without the pilot scrolling to find out. */
@Composable
private fun SetMemberFilterBar(
    selected: SetMemberFilter,
    finishedCount: Int,
    totalCount: Int,
    onSelect: (SetMemberFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Slate.copy(alpha = 0.35f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        SetMemberFilter.entries.forEach { option ->
            val isSelected = option == selected
            val count = when (option) {
                SetMemberFilter.ALL -> totalCount
                SetMemberFilter.FINISHED -> finishedCount
                SetMemberFilter.MISSING -> totalCount - finishedCount
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Amber.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${option.label} $count",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isSelected) Amber else Haze,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SetMemberRows(
    members: List<SetMemberProgress>,
    height: androidx.compose.ui.unit.Dp,
    filter: SetMemberFilter
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        if (members.isEmpty()) {
            val emptyMessage = when (filter) {
                SetMemberFilter.FINISHED -> "No destinations visited yet"
                SetMemberFilter.MISSING -> "All destinations visited!"
                SetMemberFilter.ALL -> "No destinations in this set"
            }
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Haze
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (member.isVisited) Amber.copy(alpha = 0.12f) else Slate.copy(alpha = 0.35f)
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (member.isVisited) Amber else Haze.copy(alpha = 0.35f))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = member.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (member.isVisited) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (member.isVisited) OffWhite else Haze,
                            modifier = Modifier.weight(1f)
                        )
                        if (member.isVisited) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Visited",
                                tint = Amber,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
