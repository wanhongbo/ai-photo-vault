package com.xpx.vault.ui.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xpx.vault.AppLogger

/**
 * 自动备份 Worker。
 *
 * 前置守卫：
 * 1. 自动备份未启用 → success（本次静默不跑）。
 * 2. backupKey 缓存不存在（用户未解锁过或已清）→ success（等下次解锁后刷新）。
 * 3. 外部 SAF 目录未授权或不可写 → retry。
 * 4. 实际执行失败（不含 AlreadyRunning）→ retry。
 */
class AutoIncrementalBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!AutoBackupScheduler.isEnabled(ctx)) {
            AppLogger.d(TAG, "skip: auto backup disabled")
            return Result.success()
        }
        if (!BackupSecretsStore.hasCached(ctx)) {
            AppLogger.d(TAG, "skip: backup key not cached")
            return Result.success()
        }
        if (!ExternalBackupLocation.isWritable(ctx)) {
            AppLogger.w(TAG, "retry: external backup location not writable")
            return Result.retry()
        }
        val result = LocalBackupMvpService.createBackup(ctx, BackupTrigger.AUTO)
        return when {
            result.success -> Result.success()
            result.alreadyRunning -> Result.success()
            else -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
    }
}
