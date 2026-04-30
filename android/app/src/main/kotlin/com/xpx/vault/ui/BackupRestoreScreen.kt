package com.xpx.vault.ui

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.AppLogger
import com.xpx.vault.R
import com.xpx.vault.ui.backup.AutoBackupScheduler
import com.xpx.vault.ui.backup.BackupMeta
import com.xpx.vault.ui.backup.BackupTrigger
import com.xpx.vault.ui.backup.BackupTriggerReason
import com.xpx.vault.ui.backup.ExternalBackupLocation
import com.xpx.vault.ui.backup.LocalBackupMvpService
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppDialog
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.components.PinInputDialog
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 备份与恢复页：
 * - 常驻的 SAF 授权状态卡片（绑定 Documents/AIVault 等目录）。
 * - 自动备份最近一次时间与包指纹概览。
 * - 手动备份/恢复入口。
 * - 手动备份历史列表，点击任一项即可直接从该文件恢复（输入 PIN）。
 */
@Composable
fun BackupRestoreScreen(
    onOpenBackupProgress: (String) -> Unit,
    onOpenRestoreProgress: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // App 回前台 / 首次进入时刷新 SAF 授权状态 + meta
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { viewModel.refresh() }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val manualBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        } else {
            viewModel.cancelAllProgress()
        }
    }
    val manualRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            viewModel.showPinDialog()
        } else {
            viewModel.cancelAllProgress()
        }
    }
    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.onTreeUriAuthorized(uri)
        }
    }

    AppDialog(
        show = state.errorMessage != null,
        title = stringResource(R.string.settings_pin_error_title),
        message = state.errorMessage ?: "",
        confirmText = stringResource(R.string.settings_pin_error_action),
        onConfirm = { viewModel.clearError() },
    )

    PinInputDialog(
        show = state.showPinDialog,
        title = "输入 AI Vault 密码",
        subtitle = "请输入创建此备份时使用的 6 位密码。恢复成功后，当前相册将被覆盖为备份内容。",
        confirmText = "开始恢复",
        dismissText = "取消",
        errorMessage = state.pinErrorMessage,
        busy = state.restoring,
        onConfirm = { pin ->
            val uri = pendingRestoreUri
            if (uri != null) {
                viewModel.restoreFromManualFile(uri, pin) {
                    pendingRestoreUri = null
                }
            }
        },
        onDismiss = {
            pendingRestoreUri = null
            viewModel.dismissPinDialog()
        },
    )

    AppDialog(
        show = state.showRestoreSuccess,
        title = "恢复成功",
        message = "备份已恢复。后台将立即触发一次自动备份以对齐设备上的备份文件。",
        confirmText = "好的",
        onConfirm = { viewModel.consumeRestoreSuccess() },
    )

    val backupSuccessContext = LocalContext.current
    AppDialog(
        show = state.showBackupSuccess,
        title = "备份成功",
        message = "已成功备份 ${state.backupSuccessAssetCount} 个文件，共 ${Formatter.formatShortFileSize(backupSuccessContext, state.backupSuccessSizeBytes)}。",
        confirmText = "好的",
        onConfirm = { viewModel.consumeBackupSuccess() },
    )

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
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
        ) {
            // 常驻授权状态 / 提示
            item {
                SafAuthorizationCard(
                    authorized = state.safAuthorized,
                    treeUri = state.treeUriDisplay,
                    onPickTree = {
                        safTreeLauncher.launch(null)
                    },
                    onClearTree = { viewModel.clearTreeAuthorization() },
                )
            }
            // 自动备份最近一次信息
            item {
                AutoBackupStatusCard(
                    lastBackupAtMs = state.autoLastBackupAtMs,
                    fingerprintHex = state.autoFingerprintHex,
                    externalPathHint = state.autoExternalPathHint,
                )
            }
            // 手动备份
            item {
                BackupRestoreCard(
                    title = stringResource(R.string.backup_card_title),
                    desc = stringResource(R.string.backup_card_desc),
                    action = stringResource(R.string.backup_card_action),
                    onAction = {
                        val name = "AIVault_Backup_${formatStamp(System.currentTimeMillis())}.aivb"
                        manualBackupLauncher.launch(name)
                    },
                    badgeRes = R.drawable.ic_backup_upload,
                    loading = state.backingUp,
                )
            }
            // 手动恢复：从 SAF 挑选
            item {
                BackupRestoreCard(
                    title = stringResource(R.string.restore_card_title),
                    desc = stringResource(R.string.restore_card_desc),
                    action = stringResource(R.string.restore_card_action),
                    onAction = {
                        manualRestoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    badgeRes = R.drawable.ic_backup_restore,
                    secondary = true,
                    loading = state.restoring,
                )
            }
            // 历史记录
            item {
                Text(
                    text = "手动备份历史",
                    color = UiColors.Home.title,
                    fontSize = UiTextSize.homeSubtitle,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (state.manualHistory.isEmpty()) {
                item {
                    Text(
                        text = "还没有手动备份记录。点击上方“创建手动备份”即可保存一份带时间戳的快照。",
                        color = UiColors.Home.emptyBody,
                        fontSize = UiTextSize.settingsRowDesc,
                    )
                }
            } else {
                items(state.manualHistory) { entry ->
                    ManualHistoryRow(
                        entry = entry,
                        onRestore = {
                            runCatching { Uri.parse(entry.uri) }.getOrNull()?.let { uri ->
                                pendingRestoreUri = uri
                                viewModel.showPinDialog()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SafAuthorizationCard(
    authorized: Boolean,
    treeUri: String?,
    onPickTree: () -> Unit,
    onClearTree: () -> Unit,
) {
    val borderColor = if (authorized) UiColors.Home.emptyCardStroke else UiColors.Lock.error
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
            .border(1.dp, borderColor, RoundedCornerShape(UiRadius.homeCard))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (authorized) "备份目录：已授权" else "备份目录：尚未授权",
            color = if (authorized) UiColors.Home.title else UiColors.Lock.error,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (authorized) {
                treeUri ?: "Documents/AIVault"
            } else {
                "为了让自动备份写入到设备的 Documents/AIVault/ 目录并在重装或还原后恢复，需要授权一个持久目录。"
            },
            color = UiColors.Home.emptyBody,
            fontSize = UiTextSize.settingsRowDesc,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(
                text = if (authorized) "更换目录" else "选择备份目录",
                onClick = onPickTree,
                variant = AppButtonVariant.PRIMARY,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            )
            if (authorized) {
                AppButton(
                    text = "解除授权",
                    onClick = onClearTree,
                    variant = AppButtonVariant.SECONDARY,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                )
            }
        }
    }
}

@Composable
private fun AutoBackupStatusCard(
    lastBackupAtMs: Long?,
    fingerprintHex: String?,
    externalPathHint: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "自动备份状态",
            color = UiColors.Home.title,
            fontWeight = FontWeight.SemiBold,
        )
        if (lastBackupAtMs == null || lastBackupAtMs <= 0L) {
            Text(
                text = "暂无自动备份记录。满足运行条件后会在后台生成。",
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.settingsRowDesc,
            )
        } else {
            Text(
                text = "最近一次：${formatStamp(lastBackupAtMs)}",
                color = UiColors.Home.emptyTitle,
                fontSize = UiTextSize.settingsRowDesc,
            )
            if (!fingerprintHex.isNullOrBlank()) {
                Text(
                    text = "密钥指纹：${fingerprintHex.take(16)}…",
                    color = UiColors.Home.emptyBody,
                    fontSize = UiTextSize.settingsRowDesc,
                )
            }
            if (!externalPathHint.isNullOrBlank()) {
                Text(
                    text = "外部位置：$externalPathHint",
                    color = UiColors.Home.emptyBody,
                    fontSize = UiTextSize.settingsRowDesc,
                )
            }
        }
    }
}

@Composable
private fun ManualHistoryRow(
    entry: BackupMeta.ManualEntry,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.settingsRow))
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.settingsRow))
            .throttledClickable(onClick = onRestore)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatStamp(entry.createdAtMs),
                color = UiColors.Home.emptyTitle,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "大小：${formatSize(entry.sizeBytes)}" +
                    (entry.note?.let { " · $it" } ?: ""),
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.settingsRowDesc,
            )
            Text(
                text = entry.uri,
                color = UiColors.Home.subtitle,
                fontSize = 11.sp,
            )
        }
        Text(
            text = "恢复",
            color = UiColors.Home.navItemActive,
            fontWeight = FontWeight.SemiBold,
        )
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

private val STAMP_FORMAT: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
}

private fun formatStamp(ms: Long): String =
    STAMP_FORMAT.get()!!.format(Date(ms))

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "-"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupRestoreUiState())
    val state: StateFlow<BackupRestoreUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val snap = BackupMeta.load(context)
                val treeUri = ExternalBackupLocation.getTreeUri(context)
                val authorized = ExternalBackupLocation.isWritable(context)
                Triple(snap, treeUri, authorized)
            }
            val (snap, treeUri, authorized) = snapshot
            _state.value = _state.value.copy(
                safAuthorized = authorized,
                treeUriDisplay = treeUri?.toString(),
                autoLastBackupAtMs = snap.auto?.lastBackupAtMs,
                autoFingerprintHex = snap.auto?.keyFingerprintHex,
                autoExternalPathHint = snap.auto?.externalUri?.let { summarizeExternalUri(it) },
                manualHistory = snap.manualHistory,
            )
        }
    }

    fun onTreeUriAuthorized(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ExternalBackupLocation.persistTree(context, uri) }
                    .onFailure { AppLogger.e("BackupUI", "persistTree failed: ${it.message}") }
                runCatching { ExternalBackupLocation.sanitizeOnStartup(context) }
            }
            refresh()
        }
    }

    fun clearTreeAuthorization() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ExternalBackupLocation.clearTree(context) }
            }
            refresh()
        }
    }

    fun cancelAllProgress() {
        _state.value = _state.value.copy(backingUp = false, restoring = false)
    }

    fun showPinDialog() {
        _state.value = _state.value.copy(showPinDialog = true, pinErrorMessage = null)
    }

    fun dismissPinDialog() {
        _state.value = _state.value.copy(showPinDialog = false, pinErrorMessage = null, restoring = false)
    }

    fun consumeRestoreSuccess() {
        _state.value = _state.value.copy(showRestoreSuccess = false)
    }

    fun consumeBackupSuccess() {
        _state.value = _state.value.copy(
            showBackupSuccess = false,
            backupSuccessSizeBytes = 0,
            backupSuccessAssetCount = 0,
        )
    }

    fun exportBackupToUri(uri: Uri) {
        if (_state.value.restoring) return
        viewModelScope.launch {
            AppLogger.d("BackupUI", "manual backup start uri=${uri.scheme ?: "unknown"}")
            _state.value = _state.value.copy(backingUp = true, errorMessage = null)
            val result = LocalBackupMvpService.createBackup(
                context = context,
                trigger = BackupTrigger.MANUAL,
                targetUri = uri,
            )
            if (result.success) {
                AppLogger.d("BackupUI", "manual backup success")
                _state.value = _state.value.copy(
                    backingUp = false,
                    showBackupSuccess = true,
                    backupSuccessSizeBytes = result.outputSizeBytes,
                    backupSuccessAssetCount = result.assetCount,
                )
                refresh()
            } else {
                AppLogger.e("BackupUI", "manual backup failed: ${result.message}")
                _state.value = _state.value.copy(
                    backingUp = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    fun restoreFromManualFile(uri: Uri, pin: String, onComplete: () -> Unit) {
        if (_state.value.backingUp || _state.value.restoring) return
        viewModelScope.launch {
            AppLogger.d("BackupUI", "manual restore start uri=${uri.scheme ?: "unknown"}")
            _state.value = _state.value.copy(restoring = true, pinErrorMessage = null)
            val pinChars = pin.toCharArray()
            val result = LocalBackupMvpService.restoreFromManualFile(context, uri, pinChars)
            pinChars.fill('\u0000')
            if (result.success) {
                AppLogger.d("BackupUI", "manual restore success")
                runCatching {
                    AutoBackupScheduler.runOnceNow(context, BackupTriggerReason.MANUAL_RESTORE_SYNC)
                }
                _state.value = _state.value.copy(
                    restoring = false,
                    showPinDialog = false,
                    pinErrorMessage = null,
                    showRestoreSuccess = true,
                )
                onComplete()
                refresh()
            } else {
                AppLogger.e("BackupUI", "manual restore failed: ${result.message}")
                _state.value = _state.value.copy(
                    restoring = false,
                    pinErrorMessage = result.message,
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun summarizeExternalUri(uri: String): String {
        // 只保留 ".../documents/primary:Documents/..." 之后的相对路径，便于用户理解。
        val decoded = runCatching { Uri.decode(uri) }.getOrDefault(uri)
        val idx = decoded.indexOf("primary:")
        return if (idx >= 0) decoded.substring(idx + "primary:".length) else decoded
    }
}

data class BackupRestoreUiState(
    val backingUp: Boolean = false,
    val restoring: Boolean = false,
    val errorMessage: String? = null,
    val showPinDialog: Boolean = false,
    val pinErrorMessage: String? = null,
    val showRestoreSuccess: Boolean = false,
    val showBackupSuccess: Boolean = false,
    val backupSuccessSizeBytes: Long = 0,
    val backupSuccessAssetCount: Int = 0,
    val safAuthorized: Boolean = false,
    val treeUriDisplay: String? = null,
    val autoLastBackupAtMs: Long? = null,
    val autoFingerprintHex: String? = null,
    val autoExternalPathHint: String? = null,
    val manualHistory: List<BackupMeta.ManualEntry> = emptyList(),
)
