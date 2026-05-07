package com.xpx.vault.ui.vault

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import com.xpx.vault.data.crypto.VaultCipher
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Vault 内容全部以 AES-256-CBC 密文形式落盘；IV 前置 16B + PKCS7 密文。
// 读/写路径统一通过 [VaultCipher] 完成；备份与恢复链路也保持对称加密。
// 详见 core/data/.../crypto/VaultCipher.kt。
private const val ROOT_DIR = "vault_albums"
private const val LEGACY_DIR = "vault_album"
private const val TRASH_DIR = "vault_trash"
private const val CAMERA_TMP_DIR = "camera_tmp"
private const val TRASH_RETAIN_MS: Long = 30L * 24 * 60 * 60 * 1000
const val DEFAULT_ALBUM_NAME = "Default"

data class VaultPhoto(
    val albumName: String,
    val path: String,
    val name: String,
    val modifiedAtMs: Long,
)

data class VaultTrashItem(
    val path: String,
    val name: String,
    val trashedAtMs: Long,
    val albumName: String?,
)

data class VaultAlbum(
    val name: String,
    val coverPath: String?,
    val photoCount: Int,
)

data class VaultSnapshot(
    val albums: List<VaultAlbum>,
    val recentPhotos: List<VaultPhoto>,
    val totalCount: Int,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
)

private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "avi", "3gp", "m4v", "flv")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")

fun isVaultVideo(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in VIDEO_EXTENSIONS
}

fun isVaultImage(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
}

enum class VaultImportResult {
    ADDED,
    DUPLICATE,
    FAILED,
}

object VaultStore {
    @Volatile
    private var cachedSnapshot: VaultSnapshot? = null
    private val cachedAlbumPhotos: MutableMap<String, List<VaultPhoto>> = mutableMapOf()

    fun peekCachedSnapshot(): VaultSnapshot? = cachedSnapshot
    fun peekCachedAlbumPhotos(albumName: String): List<VaultPhoto>? = cachedAlbumPhotos[albumName]

    suspend fun loadSnapshot(context: Context, recentLimit: Int = 60): VaultSnapshot = withContext(Dispatchers.IO) {
        ensureInit(context)
        val albums = listAlbumsInternal(context)
        val allPhotos = listAllPhotos(context)
        val recentPhotos = allPhotos.sortedByDescending { it.modifiedAtMs }.take(recentLimit)
        val imageCount = allPhotos.count { isVaultImage(it.path) }
        val videoCount = allPhotos.count { isVaultVideo(it.path) }
        val snapshot = VaultSnapshot(
            albums = albums,
            recentPhotos = recentPhotos,
            totalCount = albums.sumOf { it.photoCount },
            imageCount = imageCount,
            videoCount = videoCount,
        )
        cachedSnapshot = snapshot
        snapshot
    }

    suspend fun ensureInit(context: Context) = withContext(Dispatchers.IO) {
        val root = rootDir(context)
        if (!root.exists()) root.mkdirs()
        val defaultAlbum = File(root, DEFAULT_ALBUM_NAME)
        if (!defaultAlbum.exists()) defaultAlbum.mkdirs()
        migrateLegacyIfNeeded(context, defaultAlbum)
    }

    suspend fun createAlbum(context: Context, albumName: String): String = withContext(Dispatchers.IO) {
        ensureInit(context)
        val safe = sanitizeAlbumName(albumName)
        val dir = File(rootDir(context), safe)
        if (!dir.exists()) dir.mkdirs()
        safe
    }

    suspend fun listAlbums(context: Context): List<VaultAlbum> = withContext(Dispatchers.IO) {
        ensureInit(context)
        listAlbumsInternal(context)
    }

    suspend fun listRecentPhotos(context: Context, limit: Int = 60): List<VaultPhoto> = withContext(Dispatchers.IO) {
        ensureInit(context)
        listAllPhotos(context).sortedByDescending { it.modifiedAtMs }.take(limit)
    }

    suspend fun listPhotosInAlbum(context: Context, albumName: String): List<VaultPhoto> = withContext(Dispatchers.IO) {
        ensureInit(context)
        val album = File(rootDir(context), sanitizeAlbumName(albumName))
        if (!album.exists()) {
            cachedAlbumPhotos[albumName] = emptyList()
            return@withContext emptyList()
        }
        val photos = album.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                VaultPhoto(
                    albumName = album.name,
                    path = file.absolutePath,
                    name = file.nameWithoutExtension,
                    modifiedAtMs = file.lastModified(),
                )
            }
            .orEmpty()
        cachedAlbumPhotos[albumName] = photos
        photos
    }

    suspend fun searchPhotos(context: Context, query: String): List<VaultPhoto> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        listAllPhotos(context).filter { it.name.contains(query, ignoreCase = true) }
            .sortedByDescending { it.modifiedAtMs }
    }

    suspend fun totalPhotos(context: Context): Int = withContext(Dispatchers.IO) {
        ensureInit(context)
        rootDir(context).walkTopDown().count { it.isFile }
    }

    /**
     * 将一张内存中的 Bitmap（隐私脱敏后的预览图）压 JPEG 加密落盘到指定相册。
     *
     * 与 [importFromPicker] 的 SHA-256 去重不同，这里每次都落新文件：
     * 同一张原图用户可能尝试不同样式（马赛克 / 高斯模糊 / 黑条）分别保存，
     * 各自为独立资产。文件名加毫秒时戳保证唯一。
     *
     * @return 密文绝对路径；失败返回 null
     */
    suspend fun importRedactedBitmap(
        context: Context,
        bitmap: Bitmap,
        baseName: String,
        albumName: String = DEFAULT_ALBUM_NAME,
        quality: Int = 95,
    ): String? = withContext(Dispatchers.IO) {
        ensureInit(context)
        val album = File(rootDir(context), sanitizeAlbumName(albumName)).apply { mkdirs() }
        val safeBase = baseName.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "redacted" }
            .takeLast(24)
        val finalFile = File(album, "redacted_${safeBase}_${System.currentTimeMillis()}.jpg")
        runCatching {
            val bytes = java.io.ByteArrayOutputStream().use { bos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                bos.toByteArray()
            }
            java.io.ByteArrayInputStream(bytes).use { plain ->
                VaultCipher.get(context).encryptFile(plain, finalFile)
            }
            finalFile.absolutePath
        }.getOrElse {
            if (finalFile.exists()) finalFile.delete()
            null
        }
    }

    suspend fun importFromPicker(
        context: Context,
        uri: Uri,
        albumName: String = DEFAULT_ALBUM_NAME,
    ): VaultImportResult = withContext(Dispatchers.IO) {
        ensureInit(context)
        val album = File(rootDir(context), sanitizeAlbumName(albumName))
        if (!album.exists()) album.mkdirs()
        val extension = resolveExtension(context, uri)
        // 1) 先把源 URI 拉到明文 temp 文件；同时流式算 sha256 供 dedupe 使用。
        val tempPlain = File(album, "tmp_plain_${System.currentTimeMillis()}.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext VaultImportResult.FAILED
        input.use { stream ->
            tempPlain.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
            }
        }
        val hash = digest.digest().joinToString("") { b -> "%02x".format(b) }
        val finalFile = File(album, "asset_$hash.$extension")
        if (finalFile.exists()) {
            tempPlain.delete()
            return@withContext VaultImportResult.DUPLICATE
        }
        // 2) 把明文 temp 加密到 finalFile，再删除 temp；整个流程保证 vault_albums/ 下只会出现密文。
        runCatching {
            tempPlain.inputStream().buffered().use { plain ->
                VaultCipher.get(context).encryptFile(plain, finalFile)
            }
        }.onFailure {
            tempPlain.delete()
            if (finalFile.exists()) finalFile.delete()
            return@withContext VaultImportResult.FAILED
        }
        tempPlain.delete()
        VaultImportResult.ADDED
    }

    /**
     * 为相机拍照/录像预留落地位置。
     *
     * 注意：由于 CameraX 的 `ImageCapture.takePicture` 与 `VideoCapture` 只能写 `File`，
     * 这里返回的是 **明文临时文件** 路径（位于 `filesDir/camera_tmp/`），
     * 由调用方在拍摄完成后调用 [finalizeCameraCapture] 把该临时文件加密落盘到 vault。
     */
    suspend fun reserveCameraTarget(
        context: Context,
        albumName: String = DEFAULT_ALBUM_NAME,
        extension: String = "jpg",
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        val safeExt = extension.trim().removePrefix(".").ifBlank { "bin" }
        val tmpRoot = File(context.filesDir, CAMERA_TMP_DIR).apply { mkdirs() }
        // 把目标 album 编码进 temp 文件名，供 finalize 阶段恢复。
        val album = sanitizeAlbumName(albumName)
        File(tmpRoot, "cam_${album}_${System.currentTimeMillis()}.$safeExt")
    }

    /**
     * 把相机产生的明文临时文件加密入库。
     *
     * @param context Android 上下文
     * @param tempFile 由 [reserveCameraTarget] 返回的明文临时文件（函数结束后将被删除）
     * @return 入库成功后的密文文件绝对路径；失败返回 null
     */
    suspend fun finalizeCameraCapture(
        context: Context,
        tempFile: File,
    ): String? = withContext(Dispatchers.IO) {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            return@withContext null
        }
        val album = parseAlbumFromCameraTempName(tempFile.name) ?: DEFAULT_ALBUM_NAME
        val albumDir = File(rootDir(context), sanitizeAlbumName(album)).apply { mkdirs() }
        val ext = tempFile.extension.ifBlank { "bin" }
        val finalFile = File(albumDir, "camera_${System.currentTimeMillis()}.$ext")
        val ok = runCatching {
            tempFile.inputStream().buffered().use { plain ->
                VaultCipher.get(context).encryptFile(plain, finalFile)
            }
            true
        }.getOrElse { false }
        tempFile.delete()
        if (ok) finalFile.absolutePath else null
    }

    /**
     * 将保险库内全部照片/视频移入临时垃圾桶（与逐张删除相同逻辑），用于「清空私有相册」等危险操作。
     * @return 成功移入垃圾桶的文件数量
     */
    suspend fun moveAllVaultPhotosToTrash(context: Context): Int = withContext(Dispatchers.IO) {
        ensureInit(context)
        val paths = listAllPhotos(context).map { it.path }
        var moved = 0
        for (path in paths) {
            if (deletePhoto(context, path)) moved++
        }
        cachedSnapshot = null
        cachedAlbumPhotos.clear()
        moved
    }

    suspend fun deletePhoto(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext false
        val trashRoot = File(context.filesDir, TRASH_DIR)
        val albumName = file.parentFile?.name
        val targetDir = if (!albumName.isNullOrBlank()) {
            File(trashRoot, sanitizeAlbumName(albumName))
        } else {
            trashRoot
        }
        if (!targetDir.exists()) targetDir.mkdirs()
        val dest = File(targetDir, file.name)
        if (dest.exists()) dest.delete()
        val ok = file.renameTo(dest)
        if (ok) dest.setLastModified(System.currentTimeMillis())
        ok
    }

    suspend fun listTrashItems(context: Context): List<VaultTrashItem> = withContext(Dispatchers.IO) {
        val trashRoot = File(context.filesDir, TRASH_DIR)
        if (!trashRoot.exists() || !trashRoot.isDirectory) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val items = mutableListOf<VaultTrashItem>()
        trashRoot.listFiles()?.forEach { entry ->
            when {
                entry.isDirectory -> {
                    entry.listFiles()?.filter { it.isFile }?.forEach { file ->
                        if (now - file.lastModified() > TRASH_RETAIN_MS) {
                            file.delete()
                        } else {
                            items.add(
                                VaultTrashItem(
                                    path = file.absolutePath,
                                    name = file.nameWithoutExtension,
                                    trashedAtMs = file.lastModified(),
                                    albumName = entry.name,
                                )
                            )
                        }
                    }
                    if (entry.listFiles()?.isEmpty() == true) entry.delete()
                }
                entry.isFile -> {
                    if (now - entry.lastModified() > TRASH_RETAIN_MS) {
                        entry.delete()
                    } else {
                        items.add(
                            VaultTrashItem(
                                path = entry.absolutePath,
                                name = entry.nameWithoutExtension,
                                trashedAtMs = entry.lastModified(),
                                albumName = null,
                            )
                        )
                    }
                }
            }
        }
        items.sortedByDescending { it.trashedAtMs }
    }

    suspend fun restoreFromTrash(context: Context, path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        val trashRoot = File(context.filesDir, TRASH_DIR)
        val parent = file.parentFile
        val albumName = if (parent != null && parent.absolutePath != trashRoot.absolutePath) {
            parent.name
        } else {
            DEFAULT_ALBUM_NAME
        }
        ensureInit(context)
        val safeAlbum = sanitizeAlbumName(albumName)
        val albumDir = File(rootDir(context), safeAlbum)
        if (!albumDir.exists()) albumDir.mkdirs()
        var dest = File(albumDir, file.name)
        if (dest.exists()) {
            val base = file.nameWithoutExtension
            val ext = file.extension
            val suffix = if (ext.isBlank()) "" else ".$ext"
            dest = File(albumDir, "${base}_restored_${System.currentTimeMillis()}$suffix")
        }
        val ok = file.renameTo(dest)
        if (ok) {
            dest.setLastModified(System.currentTimeMillis())
            if (parent != null && parent.absolutePath != trashRoot.absolutePath) {
                if (parent.listFiles()?.isEmpty() == true) parent.delete()
            }
        }
        if (ok) safeAlbum else null
    }

    suspend fun purgeFromTrash(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext false
        val parent = file.parentFile
        val deleted = file.delete()
        if (deleted && parent != null && parent.name != TRASH_DIR) {
            if (parent.listFiles()?.isEmpty() == true) parent.delete()
        }
        deleted
    }

    fun trashRetainDurationMs(): Long = TRASH_RETAIN_MS

    private fun listAllPhotos(context: Context): List<VaultPhoto> {
        val root = rootDir(context)
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { album ->
                album.listFiles()
                    ?.filter { it.isFile }
                    ?.map { file ->
                        VaultPhoto(
                            albumName = album.name,
                            path = file.absolutePath,
                            name = file.nameWithoutExtension,
                            modifiedAtMs = file.lastModified(),
                        )
                    }
                    .orEmpty()
            }
            .orEmpty()
    }

    private fun listAlbumsInternal(context: Context): List<VaultAlbum> {
        val root = rootDir(context)
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.map { folder ->
                val photos = folder.listFiles()
                    ?.filter { it.isFile }
                    ?.sortedByDescending { it.lastModified() }
                    .orEmpty()
                VaultAlbum(
                    name = folder.name,
                    coverPath = photos.firstOrNull()?.absolutePath,
                    photoCount = photos.size,
                )
            }
            .orEmpty()
            .sortedWith(compareBy<VaultAlbum> { it.name != DEFAULT_ALBUM_NAME }.thenBy { it.name.lowercase() })
    }

    private fun rootDir(context: Context): File = File(context.filesDir, ROOT_DIR)

    /** 从 `cam_<album>_<ts>.<ext>` 格式文件名中恢复 album 名称。 */
    private fun parseAlbumFromCameraTempName(name: String): String? {
        if (!name.startsWith("cam_")) return null
        val stripped = name.removePrefix("cam_")
        // 移除 `.<ext>`
        val noExt = stripped.substringBeforeLast('.', stripped)
        // 末尾的 `_<ts>`（纯数字）
        val lastUnderscore = noExt.lastIndexOf('_')
        if (lastUnderscore <= 0) return null
        val ts = noExt.substring(lastUnderscore + 1)
        if (ts.any { !it.isDigit() }) return null
        return noExt.substring(0, lastUnderscore).ifBlank { null }
    }

    private fun sanitizeAlbumName(raw: String): String {
        val trimmed = raw.trim().ifBlank { DEFAULT_ALBUM_NAME }
        return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40)
    }

    private fun resolveExtension(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        val fromMime = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (!fromMime.isNullOrBlank()) return fromMime.lowercase()

        val path = uri.lastPathSegment.orEmpty()
        val fromPath = path.substringAfterLast('.', missingDelimiterValue = "").trim()
        if (fromPath.isNotBlank() && fromPath.none { it == '/' || it == '\\' }) {
            return fromPath.lowercase()
        }
        return "bin"
    }

    private fun migrateLegacyIfNeeded(context: Context, defaultAlbum: File) {
        val legacy = File(context.filesDir, LEGACY_DIR)
        if (!legacy.exists() || !legacy.isDirectory) return
        legacy.listFiles()?.forEach { old ->
            if (old.isFile) {
                val target = File(defaultAlbum, old.name)
                if (!target.exists()) old.copyTo(target)
                old.delete()
            }
        }
        legacy.delete()
    }
}

