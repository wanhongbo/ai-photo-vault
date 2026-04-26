package com.photovault.app.ui.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val AUTO_BACKUP_PREFS = "auto_backup_prefs"
private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
private const val KEY_AUTO_BACKUP_REQUIRE_CHARGING = "auto_backup_require_charging"
private const val KEY_AUTO_BACKUP_REQUIRE_IDLE = "auto_backup_require_idle"
private const val AUTO_BACKUP_UNIQUE_WORK = "auto_incremental_backup_work"

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
}
