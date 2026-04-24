package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.photovault.app.ui.components.AppButtonVariant
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize

@Composable
fun BackupRestoreScreen(
    onOpenBackupResult: () -> Unit,
    onOpenRestoreResult: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.backupScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
    ) {
        AppTopBar(title = stringResource(R.string.backup_restore_title), onBack = onBack)
        Text(
            text = stringResource(R.string.backup_restore_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeSubtitle,
            modifier = Modifier.padding(top = UiSize.backupSubtitleTopGap),
        )
        BackupRestoreCard(
            title = stringResource(R.string.backup_card_title),
            desc = stringResource(R.string.backup_card_desc),
            action = stringResource(R.string.backup_card_action),
            onAction = onOpenBackupResult,
            badgeRes = R.drawable.ic_backup_upload,
            modifier = Modifier.padding(top = UiSize.backupCardTopGap),
        )
        BackupRestoreCard(
            title = stringResource(R.string.restore_card_title),
            desc = stringResource(R.string.restore_card_desc),
            action = stringResource(R.string.restore_card_action),
            onAction = onOpenRestoreResult,
            badgeRes = R.drawable.ic_backup_restore,
            secondary = true,
        )
    }
}

@Composable
private fun BackupRestoreCard(
    title: String,
    desc: String,
    action: String,
    onAction: () -> Unit,
    badgeRes: Int,
    secondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(UiSize.backupCardPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupCardInnerGap),
    ) {
        Box(
            modifier = Modifier
                .size(UiSize.backupCardBadgeWrap)
                .background(UiColors.Home.emptyIconBg, RoundedCornerShape(UiRadius.backupMetaCard)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = badgeRes),
                contentDescription = null,
                tint = UiColors.Home.navItemActive,
                modifier = Modifier.size(UiSize.backupCardBadgeGlyph),
            )
        }
        Text(text = title, color = UiColors.Home.title, fontWeight = FontWeight.SemiBold)
        Text(text = desc, color = UiColors.Home.emptyBody, fontSize = UiTextSize.homeEmptyBody)
        Row(modifier = Modifier.fillMaxWidth()) {
            AppButton(
                text = action,
                onClick = onAction,
                variant = if (secondary) AppButtonVariant.SECONDARY else AppButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

