package com.xpx.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.VaultStore
import com.xpx.vault.ui.vault.VaultTrashItem
import kotlinx.coroutines.launch
import kotlin.math.ceil

@Composable
fun TrashBinScreen(
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<VaultTrashItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun refresh() {
        items = VaultStore.listTrashItems(context)
        loaded = true
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.backupScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
    ) {
        AppTopBar(title = stringResource(R.string.trash_title), onBack = onBack)
        Text(
            text = stringResource(R.string.trash_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeSubtitle,
            modifier = Modifier.padding(top = UiSize.backupSubtitleTopGap),
        )
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "加载中...", color = UiColors.Home.subtitle)
            }
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.trash_empty),
                    color = UiColors.Home.subtitle,
                    fontSize = UiTextSize.homeEmptyBody,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(top = UiSize.backupCardTopGap)
                    .clip(RoundedCornerShape(UiRadius.homeCard))
                    .background(UiColors.Home.sectionBg)
                    .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
                    .padding(UiSize.homeCardPadding),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
                    verticalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
                ) {
                    items(items, key = { it.path }) { item ->
                        TrashGridCell(
                            item = item,
                            onClick = { onOpenItem(item.path) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashGridCell(
    item: VaultTrashItem,
    onClick: () -> Unit,
) {
    val remainingDays = remember(item.trashedAtMs) { calcRemainingDays(item.trashedAtMs) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(UiRadius.homeThumb))
            .throttledClickable(onClick = onClick),
    ) {
        VaultProgressiveImage(
            path = item.path,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            thumbnailMaxPx = 360,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.trash_remaining_days, remainingDays),
                color = UiColors.Home.title,
                fontSize = UiTextSize.homeNavLabel,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun calcRemainingDays(trashedAtMs: Long): Int {
    val retainMs = VaultStore.trashRetainDurationMs()
    val remainingMs = (trashedAtMs + retainMs) - System.currentTimeMillis()
    if (remainingMs <= 0L) return 0
    val dayMs = 24L * 60 * 60 * 1000
    return ceil(remainingMs.toDouble() / dayMs).toInt().coerceAtLeast(1)
}
