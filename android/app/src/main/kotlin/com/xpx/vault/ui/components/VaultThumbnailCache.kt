package com.xpx.vault.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.xpx.vault.data.crypto.VaultCipher
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vault 图片缩略图两级缓存。
 *
 * - **内存**：LruCache<Key, Bitmap>，约 32MB；key 由 path + size + mtime + targetMaxPx 组合。
 * - **磁盘**：加密缩略图落盘到 `cacheDir/thumb_cache/<sha256>.enc`，首次加载之后再点开同一张图只需
 *   解密 ~60KB 的 JPEG（而非整张原图）；无缓存或缓存失效时从 vault 原文解密 → 生成缩略图 → 回填两级缓存。
 *
 * 设计初衷：相册滑动时每秒可能需要解码几十张缩略图；直接走"原图整解 + BitmapFactory 采样"的成本对
 * 中端机会体现为"滑动顿感"，加上磁盘缓存后稳态基本能维持 60fps。
 */
object VaultThumbnailCache {

    private const val THUMB_QUALITY = 80
    private const val DISK_DIR = "thumb_cache"
    private const val MEMORY_BYTES = 32 * 1024 * 1024

    /**
     * 返回给调用方的解码结果，包含缩略图 bitmap 和原图尺寸。
     *
     * [originalWidth] / [originalHeight] 在“首次生成缩略图”的路径能准确得到（来自 BitmapFactory.inJustDecodeBounds）；
     * 从磁盘缓存命中时，如 `.dim` 边信息文件存在则同样准确，否则为 0。
     * 调用方在拿不到原图尺寸时应有 fallback。
     */
    data class DecodedImage(
        val bitmap: Bitmap,
        val originalWidth: Int,
        val originalHeight: Int,
    )

    private val memory: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * 或相小型映射：把缩略图 key 映射到原图尺寸，避免消费方再读一次 `.dim` 磁盘文件。
     */
    private val originalDimensions = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()

    /** 兼容旧 API：只要 bitmap 时使用。 */
    suspend fun load(
        context: Context,
        path: String,
        targetMaxPx: Int,
    ): Bitmap? = loadDecoded(context, path, targetMaxPx)?.bitmap

    /**
     * 新 API：同时返回 bitmap + 原图尺寸。
     *
     * 相比 [load]，调用方可复用解密过程中已经读出的 JPEG/PNG 头宽高，无需再次解密原图文件。
     */
    suspend fun loadDecoded(
        context: Context,
        path: String,
        targetMaxPx: Int,
    ): DecodedImage? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        val key = makeKey(file, targetMaxPx)

        memory.get(key)?.let { cached ->
            val dim = originalDimensions[key] ?: readDimFromDisk(context, key)
            val (ow, oh) = dim ?: (0 to 0)
            return@withContext DecodedImage(cached, ow, oh)
        }

        val diskFile = diskEntry(context, key)
        decodeFromDisk(context, diskFile)?.let { cached ->
            memory.put(key, cached)
            val dim = originalDimensions[key] ?: readDimFromDisk(context, key)?.also { originalDimensions[key] = it }
            val (ow, oh) = dim ?: (0 to 0)
            return@withContext DecodedImage(cached, ow, oh)
        }

        val decoded = runCatching {
            val bytes = VaultCipher.get(context).decryptToByteArray(file)
            sampleDecodeWithBounds(bytes, targetMaxPx)
        }.getOrNull() ?: return@withContext null

        // 写磁盘缓存（加密 JPG），并同步写入原图宽高 `.dim` 边信息；失败不影响返回。
        runCatching { writeDiskCache(context, diskFile, decoded.bitmap) }
        if (decoded.originalWidth > 0 && decoded.originalHeight > 0) {
            originalDimensions[key] = decoded.originalWidth to decoded.originalHeight
            runCatching { writeDimSidecar(context, key, decoded.originalWidth, decoded.originalHeight) }
        }
        memory.put(key, decoded.bitmap)
        decoded
    }

    fun invalidate(path: String) {
        // memory 的 key 带 mtime，文件覆写后自然失效；磁盘缓存同理依赖 sha256 key，无需显式清。
        // 这里只做 memory 的简单全扫描失败兜底，实际场景调用极少。
        val file = File(path)
        val snapshot = memory.snapshot().keys.toList()
        snapshot.forEach { k ->
            if (k.startsWith(file.absolutePath + "|")) memory.remove(k)
        }
    }

    private fun makeKey(file: File, targetMaxPx: Int): String {
        return file.absolutePath + "|" + file.length() + "|" + file.lastModified() + "|" + targetMaxPx
    }

    private fun diskEntry(context: Context, key: String): File {
        val dir = File(context.cacheDir, DISK_DIR).apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { b -> "%02x".format(b) }
        return File(dir, "$hash.enc")
    }

    private fun decodeFromDisk(context: Context, diskFile: File): Bitmap? {
        if (!diskFile.exists() || diskFile.length() == 0L) return null
        return runCatching {
            val bytes = VaultCipher.get(context).decryptToByteArray(diskFile)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun writeDiskCache(context: Context, diskFile: File, bitmap: Bitmap) {
        val jpg = ByteArrayOutputStream(64 * 1024).also {
            bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it)
        }.toByteArray()
        jpg.inputStream().use { input ->
            VaultCipher.get(context).encryptFile(input, diskFile)
        }
    }

    private fun sampleDecodeWithBounds(bytes: ByteArray, targetMaxPx: Int): DecodedImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var inSampleSize = 1
        val longest = if (w > h) w else h
        while (longest / inSampleSize > targetMaxPx) inSampleSize *= 2
        val options = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return DecodedImage(bitmap = bitmap, originalWidth = w, originalHeight = h)
    }

    private fun dimSidecar(context: Context, key: String): File {
        val dir = File(context.cacheDir, DISK_DIR).apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { b -> "%02x".format(b) }
        return File(dir, "$hash.dim")
    }

    private fun writeDimSidecar(context: Context, key: String, width: Int, height: Int) {
        dimSidecar(context, key).writeText("${width}x${height}")
    }

    private fun readDimFromDisk(context: Context, key: String): Pair<Int, Int>? {
        val f = dimSidecar(context, key)
        if (!f.exists() || f.length() == 0L) return null
        return runCatching {
            val parts = f.readText().trim().split('x')
            if (parts.size != 2) return@runCatching null
            val w = parts[0].toInt()
            val h = parts[1].toInt()
            if (w > 0 && h > 0) w to h else null
        }.getOrNull()
    }
}
