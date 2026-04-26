package com.photovault.app

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.photovault.app.ui.AlbumScreen
import com.photovault.app.ui.AlbumListScreen
import com.photovault.app.ui.BackupRestoreScreen
import com.photovault.app.ui.BackupProgressScreen
import com.photovault.app.ui.BackupResultScreen
import com.photovault.app.ui.PrivateCameraScreen
import com.photovault.app.ui.ChangePinScreen
import com.photovault.app.ui.MainScreen
import com.photovault.app.ui.LanguageSettingsScreen
import com.photovault.app.ui.PaywallScreen
import com.photovault.app.ui.PhotoViewerScreen
import com.photovault.app.ui.RecentPhotosScreen
import com.photovault.app.ui.RestoreProgressScreen
import com.photovault.app.ui.RestoreResultScreen
import com.photovault.app.ui.SplashScreen
import com.photovault.app.ui.StorageUsagePlaceholderScreen
import com.photovault.app.ui.TrashBinScreen
import com.photovault.app.ui.VideoPlayerScreen
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
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.statusBars())
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
                    val previousRoute = navController.previousBackStackEntry?.destination?.route

                    LaunchedEffect(requireUnlock, currentRoute) {
                        if (!requireUnlock) return@LaunchedEffect
                        val shouldSkipLock = isBackupRestoreRoute(currentRoute) || isBackupRestoreRoute(previousRoute)
                        if (currentRoute != null &&
                            currentRoute != ROUTE_LOCK &&
                            currentRoute != ROUTE_PRIVATE_CAMERA &&
                            !shouldSkipLock
                        ) {
                            navController.navigate(ROUTE_LOCK) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
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
                                    navController.navigate(ROUTE_MAIN) {
                                        popUpTo(ROUTE_LOCK) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onQuickCapture = {
                                    navController.navigate(ROUTE_PRIVATE_CAMERA) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(ROUTE_MAIN) {
                            MainScreen(
                                onOpenPrivateCamera = {
                                    navController.navigate(ROUTE_PRIVATE_CAMERA) {
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
                                    navController.navigate(viewerRouteForPath(path)) { launchSingleTop = true }
                                },
                                onOpenAlbumList = {
                                    navController.navigate(ROUTE_ALBUM_LIST) { launchSingleTop = true }
                                },
                                onOpenRecentList = {
                                    navController.navigate(ROUTE_RECENT_LIST) { launchSingleTop = true }
                                },
                                onOpenBackupRestore = {
                                    navController.navigate(ROUTE_BACKUP_RESTORE) { launchSingleTop = true }
                                },
                                onOpenTrashBin = {
                                    navController.navigate(ROUTE_TRASH_BIN) { launchSingleTop = true }
                                },
                                onOpenPaywall = {
                                    navController.navigate(ROUTE_PAYWALL) { launchSingleTop = true }
                                },
                                onOpenChangePin = {
                                    navController.navigate(ROUTE_CHANGE_PIN) { launchSingleTop = true }
                                },
                                onOpenStorageUsage = {
                                    navController.navigate(ROUTE_STORAGE_USAGE) { launchSingleTop = true }
                                },
                                onOpenLanguageSettings = {
                                    navController.navigate(ROUTE_LANGUAGE_SETTINGS) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_BACKUP_RESTORE) {
                            BackupRestoreScreen(
                                onOpenBackupProgress = { outputUri ->
                                    navController.navigate("$ROUTE_BACKUP_PROGRESS/$outputUri") { launchSingleTop = true }
                                },
                                onOpenRestoreProgress = { inputUri ->
                                    navController.navigate("$ROUTE_RESTORE_PROGRESS/$inputUri") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "$ROUTE_RESTORE_PROGRESS/{inputUri}",
                            arguments = listOf(navArgument("inputUri") { defaultValue = "" }),
                        ) { entry ->
                            RestoreProgressScreen(
                                inputUri = entry.arguments?.getString("inputUri") ?: "",
                                onRestoreSuccess = {
                                    navController.navigate(ROUTE_RESTORE_RESULT) {
                                        popUpTo(ROUTE_BACKUP_RESTORE) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "$ROUTE_BACKUP_PROGRESS/{outputUri}",
                            arguments = listOf(navArgument("outputUri") { defaultValue = "" }),
                        ) { entry ->
                            BackupProgressScreen(
                                outputUri = entry.arguments?.getString("outputUri") ?: "",
                                onBackupSuccess = {
                                    navController.navigate(ROUTE_BACKUP_RESULT) {
                                        popUpTo(ROUTE_BACKUP_RESTORE) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_BACKUP_RESULT) {
                            BackupResultScreen(
                                onDone = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_RESTORE_RESULT) {
                            RestoreResultScreen(
                                onDone = {
                                    appLockManager.onUnlockSucceeded()
                                    navController.navigate(ROUTE_MAIN) {
                                        popUpTo(ROUTE_MAIN) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(ROUTE_TRASH_BIN) {
                            TrashBinScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_PAYWALL) {
                            PaywallScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_CHANGE_PIN) {
                            ChangePinScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_STORAGE_USAGE) {
                            StorageUsagePlaceholderScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_LANGUAGE_SETTINGS) {
                            LanguageSettingsScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_PRIVATE_CAMERA) {
                            PrivateCameraScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_VAULT_SEARCH) {
                            VaultSearchScreen(
                                onOpenPhoto = { path ->
                                    navController.navigate(viewerRouteForPath(path)) { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
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
                                    navController.navigate(viewerRouteForPath(path)) { launchSingleTop = true }
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
                                    navController.navigate(viewerRouteForPath(path)) { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "photo_viewer/{path}",
                            arguments = listOf(navArgument("path") { defaultValue = "" }),
                        ) { entry ->
                            PhotoViewerScreen(
                                path = Uri.decode(entry.arguments?.getString("path") ?: ""),
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "video_player/{path}",
                            arguments = listOf(navArgument("path") { defaultValue = "" }),
                        ) { entry ->
                            VideoPlayerScreen(
                                path = Uri.decode(entry.arguments?.getString("path") ?: ""),
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun viewerRouteForPath(path: String): String {
        val encoded = Uri.encode(path)
        return if (isVideoPath(path)) {
            "video_player/$encoded"
        } else {
            "photo_viewer/$encoded"
        }
    }

    private fun isVideoPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".mp4") ||
            lower.endsWith(".m4v") ||
            lower.endsWith(".mov") ||
            lower.endsWith(".3gp") ||
            lower.endsWith(".webm") ||
            lower.endsWith(".mkv")
    }

    private fun isBackupRestoreRoute(route: String?): Boolean {
        if (route == null) return false
        return route == ROUTE_BACKUP_RESTORE ||
            route == ROUTE_BACKUP_RESULT ||
            route == ROUTE_RESTORE_RESULT ||
            route.startsWith(ROUTE_BACKUP_PROGRESS) ||
            route.startsWith(ROUTE_RESTORE_PROGRESS)
    }

    companion object {
        private const val ROUTE_SPLASH = "splash"
        private const val ROUTE_LOCK = "lock"
        private const val ROUTE_MAIN = "main"
        private const val ROUTE_PRIVATE_CAMERA = "private_camera"
        private const val ROUTE_VAULT_SEARCH = "vault_search"
        private const val ROUTE_ALBUM_LIST = "album_list"
        private const val ROUTE_RECENT_LIST = "recent_list"
        private const val ROUTE_BACKUP_RESTORE = "backup_restore"
        private const val ROUTE_BACKUP_RESULT = "backup_result"
        private const val ROUTE_BACKUP_PROGRESS = "backup_progress"
        private const val ROUTE_RESTORE_PROGRESS = "restore_progress"
        private const val ROUTE_RESTORE_RESULT = "restore_result"
        private const val ROUTE_TRASH_BIN = "trash_bin"
        private const val ROUTE_PAYWALL = "paywall"
        private const val ROUTE_CHANGE_PIN = "change_pin"
        private const val ROUTE_STORAGE_USAGE = "storage_usage"
        private const val ROUTE_LANGUAGE_SETTINGS = "language_settings"
    }
}
