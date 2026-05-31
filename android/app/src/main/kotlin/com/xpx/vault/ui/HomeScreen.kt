package com.xpx.vault.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xpx.vault.R
import com.xpx.vault.ai.core.ClassifyCategory
import com.xpx.vault.billing.SoftPaywallReason
import com.xpx.vault.findAppLockManager
import com.xpx.vault.launchExternalSystemUi
import com.xpx.vault.ui.ai.AiClassifyVirtualAlbum
import com.xpx.vault.ui.ai.loadAiClassifyVirtualAlbums
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppInputDialog
import com.xpx.vault.ui.components.TaskSnapshotFocusGuard
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.AppFontFamily
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.DEFAULT_ALBUM_NAME
import com.xpx.vault.ui.vault.ImportOriginalsAction
import com.xpx.vault.ui.vault.ImportOriginalsPreferenceStore
import com.xpx.vault.ui.vault.VaultAlbum
import com.xpx.vault.ui.vault.VaultImportResult
import com.xpx.vault.ui.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onOpenPinSettings: () -> Unit = {},
    onOpenPrivateCamera: () -> Unit = {},
    onOpenTab: (HomeTab) -> Unit = {},
    selectedTab: HomeTab = HomeTab.VAULT,
    showBottomNav: Boolean = true,
    onOpenSearch: () -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onOpenAiClassifyAlbum: (ClassifyCategory) -> Unit = {},
    onOpenPhotoViewer: (String) -> Unit = {},
    onOpenAlbumList: () -> Unit = {},
    onOpenRecentList: () -> Unit = {},
    onPaywallRequired: () -> Unit = {},
    onSoftPaywallRequested: (SoftPaywallReason) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pinStatusVm: PinSetupStatusViewModel = hiltViewModel()
    val hasPin by pinStatusVm.hasPin.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val appLockManager = remember(context) { context.findAppLockManager() }
    fun launchSystemUi(reason: String, launch: () -> Unit) {
        appLockManager?.launchExternalSystemUi(reason, launch) ?: launch()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var creatingAlbum by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    val cachedSnapshot = remember { VaultStore.peekCachedSnapshot() }
    var albums by remember { mutableStateOf(cachedSnapshot?.albums.orEmpty()) }
    var aiAlbums by remember { mutableStateOf<List<AiClassifyVirtualAlbum>>(emptyList()) }
    var recentPhotos by remember { mutableStateOf(cachedSnapshot?.recentPhotos.orEmpty()) }
    var totalCount by remember { mutableStateOf(cachedSnapshot?.totalCount ?: 0) }
    var imageCount by remember { mutableStateOf(cachedSnapshot?.imageCount ?: 0) }
    var videoCount by remember { mutableStateOf(cachedSnapshot?.videoCount ?: 0) }
    var vaultLoaded by remember { mutableStateOf(cachedSnapshot != null) }
    var importing by remember { mutableStateOf(false) }
    var importTip by remember { mutableStateOf<ImportTip?>(null) }
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var rememberOriginalsChoice by remember { mutableStateOf(false) }
    var retryDeleteUrisAfterPermission by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingDeleteRequestCount by remember { mutableStateOf(0) }
    var pendingDeleteUnsupportedCount by remember { mutableStateOf(0) }
    val tabs = remember { homeTabs() }
    val isVaultEmpty = remember(albums, recentPhotos) {
        recentPhotos.isEmpty() && albums.sumOf { it.photoCount } == 0
    }

    suspend fun refreshVault() {
        val snapshot = VaultStore.loadSnapshot(context, recentLimit = 60)
        val latestAiAlbums = loadAiClassifyVirtualAlbums(context)
        if (albums != snapshot.albums) albums = snapshot.albums
        if (aiAlbums != latestAiAlbums) aiAlbums = latestAiAlbums
        if (recentPhotos != snapshot.recentPhotos) recentPhotos = snapshot.recentPhotos
        if (totalCount != snapshot.totalCount) totalCount = snapshot.totalCount
        if (imageCount != snapshot.imageCount) imageCount = snapshot.imageCount
        if (videoCount != snapshot.videoCount) videoCount = snapshot.videoCount
        vaultLoaded = true
        // 推送 vault 计数给 QuotaManager，确保配额硬墙能正确触发
        com.xpx.vault.billing.QuotaManagerProvider.get(context)?.updateVaultCount(snapshot.totalCount)
    }

    LaunchedEffect(Unit) { refreshVault() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refreshVault() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val deleteOriginalsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        appLockManager?.endExternalSystemUi("home delete originals result")
        val retryUris = retryDeleteUrisAfterPermission
        val requestedCount = pendingDeleteRequestCount
        val unsupportedCount = pendingDeleteUnsupportedCount
        retryDeleteUrisAfterPermission = emptyList()
        pendingDeleteRequestCount = 0
        pendingDeleteUnsupportedCount = 0
        if (result.resultCode == Activity.RESULT_OK) {
            if (retryUris.isNotEmpty()) {
                scope.launch {
                    val deleted = deleteOriginalUrisDirect(context, retryUris).deletedCount
                    importTip = buildDeleteOriginalsTip(context, deleted, unsupportedCount)
                }
            } else {
                importTip = buildDeleteOriginalsTip(context, requestedCount, unsupportedCount)
            }
        } else {
            importTip = ImportTip(context.getString(R.string.import_originals_delete_canceled), false)
        }
    }

    fun requestDeleteOriginals(uris: List<Uri>) {
        val targets = mediaStoreDeleteTargets(context, uris)
        if (targets.uris.isEmpty()) {
            importTip = ImportTip(context.getString(R.string.import_originals_delete_unavailable), true)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = runCatching {
                MediaStore.createDeleteRequest(context.contentResolver, targets.uris).let { pendingIntent ->
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                }
            }.getOrNull()
            if (request != null) {
                retryDeleteUrisAfterPermission = emptyList()
                pendingDeleteRequestCount = targets.uris.size
                pendingDeleteUnsupportedCount = targets.unsupportedCount
                launchSystemUi("home delete originals permission") {
                    deleteOriginalsLauncher.launch(request)
                }
            } else {
                importTip = ImportTip(context.getString(R.string.import_originals_delete_failed), true)
            }
        } else {
            scope.launch {
                val result = deleteOriginalUrisDirect(context, targets.uris)
                if (result.permissionRequest != null) {
                    retryDeleteUrisAfterPermission = result.retryUris
                    pendingDeleteRequestCount = result.retryUris.size
                    pendingDeleteUnsupportedCount = targets.unsupportedCount
                    launchSystemUi("home delete originals permission") {
                        deleteOriginalsLauncher.launch(result.permissionRequest)
                    }
                } else {
                    importTip = buildDeleteOriginalsTip(context, result.deletedCount, targets.unsupportedCount)
                }
            }
        }
    }

    fun importSelectedUris(uris: List<Uri>, originalsAction: ImportOriginalsAction) {
        if (uris.isEmpty()) return
        scope.launch {
            importing = true
            var added = 0
            var duplicate = 0
            var failed = 0
            var quotaExceeded = false
            val deleteCandidates = mutableListOf<Uri>()
            for (uri in uris) {
                when (VaultStore.importFromPicker(context, uri, DEFAULT_ALBUM_NAME)) {
                    VaultImportResult.ADDED -> {
                        added += 1
                        deleteCandidates += uri
                    }
                    VaultImportResult.DUPLICATE -> {
                        duplicate += 1
                        deleteCandidates += uri
                    }
                    VaultImportResult.QUOTA_EXCEEDED -> {
                        quotaExceeded = true
                        break
                    }
                    VaultImportResult.FAILED -> failed += 1
                }
            }
            refreshVault()
            // 导入完成后触发一次增量 AI 扫描（mutex + rescanRequested 会合并多次触发）。
            if (added > 0) {
                com.xpx.vault.ai.AiScanEntryPoint.from(context).requestScan()
            }
            importTip = if (uris.size == 1) {
                when {
                    added == 1 -> ImportTip(context.getString(R.string.home_import_success_default_album), false)
                    duplicate == 1 -> ImportTip(context.getString(R.string.home_import_duplicate_default_album), false)
                    else -> ImportTip(context.getString(R.string.home_import_failed), true)
                }
            } else {
                ImportTip(
                    context.getString(R.string.home_import_multi_result, added, duplicate, failed),
                    failed > 0 && added == 0,
                )
            }
            importing = false
            if (added > 0 && originalsAction != ImportOriginalsAction.DELETE_ORIGINALS) {
                val promptManager = com.xpx.vault.billing.PaywallPromptManagerProvider.get(context)
                val isPremium = com.xpx.vault.billing.SubscriptionRepoProvider.get(context)?.isPremium?.value ?: false
                val reason = promptManager?.shouldPromptAfterVaultImport(totalCount, isPremium)
                if (reason != null) {
                    onSoftPaywallRequested(reason)
                }
            }
            if (originalsAction == ImportOriginalsAction.DELETE_ORIGINALS && deleteCandidates.isNotEmpty()) {
                requestDeleteOriginals(deleteCandidates)
            }
            if (quotaExceeded) {
                onPaywallRequired()
            }
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        appLockManager?.endExternalSystemUi("home photo picker result")
        if (uris.isNotEmpty()) {
            when (val action = ImportOriginalsPreferenceStore.current(context)) {
                ImportOriginalsAction.ASK_EACH_TIME -> {
                    pendingImportUris = uris
                    rememberOriginalsChoice = false
                }
                ImportOriginalsAction.KEEP_ORIGINALS,
                ImportOriginalsAction.DELETE_ORIGINALS,
                -> importSelectedUris(uris, action)
            }
        }
    }

    val triggerImportFromLibrary: () -> Unit = {
        if (!importing) {
            importTip = null
            scope.launch {
                VaultStore.canAddNewItem(context)
                val gatekeeper = com.xpx.vault.billing.PaywallGatekeeperProvider.get(context)
                val gate = gatekeeper?.checkAccess(com.xpx.vault.domain.quota.ProFeature.VAULT_IMPORT)
                if (gate is com.xpx.vault.billing.GateResult.HardWall) {
                    onPaywallRequired()
                } else {
                    val promptManager = com.xpx.vault.billing.PaywallPromptManagerProvider.get(context)
                    val isPremium = com.xpx.vault.billing.SubscriptionRepoProvider.get(context)?.isPremium?.value ?: false
                    val nearLimitReason = promptManager
                        ?.shouldPromptAfterVaultImport(totalCount, isPremium)
                        ?.takeIf { it == SoftPaywallReason.VAULT_NEAR_LIMIT }
                    if (nearLimitReason != null) {
                        onSoftPaywallRequested(nearLimitReason)
                        return@launch
                    }
                    launchSystemUi("home photo picker") {
                        pickerLauncher.launch(
                            PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                .build(),
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFF0E233A),
                        0.24f to Color(0xFF0B1D31),
                        0.54f to Color(0xFF071423),
                        1.00f to Color(0xFF05080D),
                    ),
                ),
            )
            .drawBehind {
                drawCircle(
                    color = Color(0x2E123A5E),
                    radius = size.width * 0.58f,
                    center = Offset(size.width * 0.55f, size.height * 0.49f),
                )
                drawCircle(
                    color = Color(0x1F0B2740),
                    radius = size.width * 0.38f,
                    center = Offset(size.width * 0.55f, size.height * 0.49f),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 16.dp, top = 32.dp, end = 16.dp, bottom = 16.dp),
        ) {
            VaultHeroCard(
                totalCount = totalCount,
                onImport = triggerImportFromLibrary,
            )
            if (selectedTab == HomeTab.VAULT && !hasPin && !isVaultEmpty) {
                HomePinSetupBanner(
                    message = stringResource(R.string.home_pin_banner_message),
                    actionLabel = stringResource(R.string.home_pin_banner_action),
                    onClick = onOpenPinSettings,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            importTip?.let { tip ->
                Text(
                    text = tip.message,
                    color = if (tip.isError) UiColors.Lock.error else UiColors.Lock.success,
                    fontSize = UiTextSize.homeNavLabel,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (!vaultLoaded) {
                val vaultBodyTapSink = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = vaultBodyTapSink,
                            indication = null,
                        ) { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.common_loading), color = UiColors.Home.subtitle)
                }
            } else if (isVaultEmpty) {
                val vaultBodyTapSink = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .clickable(
                            interactionSource = vaultBodyTapSink,
                            indication = null,
                        ) { },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    VaultEmptyState(
                        isLoading = importing,
                        onImport = triggerImportFromLibrary,
                        onTakePrivatePhoto = onOpenPrivateCamera,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(UiSize.homeSectionGap),
                ) {
                    item {
                        AlbumsSection(
                            albums = albums,
                            aiAlbums = aiAlbums,
                            onOpenAlbum = onOpenAlbum,
                            onOpenAiClassifyAlbum = onOpenAiClassifyAlbum,
                            onCreateAlbum = { creatingAlbum = true },
                        )
                    }
                }
            }

            if (showBottomNav) {
                HomeBottomNav(
                    tabs = tabs,
                    selectedIndex = selectedTab.ordinal,
                    onSelect = { idx -> onOpenTab(tabs[idx].tab) },
                )
            }
        }
    }

    AppInputDialog(
        show = creatingAlbum,
        title = stringResource(R.string.home_album_create_title),
        value = newAlbumName,
        onValueChange = { newAlbumName = it },
        placeholder = stringResource(R.string.home_album_create_input_hint),
        confirmText = stringResource(R.string.home_album_create_confirm),
        onConfirm = {
            scope.launch {
                val name = VaultStore.createAlbum(context, newAlbumName)
                newAlbumName = ""
                creatingAlbum = false
                refreshVault()
                onOpenAlbum(name)
            }
        },
        dismissText = stringResource(R.string.common_cancel),
        onDismiss = {
            newAlbumName = ""
            creatingAlbum = false
        },
    )

    ImportOriginalsDecisionDialog(
        show = pendingImportUris.isNotEmpty(),
        itemCount = pendingImportUris.size,
        rememberChoice = rememberOriginalsChoice,
        onRememberChoiceChange = { rememberOriginalsChoice = it },
        onKeepOriginals = {
            val uris = pendingImportUris
            pendingImportUris = emptyList()
            if (rememberOriginalsChoice) {
                ImportOriginalsPreferenceStore.set(context, ImportOriginalsAction.KEEP_ORIGINALS)
            }
            importSelectedUris(uris, ImportOriginalsAction.KEEP_ORIGINALS)
        },
        onDeleteOriginals = {
            val uris = pendingImportUris
            pendingImportUris = emptyList()
            if (rememberOriginalsChoice) {
                ImportOriginalsPreferenceStore.set(context, ImportOriginalsAction.DELETE_ORIGINALS)
            }
            importSelectedUris(uris, ImportOriginalsAction.DELETE_ORIGINALS)
        },
        onDismiss = {
            pendingImportUris = emptyList()
            rememberOriginalsChoice = false
        },
    )
}

@Composable
private fun ImportOriginalsDecisionDialog(
    show: Boolean,
    itemCount: Int,
    rememberChoice: Boolean,
    onRememberChoiceChange: (Boolean) -> Unit,
    onKeepOriginals: () -> Unit,
    onDeleteOriginals: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    TaskSnapshotFocusGuard("import originals decision dialog")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 328.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(26.dp),
                        clip = false,
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(UiColors.Dialog.bg)
                    .border(
                        width = 1.dp,
                        color = UiColors.Home.emptyCardStroke,
                        shape = RoundedCornerShape(26.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { }
                    .padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.import_originals_sheet_title),
                    color = UiColors.Dialog.title,
                    fontFamily = AppFontFamily,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 31.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.import_originals_sheet_message, itemCount),
                    color = UiColors.Home.subtitle,
                    fontFamily = AppFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                ImportOriginalsFlow()
                ImportOriginalsDeleteNote()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(UiColors.Home.sectionBg)
                        .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(14.dp))
                        .clickable { onRememberChoiceChange(!rememberChoice) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ImportOriginalsCheckbox(checked = rememberChoice)
                    Text(
                        text = stringResource(R.string.import_originals_remember),
                        color = UiColors.Home.title,
                        fontFamily = AppFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppButton(
                        text = stringResource(R.string.import_originals_keep_action),
                        onClick = onKeepOriginals,
                        variant = AppButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = stringResource(R.string.import_originals_delete_action),
                        onClick = onDeleteOriginals,
                        variant = AppButtonVariant.DANGER,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportOriginalsFlow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ImportOriginalsFlowItem(
            iconRes = R.drawable.ic_home_nav_album,
            title = stringResource(R.string.import_originals_gallery_label),
            tint = UiColors.Ai.cleanupExecBtnBg,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = UiColors.Home.title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .size(30.dp),
        )
        ImportOriginalsFlowItem(
            iconRes = R.drawable.ic_home_nav_vault,
            title = stringResource(R.string.app_name),
            tint = UiColors.Lock.brandBlue,
        )
    }
}

@Composable
private fun ImportOriginalsFlowItem(
    iconRes: Int,
    title: String,
    tint: Color,
) {
    Column(
        modifier = Modifier.width(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(tint.copy(alpha = 0.12f))
                .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(25.dp),
            )
        }
        Text(
            text = title,
            color = UiColors.Home.subtitle,
            fontFamily = AppFontFamily,
            fontSize = UiTextSize.homeNavLabel,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ImportOriginalsDeleteNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(UiColors.Home.sectionBg.copy(alpha = 0.80f))
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ai_help),
            contentDescription = null,
            tint = UiColors.Lock.brandBlue,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.import_originals_delete_note_android),
            color = UiColors.Home.subtitle,
            fontFamily = AppFontFamily,
            fontSize = UiTextSize.settingsRowDesc,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ImportOriginalsCheckbox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (checked) UiColors.Lock.brandBlue else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (checked) UiColors.Lock.brandBlue else UiColors.Home.navItemIdle,
                shape = RoundedCornerShape(7.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.ic_result_success),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun HomePinSetupBanner(
    message: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Lock.error.copy(alpha = 0.45f), RoundedCornerShape(UiRadius.homeCard))
            .throttledClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            color = UiColors.Home.title,
            fontSize = UiTextSize.settingsRowDesc,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = actionLabel,
            color = UiColors.Home.navItemActive,
            fontSize = UiTextSize.settingsRowDesc,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun VaultHeroCard(
    totalCount: Int,
    onImport: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(163.dp)
            .shadow(18.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(homeCardBrush())
            .border(1.dp, Color(0xFF274260), RoundedCornerShape(28.dp)),
    ) {
        Column(
            modifier = Modifier
                .padding(start = 20.dp, top = 34.dp)
                .width(230.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_vault_title),
                color = Color(0xFFF2F6FF),
                fontFamily = AppFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.home_vault_hero_status, totalCount),
                color = Color(0xFF9BAEC8),
                fontFamily = AppFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VaultHeroChip(
                iconRes = R.drawable.ic_ai_eye_off,
                text = stringResource(R.string.home_hero_chip_offline),
                width = 68.dp,
            )
            VaultHeroChip(
                iconRes = R.drawable.ic_home_nav_vault,
                text = stringResource(R.string.home_hero_chip_encrypted),
                width = 80.dp,
            )
            VaultHeroChip(
                iconRes = R.drawable.ic_home_nav_ai,
                text = stringResource(R.string.home_hero_chip_ai_local),
                width = 104.dp,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 12.dp)
                .size(112.dp)
                .throttledClickable(onClick = onImport),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo_hero),
                contentDescription = stringResource(R.string.home_vault_empty_action),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VaultHeroChip(
    iconRes: Int,
    text: String,
    width: Dp,
) {
    Row(
        modifier = Modifier
            .width(width)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(13.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color(0xFFB7D7FF),
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = Color(0xFFD9E9FF),
            fontFamily = AppFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun homeShelfBrush(): Brush =
    homeCardBrush()

private fun homeCardBrush(): Brush =
    Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color(0xFF0C1929),
            0.52f to Color(0xFF08111D),
            1.00f to Color(0xFF0D2238),
        ),
    )

@Composable
private fun AlbumsSection(
    albums: List<VaultAlbum>,
    aiAlbums: List<AiClassifyVirtualAlbum>,
    onOpenAlbum: (String) -> Unit,
    onOpenAiClassifyAlbum: (ClassifyCategory) -> Unit,
    onCreateAlbum: () -> Unit,
) {
    val albumItems = remember(albums, aiAlbums) {
        buildList<VaultHomeAlbumTile> {
            albums.forEach { add(VaultHomeAlbumTile.Album(it)) }
            aiAlbums.forEach { add(VaultHomeAlbumTile.AiAlbum(it)) }
            add(VaultHomeAlbumTile.Create)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(26.dp), clip = false)
            .clip(RoundedCornerShape(26.dp))
            .background(homeShelfBrush())
            .border(1.dp, Color(0xFF203A59), RoundedCornerShape(26.dp))
            .padding(UiSize.homeCardPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.home_albums_title),
            color = Color(0xFFF6F9FF),
            fontFamily = AppFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.height(34.dp),
        )
        albumItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { item ->
                    when (item) {
                        is VaultHomeAlbumTile.Album -> AlbumCard(
                            album = item.album,
                            onClick = { onOpenAlbum(item.album.name) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        is VaultHomeAlbumTile.AiAlbum -> AiAlbumCard(
                            album = item.album,
                            onClick = { onOpenAiClassifyAlbum(item.album.category) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        VaultHomeAlbumTile.Create -> CreateAlbumCard(
                            onClick = onCreateAlbum,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: VaultAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberFeedbackInteractionSource()
    val albumName = if (album.name == DEFAULT_ALBUM_NAME) {
        stringResource(R.string.album_default_name)
    } else {
        album.name
    }
    Column(
        modifier = modifier
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF12304C)),
            contentAlignment = Alignment.Center,
        ) {
            if (album.coverPath != null) {
                VaultProgressiveImage(
                    path = album.coverPath,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    thumbnailMaxPx = 420,
                    showVideoIndicator = true,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_album_empty_placeholder),
                    contentDescription = null,
                    tint = Color(0x8AAFC4E2),
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Text(
            text = albumName,
            color = Color(0xFFF4F8FF),
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AiAlbumCard(
    album: AiClassifyVirtualAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberFeedbackInteractionSource()
    Column(
        modifier = modifier
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF102A46)),
            contentAlignment = Alignment.Center,
        ) {
            if (album.coverPath != null) {
                VaultProgressiveImage(
                    path = album.coverPath,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    thumbnailMaxPx = 420,
                    showVideoIndicator = true,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_sparkles),
                    contentDescription = null,
                    tint = Color(0xFF8EC5FF),
                    modifier = Modifier.size(34.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC0A1828)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_sparkles),
                    contentDescription = null,
                    tint = Color(0xFF8EC5FF),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = stringResource(album.titleRes),
            color = Color(0xFFF4F8FF),
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CreateAlbumCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberFeedbackInteractionSource()
    Column(
        modifier = modifier
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0D2742)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_action_add),
                contentDescription = null,
                tint = UiColors.Home.navItemActive,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(modifier = Modifier.height(34.dp))
    }
}

private sealed interface VaultHomeAlbumTile {
    data class Album(val album: VaultAlbum) : VaultHomeAlbumTile
    data class AiAlbum(val album: AiClassifyVirtualAlbum) : VaultHomeAlbumTile
    data object Create : VaultHomeAlbumTile
}

@Composable
private fun VaultEmptyState(
    isLoading: Boolean,
    onImport: () -> Unit,
    onTakePrivatePhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(406.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .background(homeCardBrush())
            .border(1.dp, Color(0xFF203A59), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF132944)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_nav_vault),
                contentDescription = null,
                tint = UiColors.Lock.brandBlue,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = stringResource(R.string.home_vault_empty_title),
            color = UiColors.Home.emptyTitle,
            fontSize = UiTextSize.vaultEmptyTitle,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_vault_empty_desc),
            color = UiColors.Home.emptyBody,
            fontSize = UiTextSize.vaultEmptyBody,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(296.dp),
        )
        VaultEmptyActionButton(
            text = stringResource(R.string.home_vault_empty_action),
            onClick = onImport,
            isPrimary = true,
            loading = isLoading,
            showIcon = false,
            modifier = Modifier
                .fillMaxWidth(),
        )
        VaultEmptyActionButton(
            text = stringResource(R.string.home_vault_take_private_photo),
            onClick = onTakePrivatePhoto,
            isPrimary = false,
            modifier = Modifier
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun VaultEmptyActionButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    showIcon: Boolean = true,
) {
    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPrimary) UiColors.Button.primaryContainer else UiColors.Button.secondaryContainer)
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                enabled = !loading,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPrimary && !loading && showIcon) {
            Icon(
                painter = painterResource(R.drawable.ic_home_nav_import),
                contentDescription = null,
                tint = UiColors.Button.primaryContent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = if (isPrimary) UiColors.Button.primaryContent else UiColors.Button.secondaryContent,
            )
        } else {
            Text(
                text = text,
                color = if (isPrimary) UiColors.Button.primaryContent else UiColors.Button.secondaryContent,
                fontFamily = AppFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun HomeBottomNav(
    tabs: List<HomeNavTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(UiSize.homeNavBarHeight)
                .clip(RoundedCornerShape(UiRadius.homeNavBar))
                .background(UiColors.Home.navBarBg)
                .border(1.dp, UiColors.Home.navBarStroke, RoundedCornerShape(UiRadius.homeNavBar))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { idx, tab ->
                val selected = idx == selectedIndex
                val interaction = rememberFeedbackInteractionSource()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(UiRadius.homeNavItem))
                        .background(if (selected) UiColors.Home.navItemActiveBg else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (selected) UiColors.Home.navItemActiveStroke else Color.Transparent,
                            shape = RoundedCornerShape(UiRadius.homeNavItem),
                        )
                        .pressFeedback(interaction)
                        .throttledClickable(interactionSource = interaction, indication = null, onClick = { onSelect(idx) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = stringResource(tab.labelRes),
                        tint = if (selected) UiColors.Home.navItemActive else UiColors.Home.navItemIdle,
                        modifier = Modifier.size(UiSize.homeNavIcon),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        color = if (selected) UiColors.Home.navItemActive else UiColors.Home.navItemIdle,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

data class HomeNavTab(
    val tab: HomeTab,
    val iconRes: Int,
    val labelRes: Int,
    val emptyTitleRes: Int,
    val emptyDescRes: Int,
    val emptyActionRes: Int,
)

enum class HomeTab { VAULT, CAMERA, AI, SETTINGS }

fun homeTabs(): List<HomeNavTab> = listOf(
    HomeNavTab(HomeTab.VAULT, R.drawable.ic_home_nav_vault, R.string.home_nav_vault, R.string.home_vault_empty_title, R.string.home_vault_empty_desc, R.string.home_vault_empty_action),
    HomeNavTab(HomeTab.CAMERA, R.drawable.ic_home_nav_camera, R.string.home_nav_camera, R.string.home_camera_empty_title, R.string.home_camera_empty_desc, R.string.home_camera_empty_action),
    HomeNavTab(HomeTab.AI, R.drawable.ic_home_nav_ai, R.string.home_nav_ai, R.string.home_ai_empty_title, R.string.home_ai_empty_desc, R.string.home_ai_empty_action),
    HomeNavTab(HomeTab.SETTINGS, R.drawable.ic_home_nav_settings, R.string.home_nav_settings, R.string.home_settings_empty_title, R.string.home_settings_empty_desc, R.string.home_settings_empty_action),
)

private data class DeleteOriginalsDirectResult(
    val deletedCount: Int,
    val permissionRequest: IntentSenderRequest?,
    val retryUris: List<Uri>,
)

private data class DeleteOriginalsTargets(
    val uris: List<Uri>,
    val unsupportedCount: Int,
)

private suspend fun deleteOriginalUrisDirect(
    context: Context,
    uris: List<Uri>,
): DeleteOriginalsDirectResult = withContext(Dispatchers.IO) {
    val deleteUris = mediaStoreDeleteTargets(context, uris).uris
    var deletedCount = 0
    for ((index, uri) in deleteUris.withIndex()) {
        try {
            if (context.contentResolver.delete(uri, null, null) > 0) {
                deletedCount += 1
            }
        } catch (error: Throwable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error is RecoverableSecurityException) {
                val retryUris = deleteUris.drop(index)
                val request = IntentSenderRequest.Builder(error.userAction.actionIntent.intentSender).build()
                return@withContext DeleteOriginalsDirectResult(
                    deletedCount = deletedCount,
                    permissionRequest = request,
                    retryUris = retryUris,
                )
            }
        }
    }
    DeleteOriginalsDirectResult(
        deletedCount = deletedCount,
        permissionRequest = null,
        retryUris = emptyList(),
    )
}

private fun mediaStoreDeleteTargets(context: Context, uris: List<Uri>): DeleteOriginalsTargets {
    val resolved = uris.mapNotNull { resolveMediaStoreDeleteUri(context, it) }.distinct()
    return DeleteOriginalsTargets(
        uris = resolved,
        unsupportedCount = (uris.size - resolved.size).coerceAtLeast(0),
    )
}

private fun resolveMediaStoreDeleteUri(context: Context, uri: Uri): Uri? {
    if (isDeletableMediaStoreUri(uri)) return uri

    val pickerMetadata = readPickerMetadata(context, uri)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val converted = runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
        if (converted != null && isDeletableMediaStoreUri(converted)) {
            return converted.takeIf { mediaStoreUriExists(context, it) }
        }
    }

    resolvePickerUriByMediaId(context, uri, pickerMetadata)?.let { return it }
    return resolveMediaStoreUriByMetadata(context, pickerMetadata)
}

private fun resolvePickerUriByMediaId(
    context: Context,
    uri: Uri,
    metadata: PickerMediaMetadata,
): Uri? {
    val id = uri.pathSegments.lastOrNull()?.toLongOrNull() ?: return null
    val candidates = mediaStoreCollections(metadata.mimeType)
        .map { collection -> ContentUris.withAppendedId(collection, id) }
    return candidates.firstOrNull { candidate -> mediaStoreUriExists(context, candidate) }
        ?: candidates.firstOrNull()
}

private fun mediaStoreUriExists(context: Context, uri: Uri): Boolean =
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns._ID),
            null,
            null,
            null,
        )?.use { cursor -> cursor.moveToFirst() } ?: false
    }.getOrElse { true }

private fun resolveMediaStoreUriByMetadata(
    context: Context,
    metadata: PickerMediaMetadata,
): Uri? {
    val displayName = metadata.displayName ?: return null
    val size = metadata.size ?: return null
    val matches = mediaStoreCollections(metadata.mimeType).flatMap { collection ->
        queryMediaStoreMatches(context, collection, displayName, size, metadata.mimeType)
    }
    return matches.distinct().singleOrNull()
}

private fun queryMediaStoreMatches(
    context: Context,
    collection: Uri,
    displayName: String,
    size: Long,
    mimeType: String?,
): List<Uri> {
    val selectionParts = mutableListOf(
        "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
        "${MediaStore.MediaColumns.SIZE}=?",
    )
    val selectionArgs = mutableListOf(displayName, size.toString())
    if (!mimeType.isNullOrBlank()) {
        selectionParts += "${MediaStore.MediaColumns.MIME_TYPE}=?"
        selectionArgs += mimeType
    }
    val projection = arrayOf(MediaStore.MediaColumns._ID)
    return runCatching {
        context.contentResolver.query(
            collection,
            projection,
            selectionParts.joinToString(" AND "),
            selectionArgs.toTypedArray(),
            null,
        )?.use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    add(ContentUris.withAppendedId(collection, cursor.getLong(idIndex)))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

private fun mediaStoreCollections(mimeType: String?): List<Uri> {
    val includeImages = mimeType == null || mimeType.startsWith("image/")
    val includeVideos = mimeType == null || mimeType.startsWith("video/")
    val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        listOf(MediaStore.VOLUME_EXTERNAL, MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        emptyList()
    }
    return buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (volume in volumes) {
                if (includeImages) add(MediaStore.Images.Media.getContentUri(volume))
                if (includeVideos) add(MediaStore.Video.Media.getContentUri(volume))
            }
        } else {
            if (includeImages) add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            if (includeVideos) add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
    }.distinct()
}

private fun isDeletableMediaStoreUri(uri: Uri): Boolean {
    val segments = uri.pathSegments
    return uri.scheme == "content" &&
        uri.authority == MediaStore.AUTHORITY &&
        segments.size >= 4 &&
        segments[1] in setOf("images", "video") &&
        segments[2] == "media" &&
        segments.lastOrNull()?.toLongOrNull() != null
}

private data class PickerMediaMetadata(
    val displayName: String?,
    val size: Long?,
    val mimeType: String?,
)

private fun readPickerMetadata(context: Context, uri: Uri): PickerMediaMetadata {
    var displayName: String? = null
    var size: Long? = null
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)
                size = cursor.longOrNull(OpenableColumns.SIZE)
            }
        }
    }
    return PickerMediaMetadata(
        displayName = displayName,
        size = size,
        mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull(),
    )
}

private fun android.database.Cursor.stringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun android.database.Cursor.longOrNull(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

private fun buildDeleteOriginalsTip(
    context: Context,
    deletedCount: Int,
    unsupportedCount: Int,
): ImportTip {
    val message = when {
        deletedCount > 0 && unsupportedCount > 0 -> context.getString(
            R.string.import_originals_delete_partial,
            deletedCount,
            unsupportedCount,
        )
        deletedCount > 0 -> context.getString(R.string.import_originals_delete_success, deletedCount)
        else -> context.getString(R.string.import_originals_delete_unavailable)
    }
    return ImportTip(message, deletedCount == 0 || unsupportedCount > 0)
}

private data class ImportTip(val message: String, val isError: Boolean)
