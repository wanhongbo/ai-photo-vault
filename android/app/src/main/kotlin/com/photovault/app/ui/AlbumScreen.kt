package com.photovault.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.photovault.app.R
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.ui.components.VaultProgressiveImage
import com.photovault.app.ui.feedback.pressFeedback
import com.photovault.app.ui.feedback.rememberFeedbackInteractionSource
import com.photovault.app.ui.feedback.throttledClickable
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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cachedPhotos = remember(albumName) { VaultStore.peekCachedAlbumPhotos(albumName) }
    var photos by remember(albumName) { mutableStateOf(cachedPhotos.orEmpty()) }
    var loaded by remember(albumName) { mutableStateOf(cachedPhotos != null) }

    suspend fun reload() {
        val latest = VaultStore.listPhotosInAlbum(context, albumName)
        if (photos != latest) photos = latest
        loaded = true
    }
    LaunchedEffect(albumName) { reload() }
    DisposableEffect(lifecycleOwner, albumName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { reload() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    VaultStore.importFromPicker(context, uri, albumName)
                }
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
        AppTopBar(title = albumName, onBack = onBack)
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "加载中...", color = UiColors.Home.subtitle)
            }
        } else if (photos.isEmpty()) {
            val emptyAddInteraction = rememberFeedbackInteractionSource()
            val emptyPressed = emptyAddInteraction.collectIsPressedAsState()
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(UiSize.homeEmptyIconWrap)
                        .plusPressFeedback(emptyAddInteraction, CircleShape)
                        .clip(CircleShape)
                        .background(UiColors.Home.emptyIconBg)
                        .border(
                            width = if (emptyPressed.value) 1.8.dp else 1.2.dp,
                            color = if (emptyPressed.value) UiColors.Home.navItemActive else UiColors.Home.navItemActiveStroke.copy(alpha = 0.55f),
                            shape = CircleShape,
                        )
                        .throttledClickable(interactionSource = emptyAddInteraction, indication = null) {
                            pickerLauncher.launch(
                                PickVisualMediaRequest.Builder()
                                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    .build(),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painter = painterResource(R.drawable.ic_home_action_add), contentDescription = null, tint = UiColors.Home.navItemActive)
                }
                Text(
                    text = stringResource(R.string.album_empty_title),
                    color = UiColors.Home.emptyTitle,
                    fontSize = UiTextSize.homeEmptyTitle,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = stringResource(R.string.album_empty_desc),
                    color = UiColors.Home.emptyBody,
                    fontSize = UiTextSize.homeEmptyBody,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
                    item {
                        val addInteraction = rememberFeedbackInteractionSource()
                        val addPressed = addInteraction.collectIsPressedAsState()
                        Box(
                            modifier = Modifier
                                .size(UiSize.homeThumbSize)
                                .plusPressFeedback(addInteraction, RoundedCornerShape(UiRadius.homeThumb))
                                .clip(RoundedCornerShape(UiRadius.homeThumb))
                                .background(UiColors.Home.emptyIconBg)
                                .border(
                                    width = if (addPressed.value) 1.8.dp else 1.2.dp,
                                    color = if (addPressed.value) UiColors.Home.navItemActive else UiColors.Home.navItemActiveStroke.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(UiRadius.homeThumb),
                                )
                                .throttledClickable(interactionSource = addInteraction, indication = null) {
                                    pickerLauncher.launch(
                                        PickVisualMediaRequest.Builder()
                                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            .build(),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_home_action_add),
                                contentDescription = null,
                                tint = UiColors.Home.navItemActive,
                            )
                        }
                    }
                    items(photos, key = { it.path }) { photo ->
                        VaultProgressiveImage(
                            path = photo.path,
                            modifier = Modifier
                                .size(UiSize.homeThumbSize)
                                .clip(RoundedCornerShape(UiRadius.homeThumb))
                                .throttledClickable { onOpenPhoto(photo.path) },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            thumbnailMaxPx = 360,
                            showVideoIndicator = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.plusPressFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape,
): Modifier {
    val pressed = interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed.value) 0.86f else 1f,
        animationSpec = tween(durationMillis = if (pressed.value) 60 else 220),
        label = "plusPressScale",
    )
    val elevation = animateDpAsState(
        targetValue = if (pressed.value) 0.dp else 10.dp,
        animationSpec = tween(durationMillis = if (pressed.value) 60 else 220),
        label = "plusPressElevation",
    )
    return this
        .shadow(elevation = elevation.value, shape = shape, clip = false)
        .then(Modifier.pressFeedback(interactionSource, extraHighlight = true))
        .then(Modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value))
}

