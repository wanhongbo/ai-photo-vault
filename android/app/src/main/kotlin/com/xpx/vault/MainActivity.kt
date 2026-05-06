package com.xpx.vault

import android.os.Bundle
import android.net.Uri
import android.content.Context
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
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
import com.xpx.vault.ui.AlbumScreen
import com.xpx.vault.ui.AlbumListScreen
import com.xpx.vault.ui.BackupRestoreScreen
import com.xpx.vault.ui.BackupProgressScreen
import com.xpx.vault.ui.BackupResultScreen
import com.xpx.vault.ui.BulkExportScreen
import com.xpx.vault.ui.ExportProgressScreen
import com.xpx.vault.ui.ExportResultScreen
import com.xpx.vault.ui.PrivateCameraScreen
import com.xpx.vault.ui.ChangePinScreen
import com.xpx.vault.ui.MainScreen
import com.xpx.vault.ui.AiFeatureKey
import com.xpx.vault.ui.ai.AiCleanupScreen
import com.xpx.vault.ui.ai.AiClassifyScreen
import com.xpx.vault.ui.ai.AiFeaturePlaceholderScreen
import com.xpx.vault.ui.ai.AiSensitiveReviewScreen
import com.xpx.vault.ui.ai.PrivacyRedactScreen
import com.xpx.vault.ui.LanguageSettingsScreen
import com.xpx.vault.ui.PaywallScreen
import com.xpx.vault.ui.PhotoViewerScreen
import com.xpx.vault.ui.RecentPhotosScreen
import com.xpx.vault.ui.RestoreProgressScreen
import com.xpx.vault.ui.RestoreResultScreen
import com.xpx.vault.ui.SplashScreen
import com.xpx.vault.ui.StorageUsagePlaceholderScreen
import com.xpx.vault.ui.TrashBinScreen
import com.xpx.vault.ui.VideoPlayerScreen
import com.xpx.vault.ui.VaultSearchScreen
import com.xpx.vault.ui.lock.LockScreen
import com.xpx.vault.ui.theme.XpxVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var aiLocalScanUseCase: com.xpx.vault.ai.AiLocalScanUseCase

    override fun attachBaseContext(newBase: Context) {
        // 将持久化的应用内语言应用到 Activity Context，覆盖所有 API 级别
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.statusBars())
        setContent {
            XpxVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val requireUnlock by appLockManager.requireUnlock.collectAsState()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route
                    val previousRoute = navController.previousBackStackEntry?.destination?.route

                    // 解锁后自动启动一次增量 AI 扫描（增量扫描、mutex 去重，安全反复触发）。
                    LaunchedEffect(requireUnlock) {
                        if (!requireUnlock) aiLocalScanUseCase.requestScan()
                    }

                    // 用 rememberUpdatedState 保障 LaunchedEffect 内能读到最新值，避免闭包过期。
                    val requireUnlockState = rememberUpdatedState(requireUnlock)
                    val currentRouteState = rememberUpdatedState(currentRoute)
                    val previousRouteState = rememberUpdatedState(previousRoute)

                    LaunchedEffect(requireUnlock, currentRoute, previousRoute) {
                        if (!requireUnlock) return@LaunchedEffect
                        // Race 保护：解锁成功瞬间 requireUnlock=false 和 currentRoute=main 可能不同步推送，
                        // 等下一帧开始时再重读，此刻 Compose 已应用本帧所有 snapshot 变化。
                        withFrameNanos { }
                        if (!requireUnlockState.value) return@LaunchedEffect
                        val latestRoute = currentRouteState.value
                        val shouldSkipLock =
                            isBackupRestoreRoute(latestRoute) || isBackupRestoreRoute(previousRouteState.value)
                        if (latestRoute != null &&
                            latestRoute != ROUTE_LOCK &&
                            latestRoute != ROUTE_PRIVATE_CAMERA &&
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
                                onOpenBulkExport = {
                                    navController.navigate(ROUTE_BULK_EXPORT) { launchSingleTop = true }
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
                                onOpenAiFeature = { key ->
                                    val route = when (key) {
                                        AiFeatureKey.CLASSIFY, AiFeatureKey.SEARCH -> ROUTE_AI_CLASSIFY
                                        AiFeatureKey.PRIVACY -> ROUTE_RECENT_LIST
                                        AiFeatureKey.ENCRYPT -> ROUTE_AI_SENSITIVE
                                        AiFeatureKey.COMPRESS, AiFeatureKey.DEDUP -> ROUTE_AI_CLEANUP
                                    }
                                    navController.navigate(route) { launchSingleTop = true }
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
                                onOpenItem = { path ->
                                    navController.navigate(trashViewerRouteForPath(path)) { launchSingleTop = true }
                                },
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
                        composable(ROUTE_AI_CLEANUP) {
                            AiCleanupScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPhoto = { path ->
                                    navController.navigate(viewerRouteForPath(path)) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(ROUTE_AI_SENSITIVE) {
                            AiSensitiveReviewScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPhoto = { path ->
                                    navController.navigate(viewerRouteForPath(path)) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(ROUTE_AI_CLASSIFY) {
                            AiClassifyScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPhoto = { path ->
                                    navController.navigate(viewerRouteForPath(path)) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(ROUTE_AI_PRIVACY) {
                            AiFeaturePlaceholderScreen(
                                title = "\u9690\u79c1\u8131\u654f",
                                description = "Canvas \u5b9e\u65f6\u9a6c\u8d5b\u514b / \u9ad8\u65af\u6a21\u7cca / \u9ed1\u6761\u9884\u89c8\u4e0e\u5bfc\u51fa\uff0c\u4e0d\u6539\u539f\u56fe\u3002",
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_PRIVATE_CAMERA) {
                            PrivateCameraScreen(
                                onBack = { navController.popBackStack() },
                                onViewMedia = { path ->
                                    navController.navigate(viewerRouteForPath(path)) { launchSingleTop = true }
                                },
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
                                onOpenExportProgress = {
                                    navController.navigate(ROUTE_EXPORT_PROGRESS) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_BULK_EXPORT) {
                            BulkExportScreen(
                                onOpenExportProgress = {
                                    navController.navigate(ROUTE_EXPORT_PROGRESS) { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_EXPORT_PROGRESS) {
                            ExportProgressScreen(
                                onExportSuccess = {
                                    navController.navigate(ROUTE_EXPORT_RESULT) {
                                        popUpTo(ROUTE_EXPORT_PROGRESS) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_EXPORT_RESULT) {
                            ExportResultScreen(
                                onDone = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "photo_viewer/{path}",
                            arguments = listOf(navArgument("path") { defaultValue = "" }),
                        ) { entry ->
                            PhotoViewerScreen(
                                path = Uri.decode(entry.arguments?.getString("path") ?: ""),
                                onBack = { navController.popBackStack() },
                                onOpenRedact = { path ->
                                    navController.navigate("$ROUTE_AI_PRIVACY_REDACT/${Uri.encode(path)}") {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(
                            route = "$ROUTE_AI_PRIVACY_REDACT/{path}",
                            arguments = listOf(navArgument("path") { defaultValue = "" }),
                        ) { entry ->
                            PrivacyRedactScreen(
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
                        composable(
                            route = "$ROUTE_TRASH_PHOTO_VIEWER/{path}",
                            arguments = listOf(navArgument("path") { defaultValue = "" }),
                        ) { entry ->
                            PhotoViewerScreen(
                                path = Uri.decode(entry.arguments?.getString("path") ?: ""),
                                onBack = { navController.popBackStack() },
                                isTrash = true,
                                onOpenAlbum = { albumName ->
                                    navController.navigate("album/${Uri.encode(albumName)}") {
                                        popUpTo(ROUTE_TRASH_BIN) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(
                            route = "$ROUTE_TRASH_VIDEO_PLAYER/{path}",
                            arguments = listOf(navArgument("path") { defaultValue = "" }),
                        ) { entry ->
                            VideoPlayerScreen(
                                path = Uri.decode(entry.arguments?.getString("path") ?: ""),
                                onBack = { navController.popBackStack() },
                                isTrash = true,
                                onOpenAlbum = { albumName ->
                                    navController.navigate("album/${Uri.encode(albumName)}") {
                                        popUpTo(ROUTE_TRASH_BIN) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
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

    private fun trashViewerRouteForPath(path: String): String {
        val encoded = Uri.encode(path)
        return if (isVideoPath(path)) {
            "$ROUTE_TRASH_VIDEO_PLAYER/$encoded"
        } else {
            "$ROUTE_TRASH_PHOTO_VIEWER/$encoded"
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
        private const val ROUTE_TRASH_PHOTO_VIEWER = "trash_photo_viewer"
        private const val ROUTE_TRASH_VIDEO_PLAYER = "trash_video_player"
        private const val ROUTE_PAYWALL = "paywall"
        private const val ROUTE_CHANGE_PIN = "change_pin"
        private const val ROUTE_STORAGE_USAGE = "storage_usage"
        private const val ROUTE_LANGUAGE_SETTINGS = "language_settings"
        private const val ROUTE_BULK_EXPORT = "bulk_export"
        private const val ROUTE_EXPORT_PROGRESS = "export_progress"
        private const val ROUTE_EXPORT_RESULT = "export_result"
        private const val ROUTE_AI_CLEANUP = "ai_cleanup"
        private const val ROUTE_AI_SENSITIVE = "ai_sensitive"
        private const val ROUTE_AI_CLASSIFY = "ai_classify"
        private const val ROUTE_AI_PRIVACY = "ai_privacy"
        private const val ROUTE_AI_PRIVACY_REDACT = "ai_privacy_redact"
    }
}
