package com.xpx.vault.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.export.ExportRuntimeState
import com.xpx.vault.ui.export.buildOpenGalleryIntent
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

@Composable
fun ExportResultScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val result = ExportRuntimeState.lastResult
    val successCount = result?.success?.size ?: 0
    val failedCount = result?.failed?.size ?: 0
    val totalCount = result?.total ?: 0
    val isAllSuccess = failedCount == 0 && successCount > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.backupScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
    ) {
        AppTopBar(title = stringResource(R.string.export_result_title), onBack = onDone)
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
            ExportResultBadge(allSuccess = isAllSuccess)
            Text(
                text = stringResource(
                    if (isAllSuccess) R.string.export_result_success_title
                    else if (successCount == 0) R.string.export_result_failed_title
                    else R.string.export_result_partial_title,
                ),
                color = UiColors.Home.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = UiTextSize.homeEmptyTitle,
            )
            Text(
                text = stringResource(R.string.export_result_summary, successCount, totalCount),
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.homeEmptyBody,
                modifier = Modifier.padding(top = UiSize.backupResultInfoTopGap),
            )
            if (failedCount > 0) {
                Text(
                    text = stringResource(R.string.export_result_failed_n, failedCount),
                    color = UiColors.Lock.error,
                    fontSize = UiTextSize.homeEmptyBody,
                )
            }
        }
        AppButton(
            text = stringResource(R.string.export_result_open_gallery),
            onClick = {
                try {
                    context.startActivity(buildOpenGalleryIntent())
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_result_open_gallery_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.SECONDARY,
        )
        AppButton(
            text = stringResource(R.string.backup_result_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExportResultBadge(allSuccess: Boolean) {
    Box(
        modifier = Modifier
            .padding(top = UiSize.backupResultBadgeTopGap)
            .size(UiSize.backupResultBadgeSize)
            .background(
                if (allSuccess) UiColors.Lock.successHalo else UiColors.Home.emptyCardBg,
                RoundedCornerShape(UiRadius.backupResultBadge),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_result_success),
            contentDescription = null,
            tint = if (allSuccess) UiColors.Lock.success else UiColors.Lock.error,
            modifier = Modifier.size(UiSize.backupResultBadgeGlyph),
        )
    }
}
