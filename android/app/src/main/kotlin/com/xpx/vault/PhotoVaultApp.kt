package com.xpx.vault

import android.app.Application
import android.content.Context
import com.xpx.vault.ui.backup.AutoBackupScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhotoVaultApp : Application() {
    @Inject
    lateinit var appLockManager: AppLockManager

    override fun attachBaseContext(base: Context) {
        // Application 级别也包装 Locale，避免偶然使用 application context 回落到系统默认语言。
        super.attachBaseContext(LanguageManager.wrapContext(base))
    }

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
