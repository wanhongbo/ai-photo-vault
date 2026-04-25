package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppButtonVariant
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.ui.components.VaultProgressiveImage
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiTextSize
import com.photovault.app.ui.vault.VaultStore

@Composable
fun PhotoViewerPlaceholderScreen(
    path: String,
    onBack: () -> Unit,
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

    LaunchedEffect(path) {
        currentPath = path
        val allPaths = VaultStore.listRecentPhotos(context, limit = 5000)
            .map { it.path }
            .filterNot(::isVideoPath)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppButton(
                text = stringResource(R.string.photo_viewer_prev),
                onClick = ::showPrevious,
                variant = AppButtonVariant.SECONDARY,
                enabled = currentIndex > 0,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = stringResource(R.string.photo_viewer_next),
                onClick = ::showNext,
                enabled = currentIndex < orderedPaths.lastIndex,
                modifier = Modifier.weight(1f),
            )
        }
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

