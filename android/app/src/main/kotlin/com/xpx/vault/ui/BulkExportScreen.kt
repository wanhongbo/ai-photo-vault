package com.xpx.vault.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.export.ExportRuntimeState
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.VaultPhoto
import com.xpx.vault.ui.vault.VaultStore
import com.xpx.vault.ui.vault.isVaultImage
import com.xpx.vault.ui.vault.isVaultVideo

enum class BulkExportFilter { ALL, IMAGES, VIDEOS }

@Composable
fun BulkExportScreen(
    onOpenExportProgress: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var allPhotos by remember { mutableStateOf<List<VaultPhoto>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(BulkExportFilter.ALL) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        val albums = VaultStore.listAlbums(context)
        val merged = albums.flatMap { VaultStore.listPhotosInAlbum(context, it.name) }
            .sortedByDescending { it.modifiedAtMs }
        allPhotos = merged
        loaded = true
    }

    val filteredPhotos = remember(filter, allPhotos) {
        when (filter) {
            BulkExportFilter.ALL -> allPhotos
            BulkExportFilter.IMAGES -> allPhotos.filter { isVaultImage(it.path) }
            BulkExportFilter.VIDEOS -> allPhotos.filter { isVaultVideo(it.path) }
        }
    }

    val allInViewSelected = filteredPhotos.isNotEmpty() &&
        filteredPhotos.all { it.path in selected }

    fun toggleSelected(path: String) {
        selected = if (selected.contains(path)) selected - path else selected + path
    }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        BulkExportTopBar(
            title = if (selected.isEmpty()) {
                stringResource(R.string.bulk_export_title)
            } else {
                stringResource(R.string.album_select_mode_title, selected.size)
            },
            allSelected = allInViewSelected,
            canToggleAll = filteredPhotos.isNotEmpty(),
            onBack = onBack,
            onToggleAll = {
                selected = if (allInViewSelected) {
                    selected - filteredPhotos.map { it.path }.toSet()
                } else {
                    selected + filteredPhotos.map { it.path }.toSet()
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        BulkExportFilterChips(
            filter = filter,
            onFilterChange = { filter = it },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (!loaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.bulk_export_loading),
                        color = UiColors.Home.subtitle,
                    )
                }
            } else if (filteredPhotos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.bulk_export_empty),
                        color = UiColors.Home.subtitle,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(UiRadius.homeCard))
                        .background(UiColors.Home.sectionBg)
                        .padding(UiSize.homeCardPadding),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
                        verticalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
                    ) {
                        items(filteredPhotos, key = { it.path }) { photo ->
                            BulkExportGridItem(
                                photo = photo,
                                selected = selected.contains(photo.path),
                                onClick = { toggleSelected(photo.path) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        AppButton(
            text = if (selected.isEmpty()) {
                stringResource(R.string.bulk_export_action_empty)
            } else {
                stringResource(R.string.bulk_export_action_n, selected.size)
            },
            onClick = {
                if (selected.isNotEmpty()) {
                    ExportRuntimeState.enqueue(selected.toList())
                    onOpenExportProgress()
                }
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BulkExportTopBar(
    title: String,
    allSelected: Boolean,
    canToggleAll: Boolean,
    onBack: () -> Unit,
    onToggleAll: () -> Unit,
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
        if (canToggleAll) {
            Text(
                text = stringResource(if (allSelected) R.string.album_deselect_all else R.string.album_select_all),
                color = UiColors.Home.navItemActive,
                fontSize = UiTextSize.homeNavLabel,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .throttledClickable(onClick = onToggleAll)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else {
            Spacer(modifier = Modifier.width(38.dp))
        }
    }
}

@Composable
private fun BulkExportFilterChips(
    filter: BulkExportFilter,
    onFilterChange: (BulkExportFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BulkExportFilterChip(
            label = stringResource(R.string.bulk_export_filter_all),
            selected = filter == BulkExportFilter.ALL,
            onClick = { onFilterChange(BulkExportFilter.ALL) },
        )
        BulkExportFilterChip(
            label = stringResource(R.string.bulk_export_filter_images),
            selected = filter == BulkExportFilter.IMAGES,
            onClick = { onFilterChange(BulkExportFilter.IMAGES) },
        )
        BulkExportFilterChip(
            label = stringResource(R.string.bulk_export_filter_videos),
            selected = filter == BulkExportFilter.VIDEOS,
            onClick = { onFilterChange(BulkExportFilter.VIDEOS) },
        )
    }
}

@Composable
private fun BulkExportFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) UiColors.Home.navItemActiveBg else UiColors.Home.emptyCardBg
    val fg = if (selected) UiColors.Home.navItemActive else UiColors.Home.emptyTitle
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (selected) UiColors.Home.navItemActiveStroke else UiColors.Home.emptyCardStroke,
                shape = RoundedCornerShape(999.dp),
            )
            .throttledClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = UiTextSize.homeNavLabel,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun BulkExportGridItem(
    photo: VaultPhoto,
    selected: Boolean,
    onClick: () -> Unit,
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
                .border(1.2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
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
