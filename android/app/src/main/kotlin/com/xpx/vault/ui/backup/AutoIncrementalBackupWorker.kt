package com.xpx.vault.ui.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AutoIncrementalBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val result = LocalBackupMvpService.createBackup(applicationContext)
        return if (result.success) Result.success() else Result.retry()
    }
}
