package com.photovault.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppButtonVariant
import com.photovault.app.ui.components.VaultProgressiveImage
import com.photovault.app.ui.feedback.pressFeedback
import com.photovault.app.ui.feedback.rememberFeedbackInteractionSource
import com.photovault.app.ui.feedback.throttledClickable
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import com.photovault.app.ui.vault.DEFAULT_ALBUM_NAME
import com.photovault.app.ui.vault.VaultAlbum
import com.photovault.app.ui.vault.VaultImportResult
import com.photovault.app.ui.vault.VaultPhoto
import com.photovault.app.ui.vault.VaultStore
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenPrivateCamera: () -> Unit = {},
    onOpenTab: (HomeTab) -> Unit = {},
    selectedTab: HomeTab = HomeTab.VAULT,
    showBottomNav: Boolean = true,
    onOpenSearch: () -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onOpenPhotoViewer: (String) -> Unit = {},
    onOpenAlbumList: () -> Unit = {},
    onOpenRecentList: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostActivity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var hasAlbumPermission by remember { mutableStateOf(checkAlbumReadPermission(context)) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var creatingAlbum by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    val cachedSnapshot = remember { VaultStore.peekCachedSnapshot() }
    var albums by remember { mutableStateOf(cachedSnapshot?.albums.orEmpty()) }
    var recentPhotos by remember { mutableStateOf(cachedSnapshot?.recentPhotos.orEmpty()) }
    var totalCount by remember { mutableStateOf(cachedSnapshot?.totalCount ?: 0) }
    var vaultLoaded by remember { mutableStateOf(cachedSnapshot != null) }
    var importing by remember { mutableStateOf(false) }
    var importTip by remember { mutableStateOf<ImportTip?>(null) }
    val tabs = remember { homeTabs() }
    val isVaultEmpty = remember(albums, recentPhotos) {
        recentPhotos.isEmpty() && albums.sumOf { it.photoCount } == 0
    }

    suspend fun refreshVault() {
        val snapshot = VaultStore.loadSnapshot(context, recentLimit = 60)
        if (albums != snapshot.albums) albums = snapshot.albums
        if (recentPhotos != snapshot.recentPhotos) recentPhotos = snapshot.recentPhotos
        if (totalCount != snapshot.totalCount) totalCount = snapshot.totalCount
        vaultLoaded = true
    }

    LaunchedEffect(Unit) { refreshVault() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refreshVault() }
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
                importing = true
                var added = 0
                var duplicate = 0
                var failed = 0
                uris.forEach { uri ->
                    when (VaultStore.importFromPicker(context, uri, DEFAULT_ALBUM_NAME)) {
                        VaultImportResult.ADDED -> added += 1
                        VaultImportResult.DUPLICATE -> duplicate += 1
                        VaultImportResult.FAILED -> failed += 1
                    }
                }
                refreshVault()
                importTip = if (uris.size == 1) {
                    when {
                        added == 1 -> ImportTip(context.getString(R.string.home_import_success_default_album), false)
                        duplicate == 1 -> ImportTip(context.getString(R.string.home_import_duplicate_default_album), false)
                        else -> ImportTip(context.getString(R.string.home_import_failed), true)
                    }
                } else {
                    ImportTip(
                        context.getString(R.string.home_import_multi_result, added, duplicate, failed),
                        failed > 0 && added == 0,
                    )
                }
                importing = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasAlbumPermission = checkAlbumReadPermission(context)
        permanentlyDenied = !hasAlbumPermission && isPermanentlyDenied(hostActivity)
    }

    val triggerImportFromLibrary = {
        if (!hasAlbumPermission) {
            permissionLauncher.launch(requiredAlbumPermissions())
        } else if (!importing) {
            importTip = null
            pickerLauncher.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    .build(),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(UiColors.Home.bgTop, UiColors.Home.bgBottom)))
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        VaultHeader(
            totalCount = totalCount,
            onSearch = onOpenSearch,
            onCreateAlbum = { creatingAlbum = true },
        )
        importTip?.let { tip ->
            Text(
                text = tip.message,
                color = if (tip.isError) UiColors.Lock.error else UiColors.Lock.success,
                fontSize = UiTextSize.homeNavLabel,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (!hasAlbumPermission) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                HomeAlbumPermissionState(
                    onGrant = { permissionLauncher.launch(requiredAlbumPermissions()) },
                    onOpenSettings = { openAppSettings(context) },
                    permanentlyDenied = permanentlyDenied,
                )
            }
        } else if (!vaultLoaded) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "加载中...", color = UiColors.Home.subtitle)
            }
        } else if (isVaultEmpty) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                VaultEmptyState(
                    isLoading = importing,
                    onImport = triggerImportFromLibrary,
                    onTakePrivatePhoto = onOpenPrivateCamera,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(UiSize.homeSectionGap),
            ) {
                item {
                    AlbumsSection(
                        albums = albums,
                        onOpenAlbum = onOpenAlbum,
                        onViewAll = onOpenAlbumList,
                    )
                }
                item {
                    RecentSection(
                        photos = recentPhotos,
                        onOpenPhoto = { onOpenPhotoViewer(it.path) },
                        onViewMore = onOpenRecentList,
                    )
                }
            }
        }

        if (showBottomNav) {
            HomeBottomNav(
                tabs = tabs,
                selectedIndex = selectedTab.ordinal,
                onSelect = { idx -> onOpenTab(tabs[idx].tab) },
            )
        }
    }

    if (creatingAlbum) {
        AlertDialog(
            onDismissRequest = { creatingAlbum = false },
            containerColor = Color.Transparent,
            confirmButton = {},
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(UiColors.Dialog.bg)
                        .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_album_create_title),
                        color = UiColors.Dialog.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    TextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(14.dp)),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = UiColors.Dialog.title,
                            fontSize = UiTextSize.homeEmptyBody,
                        ),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.home_album_create_input_hint),
                                color = UiColors.Home.subtitle,
                                fontSize = UiTextSize.homeEmptyBody,
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = UiColors.Home.emptyCardBg,
                            unfocusedContainerColor = UiColors.Home.emptyCardBg,
                            disabledContainerColor = UiColors.Home.emptyCardBg,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = UiColors.Home.navItemActive,
                        ),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppButton(
                            text = stringResource(R.string.common_cancel),
                            onClick = { creatingAlbum = false },
                            variant = AppButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(
                            text = stringResource(R.string.home_album_create_confirm),
                            onClick = {
                                scope.launch {
                                    val name = VaultStore.createAlbum(context, newAlbumName)
                                    newAlbumName = ""
                                    creatingAlbum = false
                                    refreshVault()
                                    onOpenAlbum(name)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun VaultHeader(
    totalCount: Int,
    onSearch: () -> Unit,
    onCreateAlbum: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_vault_title),
                color = UiColors.Home.title,
                fontSize = UiTextSize.homeTitle,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_vault_security_info, totalCount),
                color = UiColors.Home.subtitle,
                fontSize = UiTextSize.homeSubtitle,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HeaderInfoTag(stringResource(R.string.home_header_stat_photos, totalCount))
                HeaderInfoTag(stringResource(R.string.home_header_stat_videos))
                HeaderInfoTag(stringResource(R.string.home_header_stat_files))
            }
        }
        HeaderActionButton(
            iconRes = R.drawable.ic_home_action_search,
            contentDesc = stringResource(R.string.home_action_search),
            onClick = onSearch,
        )
        Spacer(modifier = Modifier.width(8.dp))
        HeaderActionButton(
            iconRes = R.drawable.ic_home_action_add,
            contentDesc = stringResource(R.string.home_action_add),
            onClick = onCreateAlbum,
        )
    }
}

@Composable
private fun AlbumsSection(
    albums: List<VaultAlbum>,
    onOpenAlbum: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(UiSize.homeCardPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.home_albums_title),
                color = UiColors.Home.title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.home_albums_view_all),
                color = UiColors.Home.navItemActive,
                modifier = Modifier.throttledClickable(onClick = onViewAll),
            )
        }
        LazyRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.name }) { album ->
                AlbumCard(album = album, onClick = { onOpenAlbum(album.name) })
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: VaultAlbum,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Column(
        modifier = Modifier
            .width(UiSize.homeAlbumCardWidth)
            .clip(RoundedCornerShape(UiRadius.homeAlbumCard))
            .background(UiColors.Home.bgBottom)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeAlbumCard))
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(UiSize.homeAlbumCoverHeight)
                .clip(RoundedCornerShape(UiRadius.homeThumb))
                .background(UiColors.Home.emptyIconBg),
            contentAlignment = Alignment.Center,
        ) {
            if (album.coverPath != null) {
                VaultProgressiveImage(
                    path = album.coverPath,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    thumbnailMaxPx = 420,
                    showVideoIndicator = true,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_home_nav_vault),
                    contentDescription = null,
                    tint = UiColors.Home.navItemActive,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(album.name, color = UiColors.Home.emptyTitle, modifier = Modifier.padding(top = 6.dp))
        Text(
            text = stringResource(R.string.home_album_photo_count, album.photoCount),
            color = UiColors.Home.emptyBody,
            fontSize = UiTextSize.homeNavLabel,
        )
    }
}

@Composable
private fun RecentSection(
    photos: List<VaultPhoto>,
    onOpenPhoto: (VaultPhoto) -> Unit,
    onViewMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(UiSize.homeCardPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.home_recent_title),
                color = UiColors.Home.title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.home_recent_view_more),
                color = UiColors.Home.navItemActive,
                modifier = Modifier.throttledClickable(onClick = onViewMore),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .height(240.dp)
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
            verticalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
        ) {
            items(photos.take(30), key = { it.path }) { photo ->
                PhotoThumb(path = photo.path, onClick = { onOpenPhoto(photo) })
            }
        }
    }
}

@Composable
private fun HeaderInfoTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = text, color = UiColors.Home.subtitle, fontSize = UiTextSize.homeNavLabel)
    }
}

@Composable
private fun PhotoThumb(
    path: String,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Box(
        modifier = Modifier
            .size(UiSize.homeThumbSize)
            .clip(RoundedCornerShape(UiRadius.homeThumb))
            .background(UiColors.Home.emptyIconBg)
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        VaultProgressiveImage(
            path = path,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            thumbnailMaxPx = 320,
            showVideoIndicator = true,
        )
    }
}

@Composable
private fun HeaderActionButton(
    iconRes: Int,
    contentDesc: String,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(UiColors.Home.navBarBg)
            .border(1.dp, UiColors.Home.navBarStroke, RoundedCornerShape(12.dp))
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDesc,
            tint = UiColors.Home.navItemActive,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HomeAlbumPermissionState(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    permanentlyDenied: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = UiSize.permissionCardTopPad,
                bottom = UiSize.permissionCardBottomPad,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(UiSize.permissionIconWrap)
                .background(UiColors.Home.emptyIconBg, CircleShape)
                .border(1.dp, UiColors.Home.navItemActiveStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_album_permission),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(UiSize.permissionIcon),
            )
        }
        Text(
            text = stringResource(R.string.home_permission_title),
            color = UiColors.Home.emptyTitle,
            fontSize = UiTextSize.permissionTitle,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = UiSize.permissionTitleTopGap),
        )
        Text(
            text = if (permanentlyDenied) stringResource(R.string.home_permission_denied_desc) else stringResource(R.string.home_permission_desc),
            color = UiColors.Home.emptyBody,
            fontSize = UiTextSize.permissionBody,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = UiSize.permissionBodyTopGap),
        )
        AppButton(
            text = if (permanentlyDenied) stringResource(R.string.home_permission_settings) else stringResource(R.string.home_permission_grant),
            onClick = if (permanentlyDenied) onOpenSettings else onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .height(UiSize.permissionButtonHeight)
                .padding(top = UiSize.permissionPrimaryTopGap),
        )
        if (permanentlyDenied) {
            AppButton(
                text = stringResource(R.string.home_permission_later),
                onClick = {},
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(UiSize.permissionButtonHeight)
                    .padding(top = UiSize.permissionSecondaryTopGap),
            )
        }
    }
}

@Composable
private fun VaultEmptyState(
    isLoading: Boolean,
    onImport: () -> Unit,
    onTakePrivatePhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = UiSize.vaultEmptyCardTopPad,
                bottom = UiSize.vaultEmptyCardBottomPad,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(UiSize.vaultEmptyIconWrap)
                .background(UiColors.Home.emptyIconBg, CircleShape)
                .border(1.dp, UiColors.Home.navItemActiveStroke, CircleShape)
                .clip(RoundedCornerShape(UiRadius.vaultEmptyIconWrap)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.shield_check),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(UiSize.vaultEmptyIcon),
            )
        }
        Text(
            text = stringResource(R.string.home_vault_empty_title),
            color = UiColors.Home.emptyTitle,
            fontSize = UiTextSize.vaultEmptyTitle,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = UiSize.vaultEmptyTitleTopGap),
        )
        Text(
            text = stringResource(R.string.home_vault_empty_desc),
            color = UiColors.Home.emptyBody,
            fontSize = UiTextSize.vaultEmptyBody,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = UiSize.vaultEmptyBodyTopGap),
        )
        VaultEmptyActionButton(
            text = stringResource(R.string.home_vault_empty_action),
            onClick = onImport,
            isPrimary = true,
            loading = isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = UiSize.vaultEmptyPrimaryTopGap),
        )
        VaultEmptyActionButton(
            text = stringResource(R.string.home_vault_take_private_photo),
            onClick = onTakePrivatePhoto,
            isPrimary = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = UiSize.vaultEmptySecondaryTopGap),
        )
    }
}

@Composable
private fun VaultEmptyActionButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (isPrimary) UiColors.Button.primaryContainer else UiColors.Button.secondaryContainer)
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                enabled = !loading,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPrimary && !loading) {
            Icon(
                painter = painterResource(R.drawable.ic_home_nav_import),
                contentDescription = null,
                tint = UiColors.Button.primaryContent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = if (isPrimary) UiColors.Button.primaryContent else UiColors.Button.secondaryContent,
            )
        } else {
            Text(
                text = text,
                color = if (isPrimary) UiColors.Button.primaryContent else UiColors.Button.secondaryContent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun HomeBottomNav(
    tabs: List<HomeNavTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(UiSize.homeNavBarHeight)
                .clip(RoundedCornerShape(UiRadius.homeNavBar))
                .background(UiColors.Home.navBarBg)
                .border(1.dp, UiColors.Home.navBarStroke, RoundedCornerShape(UiRadius.homeNavBar))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { idx, tab ->
                val selected = idx == selectedIndex
                val interaction = rememberFeedbackInteractionSource()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(UiRadius.homeNavItem))
                        .background(if (selected) UiColors.Home.navItemActiveBg else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (selected) UiColors.Home.navItemActiveStroke else Color.Transparent,
                            shape = RoundedCornerShape(UiRadius.homeNavItem),
                        )
                        .pressFeedback(interaction)
                        .throttledClickable(interactionSource = interaction, indication = null, onClick = { onSelect(idx) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = stringResource(tab.labelRes),
                        tint = if (selected) UiColors.Home.navItemActive else UiColors.Home.navItemIdle,
                        modifier = Modifier.size(UiSize.homeNavIcon),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        color = if (selected) UiColors.Home.navItemActive else UiColors.Home.navItemIdle,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

data class HomeNavTab(
    val tab: HomeTab,
    val iconRes: Int,
    val labelRes: Int,
    val emptyTitleRes: Int,
    val emptyDescRes: Int,
    val emptyActionRes: Int,
)

enum class HomeTab { VAULT, CAMERA, AI, SETTINGS }

fun homeTabs(): List<HomeNavTab> = listOf(
    HomeNavTab(HomeTab.VAULT, R.drawable.ic_home_nav_vault, R.string.home_nav_vault, R.string.home_vault_empty_title, R.string.home_vault_empty_desc, R.string.home_vault_empty_action),
    HomeNavTab(HomeTab.CAMERA, R.drawable.ic_home_nav_camera, R.string.home_nav_camera, R.string.home_camera_empty_title, R.string.home_camera_empty_desc, R.string.home_camera_empty_action),
    HomeNavTab(HomeTab.AI, R.drawable.ic_home_nav_ai, R.string.home_nav_ai, R.string.home_ai_empty_title, R.string.home_ai_empty_desc, R.string.home_ai_empty_action),
    HomeNavTab(HomeTab.SETTINGS, R.drawable.ic_home_nav_settings, R.string.home_nav_settings, R.string.home_settings_empty_title, R.string.home_settings_empty_desc, R.string.home_settings_empty_action),
)

private data class ImportTip(val message: String, val isError: Boolean)

private fun requiredAlbumPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun checkAlbumReadPermission(context: Context): Boolean =
    requiredAlbumPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

private fun isPermanentlyDenied(activity: Activity?): Boolean {
    if (activity == null) return false
    return requiredAlbumPermissions().any { permission ->
        ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED &&
            !activity.shouldShowRequestPermissionRationale(permission)
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
