package com.xpx.vault.ui.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.xpx.vault.AppLogger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 一次性自动备份触发来源。 */
enum class BackupTriggerReason { PASSWORD_CHANGED, MANUAL_RESTORE_SYNC, USER_MANUAL_BUTTON, COLD_START_DUE }

private const val AUTO_BACKUP_PREFS = "auto_backup_prefs"
private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
private const val AUTO_BACKUP_UNIQUE_WORK = "auto_incremental_backup_work_v2"
/** 旧版唯一名（曾含充电/空闲约束）；升级后取消以免沿用旧约束。 */
private const val LEGACY_AUTO_BACKUP_UNIQUE_WORK = "auto_incremental_backup_work"
private const val AUTO_BACKUP_ONE_TIME_WORK = "auto_incremental_backup_once"
private const val AUTO_BACKUP_COLD_START_WORK = "auto_incremental_backup_cold_start"
private const val TAG = "AutoBackupScheduler"

/** 冷启补备份的最小间隔：距上次备份超过该间隔才会触发。 */
private const val COLD_START_MIN_INTERVAL_MS: Long = 8L * 60L * 60L * 1000L  // 8h

/** 冷启排队后的启动延迟，避免与冷启 UI 争 IO。 */
private const val COLD_START_INITIAL_DELAY_MS: Long = 30L * 1000L  // 30s

private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

object AutoBackupScheduler {
    fun ensureScheduled(context: Context) {
        if (isEnabled(context)) {
            schedule(context)
        } else {
            cancel(context)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(AUTO_BACKUP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled)
            .apply()
        ensureScheduled(context)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(AUTO_BACKUP_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_BACKUP_ENABLED, true)

    private fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(LEGACY_AUTO_BACKUP_UNIQUE_WORK)
        val request = PeriodicWorkRequestBuilder<AutoIncrementalBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        // KEEP: 冷启不重置 nextRunTime；仅当不存在时第一次排队。
        wm.enqueueUniquePeriodicWork(
            AUTO_BACKUP_UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_UNIQUE_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_AUTO_BACKUP_UNIQUE_WORK)
    }

    /**
     * 立即触发一次自动备份（不需全局开关、不受周期约束）。
     * 常用于：修改 PIN 后同步外部包、手动恢复成功后重新对齐。
     */
    fun runOnceNow(context: Context, reason: BackupTriggerReason) {
        val request = OneTimeWorkRequestBuilder<AutoIncrementalBackupWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AUTO_BACKUP_ONE_TIME_WORK + "_" + reason.name.lowercase(),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * 冷启时调用：若距上次自动备份超过 [minIntervalMs]，在后台补一次。
     * - 在后台 IO 协程里读 [BackupMeta]，不阻塞冷启。
     * - 延迟 [initialDelayMs] 后执行，避免与 UI 冷启争 IO。
     * - ExistingWorkPolicy.KEEP：连续冷启不会重复排队。
     * - 注：Worker 内部同样会走用户设置的 enabled / keyCached / safWritable 守卫。
     */
    fun scheduleColdStartIfDue(
        context: Context,
        minIntervalMs: Long = COLD_START_MIN_INTERVAL_MS,
        initialDelayMs: Long = COLD_START_INITIAL_DELAY_MS,
    ) {
        val app = context.applicationContext
        if (!isEnabled(app)) {
            AppLogger.d(TAG, "coldStart skip: auto backup disabled")
            return
        }
        schedulerScope.launch {
            runCatching {
                val last = BackupMeta.load(app).auto?.lastBackupAtMs ?: 0L
                val now = System.currentTimeMillis()
                val elapsed = if (last > 0L) now - last else Long.MAX_VALUE
                if (elapsed < minIntervalMs) {
                    AppLogger.d(TAG, "coldStart skip: not due, elapsedMs=$elapsed min=$minIntervalMs")
                    return@launch
                }
                val request = OneTimeWorkRequestBuilder<AutoIncrementalBackupWorker>()
                    .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    )
                    .build()
                WorkManager.getInstance(app).enqueueUniqueWork(
                    AUTO_BACKUP_COLD_START_WORK,
                    ExistingWorkPolicy.KEEP,
                    request,
                )
                AppLogger.d(
                    TAG,
                    "coldStart enqueue: elapsedMs=$elapsed min=$minIntervalMs delayMs=$initialDelayMs",
                )
            }.onFailure { AppLogger.w(TAG, "coldStart schedule failed: ${it.message}") }
        }
    }
}
