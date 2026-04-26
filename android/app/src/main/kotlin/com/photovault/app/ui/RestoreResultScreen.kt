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
import com.photovault.app.ui.backup.BackupRuntimeState
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize

@Composable
fun RestoreResultScreen(onDone: () -> Unit) {
    val result = BackupRuntimeState.lastRestoreResult
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.backupScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
    ) {
        AppTopBar(title = stringResource(R.string.restore_result_title), onBack = onDone)
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
            RestoreResultBadge()
            Text(
                text = stringResource(R.string.restore_result_success),
                color = UiColors.Home.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = UiTextSize.homeEmptyTitle,
            )
            Text(
                text = stringResource(R.string.restore_result_success_desc),
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.homeEmptyBody,
                modifier = Modifier.padding(top = UiSize.backupResultInfoTopGap),
            )
            RestoreStatsRow(
                statsText = result?.let { "恢复 ${it.restored}，跳过 ${it.skipped}，失败 ${it.failed}" }
                    ?: stringResource(R.string.restore_result_stats),
            )
        }
        AppButton(
            text = stringResource(R.string.restore_result_done),
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = UiSize.backupActionTopGap),
        )
    }
}

@Composable
private fun RestoreResultBadge() {
    Box(
        modifier = Modifier
            .padding(top = UiSize.backupResultBadgeTopGap)
            .size(UiSize.backupResultBadgeSize)
            .background(UiColors.Lock.successHalo, RoundedCornerShape(UiRadius.backupResultBadge)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_result_restore),
            contentDescription = null,
            tint = UiColors.Lock.success,
            modifier = Modifier.size(UiSize.backupResultBadgeGlyph),
        )
    }
}

@Composable
private fun RestoreStatsRow(statsText: String) {
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
        Text(
            text = stringResource(R.string.restore_result_stats_label),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.backupMetaLabel,
        )
        Text(
            text = statsText,
            color = UiColors.Home.title,
            fontSize = UiTextSize.backupMetaValue,
            modifier = Modifier.padding(top = UiSize.trashMetaTopGap),
        )
    }
}

