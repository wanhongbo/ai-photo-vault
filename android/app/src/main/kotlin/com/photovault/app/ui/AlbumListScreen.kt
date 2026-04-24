package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.R
import com.photovault.app.ui.components.VaultProgressiveImage
import com.photovault.app.ui.feedback.throttledClickable
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import com.photovault.app.ui.vault.VaultAlbum
import com.photovault.app.ui.vault.VaultStore
import kotlinx.coroutines.launch

@Composable
fun AlbumListScreen(
    onOpenAlbum: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cachedAlbums = remember { VaultStore.peekCachedSnapshot()?.albums.orEmpty() }
    var albums by remember { mutableStateOf(cachedAlbums) }
    var loaded by remember { mutableStateOf(cachedAlbums.isNotEmpty()) }

    suspend fun refreshAlbums() {
        val latest = VaultStore.listAlbums(context)
        if (albums != latest) albums = latest
        loaded = true
    }

    LaunchedEffect(Unit) { refreshAlbums() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refreshAlbums() }
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
        AppTopBar(title = stringResource(R.string.album_list_title), onBack = onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterTag(text = stringResource(R.string.album_list_filter_recent), selected = true)
            FilterTag(text = stringResource(R.string.album_list_filter_name), selected = false)
        }
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    text = "加载中...",
                    color = UiColors.Home.subtitle,
                )
            }
        } else if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    text = stringResource(R.string.album_list_empty),
                    color = UiColors.Home.subtitle,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(albums, key = { it.name }) { album ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(UiRadius.homeCard))
                            .background(UiColors.Home.sectionBg)
                            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
                            .throttledClickable { onOpenAlbum(album.name) }
                            .padding(UiSize.homeCardPadding),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(UiRadius.homeThumb))
                                .background(UiColors.Home.emptyIconBg),
                        ) {
                            album.coverPath?.let { coverPath ->
                                VaultProgressiveImage(
                                    path = coverPath,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    thumbnailMaxPx = 480,
                                )
                            }
                        }
                        Text(text = album.name, color = UiColors.Home.title, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            text = stringResource(R.string.home_album_photo_count, album.photoCount),
                            color = UiColors.Home.subtitle,
                            fontSize = UiTextSize.homeNavLabel,
                        )
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

