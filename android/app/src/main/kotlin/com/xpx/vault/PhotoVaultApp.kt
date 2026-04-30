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
        // 备份启动自检：修复上次可能残留的 .writing / .bak 中间态；清理旧模型的文件。
        runCatching {
            com.xpx.vault.ui.backup.ExternalBackupLocation.sanitizeOnStartup(this)
        }.onFailure { AppLogger.w("AppInit", "sanitize failed: ${it.message}") }
        runCatching {
            com.xpx.vault.ui.backup.LegacyBackupCleanup.runOnce(this)
        }.onFailure { AppLogger.w("AppInit", "legacy cleanup failed: ${it.message}") }
        // 开发期一次性迁移：后台协程里静默将存量明文加密，完成后打 marker 避免重复计算。
        runCatching {
            com.xpx.vault.ui.vault.VaultPlaintextMigration.scheduleOnStartup(this)
        }.onFailure { AppLogger.w("AppInit", "vault migration schedule failed: ${it.message}") }
        AutoBackupScheduler.ensureScheduled(this)
        // 冷启补偿：若距上次自动备份已过阈值（默认 8h），在后台排一次延迟 30s 的 OneTimeWork，避免只靠 24h 周期。
        runCatching { AutoBackupScheduler.scheduleColdStartIfDue(this) }
            .onFailure { AppLogger.w("AppInit", "coldStart backup schedule failed: ${it.message}") }
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
