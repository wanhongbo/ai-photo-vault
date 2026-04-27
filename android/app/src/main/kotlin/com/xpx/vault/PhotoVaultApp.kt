package com.xpx.vault

import android.app.Application
import com.xpx.vault.ui.backup.AutoBackupScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhotoVaultApp : Application() {
    @Inject
    lateinit var appLockManager: AppLockManager

    override fun onCreate() {
        super.onCreate()
        LanguageManager.initialize(this)
        AppLogger.install()
        installGlobalExceptionBoundary()
        appLockManager.start()
        AutoBackupScheduler.ensureScheduled(this)
    }

    private fun installGlobalExceptionBoundary() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e(
                tag = "Uncaught",
                message = "thread=${thread.name} ${throwable.javaClass.simpleName}",
                throwable = throwable,
            )
            previous?.uncaughtException(thread, throwable)
        }
    }
}
