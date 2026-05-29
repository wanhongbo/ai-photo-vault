package com.xpx.vault.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xpx.vault.R
import com.xpx.vault.findAppLockManager
import com.xpx.vault.launchExternalSystemUi
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.export.ExportRuntimeState
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.AppFontFamily
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.theme.UiTouch
import com.xpx.vault.ui.vault.VaultPhoto
import com.xpx.vault.ui.vault.VaultStore
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(
    albumName: String,
    onOpenPhoto: (String) -> Unit,
    onBack: () -> Unit,
    onOpenExportProgress: () -> Unit = {},
    onPaywallRequired: () -> Unit = {},
) {
    val context = LocalContext.current
    val appLockManager = remember(context) { context.findAppLockManager() }
    fun launchSystemUi(reason: String, launch: () -> Unit) {
        appLockManager?.launchExternalSystemUi(reason, launch) ?: launch()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cachedPhotos = remember(albumName) { VaultStore.peekCachedAlbumPhotos(albumName) }
    var photos by remember(albumName) { mutableStateOf(cachedPhotos.orEmpty()) }
    var loaded by remember(albumName) { mutableStateOf(cachedPhotos != null) }

    // Selection mode state
    var selectionMode by remember(albumName) { mutableStateOf(false) }
    var selected by remember(albumName) { mutableStateOf(setOf<String>()) }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    fun toggleSelected(path: String) {
        selected = if (selected.contains(path)) selected - path else selected + path
    }

    suspend fun reload() {
        val latest = VaultStore.listPhotosInAlbum(context, albumName)
        VaultStore.loadSnapshot(context, recentLimit = 0)
        if (photos != latest) photos = latest
        loaded = true
        // Keep only still-existing selections.
        if (selected.isNotEmpty()) {
            val stillExists = latest.map { it.path }.toSet()
            val newSelected = selected.filter { it in stillExists }.toSet()
            if (newSelected.size != selected.size) selected = newSelected
        }
    }
    LaunchedEffect(albumName) { reload() }
    DisposableEffect(lifecycleOwner, albumName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { reload() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        appLockManager?.endExternalSystemUi("album photo picker result")
        if (uris.isNotEmpty()) {
            scope.launch {
                var quotaExceeded = false
                for (uri in uris) {
                    val result = VaultStore.importFromPicker(context, uri, albumName)
                    if (result == com.xpx.vault.ui.vault.VaultImportResult.QUOTA_EXCEEDED) {
                        quotaExceeded = true
                        break
                    }
                }
                reload()
                // 导入完成后触发一次增量 AI 扫描。
                com.xpx.vault.ai.AiScanEntryPoint.from(context).requestScan()
                if (quotaExceeded) {
                    onPaywallRequired()
                }
            }
        }
    }

    // 导入入口统一走配额硬墙检查；vault 已满时跳支付墙。
    val triggerImport: () -> Unit = {
        scope.launch {
            VaultStore.canAddNewItem(context)
            val gatekeeper = com.xpx.vault.billing.PaywallGatekeeperProvider.get(context)
            val gate = gatekeeper?.checkAccess(com.xpx.vault.domain.quota.ProFeature.VAULT_IMPORT)
            if (gate is com.xpx.vault.billing.GateResult.HardWall) {
                onPaywallRequired()
            } else {
                launchSystemUi("album photo picker") {
                    pickerLauncher.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            .build(),
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
    ) {
        AlbumTopBar(
            title = if (selectionMode) stringResource(R.string.album_select_mode_title, selected.size) else albumName,
            selectionMode = selectionMode,
            allSelected = selectionMode && selected.size == photos.size && photos.isNotEmpty(),
            onBack = { if (selectionMode) exitSelection() else onBack() },
            onToggleSelection = {
                if (!selectionMode) {
                    selectionMode = true
                } else {
                    exitSelection()
                }
            },
            onToggleSelectAll = {
                selected = if (selected.size == photos.size) emptySet() else photos.map { it.path }.toSet()
            },
        )
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.common_loading), color = UiColors.Home.subtitle)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp)
                    .height(if (photos.isEmpty()) 135.dp else 337.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(UiColors.Home.sectionBg)
                    .border(1.dp, UiColors.Home.navBarStroke, RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!selectionMode) {
                        item {
                            AlbumImportGridItem(onClick = triggerImport)
                        }
                    }
                    items(photos, key = { it.path }) { photo ->
                        AlbumGridItem(
                            photo = photo,
                            selectionMode = selectionMode,
                            selected = selected.contains(photo.path),
                            onClick = {
                                if (selectionMode) toggleSelected(photo.path) else onOpenPhoto(photo.path)
                            },
                            onLongPress = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selected = setOf(photo.path)
                                }
                            },
                        )
                    }
                }
            }
            if (selectionMode) {
                AlbumSelectionBottomBar(
                    selectedCount = selected.size,
                    onShare = { /* TODO: share selected */ },
                    onExport = {
                        if (selected.isNotEmpty()) {
                            val isPremium = com.xpx.vault.billing.SubscriptionRepoProvider.get(context)?.isPremium?.value ?: false
                            ExportRuntimeState.enqueue(selected.toList(), skipWatermark = isPremium)
                            onOpenExportProgress()
                            // Exit selection so returning to album is clean.
                            exitSelection()
                        }
                    },
                    onDelete = {
                        val toDelete = selected.toList()
                        scope.launch {
                            toDelete.forEach { VaultStore.deletePhoto(context, it) }
                            exitSelection()
                            reload()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AlbumImportGridItem(
    onClick: () -> Unit,
) {
    val addInteraction = rememberFeedbackInteractionSource()
    val addPressed = addInteraction.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(103.dp)
            .plusPressFeedback(addInteraction, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF142741))
            .border(
                width = if (addPressed.value) 1.8.dp else 1.2.dp,
                color = if (addPressed.value) UiColors.Home.navItemActive else UiColors.Home.emptyCardStroke,
                shape = RoundedCornerShape(10.dp),
            )
            .throttledClickable(interactionSource = addInteraction, indication = null) {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_home_action_add),
            contentDescription = stringResource(R.string.album_action_add),
            tint = Color(0xFF9FB2D1),
            modifier = Modifier.size(29.dp),
        )
    }
}

@Composable
private fun AlbumTopBar(
    title: String,
    selectionMode: Boolean,
    allSelected: Boolean,
    onBack: () -> Unit,
    onToggleSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(UiTouch.minTarget)
                .clip(RoundedCornerShape(14.dp))
                .background(UiColors.Home.sectionBg)
                .throttledClickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_topbar_back),
                contentDescription = stringResource(R.string.common_back),
                tint = UiColors.Home.title,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            color = UiColors.Home.title,
            fontFamily = AppFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
        )
        if (selectionMode) {
            Text(
                text = stringResource(if (allSelected) R.string.album_deselect_all else R.string.album_select_all),
                color = UiColors.Home.navItemActive,
                fontSize = UiTextSize.homeNavLabel,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .throttledClickable(onClick = onToggleSelectAll)
                    .padding(horizontal = 10.dp)
                    .wrapContentVerticalCenter(),
            )
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(UiTouch.minTarget)
                    .clip(RoundedCornerShape(14.dp))
                    .background(UiColors.Home.sectionBg)
                    .border(1.dp, UiColors.Home.navBarStroke, RoundedCornerShape(14.dp))
                    .throttledClickable(onClick = onToggleSelection),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_video_more),
                    contentDescription = stringResource(R.string.album_action_more),
                    tint = UiColors.Home.title,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun Modifier.wrapContentVerticalCenter(): Modifier = this.then(
    Modifier.padding(top = 8.dp, bottom = 8.dp),
)

@Composable
private fun AlbumGridItem(
    photo: VaultPhoto,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(103.dp)
            .clip(RoundedCornerShape(10.dp))
            .throttledClickable(onClick = onClick),
    ) {
        VaultProgressiveImage(
            path = photo.path,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            thumbnailMaxPx = 360,
            showVideoIndicator = true,
        )
        if (selectionMode) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(UiColors.Home.navItemActive.copy(alpha = 0.28f)),
                )
            }
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) UiColors.Home.navItemActive else Color.Black.copy(alpha = 0.35f),
                    )
                    .border(
                        1.2.dp,
                        Color.White.copy(alpha = 0.9f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        fontSize = UiTextSize.homeNavLabel,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSelectionBottomBar(
    selectedCount: Int,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val enabled = selectedCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomActionButton(
            iconRes = R.drawable.ic_photo_share,
            label = stringResource(R.string.photo_viewer_share),
            enabled = enabled,
            onClick = onShare,
        )
        BottomActionButton(
            iconRes = R.drawable.ic_photo_save,
            label = stringResource(R.string.album_action_camera_roll),
            enabled = enabled,
            onClick = onExport,
            primary = true,
        )
        BottomActionButton(
            iconRes = R.drawable.ic_photo_delete,
            label = stringResource(R.string.photo_viewer_delete),
            enabled = enabled,
            onClick = onDelete,
            danger = true,
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun BottomActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    primary: Boolean = false,
    danger: Boolean = false,
) {
    val tint = when {
        !enabled -> UiColors.Home.navItemIdle
        danger -> UiColors.Lock.error
        primary -> UiColors.Home.navItemActive
        else -> UiColors.Home.title
    }
    val interaction = rememberFeedbackInteractionSource()
    Column(
        modifier = Modifier
            .pressFeedback(interaction)
            .then(
                if (enabled) Modifier.throttledClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ) else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = UiTextSize.homeNavLabel,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Modifier.plusPressFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape,
): Modifier {
    val pressed = interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed.value) 0.86f else 1f,
        animationSpec = tween(durationMillis = if (pressed.value) 60 else 220),
        label = "plusPressScale",
    )
    val elevation = animateDpAsState(
        targetValue = if (pressed.value) 0.dp else 10.dp,
        animationSpec = tween(durationMillis = if (pressed.value) 60 else 220),
        label = "plusPressElevation",
    )
    return this
        .shadow(elevation = elevation.value, shape = shape, clip = false)
        .then(Modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value))
}
