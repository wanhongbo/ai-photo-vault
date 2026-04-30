package com.xpx.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.export.ExportRuntimeState
import com.xpx.vault.ui.export.MediaExporter
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

@Composable
fun ExportProgressScreen(
    onExportSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val total by ExportRuntimeState.progressTotal
    val done by ExportRuntimeState.progressDone
    val currentName by ExportRuntimeState.progressCurrentName
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (started) return@LaunchedEffect
        started = true
        val paths = ExportRuntimeState.pendingPaths
        if (paths.isEmpty()) {
            ExportRuntimeState.publishResult(com.xpx.vault.ui.export.ExportBatchResult(emptyList(), emptyList()))
            finished = true
            return@LaunchedEffect
        }
        val result = ExportRuntimeState.runExport(context, paths) { index, outcome ->
            ExportRuntimeState.progressDone.value = index + 1
            ExportRuntimeState.progressCurrentName.value = when (outcome) {
                is MediaExporter.ExportOutcome.Success -> outcome.displayName
                is MediaExporter.ExportOutcome.Failure -> null
            }
        }
        ExportRuntimeState.publishResult(result)
        ExportRuntimeState.clearPending()
        finished = true
    }

    LaunchedEffect(finished) {
        if (finished) onExportSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.backupScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
    ) {
        AppTopBar(title = stringResource(R.string.export_progress_title), onBack = onBack)
        Box(
            modifier = Modifier
                .padding(top = UiSize.backupCardTopGap)
                .fillMaxWidth()
                .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
                .padding(horizontal = 24.dp, vertical = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(color = UiColors.Home.navItemActive)
                Text(
                    text = stringResource(R.string.export_progress_doing),
                    color = UiColors.Home.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = UiTextSize.homeEmptyTitle,
                )
                Text(
                    text = stringResource(R.string.export_progress_count, done, total),
                    color = UiColors.Home.emptyBody,
                    fontSize = UiTextSize.homeEmptyBody,
                )
                val name = currentName
                if (!name.isNullOrBlank()) {
                    Text(
                        text = name,
                        color = UiColors.Home.subtitle,
                        fontSize = UiTextSize.homeEmptyBody,
                    )
                }
            }
        }
    }
}
