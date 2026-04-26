package com.photovault.app.ui

import android.net.Uri
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppDialog
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import kotlinx.coroutines.delay

@Composable
fun RestoreProgressScreen(
    inputUri: String,
    onRestoreSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val startRestoreDelayMs = 350L
    val state by viewModel.state.collectAsState()
    val parsedUri = Uri.parse(Uri.decode(inputUri))
    var startedRestore by remember(inputUri) { mutableStateOf(false) }
    var restoreSucceeded by remember(inputUri) { mutableStateOf(false) }
    var hasNavigated by remember(inputUri) { mutableStateOf(false) }

    LaunchedEffect(inputUri) {
        if (!startedRestore && !state.restoring) {
            startedRestore = true
            delay(startRestoreDelayMs)
            viewModel.importBackupFromUri(parsedUri, onSuccess = { restoreSucceeded = true })
        }
    }

    LaunchedEffect(restoreSucceeded) {
        if (!restoreSucceeded || hasNavigated) return@LaunchedEffect
        hasNavigated = true
        onRestoreSuccess()
    }

    AppDialog(
        show = state.errorMessage != null,
        title = stringResource(R.string.settings_pin_error_title),
        message = state.errorMessage ?: "",
        confirmText = stringResource(R.string.backup_progress_error_confirm),
        onConfirm = {
            viewModel.clearError()
            onBack()
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.backupScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
    ) {
        AppTopBar(title = stringResource(R.string.restore_progress_title), onBack = onBack)
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
                    text = stringResource(R.string.restore_progress_doing),
                    color = UiColors.Home.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = UiTextSize.homeEmptyTitle,
                )
                Text(
                    text = stringResource(R.string.restore_progress_desc),
                    color = UiColors.Home.emptyBody,
                    fontSize = UiTextSize.homeEmptyBody,
                )
            }
        }
        if (!state.restoring) {
            AppButton(
                text = stringResource(R.string.restore_progress_back_action),
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = UiSize.backupActionTopGap),
            )
        }
    }
}
