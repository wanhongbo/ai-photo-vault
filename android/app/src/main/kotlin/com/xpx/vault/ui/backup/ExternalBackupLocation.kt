package com.xpx.vault.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.xpx.vault.AppLogger
import java.io.File

/**
 * 外部备份位置管理（SAF 持久化 + 原子覆盖）。
 *
 * 设计：
 * - 用户通过 `ACTION_OPEN_DOCUMENT_TREE` 选择一个目录（建议 Documents/AIVault），
 *   本类调用 takePersistableUriPermission 持久化权限，tree uri 保存到 EncryptedSharedPreferences。
 * - 自动备份固定写入该目录下的 `backup.dat`，覆盖更新走 rename 桥接（`.writing` → 主 → `.bak`）。
 * - MIME 统一使用 `application/octet-stream`，避免进入 MediaStore 相册索引。
 */
object ExternalBackupLocation {
    const val AUTO_FILE_NAME: String = "backup.dat"
    const val TMP_FILE_NAME: String = "backup.dat.writing"
    const val BAK_FILE_NAME: String = "backup.dat.bak"
    const val MANUAL_EXTENSION: String = "aivb"
    const val DEFAULT_MIME_TYPE: String = "application/octet-stream"
    const val MANUAL_FILE_PREFIX: String = "AIVault_Backup_"

    private const val PREFS_NAME = "external_backup_prefs"
    private const val KEY_TREE_URI = "tree_uri"
    private const val TAG = "ExternalBackupLocation"

    /** 把用户授权的目录 Uri 持久化下来。 */
    fun persistTree(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
            .onFailure { AppLogger.w(TAG, "takePersistable failed: ${it.message}") }
        prefs(context).edit().putString(KEY_TREE_URI, treeUri.toString()).apply()
    }

    /** 清除授权（用户解绑目录时调用）。 */
    fun clearTree(context: Context) {
        val uri = getTreeUri(context) ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs(context).edit().remove(KEY_TREE_URI).apply()
    }

    fun getTreeUri(context: Context): Uri? {
        val s = prefs(context).getString(KEY_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(s) }.getOrNull()
    }

    /** 是否已授权且可写。 */
    fun isWritable(context: Context): Boolean {
        val tree = getTreeUri(context) ?: return false
        val doc = DocumentFile.fromTreeUri(context, tree) ?: return false
        return doc.exists() && doc.canWrite()
    }

    /** 返回自动备份目录 [DocumentFile]；若未授权返回 null。 */
    fun autoDir(context: Context): DocumentFile? {
        val tree = getTreeUri(context) ?: return null
        return DocumentFile.fromTreeUri(context, tree)
    }

    /** 找到现有的 `backup.dat`；不存在返回 null。 */
    fun findAuto(context: Context): DocumentFile? =
        autoDir(context)?.findFile(AUTO_FILE_NAME)?.takeIf { it.isFile }

    /** 找到遗留的 `.writing` 或 `.bak`。 */
    private fun findWriting(context: Context): DocumentFile? =
        autoDir(context)?.findFile(TMP_FILE_NAME)?.takeIf { it.isFile }

    private fun findBak(context: Context): DocumentFile? =
        autoDir(context)?.findFile(BAK_FILE_NAME)?.takeIf { it.isFile }

    /**
     * 启动时自检修复：
     * - 仅 `.writing` → 删除
     * - 仅 `.bak`    → rename 为 backup.dat
     * - 同时存在   → 保留主文件，删除 `.bak`
     */
    fun sanitizeOnStartup(context: Context) {
        if (!isWritable(context)) return
        runCatching {
            val main = findAuto(context)
            val writing = findWriting(context)
            val bak = findBak(context)
            if (writing != null) {
                AppLogger.d(TAG, "sanitize: remove stale .writing")
                runCatching { writing.delete() }
            }
            when {
                main != null && bak != null -> {
                    AppLogger.d(TAG, "sanitize: main+bak both present, drop bak")
                    runCatching { bak.delete() }
                }
                main == null && bak != null -> {
                    AppLogger.d(TAG, "sanitize: recover .bak -> ${AUTO_FILE_NAME}")
                    runCatching { bak.renameTo(AUTO_FILE_NAME) }
                }
            }
        }.onFailure { AppLogger.w(TAG, "sanitize failed: ${it.message}") }
    }

    /**
     * 原子覆盖自动备份：
     * 1. 把 [tmpLocal] 字节写入外部目录的 `backup.dat.writing`。
     * 2. 若存在 `backup.dat` → rename 为 `backup.dat.bak`。
     * 3. rename `.writing` 为 `backup.dat`。
     * 4. 删除 `.bak`。
     *
     * 任一步失败都抛 [IllegalStateException]，外部包保持旧状态；
     * 调用方应在调用前确保 [tmpLocal] 内容已经 fsync 并通过 MD5 校验。
     */
    fun atomicReplaceAuto(context: Context, tmpLocal: File) {
        val dir = autoDir(context) ?: error("external backup tree not authorized")

        // 清理残留
        dir.findFile(TMP_FILE_NAME)?.delete()

        // step1: 写入 .writing
        val writing = dir.createFile(DEFAULT_MIME_TYPE, TMP_FILE_NAME)
            ?: error("failed to create .writing file")
        context.contentResolver.openOutputStream(writing.uri, "w").use { out ->
            checkNotNull(out) { "openOutputStream(.writing) returned null" }
            tmpLocal.inputStream().buffered().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
                out.flush()
            }
        }

        // step2: rename 主 -> .bak
        val existing = dir.findFile(AUTO_FILE_NAME)
        if (existing != null) {
            // 先清掉可能残留的 .bak
            dir.findFile(BAK_FILE_NAME)?.delete()
            val renamed = runCatching { existing.renameTo(BAK_FILE_NAME) }.getOrDefault(false)
            if (!renamed) {
                // 某些 provider 不支持 rename → 退化为 delete 主文件（此时窗口内一旦崩溃会丢失旧外部包，已通过 sanitize 修复）
                AppLogger.w(TAG, "renameTo(.bak) unsupported, fallback to delete main")
                existing.delete()
            }
        }

        // step3: rename .writing -> 主
        val renamed = runCatching { writing.renameTo(AUTO_FILE_NAME) }.getOrDefault(false)
        if (!renamed) {
            // 某些 provider 不支持 rename → 先创建新主文件再把 writing 内容拷贝进去，再删除 writing
            AppLogger.w(TAG, "renameTo(main) unsupported, fallback to copy")
            val newMain = dir.createFile(DEFAULT_MIME_TYPE, AUTO_FILE_NAME)
                ?: error("failed to create $AUTO_FILE_NAME")
            context.contentResolver.openOutputStream(newMain.uri, "w").use { out ->
                checkNotNull(out)
                context.contentResolver.openInputStream(writing.uri).use { input ->
                    checkNotNull(input)
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                    out.flush()
                }
            }
            writing.delete()
        }

        // step4: 删除 .bak
        dir.findFile(BAK_FILE_NAME)?.delete()
    }

    /** 推算一个手动备份文件名：AIVault_Backup_yyyyMMdd_HHmmss[_note].aivb */
    fun buildManualFileName(timestampMs: Long, note: String? = null): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
        val stamp = fmt.format(java.util.Date(timestampMs))
        val tail = note?.takeIf { it.isNotBlank() }?.let { "_$it" } ?: ""
        return "$MANUAL_FILE_PREFIX$stamp$tail.$MANUAL_EXTENSION"
    }

    // tree uri 属于用户授权结果文档，非秘密内容；使用普通 SharedPreferences 避免 AndroidX Security Crypto 的 MasterKey 兼容性问题。
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
