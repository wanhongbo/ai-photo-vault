package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize

@Composable
fun BackupResultScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.backupScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
    ) {
        AppTopBar(title = stringResource(R.string.backup_result_title), onBack = onDone)
        Column(
            modifier = Modifier
                .padding(top = UiSize.backupCardTopGap)
                .fillMaxWidth()
                .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
                .padding(UiSize.backupCardPadding),
            verticalArrangement = Arrangement.spacedBy(UiSize.backupCardInnerGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BackupResultBadge()
            Text(
                text = stringResource(R.string.backup_result_success),
                color = UiColors.Home.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = UiTextSize.homeEmptyTitle,
            )
            Text(
                text = stringResource(R.string.backup_result_success_desc),
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.homeEmptyBody,
                modifier = Modifier.padding(top = UiSize.backupResultInfoTopGap),
            )
            BackupMetaRow(
                label = stringResource(R.string.backup_result_file_label),
                value = stringResource(R.string.backup_result_file),
            )
            BackupMetaRow(
                label = stringResource(R.string.backup_result_size_label),
                value = stringResource(R.string.backup_result_size),
            )
        }
        AppButton(
            text = stringResource(R.string.backup_result_done),
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = UiSize.backupActionTopGap),
        )
    }
}

@Composable
private fun BackupResultBadge() {
    Box(
        modifier = Modifier
            .padding(top = UiSize.backupResultBadgeTopGap)
            .size(UiSize.backupResultBadgeSize)
            .background(UiColors.Lock.successHalo, RoundedCornerShape(UiRadius.backupResultBadge)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_result_success),
            contentDescription = null,
            tint = UiColors.Lock.success,
            modifier = Modifier.size(UiSize.backupResultBadgeGlyph),
        )
    }
}

@Composable
private fun BackupMetaRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UiSize.backupResultMetaTopGap)
            .background(UiColors.Home.emptyCardBg, RoundedCornerShape(UiRadius.backupMetaCard))
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.backupMetaCard))
            .padding(
                horizontal = UiSize.backupMetaRowPadHorizontal,
                vertical = UiSize.backupMetaRowPadVertical,
            ),
    ) {
        Text(text = label, color = UiColors.Home.subtitle, fontSize = UiTextSize.backupMetaLabel)
        Text(
            text = value,
            color = UiColors.Home.title,
            fontSize = UiTextSize.backupMetaValue,
            modifier = Modifier.padding(top = UiSize.trashMetaTopGap),
        )
    }
}

