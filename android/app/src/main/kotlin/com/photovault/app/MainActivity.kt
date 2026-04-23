package com.photovault.app

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.photovault.app.ui.AiHomeScreen
import com.photovault.app.ui.AlbumScreen
import com.photovault.app.ui.AlbumListScreen
import com.photovault.app.ui.CameraPlaceholderScreen
import com.photovault.app.ui.CameraHomeScreen
import com.photovault.app.ui.HomeTab
import com.photovault.app.ui.HomeScreen
import com.photovault.app.ui.PhotoViewerPlaceholderScreen
import com.photovault.app.ui.RecentPhotosScreen
import com.photovault.app.ui.SettingsHomeScreen
import com.photovault.app.ui.SplashScreen
import com.photovault.app.ui.VaultSearchScreen
import com.photovault.app.ui.lock.LockScreen
import com.photovault.app.ui.theme.PhotoVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appLockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val requireUnlock by appLockManager.requireUnlock.collectAsState()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route

                    LaunchedEffect(requireUnlock, currentRoute) {
                        if (!requireUnlock) return@LaunchedEffect
                        if (currentRoute != null && currentRoute != ROUTE_LOCK) {
                            navController.navigate(ROUTE_LOCK) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_SPLASH,
                    ) {
                        composable(ROUTE_SPLASH) {
                            SplashScreen(
                                onFinished = {
                                    navController.navigate(ROUTE_LOCK) {
                                        popUpTo(ROUTE_SPLASH) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(ROUTE_LOCK) {
                            LockScreen(
                                onUnlockSuccess = {
                                    appLockManager.onUnlockSucceeded()
                                    navController.navigate(ROUTE_HOME_VAULT) {
                                        popUpTo(ROUTE_LOCK) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(ROUTE_HOME_VAULT) {
                            HomeScreen(
                                onOpenPrivateCamera = {
                                    navController.navigate(ROUTE_CAMERA_PLACEHOLDER) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenTab = { tab ->
                                    navController.navigate(tab.toRoute()) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenSearch = {
                                    navController.navigate(ROUTE_VAULT_SEARCH) { launchSingleTop = true }
                                },
                                onOpenAlbum = { albumName ->
                                    navController.navigate("album/${Uri.encode(albumName)}") { launchSingleTop = true }
                                },
                                onOpenPhotoViewer = { path ->
                                    navController.navigate("photo_viewer/${Uri.encode(path)}") { launchSingleTop = true }
                                },
                                onOpenAlbumList = {
                                    navController.navigate(ROUTE_ALBUM_LIST) { launchSingleTop = true }
                                },
                                onOpenRecentList = {
                                    navController.navigate(ROUTE_RECENT_LIST) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_HOME_CAMERA) {
                            CameraHomeScreen(
                                onOpenTab = { tab ->
                                    navController.navigate(tab.toRoute()) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_HOME_AI) {
                            AiHomeScreen(
                                onOpenTab = { tab ->
                                    navController.navigate(tab.toRoute()) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_HOME_SETTINGS) {
                            SettingsHomeScreen(
                                onOpenTab = { tab ->
                                    navController.navigate(tab.toRoute()) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_CAMERA_PLACEHOLDER) {
                            CameraPlaceholderScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_VAULT_SEARCH) {
                            VaultSearchScreen(
                                onOpenPhoto = { path ->
                                    navController.navigate("photo_viewer/${Uri.encode(path)}") { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_ALBUM_LIST) {
                            AlbumListScreen(
                                onOpenAlbum = { albumName ->
                                    navController.navigate("album/${Uri.encode(albumName)}") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_RECENT_LIST) {
                            RecentPhotosScreen(
                                onOpenPhoto = { path ->
                                    navController.navigate("photo_viewer/${Uri.encode(path)}") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "album/{albumName}",
                            arguments = listOf(navArgument("albumName") { defaultValue = "Default" }),
                        ) { entry ->
                            val album = Uri.decode(entry.arguments?.getString("albumName") ?: "Default")
                            AlbumScreen(
                                albumName = album,
                                onOpenPhoto = { path ->
                                    navController.navigate("photo_viewer/${Uri.encode(path)}") { launchSingleTop = true }
                                },
                            )
                        }
                        composable(
                            route = "photo_viewer/{path}",
                            arguments = listOf(navArgument("path") { defaultValue = "" }),
                        ) { entry ->
                            PhotoViewerPlaceholderScreen(path = Uri.decode(entry.arguments?.getString("path") ?: ""))
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ROUTE_SPLASH = "splash"
        private const val ROUTE_LOCK = "lock"
        private const val ROUTE_HOME_VAULT = "home_vault"
        private const val ROUTE_HOME_CAMERA = "home_camera"
        private const val ROUTE_HOME_AI = "home_ai"
        private const val ROUTE_HOME_SETTINGS = "home_settings"
        private const val ROUTE_CAMERA_PLACEHOLDER = "camera_placeholder"
        private const val ROUTE_VAULT_SEARCH = "vault_search"
        private const val ROUTE_ALBUM_LIST = "album_list"
        private const val ROUTE_RECENT_LIST = "recent_list"
    }
}

private fun HomeTab.toRoute(): String = when (this) {
    HomeTab.VAULT -> "home_vault"
    HomeTab.CAMERA -> "camera_placeholder"
    HomeTab.AI -> "home_ai"
    HomeTab.SETTINGS -> "home_settings"
}
