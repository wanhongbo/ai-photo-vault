package com.photovault.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.photovault.app.R
import com.photovault.app.ui.feedback.throttledClickable
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import com.photovault.app.ui.vault.VaultPhoto
import com.photovault.app.ui.vault.VaultStore
import kotlinx.coroutines.launch

@Composable
fun RecentPhotosScreen(
    onOpenPhoto: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cachedPhotos = remember { VaultStore.peekCachedSnapshot()?.recentPhotos.orEmpty() }
    var photos by remember { mutableStateOf(cachedPhotos) }
    var loaded by remember { mutableStateOf(cachedPhotos.isNotEmpty()) }

    suspend fun refreshPhotos() {
        val latest = VaultStore.listRecentPhotos(context, limit = 500)
        if (photos != latest) photos = latest
        loaded = true
    }

    LaunchedEffect(Unit) { refreshPhotos() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refreshPhotos() }
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
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = "<",
                color = UiColors.Home.navItemActive,
                modifier = Modifier.throttledClickable(onClick = onBack),
            )
            Text(
                text = stringResource(R.string.recent_list_title),
                color = UiColors.Home.title,
                fontSize = UiTextSize.homeTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(modifier = Modifier.size(20.dp))
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterTag(text = stringResource(R.string.recent_list_filter_date), selected = true)
            FilterTag(text = stringResource(R.string.recent_list_filter_album), selected = false)
        }
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(text = "加载中...", color = UiColors.Home.subtitle)
            }
        } else if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(text = stringResource(R.string.recent_list_empty), color = UiColors.Home.subtitle)
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
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
                    items(photos, key = { it.path }) { photo ->
                        Box(
                            modifier = Modifier
                                .size(UiSize.homeThumbSize)
                                .clip(RoundedCornerShape(UiRadius.homeThumb))
                                .throttledClickable { onOpenPhoto(photo.path) },
                        ) {
                            val bitmap = remember(photo.path) { android.graphics.BitmapFactory.decodeFile(photo.path) }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTag(text: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) UiColors.Home.navItemActiveBg else UiColors.Home.sectionBg)
            .border(
                width = 1.dp,
                color = if (selected) UiColors.Home.navItemActiveStroke else UiColors.Home.emptyCardStroke,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (selected) UiColors.Home.navItemActive else UiColors.Home.subtitle,
            fontSize = UiTextSize.homeNavLabel,
        )
    }
}

