package com.silas270.blocktime.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.silas270.blocktime.data.model.Credit
import com.silas270.blocktime.data.model.CreditsCatalog
import com.silas270.blocktime.ui.components.BackTopAppBar
import com.silas270.blocktime.ui.components.CaptionLabel
import com.silas270.blocktime.ui.components.SectionHeader
import com.silas270.blocktime.ui.theme.DeepNavy
import com.silas270.blocktime.ui.theme.Dim
import com.silas270.blocktime.ui.theme.Haze
import com.silas270.blocktime.ui.theme.Midnight
import com.silas270.blocktime.ui.theme.OffWhite
import com.silas270.blocktime.ui.theme.Radius
import com.silas270.blocktime.ui.theme.ScreenGutter
import com.silas270.blocktime.ui.theme.Spacing

/**
 * Settings → Credits & licenses: every third-party source in [CreditsCatalog], grouped the same
 * way the Settings screen groups its rows. Full screen over Settings like [ChangeHomeBaseScreen],
 * and its cards use [SettingsRow]'s surface and type so it reads as part of the same screen family.
 * Tapping a card opens its source.
 */
@Composable
internal fun CreditsScreen(onBackClick: () -> Unit) {
    BackHandler(onBack = onBackClick)
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            BackTopAppBar(title = "CREDITS", onBackClick = onBackClick, scrolled = listState.canScrollBackward)
        },
        containerColor = Midnight
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            contentPadding = PaddingValues(
                start = ScreenGutter,
                end = ScreenGutter,
                top = Spacing.Small,
                bottom = Spacing.ExtraLarge
            )
        ) {
            CreditsCatalog.sections.forEachIndexed { index, section ->
                item(key = section.title) {
                    SectionHeader(
                        title = section.title,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else Spacing.Medium)
                    )
                }
                items(section.credits, key = { "${section.title}/${it.title}" }) { credit ->
                    CreditCard(credit)
                }
            }
        }
    }
}

@Composable
private fun CreditCard(credit: Credit) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.Large))
            .background(DeepNavy)
            .clickable(onClickLabel = "Open ${credit.title} source") {
                runCatching { uriHandler.openUri(credit.url) }
            }
            .padding(horizontal = Spacing.Medium, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = credit.title,
            style = MaterialTheme.typography.bodyLarge,
            color = OffWhite
        )
        Text(
            text = credit.detail,
            style = MaterialTheme.typography.bodySmall,
            color = Haze
        )
        CaptionLabel(text = credit.license, modifier = Modifier.padding(top = Spacing.ExtraSmall))
        credit.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
                modifier = Modifier.padding(top = Spacing.Small)
            )
        }
    }
}
