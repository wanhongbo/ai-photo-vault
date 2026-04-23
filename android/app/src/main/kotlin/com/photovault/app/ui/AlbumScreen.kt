package com.photovault.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import com.photovault.app.ui.vault.VaultPhoto
import com.photovault.app.ui.vault.VaultStore
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(
    albumName: String,
    onOpenPhoto: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf(emptyList<VaultPhoto>()) }

    suspend fun reload() {
        photos = VaultStore.listPhotosInAlbum(context, albumName)
    }
    LaunchedEffect(albumName) { reload() }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                VaultStore.importFromPicker(context, uri, albumName)
                reload()
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
        Text(text = albumName, color = UiColors.Home.title, fontSize = UiTextSize.homeTitle, fontWeight = FontWeight.Bold)
        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(UiColors.Home.emptyIconBg)
                        .clickable {
                            pickerLauncher.launch(
                                PickVisualMediaRequest.Builder()
                                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    .build(),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painter = painterResource(R.drawable.ic_home_action_add), contentDescription = null, tint = UiColors.Home.navItemActive)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                    items(photos, key = { it.path }) { photo ->
                        Box(
                            modifier = Modifier
                                .size(UiSize.homeThumbSize)
                                .clip(RoundedCornerShape(UiRadius.homeThumb))
                                .clickable { onOpenPhoto(photo.path) },
                        ) {
                            val bmp = remember(photo.path) { android.graphics.BitmapFactory.decodeFile(photo.path) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
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

