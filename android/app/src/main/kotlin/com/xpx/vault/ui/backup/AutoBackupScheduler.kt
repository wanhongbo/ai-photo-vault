package com.xpx.vault.ui.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** 一次性自动备份触发来源。 */
enum class BackupTriggerReason { PASSWORD_CHANGED, MANUAL_RESTORE_SYNC, USER_MANUAL_BUTTON }

private const val AUTO_BACKUP_PREFS = "auto_backup_prefs"
private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
private const val KEY_AUTO_BACKUP_REQUIRE_CHARGING = "auto_backup_require_charging"
private const val KEY_AUTO_BACKUP_REQUIRE_IDLE = "auto_backup_require_idle"
private const val AUTO_BACKUP_UNIQUE_WORK = "auto_incremental_backup_work"
private const val AUTO_BACKUP_ONE_TIME_WORK = "auto_incremental_backup_once"

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

    fun isRequireCharging(context: Context): Boolean =
        context.getSharedPreferences(AUTO_BACKUP_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_BACKUP_REQUIRE_CHARGING, false)

    fun isRequireIdle(context: Context): Boolean =
        context.getSharedPreferences(AUTO_BACKUP_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_BACKUP_REQUIRE_IDLE, false)

    fun setRequireCharging(context: Context, requireCharging: Boolean) {
        context.getSharedPreferences(AUTO_BACKUP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_BACKUP_REQUIRE_CHARGING, requireCharging)
            .apply()
        ensureScheduled(context)
    }

    fun setRequireIdle(context: Context, requireIdle: Boolean) {
        context.getSharedPreferences(AUTO_BACKUP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_BACKUP_REQUIRE_IDLE, requireIdle)
            .apply()
        ensureScheduled(context)
    }

    private fun schedule(context: Context) {
        val requireCharging = isRequireCharging(context)
        val requireIdle = isRequireIdle(context)
        val request = PeriodicWorkRequestBuilder<AutoIncrementalBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresCharging(requireCharging)
                    .setRequiresDeviceIdle(requireIdle)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_BACKUP_UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_UNIQUE_WORK)
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
}
