package com.xpx.vault.ui.backup

import android.content.Context
import com.xpx.vault.AppLogger

/**
 * 双密钥重构上线后的一次性迁移清理：
 *
 * - 删除旧版零散落在私有目录下的备份中间产物（vault_backup_*.zip / backup_index.json / 临时 aiv 包等）。
 * - 保留数据库中的 `backup_records` 表；新版仍用它作为免费备份次数配额记录。
 *
 * 通过 SharedPreferences 的标记位确保整轮清理只会执行一次，避免每次启动重复扫描。
 */
object LegacyBackupCleanup {
    private const val PREFS_NAME = "legacy_backup_cleanup"
    private const val KEY_DONE_VERSION = "done_version"
    private const val CURRENT_VERSION = 1
    private const val TAG = "LegacyBackupCleanup"

    fun runOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_DONE_VERSION, 0) >= CURRENT_VERSION) return

        val filesDir = context.filesDir
        if (filesDir != null && filesDir.isDirectory) {
            runCatching {
                filesDir.listFiles()?.forEach { f ->
                    val name = f.name
                    val isLegacy = name.startsWith("vault_backup_") ||
                        name == "backup_index.json" ||
                        name.endsWith(".aiv") ||
                        name.endsWith(".aiv.tmp") ||
                        name.startsWith("manifest_") ||
                        name == "backup_manifest.json"
                    if (isLegacy) {
                        val ok = f.deleteRecursively()
                        AppLogger.d(TAG, "legacy cleanup: $name deleted=$ok")
                    }
                }
            }.onFailure { AppLogger.w(TAG, "filesDir scan failed: ${it.message}") }
        }

        // backup_records 仍作为配额计数来源，不能在启动清理中删除。
        prefs.edit().putInt(KEY_DONE_VERSION, CURRENT_VERSION).apply()
        AppLogger.d(TAG, "cleanup version=$CURRENT_VERSION completed")
    }
}
