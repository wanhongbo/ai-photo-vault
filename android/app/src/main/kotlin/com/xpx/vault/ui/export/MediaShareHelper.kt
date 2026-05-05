package com.xpx.vault.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.xpx.vault.data.crypto.VaultCipher
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将 vault 中的密文解密到 `cacheDir/share_cache/`，再通过 FileProvider 以 content:// URI 共享给外部 App。
 *
 * 注意：
 * - 分享的明文文件会短暂落在 cacheDir 中（标准 private external 之外的 App 内部缓存），
 *   这是 FileProvider 允许授权的目录；系统在空间紧张时会自动回收。
 * - 每次分享前会清理同目录下 > 1h 的残留，避免磁盘堆积。
 * - FLAG_GRANT_READ_URI_PERMISSION 仅授予目标 App 临时读权限；分享会话关闭即收回。
 */
object MediaShareHelper {

    private const val SHARE_SUB_DIR = "share_cache"
    private const val EXPIRE_MS = 60L * 60 * 1000

    sealed class ShareOutcome {
        data class Success(val uri: Uri, val mimeType: String) : ShareOutcome()
        data class Failure(val reason: String) : ShareOutcome()
    }

    /**
     * 解密 vault 中的密文文件并发起系统分享。
     *
     * @param context Activity/Application Context 均可
     * @param sourcePath vault 中密文文件的绝对路径
     * @param chooserTitle 系统选择器标题
     */
    suspend fun shareFile(
        context: Context,
        sourcePath: String,
        chooserTitle: String,
    ): ShareOutcome {
        val prepared = prepare(context, sourcePath)
        if (prepared is ShareOutcome.Failure) return prepared
        prepared as ShareOutcome.Success
        return launchShareIntent(context, prepared, chooserTitle)
    }

    /**
     * 将内存中的 [Bitmap] 以 JPEG 编码后通过系统分享。
     *
     * 适用于隐私脱敏预览图：脱敏后的预览图不落 vault 也不导出系统相册，
     * 直接走 cacheDir 中转给外部 App。文件名以 [baseName] 为后缀，1h 内自动清理。
     */
    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        baseName: String,
        chooserTitle: String,
        quality: Int = 95,
    ): ShareOutcome {
        val prepared = prepareBitmap(context, bitmap, baseName, quality)
        if (prepared is ShareOutcome.Failure) return prepared
        prepared as ShareOutcome.Success
        return launchShareIntent(context, prepared, chooserTitle)
    }

    private fun launchShareIntent(
        context: Context,
        prepared: ShareOutcome.Success,
        chooserTitle: String,
    ): ShareOutcome {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = prepared.mimeType
            putExtra(Intent.EXTRA_STREAM, prepared.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(chooser)
            prepared
        } catch (t: Throwable) {
            ShareOutcome.Failure(t.message ?: "no_share_app")
        }
    }

    private suspend fun prepareBitmap(
        context: Context,
        bitmap: Bitmap,
        baseName: String,
        quality: Int,
    ): ShareOutcome = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) return@withContext ShareOutcome.Failure("bitmap_recycled")
        val shareRoot = File(context.cacheDir, SHARE_SUB_DIR).apply { mkdirs() }
        pruneStale(shareRoot)
        val safeBase = baseName.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "redacted" }
            .takeLast(24)
        val dest = File(shareRoot, "${System.nanoTime()}_${safeBase}.jpg")
        try {
            FileOutputStream(dest).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                output.flush()
            }
        } catch (t: Throwable) {
            if (dest.exists()) dest.delete()
            return@withContext ShareOutcome.Failure(t.message ?: "encode_failed")
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = try {
            FileProvider.getUriForFile(context, authority, dest)
        } catch (t: Throwable) {
            dest.delete()
            return@withContext ShareOutcome.Failure(t.message ?: "provider_uri_failed")
        }
        ShareOutcome.Success(uri, "image/jpeg")
    }

    private suspend fun prepare(context: Context, sourcePath: String): ShareOutcome = withContext(Dispatchers.IO) {
        val src = File(sourcePath)
        if (!src.exists() || !src.isFile) {
            return@withContext ShareOutcome.Failure("file_not_found")
        }
        val ext = src.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: fallbackMime(ext)
            ?: return@withContext ShareOutcome.Failure("unknown_mime")

        val shareRoot = File(context.cacheDir, SHARE_SUB_DIR).apply { mkdirs() }
        pruneStale(shareRoot)

        val displayName = pickDisplayName(src)
        val dest = File(shareRoot, "${System.nanoTime()}_$displayName")
        try {
            FileInputStream(src).buffered(256 * 1024).use { input ->
                FileOutputStream(dest).use { output ->
                    VaultCipher.get(context).decryptStream(input) { data, offset, length ->
                        output.write(data, offset, length)
                    }
                    output.flush()
                }
            }
        } catch (t: Throwable) {
            if (dest.exists()) dest.delete()
            return@withContext ShareOutcome.Failure(t.message ?: "decrypt_failed")
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = try {
            FileProvider.getUriForFile(context, authority, dest)
        } catch (t: Throwable) {
            dest.delete()
            return@withContext ShareOutcome.Failure(t.message ?: "provider_uri_failed")
        }
        ShareOutcome.Success(uri, mimeType)
    }

    private fun pickDisplayName(file: File): String {
        val name = file.name
        return if (name.startsWith("asset_") || name.startsWith("camera_") || name.startsWith("tmp_")) {
            val ext = file.extension.ifBlank { "bin" }
            val tail = file.nameWithoutExtension.takeLast(8).ifBlank { System.currentTimeMillis().toString() }
            "AIPhotoVault_$tail.$ext"
        } else {
            name
        }
    }

    private fun pruneStale(dir: File) {
        val expireAt = System.currentTimeMillis() - EXPIRE_MS
        dir.listFiles()?.forEach { entry ->
            if (entry.isFile && entry.lastModified() < expireAt) {
                runCatching { entry.delete() }
            }
        }
    }

    private fun fallbackMime(ext: String): String? = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heif"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "avi" -> "video/x-msvideo"
        "flv" -> "video/x-flv"
        else -> null
    }
}
