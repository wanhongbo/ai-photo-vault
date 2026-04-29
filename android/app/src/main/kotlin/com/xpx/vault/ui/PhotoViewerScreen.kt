package com.xpx.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppDialog
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.VaultStore
import com.xpx.vault.ui.vault.isVaultImage

@Composable
fun PhotoViewerScreen(
    path: String,
    onBack: () -> Unit,
    isTrash: Boolean = false,
    onOpenAlbum: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenMaxPx = with(density) {
        maxOf(configuration.screenWidthDp.dp.roundToPx(), configuration.screenHeightDp.dp.roundToPx())
    }
    var currentPath by remember(path) { mutableStateOf(path) }
    var orderedPaths by remember { mutableStateOf(listOf(path)) }
    var horizontalDragOffset by remember { mutableStateOf(0f) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPurgeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentIndex = remember(currentPath, orderedPaths) {
        orderedPaths.indexOf(currentPath).coerceAtLeast(0)
    }
    val swipeTriggerPx = with(density) { 48.dp.toPx() }

    fun showPrevious() {
        if (currentIndex > 0) currentPath = orderedPaths[currentIndex - 1]
    }

    fun showNext() {
        if (currentIndex < orderedPaths.lastIndex) currentPath = orderedPaths[currentIndex + 1]
    }

    fun removeCurrentFromList() {
        orderedPaths = orderedPaths.filter { it != currentPath }
        when {
            orderedPaths.isEmpty() -> onBack()
            currentIndex >= orderedPaths.size -> currentPath = orderedPaths.last()
            else -> currentPath = orderedPaths[currentIndex]
        }
    }

    LaunchedEffect(path, isTrash) {
        currentPath = path
        val allPaths = if (isTrash) {
            VaultStore.listTrashItems(context)
                .map { it.path }
                .filter(::isVaultImage)
        } else {
            VaultStore.listRecentPhotos(context, limit = 5000)
                .map { it.path }
                .filterNot(::isVideoPath)
        }
        orderedPaths = if (allPaths.contains(path)) {
            allPaths
        } else {
            listOf(path) + allPaths
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppDialog(
            show = showDeleteDialog,
            title = stringResource(R.string.photo_viewer_delete_title),
            message = stringResource(R.string.photo_viewer_delete_message),
            confirmText = stringResource(R.string.common_confirm),
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    val deleted = VaultStore.deletePhoto(context, currentPath)
                    if (deleted) removeCurrentFromList()
                }
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { showDeleteDialog = false },
            confirmVariant = com.xpx.vault.ui.components.AppButtonVariant.DANGER,
        )
        AppDialog(
            show = showPurgeDialog,
            title = stringResource(R.string.trash_purge_title),
            message = stringResource(R.string.trash_purge_message),
            confirmText = stringResource(R.string.trash_delete),
            onConfirm = {
                showPurgeDialog = false
                scope.launch {
                    val purged = VaultStore.purgeFromTrash(currentPath)
                    if (purged) onBack()
                }
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { showPurgeDialog = false },
            confirmVariant = AppButtonVariant.DANGER,
        )
        AppTopBar(title = stringResource(R.string.photo_viewer_title), onBack = onBack)
        VaultProgressiveImage(
            path = currentPath,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(currentPath, currentIndex, orderedPaths.size) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            horizontalDragOffset += dragAmount
                        },
                        onDragEnd = {
                            when {
                                horizontalDragOffset > swipeTriggerPx -> showPrevious()
                                horizontalDragOffset < -swipeTriggerPx -> showNext()
                            }
                            horizontalDragOffset = 0f
                        },
                        onDragCancel = { horizontalDragOffset = 0f },
                    )
                },
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            thumbnailMaxPx = 1080,
            loadHighQuality = true,
            highQualityMaxPx = screenMaxPx,
        )
        Text(
            text = "左右滑动可切换",
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeNavLabel,
        )
        if (isTrash) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButton(
                    text = stringResource(R.string.trash_recover),
                    onClick = {
                        scope.launch {
                            val album = VaultStore.restoreFromTrash(context, currentPath)
                            if (album != null) {
                                if (onOpenAlbum != null) onOpenAlbum(album) else onBack()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.SECONDARY,
                )
                AppButton(
                    text = stringResource(R.string.trash_delete),
                    onClick = { showPurgeDialog = true },
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.DANGER,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhotoViewerActionButton(
                    iconRes = R.drawable.ic_photo_share,
                    label = stringResource(R.string.photo_viewer_share),
                    onClick = { /* TODO: share */ },
                )
                PhotoViewerActionButton(
                    iconRes = R.drawable.ic_photo_edit,
                    label = stringResource(R.string.photo_viewer_edit),
                    onClick = { /* TODO: edit */ },
                )
                PhotoViewerActionButton(
                    iconRes = R.drawable.ic_photo_info,
                    label = stringResource(R.string.photo_viewer_info),
                    onClick = { /* TODO: info */ },
                )
                PhotoViewerActionButton(
                    iconRes = R.drawable.ic_photo_delete,
                    label = stringResource(R.string.photo_viewer_delete),
                    onClick = { showDeleteDialog = true },
                )
            }
        }
    }
}

@Composable
private fun PhotoViewerActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Column(
        modifier = Modifier
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = UiColors.Home.title,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeNavLabel,
        )
    }
}

private fun isVideoPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".mp4") ||
        lower.endsWith(".m4v") ||
        lower.endsWith(".mov") ||
        lower.endsWith(".3gp") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".mkv")
}
