package com.xpx.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xpx.vault.R
import com.xpx.vault.ai.core.ClassifyCategory
import com.xpx.vault.ui.ai.AiClassifyVirtualAlbum
import com.xpx.vault.ui.ai.loadAiClassifyVirtualAlbums
import com.xpx.vault.ui.components.AppInputDialog
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.AppFontFamily
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.vault.DEFAULT_ALBUM_NAME
import com.xpx.vault.ui.vault.VaultAlbum
import com.xpx.vault.ui.vault.VaultStore
import kotlinx.coroutines.launch

@Composable
fun AlbumListScreen(
    onOpenAlbum: (String) -> Unit,
    onOpenAiClassifyAlbum: (ClassifyCategory) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cachedAlbums = remember { VaultStore.peekCachedSnapshot()?.albums.orEmpty() }
    var albums by remember { mutableStateOf(cachedAlbums) }
    var aiAlbums by remember { mutableStateOf<List<AiClassifyVirtualAlbum>>(emptyList()) }
    var loaded by remember { mutableStateOf(cachedAlbums.isNotEmpty()) }
    var creatingAlbum by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

    suspend fun refreshAlbums() {
        val latest = VaultStore.listAlbums(context)
        val latestAiAlbums = loadAiClassifyVirtualAlbums(context)
        if (albums != latest) albums = latest
        if (aiAlbums != latestAiAlbums) aiAlbums = latestAiAlbums
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
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
    ) {
        AlbumListTopBar(title = stringResource(R.string.album_list_title), onBack = onBack)
        FilterTag(
            text = stringResource(R.string.album_list_filter_recent),
            selected = true,
            modifier = Modifier.padding(top = 15.dp),
        )
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    text = stringResource(R.string.common_loading),
                    color = UiColors.Home.subtitle,
                )
            }
        } else if (albums.isEmpty() && aiAlbums.isEmpty()) {
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
                    .padding(top = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(albumListItems(albums, aiAlbums), key = { it.key }) { item ->
                    when (item) {
                        is AlbumListItem.Album -> AlbumListCard(
                            album = item.album,
                            onClick = { onOpenAlbum(item.album.name) },
                        )
                        is AlbumListItem.AiAlbum -> AiAlbumListCard(
                            album = item.album,
                            onClick = { onOpenAiClassifyAlbum(item.album.category) },
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
private fun AlbumListTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val backDesc = stringResource(R.string.common_back)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(UiColors.Home.sectionBg)
                .throttledClickable(onClick = onBack)
                .semantics {
                    role = Role.Button
                    contentDescription = backDesc
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_topbar_back),
                contentDescription = null,
                tint = UiColors.Home.title,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            color = UiColors.Home.title,
            fontFamily = AppFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .semantics { heading() },
        )
    }
}

@Composable
private fun FilterTag(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(UiColors.Home.sectionBg)
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                color = if (selected) UiColors.Home.navItemActiveStroke else UiColors.Home.emptyCardStroke,
                shape = RoundedCornerShape(9.dp),
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) UiColors.Home.navItemActive else UiColors.Home.subtitle,
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private sealed interface AlbumListItem {
    val key: String

    data class Album(val album: VaultAlbum) : AlbumListItem {
        override val key: String = "vault:${album.name}"
    }

    data class AiAlbum(val album: AiClassifyVirtualAlbum) : AlbumListItem {
        override val key: String = "ai:${album.category.name}"
    }
}

private fun albumListItems(
    albums: List<VaultAlbum>,
    aiAlbums: List<AiClassifyVirtualAlbum>,
): List<AlbumListItem> = buildList {
    albums.forEach { add(AlbumListItem.Album(it)) }
    aiAlbums.forEach { add(AlbumListItem.AiAlbum(it)) }
}

@Composable
private fun AlbumListCard(
    album: VaultAlbum,
    onClick: () -> Unit,
) {
    val displayName = if (album.name == DEFAULT_ALBUM_NAME) {
        stringResource(R.string.album_default_name)
    } else {
        album.name
    }
    AlbumListCardFrame(onClick = onClick) { coverModifier ->
        Box(
            modifier = coverModifier.background(Color(0xFF142741)),
            contentAlignment = Alignment.Center,
        ) {
            if (album.coverPath != null) {
                VaultProgressiveImage(
                    path = album.coverPath,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    thumbnailMaxPx = 480,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_album_empty_placeholder),
                    contentDescription = null,
                    tint = Color(0xFF9FB2D1),
                    modifier = Modifier.size(31.dp),
                )
            }
        }
        Text(
            text = displayName,
            color = UiColors.Home.title,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp),
        )
        Text(
            text = stringResource(R.string.home_album_photo_count, album.photoCount),
            color = Color(0xFF9FB2D1),
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AiAlbumListCard(
    album: AiClassifyVirtualAlbum,
    onClick: () -> Unit,
) {
    AlbumListCardFrame(onClick = onClick) { coverModifier ->
        Box(
            modifier = coverModifier.background(Color(0xFF102A46)),
            contentAlignment = Alignment.Center,
        ) {
            if (album.coverPath != null) {
                VaultProgressiveImage(
                    path = album.coverPath,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    thumbnailMaxPx = 480,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_sparkles),
                    contentDescription = null,
                    tint = Color(0xFF8EC5FF),
                    modifier = Modifier.size(31.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC0A1828)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_sparkles),
                    contentDescription = null,
                    tint = Color(0xFF8EC5FF),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Text(
            text = stringResource(album.titleRes),
            color = UiColors.Home.title,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp),
        )
        Text(
            text = stringResource(R.string.home_album_photo_count, album.photoCount),
            color = Color(0xFF9FB2D1),
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun CreateAlbumGridItem(
    onClick: () -> Unit,
) {
    AlbumListCardFrame(onClick = onClick) { coverModifier ->
        Box(
            modifier = coverModifier.background(Color(0xFF142741)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_action_add),
                contentDescription = stringResource(R.string.home_album_create_title),
                tint = UiColors.Home.subtitle,
                modifier = Modifier.size(25.dp),
            )
        }
        Text(
            text = stringResource(R.string.home_album_create_title),
            color = UiColors.Home.title,
            fontFamily = AppFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp),
        )
    }
}

@Composable
private fun AlbumListCardFrame(
    onClick: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(226.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.navBarStroke, RoundedCornerShape(18.dp))
            .throttledClickable(onClick = onClick)
            .padding(16.dp),
    ) {
        content(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    }
}
