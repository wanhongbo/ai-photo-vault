package com.xpx.vault.ui.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.VisibleForTesting
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将保险库中的图片/视频文件导出到系统相册 (MediaStore)。
 *
 * 策略：
 * - Android 10+: 使用 MediaStore + RELATIVE_PATH + IS_PENDING 事务写入。
 * - Android 9 及以下：直接写入 Environment.DIRECTORY_PICTURES/MOVIES 下的 AIPhotoVault 目录，
 *   然后通过 MediaScannerConnection 触发扫描（此路径仅在声明 WRITE_EXTERNAL_STORAGE 后生效）。
 */
object MediaExporter {

    private const val EXPORT_SUB_DIR = "AIPhotoVault"

    sealed class ExportOutcome {
        data class Success(val uri: Uri, val displayName: String) : ExportOutcome()
        data class Failure(val reason: String) : ExportOutcome()
    }

    enum class Kind { IMAGE, VIDEO, UNKNOWN }

    suspend fun exportFile(context: Context, sourcePath: String): ExportOutcome = withContext(Dispatchers.IO) {
        val file = File(sourcePath)
        if (!file.exists() || !file.isFile) {
            return@withContext ExportOutcome.Failure("file_not_found")
        }
        val ext = file.extension.lowercase()
        val mimeType = resolveMimeType(ext) ?: return@withContext ExportOutcome.Failure("unknown_mime")
        val kind = kindOf(mimeType)
        if (kind == Kind.UNKNOWN) return@withContext ExportOutcome.Failure("unsupported_type")
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(context, file, mimeType, kind)
        } else {
            exportViaLegacy(context, file, mimeType, kind)
        }
    }

    private fun exportViaMediaStore(
        context: Context,
        file: File,
        mimeType: String,
        kind: Kind,
    ): ExportOutcome {
        val resolver = context.contentResolver
        val collection = when (kind) {
            Kind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.UNKNOWN -> return ExportOutcome.Failure("unsupported_type")
        }
        val relativePath = when (kind) {
            Kind.IMAGE -> "${Environment.DIRECTORY_PICTURES}/$EXPORT_SUB_DIR"
            Kind.VIDEO -> "${Environment.DIRECTORY_MOVIES}/$EXPORT_SUB_DIR"
            Kind.UNKNOWN -> return ExportOutcome.Failure("unsupported_type")
        }
        val displayName = pickDisplayName(file)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return ExportOutcome.Failure("insert_failed")
        return try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: return ExportOutcome.Failure("open_stream_failed")
            val finishValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, finishValues, null, null)
            ExportOutcome.Success(uri, displayName)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            ExportOutcome.Failure(t.message ?: "io_error")
        }
    }

    @Suppress("DEPRECATION")
    private fun exportViaLegacy(
        context: Context,
        file: File,
        mimeType: String,
        kind: Kind,
    ): ExportOutcome {
        val base = when (kind) {
            Kind.IMAGE -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            Kind.VIDEO -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            Kind.UNKNOWN -> return ExportOutcome.Failure("unsupported_type")
        }
        val targetDir = File(base, EXPORT_SUB_DIR)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return ExportOutcome.Failure("mkdir_failed")
        }
        val displayName = pickDisplayName(file)
        var candidate = File(targetDir, displayName)
        var counter = 1
        val baseName = displayName.substringBeforeLast('.', displayName)
        val ext = displayName.substringAfterLast('.', "")
        while (candidate.exists()) {
            val suffix = if (ext.isBlank()) "" else ".$ext"
            candidate = File(targetDir, "${baseName}_$counter$suffix")
            counter++
        }
        return try {
            file.inputStream().use { input ->
                candidate.outputStream().use { output -> input.copyTo(output) }
            }
            // Trigger media scanner
            val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(candidate)
            }
            context.sendBroadcast(intent)
            ExportOutcome.Success(Uri.fromFile(candidate), candidate.name)
        } catch (t: Throwable) {
            if (candidate.exists()) candidate.delete()
            ExportOutcome.Failure(t.message ?: "io_error")
        }
    }

    @VisibleForTesting
    internal fun pickDisplayName(file: File): String {
        // 使用原文件名；vault 存储的 asset_<hash>.ext 文件缺少语义，这里保留原扩展名，
        // 但用 "AIPhotoVault_<last8>.ext" 更易识别。若文件已具备明确非 asset_ 前缀，则沿用。
        val name = file.name
        return if (name.startsWith("asset_") || name.startsWith("camera_") || name.startsWith("tmp_")) {
            val ext = file.extension.ifBlank { "bin" }
            val tail = file.nameWithoutExtension.takeLast(8).ifBlank { System.currentTimeMillis().toString() }
            "AIPhotoVault_$tail.$ext"
        } else {
            name
        }
    }

    private fun resolveMimeType(ext: String): String? {
        if (ext.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: fallbackMime(ext)
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

    private fun kindOf(mimeType: String): Kind = when {
        mimeType.startsWith("image/") -> Kind.IMAGE
        mimeType.startsWith("video/") -> Kind.VIDEO
        else -> Kind.UNKNOWN
    }
}
