package com.photovault.app

import android.os.Bundle
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.photovault.app.ui.HomeScreen
import com.photovault.app.ui.SplashScreen
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
                                    navController.navigate(ROUTE_HOME) {
                                        popUpTo(ROUTE_LOCK) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(ROUTE_HOME) {
                            HomeScreen()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ROUTE_SPLASH = "splash"
        private const val ROUTE_LOCK = "lock"
        private const val ROUTE_HOME = "home"
    }
}
