package com.xpx.vault.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * 隐藏 tab 屏幕的事件吞噬：
 * MainScreen 用 Box + alpha(0f) 叠加全部 tab 屏幕，alpha 不阻断指针事件，
 * 非当前 tab 的空白点击会穿透到下层屏幕（如 Settings 顶部订阅卡片）误触发跳转。
 * enabled=true 时在 Initial Pass 消费所有指针 change，彻底拦截手势进入子树。
 */
private fun Modifier.swallowPointerInput(enabled: Boolean): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }

@Composable
fun MainScreen(
    onOpenChangePin: () -> Unit,
    onOpenPrivateCamera: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPhotoViewer: (String) -> Unit,
    onOpenAlbumList: () -> Unit,
    onOpenRecentList: () -> Unit,
    onOpenSettingsHub: (SettingsHubDestination) -> Unit,
    onOpenAiFeature: (AiFeatureKey) -> Unit,
    onPaywallRequired: () -> Unit = {},
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
            onOpenPinSettings = onOpenChangePin,
            onOpenPrivateCamera = onOpenPrivateCamera,
            onOpenTab = onSelectTab,
            selectedTab = selectedTab,
            showBottomNav = selectedTab == HomeTab.VAULT,
            onOpenSearch = onOpenSearch,
            onOpenAlbum = onOpenAlbum,
            onOpenPhotoViewer = onOpenPhotoViewer,
            onOpenAlbumList = onOpenAlbumList,
            onOpenRecentList = onOpenRecentList,
            onPaywallRequired = onPaywallRequired,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (selectedTab == HomeTab.VAULT) 1f else 0f)
                .zIndex(if (selectedTab == HomeTab.VAULT) 1f else 0f)
                .swallowPointerInput(enabled = selectedTab != HomeTab.VAULT),
        )
        CameraHomeScreen(
            onOpenTab = onSelectTab,
            selectedTab = selectedTab,
            showBottomNav = selectedTab == HomeTab.CAMERA,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (selectedTab == HomeTab.CAMERA) 1f else 0f)
                .zIndex(if (selectedTab == HomeTab.CAMERA) 1f else 0f)
                .swallowPointerInput(enabled = selectedTab != HomeTab.CAMERA),
        )
        AiHomeScreen(
            onOpenTab = onSelectTab,
            selectedTab = selectedTab,
            showBottomNav = selectedTab == HomeTab.AI,
            onOpenFeature = onOpenAiFeature,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (selectedTab == HomeTab.AI) 1f else 0f)
                .zIndex(if (selectedTab == HomeTab.AI) 1f else 0f)
                .swallowPointerInput(enabled = selectedTab != HomeTab.AI),
        )
        SettingsHomeScreen(
            onOpenTab = onSelectTab,
            onOpenSettingsHub = onOpenSettingsHub,
            selectedTab = selectedTab,
            showBottomNav = selectedTab == HomeTab.SETTINGS,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (selectedTab == HomeTab.SETTINGS) 1f else 0f)
                .zIndex(if (selectedTab == HomeTab.SETTINGS) 1f else 0f)
                .swallowPointerInput(enabled = selectedTab != HomeTab.SETTINGS),
        )
    }
}
