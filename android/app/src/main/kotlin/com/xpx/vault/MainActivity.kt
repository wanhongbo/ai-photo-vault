package com.xpx.vault

import android.os.Bundle
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
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
import com.xpx.vault.ui.SettingsHubDestination
import com.xpx.vault.ui.settings.LegalWebViewScreen
import com.xpx.vault.ui.settings.IntruderAlertScreen
import com.xpx.vault.ui.settings.SettingsAboutSupportScreen
import com.xpx.vault.ui.settings.SettingsBackupSyncScreen
import com.xpx.vault.ui.settings.SettingsDataStorageScreen
import com.xpx.vault.ui.settings.SettingsGeneralScreen
import com.xpx.vault.ui.settings.SettingsImportOriginalsScreen
import com.xpx.vault.ui.settings.SettingsSecurityPrivacyScreen
import com.xpx.vault.ui.settings.SettingsSubscriptionPlaceholderScreen
import com.xpx.vault.ui.PaywallScreen
import com.xpx.vault.ui.PhotoViewerScreen
import com.xpx.vault.ui.RecentPhotosScreen
import com.xpx.vault.ui.RestoreProgressScreen
import com.xpx.vault.ui.RestoreResultScreen
import com.xpx.vault.ui.SplashScreen
import com.xpx.vault.ui.SplashViewModel
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

    @Inject
    lateinit var onboardingPaywallManager: com.xpx.vault.billing.OnboardingPaywallManager

    @Inject
    lateinit var paywallGatekeeper: com.xpx.vault.billing.PaywallGatekeeper

    private var taskSnapshotLockCover: View? = null
    private var currentRouteForSnapshot: String? = null

    override fun attachBaseContext(newBase: Context) {
        // 将持久化的应用内语言应用到 Activity Context，覆盖所有 API 级别
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onUserLeaveHint() {
        if (shouldShowTaskSnapshotLockCover() && appLockManager.onUserLeavingApp()) {
            showTaskSnapshotLockCover()
        }
        super.onUserLeaveHint()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus &&
            !appLockManager.shouldIgnoreWindowFocusSnapshotCover() &&
            shouldShowTaskSnapshotLockCover()
        ) {
            showTaskSnapshotLockCover()
        }
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !appLockManager.isUnlockRequired()) {
            hideTaskSnapshotLockCover()
        }
    }

    override fun onPause() {
        if (shouldShowTaskSnapshotLockCover() && appLockManager.onUserLeavingApp()) {
            showTaskSnapshotLockCover()
        }
        super.onPause()
    }

    override fun onStop() {
        if (shouldShowTaskSnapshotLockCover()) {
            showTaskSnapshotLockCover()
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        appLockManager.clearExternalSystemUi("activity resumed")
    }

    override fun onDestroy() {
        hideTaskSnapshotLockCover()
        super.onDestroy()
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
                    SideEffect {
                        currentRouteForSnapshot = currentRoute
                    }

                    // 解锁后自动启动一次增量 AI 扫描（增量扫描、mutex 去重，安全反复触发）。
                    LaunchedEffect(requireUnlock) {
                        if (!requireUnlock) {
                            aiLocalScanUseCase.requestScan()
                            // 首启软墙：让用户先进入保险箱主页，延迟 5s 再弹出可跳过的 Paywall。
                            // 注：delay 期间若 requireUnlock 重新变为 true（重新加锁），
                            // LaunchedEffect 会取消当前协程，因此不会错误地在锁屏上弹出。
                            if (onboardingPaywallManager.shouldShow()) {
                                kotlinx.coroutines.delay(5_000L)
                                // 二次确认：协程恢复时仍未被加锁，且仍未展示过
                                if (!requireUnlock && onboardingPaywallManager.shouldShow()) {
                                    onboardingPaywallManager.markSeen()
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=true&source=onboarding",
                                    ) { launchSingleTop = true }
                                }
                            }
                        }
                    }

                    // 用 rememberUpdatedState 保障 LaunchedEffect 内能读到最新值，避免闭包过期。
                    val requireUnlockState = rememberUpdatedState(requireUnlock)
                    val currentRouteState = rememberUpdatedState(currentRoute)

                    LaunchedEffect(requireUnlock, currentRoute) {
                        if (!requireUnlock) return@LaunchedEffect
                        if (!requireUnlockState.value) return@LaunchedEffect
                        val latestRoute = currentRouteState.value
                        if (latestRoute != null &&
                            latestRoute != ROUTE_SPLASH &&
                            latestRoute != ROUTE_LOCK &&
                            latestRoute != ROUTE_PRIVATE_CAMERA
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
                        if (latestRoute == ROUTE_LOCK || latestRoute == ROUTE_PRIVATE_CAMERA) {
                            hideTaskSnapshotLockCover()
                        }
                    }

                    LaunchedEffect(requireUnlock) {
                        if (!requireUnlock) {
                            hideTaskSnapshotLockCover()
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_SPLASH,
                        // 全局路由过渡动画：横向 slide + fade，符合 Android 系统级导航候观感。
                        // 用 tween(260/220) 而非默认 700ms，页面切换更干脆，不阻碍操作。
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(durationMillis = 260),
                            ) + fadeIn(animationSpec = tween(durationMillis = 220))
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(durationMillis = 260),
                            ) + fadeOut(animationSpec = tween(durationMillis = 220))
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(durationMillis = 260),
                            ) + fadeIn(animationSpec = tween(durationMillis = 220))
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(durationMillis = 260),
                            ) + fadeOut(animationSpec = tween(durationMillis = 220))
                        },
                    ) {
                        composable(ROUTE_SPLASH) {
                            val splashVm: SplashViewModel = hiltViewModel()
                            val splashState by splashVm.state.collectAsState()
                            SplashScreen()
                            LaunchedEffect(splashState.ready, splashState.skipLockToMain) {
                                if (!splashState.ready) return@LaunchedEffect
                                if (splashState.skipLockToMain) {
                                    appLockManager.setPinConfigured(false)
                                    appLockManager.onUnlockSucceeded()
                                    navController.navigate(ROUTE_MAIN) {
                                        popUpTo(ROUTE_SPLASH) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.navigate(ROUTE_LOCK) {
                                        popUpTo(ROUTE_SPLASH) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
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
                                onOpenChangePin = {
                                    navController.navigate(ROUTE_CHANGE_PIN) { launchSingleTop = true }
                                },
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
                                onOpenSettingsHub = { dest ->
                                    val route = when (dest) {
                                        SettingsHubDestination.SUBSCRIPTION -> ROUTE_SETTINGS_SUBSCRIPTION
                                        SettingsHubDestination.SECURITY_PRIVACY -> ROUTE_SETTINGS_SECURITY
                                        SettingsHubDestination.BACKUP_SYNC -> ROUTE_SETTINGS_BACKUP_SYNC
                                        SettingsHubDestination.DATA_STORAGE -> ROUTE_SETTINGS_DATA_STORAGE
                                        SettingsHubDestination.GENERAL -> ROUTE_SETTINGS_GENERAL
                                        SettingsHubDestination.ABOUT_SUPPORT -> ROUTE_SETTINGS_ABOUT
                                    }
                                    navController.navigate(route) { launchSingleTop = true }
                                },
                                onOpenAiFeature = { key ->
                                    val feature = when (key) {
                                        AiFeatureKey.CLASSIFY, AiFeatureKey.SEARCH -> com.xpx.vault.domain.quota.ProFeature.AI_CLASSIFY
                                        AiFeatureKey.PRIVACY -> com.xpx.vault.domain.quota.ProFeature.AI_PRIVACY
                                        AiFeatureKey.ENCRYPT -> com.xpx.vault.domain.quota.ProFeature.AI_SENSITIVE
                                        AiFeatureKey.COMPRESS, AiFeatureKey.DEDUP -> com.xpx.vault.domain.quota.ProFeature.AI_CLEANUP
                                    }
                                    val gate = paywallGatekeeper.checkAccess(feature)
                                    if (gate is com.xpx.vault.billing.GateResult.HardWall) {
                                        navController.navigate(
                                            "$ROUTE_PAYWALL?dismissable=false&source=quota_ai",
                                        ) { launchSingleTop = true }
                                    } else {
                                        val route = when (key) {
                                            AiFeatureKey.CLASSIFY, AiFeatureKey.SEARCH -> ROUTE_AI_CLASSIFY
                                            AiFeatureKey.PRIVACY -> ROUTE_RECENT_LIST
                                            AiFeatureKey.ENCRYPT -> ROUTE_AI_SENSITIVE
                                            AiFeatureKey.COMPRESS, AiFeatureKey.DEDUP -> ROUTE_AI_CLEANUP
                                        }
                                        navController.navigate(route) { launchSingleTop = true }
                                    }
                                },
                                onPaywallRequired = {
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=false&source=quota_vault",
                                    ) { launchSingleTop = true }
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
                                onPaywallRequired = {
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=false&source=quota_backup",
                                    ) { launchSingleTop = true }
                                },
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
                        composable(
                            "$ROUTE_PAYWALL?dismissable={dismissable}&source={source}",
                            arguments = listOf(
                                navArgument("dismissable") { defaultValue = true; type = NavType.BoolType },
                                navArgument("source") { defaultValue = "manual"; type = NavType.StringType },
                            ),
                        ) { entry ->
                            val dismissable = entry.arguments?.getBoolean("dismissable") ?: true
                            val source = entry.arguments?.getString("source") ?: "manual"
                            PaywallScreen(
                                onBack = { navController.popBackStack() },
                                dismissable = dismissable,
                                source = source,
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
                        composable(ROUTE_SETTINGS_SUBSCRIPTION) {
                            SettingsSubscriptionPlaceholderScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPaywall = {
                                    navController.navigate(ROUTE_PAYWALL) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_SETTINGS_SECURITY) {
                            SettingsSecurityPrivacyScreen(
                                onBack = { navController.popBackStack() },
                                onOpenChangePin = {
                                    navController.navigate(ROUTE_CHANGE_PIN) { launchSingleTop = true }
                                },
                                onOpenIntruderAlert = {
                                    navController.navigate(ROUTE_INTRUDER_ALERT) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_INTRUDER_ALERT) {
                            IntruderAlertScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_SETTINGS_BACKUP_SYNC) {
                            SettingsBackupSyncScreen(
                                onBack = { navController.popBackStack() },
                                onOpenBackupRestore = {
                                    navController.navigate(ROUTE_BACKUP_RESTORE) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_SETTINGS_DATA_STORAGE) {
                            SettingsDataStorageScreen(
                                onBack = { navController.popBackStack() },
                                onOpenStorageUsage = {
                                    navController.navigate(ROUTE_STORAGE_USAGE) { launchSingleTop = true }
                                },
                                onOpenBulkExport = {
                                    navController.navigate(ROUTE_BULK_EXPORT) { launchSingleTop = true }
                                },
                                onOpenTrashBin = {
                                    navController.navigate(ROUTE_TRASH_BIN) { launchSingleTop = true }
                                },
                                onOpenImportOriginals = {
                                    navController.navigate(ROUTE_SETTINGS_IMPORT_ORIGINALS) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_SETTINGS_IMPORT_ORIGINALS) {
                            SettingsImportOriginalsScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_SETTINGS_GENERAL) {
                            SettingsGeneralScreen(
                                onBack = { navController.popBackStack() },
                                onOpenLanguageSettings = {
                                    navController.navigate(ROUTE_LANGUAGE_SETTINGS) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_SETTINGS_ABOUT) {
                            SettingsAboutSupportScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPrivacyPolicy = {
                                    navController.navigate(ROUTE_PRIVACY_POLICY) { launchSingleTop = true }
                                },
                                onOpenTermsOfService = {
                                    navController.navigate(ROUTE_TERMS_OF_SERVICE) { launchSingleTop = true }
                                },
                                onContactUs = {
                                    val intent = Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.parse("mailto:service@xipengxin.com"),
                                    ).apply {
                                        putExtra(Intent.EXTRA_SUBJECT, "LumaNox Support")
                                    }
                                    try {
                                        startActivity(intent)
                                    } catch (_: Exception) {
                                        // 没有邮件客户端时回退到浏览器
                                        val fallback = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://xipengxin.com"),
                                        )
                                        startActivity(fallback)
                                    }
                                },
                            )
                        }
                        composable(ROUTE_PRIVACY_POLICY) {
                            val rawResId = if (isChineseLocale()) R.raw.privacy_policy_zh else R.raw.privacy_policy_en
                            LegalWebViewScreen(
                                titleResId = R.string.settings_about_privacy,
                                rawResId = rawResId,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_TERMS_OF_SERVICE) {
                            val rawResId = if (isChineseLocale()) R.raw.terms_of_service_zh else R.raw.terms_of_service_en
                            LegalWebViewScreen(
                                titleResId = R.string.settings_about_terms,
                                rawResId = rawResId,
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
                                onPaywallRequired = {
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=false&source=quota_ai",
                                    ) { launchSingleTop = true }
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
                                onPaywallRequired = {
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=false&source=quota_ai",
                                    ) { launchSingleTop = true }
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
                                onPaywallRequired = {
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=false&source=quota_ai",
                                    ) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(ROUTE_AI_PRIVACY) {
                            AiFeaturePlaceholderScreen(
                                title = stringResource(R.string.privacy_redact_title),
                                description = stringResource(R.string.ai_privacy_redact_desc),
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(ROUTE_PRIVATE_CAMERA) {
                            PrivateCameraScreen(
                                onBack = { navController.popBackStack() },
                                onViewMedia = { path ->
                                    navController.navigate(viewerRouteForPath(path)) { launchSingleTop = true }
                                },
                                onPaywallRequired = {
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=false&source=quota_vault",
                                    ) { launchSingleTop = true }
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
                                onPaywallRequired = {
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=false&source=quota_vault",
                                    ) { launchSingleTop = true }
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
                                    // 进入隐私脱敏属 AI 功能，进入前检查月配额。
                                    val gate = paywallGatekeeper.checkAccess(com.xpx.vault.domain.quota.ProFeature.AI_PRIVACY)
                                    if (gate is com.xpx.vault.billing.GateResult.HardWall) {
                                        navController.navigate(
                                            "$ROUTE_PAYWALL?dismissable=false&source=quota_ai",
                                        ) { launchSingleTop = true }
                                    } else {
                                        navController.navigate("$ROUTE_AI_PRIVACY_REDACT/${Uri.encode(path)}") {
                                            launchSingleTop = true
                                        }
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
                                onPaywallRequired = {
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=false&source=quota_vault",
                                    ) { launchSingleTop = true }
                                },
                                onAiPaywallRequired = {
                                    navController.navigate(
                                        "$ROUTE_PAYWALL?dismissable=false&source=quota_ai",
                                    ) { launchSingleTop = true }
                                },
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

    private fun showTaskSnapshotLockCover() {
        if (taskSnapshotLockCover != null) return
        val cover = FrameLayout(this).apply {
            setBackgroundColor(LOCK_BG)
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
        }
        cover.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        content.addView(
            TextView(this).apply {
                text = getString(R.string.lock_unlock_title)
                setTextColor(LOCK_TEXT_MAIN)
                textSize = 38f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(
            TextView(this).apply {
                text = getString(R.string.lock_unlock_subtitle)
                setTextColor(LOCK_TEXT_SUB)
                textSize = 16f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 12.dp()
            },
        )
        content.addView(
            makeSnapshotPinDots(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 22.dp()
            },
        )
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫"),
        ).forEachIndexed { index, labels ->
            content.addView(makeSnapshotKeyRow(labels), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = if (index == 0) 24.dp() else 14.dp()
            })
        }
        addContentView(
            cover,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        taskSnapshotLockCover = cover
    }

    private fun shouldShowTaskSnapshotLockCover(): Boolean {
        val route = currentRouteForSnapshot
        return appLockManager.shouldProtectTaskSnapshot() &&
            route != null &&
            route != ROUTE_SPLASH &&
            route != ROUTE_LOCK &&
            route != ROUTE_PRIVATE_CAMERA
    }

    private fun hideTaskSnapshotLockCover() {
        val cover = taskSnapshotLockCover ?: return
        (cover.parent as? ViewGroup)?.removeView(cover)
        taskSnapshotLockCover = null
    }

    private fun makeSnapshotPinDots(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            repeat(6) {
                addView(
                    View(this@MainActivity).apply {
                        background = ovalDrawable(Color.TRANSPARENT, LOCK_BLUE, 2.dp())
                    },
                    LinearLayout.LayoutParams(14.dp(), 14.dp()).apply {
                        leftMargin = 8.dp()
                        rightMargin = 8.dp()
                    },
                )
            }
        }
    }

    private fun makeSnapshotKeyRow(labels: List<String>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            labels.forEach { label ->
                addView(
                    TextView(this@MainActivity).apply {
                        text = label
                        setTextColor(LOCK_TEXT_MAIN)
                        textSize = 34f
                        gravity = Gravity.CENTER
                        background = ovalDrawable(LOCK_KEY_BG, LOCK_BLUE, 2.dp())
                    },
                    LinearLayout.LayoutParams(86.dp(), 86.dp()).apply {
                        leftMargin = 8.dp()
                        rightMargin = 8.dp()
                    },
                )
            }
        }
    }

    private fun ovalDrawable(fill: Int, stroke: Int, strokeWidth: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(strokeWidth, stroke)
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()

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

    private fun isChineseLocale(): Boolean {
        val locale = resources.configuration.locales[0]
        return locale.language == "zh"
    }

    companion object {
        private const val LOCK_BG = 0xFF05080D.toInt()
        private const val LOCK_KEY_BG = 0xFF131C29.toInt()
        private const val LOCK_BLUE = 0xFF4A9EFF.toInt()
        private const val LOCK_TEXT_MAIN = 0xFFEAF1FF.toInt()
        private const val LOCK_TEXT_SUB = 0xFF7E90AB.toInt()
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
        private const val ROUTE_SETTINGS_SUBSCRIPTION = "settings_subscription"
        private const val ROUTE_SETTINGS_SECURITY = "settings_security"
        private const val ROUTE_INTRUDER_ALERT = "intruder_alert"
        private const val ROUTE_SETTINGS_BACKUP_SYNC = "settings_backup_sync"
        private const val ROUTE_SETTINGS_DATA_STORAGE = "settings_data_storage"
        private const val ROUTE_SETTINGS_IMPORT_ORIGINALS = "settings_import_originals"
        private const val ROUTE_SETTINGS_GENERAL = "settings_general"
        private const val ROUTE_SETTINGS_ABOUT = "settings_about"
        private const val ROUTE_PRIVACY_POLICY = "privacy_policy"
        private const val ROUTE_TERMS_OF_SERVICE = "terms_of_service"
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
