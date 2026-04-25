package com.photovault.app.ui.vault

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ROOT_DIR = "vault_albums"
private const val LEGACY_DIR = "vault_album"
const val DEFAULT_ALBUM_NAME = "Default"

data class VaultPhoto(
    val albumName: String,
    val path: String,
    val name: String,
    val modifiedAtMs: Long,
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
)

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
        val recentPhotos = listAllPhotos(context).sortedByDescending { it.modifiedAtMs }.take(recentLimit)
        val snapshot = VaultSnapshot(
            albums = albums,
            recentPhotos = recentPhotos,
            totalCount = albums.sumOf { it.photoCount },
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

    suspend fun importFromPicker(
        context: Context,
        uri: Uri,
        albumName: String = DEFAULT_ALBUM_NAME,
    ): VaultImportResult = withContext(Dispatchers.IO) {
        ensureInit(context)
        val album = File(rootDir(context), sanitizeAlbumName(albumName))
        if (!album.exists()) album.mkdirs()
        val extension = resolveExtension(context, uri)
        val temp = File(album, "tmp_${System.currentTimeMillis()}.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext VaultImportResult.FAILED
        input.use { stream ->
            temp.outputStream().use { output ->
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
            temp.delete()
            return@withContext VaultImportResult.DUPLICATE
        }
        val renamed = temp.renameTo(finalFile)
        if (!renamed) {
            temp.copyTo(finalFile, overwrite = true)
            temp.delete()
        }
        VaultImportResult.ADDED
    }

    suspend fun reserveCameraTarget(
        context: Context,
        albumName: String = DEFAULT_ALBUM_NAME,
        extension: String = "jpg",
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        val album = File(rootDir(context), sanitizeAlbumName(albumName))
        if (!album.exists()) album.mkdirs()
        val safeExt = extension.trim().removePrefix(".").ifBlank { "bin" }
        File(album, "camera_${System.currentTimeMillis()}.$safeExt")
    }

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

