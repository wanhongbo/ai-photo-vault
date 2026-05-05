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
import androidx.compose.foundation.layout.width
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xpx.vault.R
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.export.ExportRuntimeState
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.VaultPhoto
import com.xpx.vault.ui.vault.VaultStore
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(
    albumName: String,
    onOpenPhoto: (String) -> Unit,
    onBack: () -> Unit,
    onOpenExportProgress: () -> Unit = {},
) {
    val context = LocalContext.current
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
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    VaultStore.importFromPicker(context, uri, albumName)
                }
                reload()
                // 导入完成后触发一次增量 AI 扫描。
                com.xpx.vault.ai.AiScanEntryPoint.from(context).requestScan()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(16.dp),
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
                Text(text = "加载中...", color = UiColors.Home.subtitle)
            }
        } else if (photos.isEmpty()) {
            val emptyAddInteraction = rememberFeedbackInteractionSource()
            val emptyPressed = emptyAddInteraction.collectIsPressedAsState()
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(UiSize.homeEmptyIconWrap)
                        .plusPressFeedback(emptyAddInteraction, CircleShape)
                        .clip(CircleShape)
                        .background(UiColors.Home.emptyIconBg)
                        .border(
                            width = if (emptyPressed.value) 1.8.dp else 1.2.dp,
                            color = if (emptyPressed.value) UiColors.Home.navItemActive else UiColors.Home.navItemActiveStroke.copy(alpha = 0.55f),
                            shape = CircleShape,
                        )
                        .throttledClickable(interactionSource = emptyAddInteraction, indication = null) {
                            pickerLauncher.launch(
                                PickVisualMediaRequest.Builder()
                                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    .build(),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painter = painterResource(R.drawable.ic_home_action_add), contentDescription = null, tint = UiColors.Home.navItemActive)
                }
                Text(
                    text = stringResource(R.string.album_empty_title),
                    color = UiColors.Home.emptyTitle,
                    fontSize = UiTextSize.homeEmptyTitle,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = stringResource(R.string.album_empty_desc),
                    color = UiColors.Home.emptyBody,
                    fontSize = UiTextSize.homeEmptyBody,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(UiRadius.homeCard))
                    .background(UiColors.Home.sectionBg)
                    .padding(UiSize.homeCardPadding),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
                    verticalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
                ) {
                    if (!selectionMode) {
                        item {
                            val addInteraction = rememberFeedbackInteractionSource()
                            val addPressed = addInteraction.collectIsPressedAsState()
                            Box(
                                modifier = Modifier
                                    .size(UiSize.homeThumbSize)
                                    .plusPressFeedback(addInteraction, RoundedCornerShape(UiRadius.homeThumb))
                                    .clip(RoundedCornerShape(UiRadius.homeThumb))
                                    .background(UiColors.Home.emptyIconBg)
                                    .border(
                                        width = if (addPressed.value) 1.8.dp else 1.2.dp,
                                        color = if (addPressed.value) UiColors.Home.navItemActive else UiColors.Home.navItemActiveStroke.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(UiRadius.homeThumb),
                                    )
                                    .throttledClickable(interactionSource = addInteraction, indication = null) {
                                        pickerLauncher.launch(
                                            PickVisualMediaRequest.Builder()
                                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                                .build(),
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_home_action_add),
                                    contentDescription = null,
                                    tint = UiColors.Home.navItemActive,
                                )
                            }
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
                            ExportRuntimeState.enqueue(selected.toList())
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
private fun AlbumTopBar(
    title: String,
    selectionMode: Boolean,
    allSelected: Boolean,
    onBack: () -> Unit,
    onToggleSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(38.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(UiColors.Home.navBarBg)
                .throttledClickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_topbar_back),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        if (selectionMode) {
            Text(
                text = stringResource(if (allSelected) R.string.album_deselect_all else R.string.album_select_all),
                color = UiColors.Home.navItemActive,
                fontSize = UiTextSize.homeNavLabel,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .throttledClickable(onClick = onToggleSelectAll)
                    .padding(horizontal = 10.dp)
                    .wrapContentVerticalCenter(),
            )
        } else {
            Row(
                modifier = Modifier
                    .width(38.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UiColors.Home.navBarBg)
                    .throttledClickable(onClick = onToggleSelection),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_photo_select),
                    contentDescription = stringResource(R.string.album_action_select),
                    tint = UiColors.Home.title,
                    modifier = Modifier.size(18.dp),
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
            .size(UiSize.homeThumbSize)
            .clip(RoundedCornerShape(UiRadius.homeThumb))
            .throttledClickable(onClick = onClick),
    ) {
        VaultProgressiveImage(
            path = photo.path,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(UiRadius.homeThumb)),
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
        .then(Modifier.pressFeedback(interactionSource, extraHighlight = true))
        .then(Modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value))
}
