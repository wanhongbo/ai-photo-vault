package com.photovault.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.feedback.throttledClickable
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import com.photovault.app.ui.vault.VaultAlbum
import com.photovault.app.ui.vault.VaultStore

@Composable
fun AlbumListScreen(
    onOpenAlbum: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf(emptyList<VaultAlbum>()) }
    LaunchedEffect(Unit) { albums = VaultStore.listAlbums(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.common_back),
                color = UiColors.Home.navItemActive,
                modifier = Modifier.throttledClickable(onClick = onBack),
            )
            Text(
                text = stringResource(R.string.album_list_title),
                color = UiColors.Home.title,
                fontSize = UiTextSize.homeTitle,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterTag(text = stringResource(R.string.album_list_filter_recent), selected = true)
            FilterTag(text = stringResource(R.string.album_list_filter_name), selected = false)
        }
        if (albums.isEmpty()) {
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
                            val bitmap = remember(album.coverPath) { album.coverPath?.let { android.graphics.BitmapFactory.decodeFile(it) } }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
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

