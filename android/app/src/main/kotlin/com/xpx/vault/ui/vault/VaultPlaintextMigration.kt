package com.xpx.vault.ui.vault

import android.content.Context
import com.xpx.vault.AppLogger
import com.xpx.vault.data.crypto.VaultCipher
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开发期迁移：把 vault_albums/ 下历史遗留的明文文件静默加密为 AES-CBC 密文。
 *
 * - 没有真实用户，不需要进度 UI / WorkManager / 懒迁移等复杂机制。
 * - 通过 [VaultCipher.looksLikeCiphertext] 判别密文 / 明文；密文原样保留，明文就地加密。
 * - 完成后写入一个空 marker 文件 `.vault_encrypted_v1`，后续启动若目录未变化可以提前短路。
 * - 任意单文件失败不中断整体，仅记日志；相册滑动 / 拍照等上层功能一律走 VaultCipher.encryptFile 路径。
 */
object VaultPlaintextMigration {

    private const val VAULT_ROOT_DIR = "vault_albums"
    private const val MARKER_FILE = ".vault_encrypted_v1"
    private const val TAG = "VaultMigrate"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 在 Application.onCreate 中触发：后台协程内执行，不阻塞 UI。 */
    fun scheduleOnStartup(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { runOnce(app) }.onFailure { AppLogger.w(TAG, "migration failed: ${it.message}", it) } }
    }

    private fun runOnce(context: Context) {
        val vaultRoot = File(context.filesDir, VAULT_ROOT_DIR)
        if (!vaultRoot.exists()) return
        val marker = File(vaultRoot, MARKER_FILE)
        if (marker.exists()) {
            // 已经做过迁移；开发期也跳过，避免每次启动重复遍历磁盘。
            return
        }
        val cipher = VaultCipher.get(context)
        var migrated = 0
        var skipped = 0
        var failed = 0
        vaultRoot.walkTopDown()
            .filter { it.isFile }
            .filter { it.name != MARKER_FILE }
            .filter { !it.name.contains(".enc_tmp_") }
            .forEach { file ->
                runCatching {
                    if (cipher.looksLikeCiphertext(file)) {
                        skipped += 1
                    } else {
                        // 原子替换：encryptFile 会写同目录 .enc_tmp_xxx 再 rename 覆盖。
                        file.inputStream().buffered().use { input ->
                            cipher.encryptFile(input, file)
                        }
                        migrated += 1
                    }
                }.onFailure {
                    failed += 1
                    AppLogger.w(TAG, "migrate failed: ${file.absolutePath} ${it.message}")
                }
            }
        AppLogger.d(TAG, "migration done migrated=$migrated skipped=$skipped failed=$failed")
        runCatching {
            if (!marker.exists()) marker.createNewFile()
        }
    }
}
