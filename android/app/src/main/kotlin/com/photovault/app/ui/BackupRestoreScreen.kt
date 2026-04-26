package com.photovault.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photovault.app.AppLogger
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppButtonVariant
import com.photovault.app.ui.components.AppDialog
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.ui.backup.LocalBackupMvpService
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreScreen(
    onOpenBackupProgress: (String) -> Unit,
    onOpenRestoreProgress: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            onOpenBackupProgress(Uri.encode(uri.toString()))
        } else {
            viewModel.cancelAllProgress()
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onOpenRestoreProgress(Uri.encode(uri.toString()))
        } else {
            viewModel.cancelAllProgress()
        }
    }

    AppDialog(
        show = state.errorMessage != null,
        title = stringResource(R.string.settings_pin_error_title),
        message = state.errorMessage ?: "",
        confirmText = stringResource(R.string.settings_pin_error_action),
        onConfirm = { viewModel.clearError() },
    )

    Box(modifier = Modifier.fillMaxSize()) {
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
                onAction = {
                    val name = "vault_backup_${System.currentTimeMillis()}.zip"
                    backupExportLauncher.launch(name)
                },
                badgeRes = R.drawable.ic_backup_upload,
                modifier = Modifier.padding(top = UiSize.backupCardTopGap),
                loading = state.backingUp,
            )
            BackupRestoreCard(
                title = stringResource(R.string.restore_card_title),
                desc = stringResource(R.string.restore_card_desc),
                action = stringResource(R.string.restore_card_action),
                onAction = {
                    backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                badgeRes = R.drawable.ic_backup_restore,
                secondary = true,
                loading = state.restoring,
            )
        }

    }
}

@Composable
private fun BackupRestoreProgressOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .background(UiColors.Dialog.bg, RoundedCornerShape(UiRadius.dialog))
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.dialog))
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = UiColors.Home.navItemActive)
            Text(
                text = message,
                color = UiColors.Dialog.title,
                fontSize = UiTextSize.homeEmptyBody,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
    loading: Boolean = false,
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
                enabled = !loading,
                loading = loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupRestoreUiState())
    val state: StateFlow<BackupRestoreUiState> = _state.asStateFlow()

    fun cancelAllProgress() {
        _state.value = _state.value.copy(backingUp = false, restoring = false)
    }

    fun exportBackupToUri(uri: Uri, onSuccess: () -> Unit) {
        if (_state.value.restoring) return
        viewModelScope.launch {
            AppLogger.d("BackupUI", "export start uri=${uri.scheme ?: "unknown"}")
            _state.value = _state.value.copy(backingUp = true, errorMessage = null)
            val create = LocalBackupMvpService.createBackup(context)
            if (!create.success) {
                AppLogger.e("BackupUI", "create backup failed: ${create.message}")
                _state.value = _state.value.copy(backingUp = false, errorMessage = create.message)
                return@launch
            }
            val result = LocalBackupMvpService.exportBackupsToUri(context, uri)
            if (result.success) {
                AppLogger.d("BackupUI", "export success")
                _state.value = _state.value.copy(backingUp = false)
                onSuccess()
            } else {
                AppLogger.e("BackupUI", "export failed: ${result.message}")
                _state.value = _state.value.copy(
                    backingUp = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    fun importBackupFromUri(uri: Uri, onSuccess: () -> Unit) {
        if (_state.value.backingUp) return
        viewModelScope.launch {
            AppLogger.d("BackupUI", "import start uri=${uri.scheme ?: "unknown"}")
            _state.value = _state.value.copy(restoring = true, errorMessage = null)
            val result = LocalBackupMvpService.importBackupsFromUri(context, uri)
            if (result.success) {
                AppLogger.d("BackupUI", "import success restored=${result.restored} skipped=${result.skipped} failed=${result.failed}")
                _state.value = _state.value.copy(restoring = false)
                onSuccess()
            } else {
                AppLogger.e("BackupUI", "import failed: ${result.message}")
                _state.value = _state.value.copy(
                    restoring = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}

data class BackupRestoreUiState(
    val backingUp: Boolean = false,
    val restoring: Boolean = false,
    val errorMessage: String? = null,
)

