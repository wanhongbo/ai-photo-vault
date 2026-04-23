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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import com.photovault.app.ui.vault.VaultPhoto
import com.photovault.app.ui.vault.VaultStore

@Composable
fun RecentPhotosScreen(
    onOpenPhoto: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(emptyList<VaultPhoto>()) }
    LaunchedEffect(Unit) { photos = VaultStore.listRecentPhotos(context, limit = 500) }

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
                modifier = Modifier.clickable(onClick = onBack),
            )
            Text(
                text = stringResource(R.string.recent_list_title),
                color = UiColors.Home.title,
                fontSize = UiTextSize.homeTitle,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterTag(text = stringResource(R.string.recent_list_filter_date), selected = true)
            FilterTag(text = stringResource(R.string.recent_list_filter_album), selected = false)
        }
        if (photos.isEmpty()) {
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
                                .clickable { onOpenPhoto(photo.path) },
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

