package com.photovault.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex

@Composable
fun MainScreen(
    onOpenPrivateCamera: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPhotoViewer: (String) -> Unit,
    onOpenAlbumList: () -> Unit,
    onOpenRecentList: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenTrashBin: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenChangePin: () -> Unit,
    onOpenStorageUsage: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.VAULT) }
    val onSelectTab: (HomeTab) -> Unit = { tab ->
        if (tab == HomeTab.CAMERA) {
            onOpenPrivateCamera()
        } else {
            selectedTab = tab
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            onOpenPrivateCamera = onOpenPrivateCamera,
            onOpenTab = onSelectTab,
            selectedTab = selectedTab,
            showBottomNav = selectedTab == HomeTab.VAULT,
            onOpenSearch = onOpenSearch,
            onOpenAlbum = onOpenAlbum,
            onOpenPhotoViewer = onOpenPhotoViewer,
            onOpenAlbumList = onOpenAlbumList,
            onOpenRecentList = onOpenRecentList,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (selectedTab == HomeTab.VAULT) 1f else 0f)
                .zIndex(if (selectedTab == HomeTab.VAULT) 1f else 0f),
        )
        CameraHomeScreen(
            onOpenTab = onSelectTab,
            selectedTab = selectedTab,
            showBottomNav = selectedTab == HomeTab.CAMERA,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (selectedTab == HomeTab.CAMERA) 1f else 0f)
                .zIndex(if (selectedTab == HomeTab.CAMERA) 1f else 0f),
        )
        AiHomeScreen(
            onOpenTab = onSelectTab,
            selectedTab = selectedTab,
            showBottomNav = selectedTab == HomeTab.AI,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (selectedTab == HomeTab.AI) 1f else 0f)
                .zIndex(if (selectedTab == HomeTab.AI) 1f else 0f),
        )
        SettingsHomeScreen(
            onOpenTab = onSelectTab,
            onOpenBackupRestore = onOpenBackupRestore,
            onOpenTrashBin = onOpenTrashBin,
            onOpenPaywall = onOpenPaywall,
            onOpenChangePin = onOpenChangePin,
            onOpenStorageUsage = onOpenStorageUsage,
            onOpenLanguageSettings = onOpenLanguageSettings,
            selectedTab = selectedTab,
            showBottomNav = selectedTab == HomeTab.SETTINGS,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (selectedTab == HomeTab.SETTINGS) 1f else 0f)
                .zIndex(if (selectedTab == HomeTab.SETTINGS) 1f else 0f),
        )
    }
}

