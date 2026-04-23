package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppButtonVariant
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize

@Composable
fun TrashBinScreen() {
    val items = listOf(
        TrashItem("IMG_2198.jpg", "4.2 MB", "剩余 29 天"),
        TrashItem("IMG_1042.jpg", "3.1 MB", "剩余 18 天"),
        TrashItem("Vacation_2025_03.png", "2.6 MB", "剩余 7 天"),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.backupScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
    ) {
        Text(
            text = stringResource(R.string.trash_title),
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeTitle,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.trash_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeSubtitle,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(UiSize.trashRowGap)) {
            items(items) { item ->
                TrashRow(item = item)
            }
        }
    }
}

@Composable
private fun TrashRow(item: TrashItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(UiSize.trashItemPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.trashRowGap),
    ) {
        Text(text = item.name, color = UiColors.Home.title, fontWeight = FontWeight.Medium)
        Text(
            text = stringResource(R.string.trash_item_meta, item.size, item.remainTime),
            color = UiColors.Home.emptyBody,
            fontSize = UiTextSize.homeEmptyBody,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(
                text = stringResource(R.string.trash_recover),
                onClick = {},
                modifier = Modifier.weight(1f),
                variant = AppButtonVariant.SECONDARY,
            )
            AppButton(
                text = stringResource(R.string.trash_delete),
                onClick = {},
                modifier = Modifier.weight(1f),
                variant = AppButtonVariant.DANGER,
            )
        }
    }
}

private data class TrashItem(
    val name: String,
    val size: String,
    val remainTime: String,
)

