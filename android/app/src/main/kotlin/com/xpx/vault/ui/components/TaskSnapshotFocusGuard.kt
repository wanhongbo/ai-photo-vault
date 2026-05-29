package com.xpx.vault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.xpx.vault.findAppLockManager

@Composable
fun TaskSnapshotFocusGuard(reason: String) {
    val appLockManager = LocalContext.current.findAppLockManager()
    DisposableEffect(appLockManager, reason) {
        appLockManager?.beginAppTransientWindow(reason)
        onDispose {
            appLockManager?.endAppTransientWindow(reason)
        }
    }
}
