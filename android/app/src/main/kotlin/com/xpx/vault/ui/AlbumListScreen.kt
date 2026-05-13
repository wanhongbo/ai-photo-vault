package com.xpx.vault.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppInputDialog
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.R
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.VaultAlbum
import com.xpx.vault.ui.vault.VaultStore
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
    var creatingAlbum by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

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

    AppInputDialog(
        show = creatingAlbum,
        title = stringResource(R.string.home_album_create_title),
        value = newAlbumName,
        onValueChange = { newAlbumName = it },
        placeholder = stringResource(R.string.home_album_create_input_hint),
        confirmText = stringResource(R.string.home_album_create_confirm),
        onConfirm = {
            scope.launch {
                VaultStore.createAlbum(context, newAlbumName)
                newAlbumName = ""
                creatingAlbum = false
                refreshAlbums()
            }
        },
        dismissText = stringResource(R.string.common_cancel),
        onDismiss = {
            newAlbumName = ""
            creatingAlbum = false
        },
    )

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
        }
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    text = stringResource(R.string.common_loading),
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                .height(120.dp)
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
                item(key = "__create_album__") {
                    CreateAlbumGridItem(onClick = { creatingAlbum = true })
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

@Composable
private fun CreateAlbumGridItem(
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(
                width = 1.dp,
                color = UiColors.Home.navItemActive,
                shape = RoundedCornerShape(UiRadius.homeCard),
            )
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(UiSize.homeCardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(UiRadius.homeThumb))
                .background(UiColors.Home.emptyIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_action_add),
                contentDescription = null,
                tint = UiColors.Home.navItemActive,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = stringResource(R.string.home_album_create_title),
            color = UiColors.Home.navItemActive,
            modifier = Modifier.padding(top = 8.dp),
            fontSize = UiTextSize.homeNavLabel,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

