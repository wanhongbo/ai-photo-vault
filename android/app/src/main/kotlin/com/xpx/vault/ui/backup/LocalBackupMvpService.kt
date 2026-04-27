package com.xpx.vault.ui.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.xpx.vault.AppLogger
import com.xpx.vault.data.crypto.AesCbcEngine
import com.xpx.vault.data.crypto.PasswordHasher
import com.xpx.vault.data.db.PhotoVaultDatabase
import com.xpx.vault.data.db.entity.BackupRecordEntity
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val VAULT_ROOT_DIR = "vault_albums"
private const val BACKUP_ROOT_DIR = "vault_backups_mvp"
private const val INDEX_FILE_NAME = "index.json"
private const val MANIFEST_FILE_NAME = "manifest.json.enc"
private const val MAX_BACKUP_VERSIONS = 2
private const val VOLUME_MAX_BYTES = 32L * 1024L * 1024L
private const val STREAM_CHUNK_SIZE = 1024 * 1024
private const val BACKUP_CRYPTO_PREFS = "backup_crypto_prefs"
private const val BACKUP_CRYPTO_SEED = "backup_crypto_seed"

object LocalBackupMvpService {
    private fun engine(context: Context): AesCbcEngine {
        val prefs = context.getSharedPreferences(BACKUP_CRYPTO_PREFS, Context.MODE_PRIVATE)
        val seed = prefs.getString(BACKUP_CRYPTO_SEED, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(BACKUP_CRYPTO_SEED, it).apply()
            AppLogger.d(TAG, "backup crypto seed initialized")
        }
        val keyBytes = PasswordHasher.sha256HexOfUtf8(seed)
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        return AesCbcEngine(SecretKeySpec(keyBytes, "AES"))
    }

    suspend fun createBackup(context: Context): BackupExecutionResult = withContext(Dispatchers.IO) {
        runCatching {
            val vaultRoot = File(context.filesDir, VAULT_ROOT_DIR)
            if (!vaultRoot.exists()) return@withContext BackupExecutionResult.failure("保险箱目录不存在，无法创建备份。")
            val assets = scanVaultAssets(vaultRoot)
            val now = System.currentTimeMillis()
            val backupId = "bkp_${now}_${UUID.randomUUID().toString().take(8)}"
            AppLogger.d(TAG, "createBackup start id=${shortId(backupId)} assets=${assets.size}")
            val backupFolder = File(backupRoot(context), backupId).apply { mkdirs() }

            val previous = loadLatestBackupMeta(context)
            val previousManifest = previous?.let { readManifest(context, File(backupRoot(context), it.folderName)) }
            val previousHashes = previousManifest?.assets?.associateBy { it.relativePath }.orEmpty()
            val kind = if (previousManifest == null) BackupKind.FULL else BackupKind.INCREMENTAL
            val payloadAssets = (if (kind == BackupKind.FULL) assets else {
                assets.filter { previousHashes[it.relativePath]?.sha256Hex != it.sha256Hex }
            }).toMutableList()
            AppLogger.d(
                TAG,
                "createBackup mode=${kind.name} delta=${payloadAssets.size} total=${assets.size} prev=${previous?.backupId?.let(::shortId) ?: "none"}",
            )

            val volumes = writeVolumes(context, backupFolder, payloadAssets, vaultRoot)
            val manifest = BackupManifest(
                backupId = backupId,
                createdAtMs = now,
                kind = kind,
                baseBackupId = previousManifest?.backupId,
                assets = payloadAssets,
                volumes = volumes,
                totalAssetCount = assets.size,
            )
            writeManifest(context, backupFolder, manifest)
            updateIndexAfterCreate(context, manifest, backupFolder)
            pruneOldBackups(context)
            upsertBackupRecords(context)

            val totalSize = volumes.sumOf { it.encryptedSizeBytes }
            val result = BackupExecutionResult.success(
                backupId = backupId,
                backupKind = kind,
                outputPath = backupFolder.absolutePath,
                outputSizeBytes = totalSize,
                assetCount = payloadAssets.size,
                totalAssetCount = assets.size,
                volumeCount = volumes.size,
            )
            BackupRuntimeState.lastBackupResult = result
            AppLogger.d(
                TAG,
                "createBackup success id=${shortId(backupId)} volumes=${volumes.size} output=${backupFolder.name}",
            )
            result
        }.getOrElse {
            AppLogger.e(TAG, "createBackup failed: ${it.javaClass.simpleName} ${it.message}", it)
            BackupExecutionResult.failure("备份失败：${it.message ?: "未知异常"}")
        }
    }

    suspend fun exportBackupsToUri(context: Context, uri: Uri): BackupExecutionResult = withContext(Dispatchers.IO) {
        val latest = loadLatestBackupMeta(context) ?: return@withContext BackupExecutionResult.failure("暂无可导出的备份。")
        AppLogger.d(TAG, "exportBackups start id=${shortId(latest.backupId)} uri=${uri.scheme ?: "unknown"}")
        val root = backupRoot(context)
        val output = context.contentResolver.openOutputStream(uri)
            ?: return@withContext BackupExecutionResult.failure("无法写入所选导出位置。")
        output.use { out ->
            ZipOutputStream(out).use { zip ->
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    val rel = file.relativeTo(root).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(rel))
                    file.inputStream().use { input -> input.copyTo(zip, STREAM_CHUNK_SIZE) }
                    zip.closeEntry()
                }
            }
        }
        val verify = validateArchiveForExport(context, uri)
        if (!verify.success) {
            AppLogger.e(TAG, "exportBackups verify failed: ${verify.message}")
            return@withContext BackupExecutionResult.failure(verify.message)
        }
        BackupExecutionResult.success(
            backupId = latest.backupId,
            backupKind = latest.kind,
            outputPath = uri.toString(),
            outputSizeBytes = 0L,
            assetCount = 0,
            totalAssetCount = 0,
            volumeCount = 0,
        )
    }

    suspend fun importBackupsFromUri(context: Context, uri: Uri): RestoreExecutionResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "importBackups start uri=${uri.scheme ?: "unknown"}")
        val precheck = validateBackupArchive(context, uri)
        if (!precheck.success) {
            AppLogger.e(TAG, "importBackups precheck failed: ${precheck.message}")
            return@withContext RestoreExecutionResult.failure(precheck.message)
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: return@withContext RestoreExecutionResult.failure("无法读取所选备份文件。")
        val root = backupRoot(context)
        root.deleteRecursively()
        root.mkdirs()
        input.use { ins ->
            ZipInputStream(ins).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name.startsWith("/") || entry.name.contains("..")) {
                        throw IllegalArgumentException("备份包存在非法路径，已拒绝导入。")
                    }
                    val target = File(root, entry.name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output, STREAM_CHUNK_SIZE) }
                    zip.closeEntry()
                }
            }
        }
        AppLogger.d(TAG, "importBackups extracted root=${root.name}")
        upsertBackupRecords(context)
        val result = restoreLatest(context)
        if (!result.success) {
            AppLogger.e(TAG, "importBackups restore failed: ${result.message}")
        } else {
            AppLogger.d(TAG, "importBackups restore success from=${shortId(result.sourceBackupId)}")
        }
        result
    }

    private fun validateBackupArchive(context: Context, uri: Uri): ValidationResult {
        val input = context.contentResolver.openInputStream(uri)
            ?: return ValidationResult(false, "无法读取备份文件，请重新选择。")
        var hasIndex = false
        var hasManifest = false
        return runCatching {
            input.use { ins ->
                ZipInputStream(ins).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name == INDEX_FILE_NAME) hasIndex = true
                        if (entry.name.endsWith("/$MANIFEST_FILE_NAME")) hasManifest = true
                        if (entry.name.startsWith("/") || entry.name.contains("..")) {
                            throw IllegalArgumentException("备份包包含非法路径。")
                        }
                        zip.closeEntry()
                    }
                }
            }
            when {
                !hasIndex -> ValidationResult(false, "备份包缺少索引文件，无法导入。")
                !hasManifest -> ValidationResult(false, "备份包缺少清单文件，无法导入。")
                else -> ValidationResult(true, "ok")
            }
        }.getOrElse {
            val raw = it.message.orEmpty()
            val normalized = if (
                raw.contains("invalid literal/length code", ignoreCase = true) ||
                raw.contains("zip", ignoreCase = true)
            ) {
                "备份包已损坏或格式不正确，请重新选择有效的备份文件。"
            } else {
                "备份包校验失败：${it.message ?: "未知错误"}"
            }
            ValidationResult(false, normalized)
        }
    }

    private fun validateArchiveForExport(context: Context, uri: Uri): ValidationResult {
        val input = context.contentResolver.openInputStream(uri)
            ?: return ValidationResult(false, "导出文件写入失败，请重试。")
        var hasIndex = false
        var hasManifest = false
        return runCatching {
            input.use { ins ->
                ZipInputStream(ins).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory) {
                            if (entry.name == INDEX_FILE_NAME) hasIndex = true
                            if (entry.name.endsWith("/$MANIFEST_FILE_NAME")) hasManifest = true
                            // Consume full entry to verify archive integrity.
                            val sink = ByteArray(STREAM_CHUNK_SIZE)
                            while (zip.read(sink) > 0) { /* no-op */ }
                        }
                        zip.closeEntry()
                    }
                }
            }
            when {
                !hasIndex -> ValidationResult(false, "导出失败：备份索引缺失，请重试。")
                !hasManifest -> ValidationResult(false, "导出失败：备份清单缺失，请重试。")
                else -> ValidationResult(true, "ok")
            }
        }.getOrElse {
            val msg = when (it) {
                is ZipException -> "导出失败：备份文件损坏，请重试。"
                else -> "导出失败：${it.message ?: "未知错误"}"
            }
            ValidationResult(false, msg)
        }
    }

    suspend fun restoreLatest(context: Context): RestoreExecutionResult = withContext(Dispatchers.IO) {
        val backupMeta = loadLatestBackupMeta(context)
            ?: return@withContext RestoreExecutionResult.failure("暂无可恢复的备份文件。")
        AppLogger.d(TAG, "restoreLatest start id=${shortId(backupMeta.backupId)}")
        val backupFolder = File(backupRoot(context), backupMeta.folderName)
        val currentManifest = readManifest(context, backupFolder)
            ?: return@withContext RestoreExecutionResult.failure("备份清单读取失败。")

        val manifestChain = buildManifestChain(context, currentManifest)
        val mergedAssets = linkedMapOf<String, BackupAsset>()
        manifestChain.forEach { manifest -> manifest.assets.forEach { mergedAssets[it.relativePath] = it } }
        val assetsToRestore = mergedAssets.values.toList()
        val vaultRoot = File(context.filesDir, VAULT_ROOT_DIR).apply { mkdirs() }

        var restored = 0
        var skipped = 0
        var failed = 0
        assetsToRestore.forEach { asset ->
            runCatching {
                val target = File(vaultRoot, asset.relativePath)
                if (target.exists()) {
                    skipped += 1
                    return@runCatching
                }
                target.parentFile?.mkdirs()
                val volumeFile = File(backupRoot(context), "${asset.backupId}/${asset.volumeFileName}")
                RandomAccessFile(volumeFile, "r").use { raf ->
                    raf.seek(asset.offsetInVolume)
                    var remaining = asset.encryptedLengthInVolume
                    val digestBuffer = ByteArray(STREAM_CHUNK_SIZE)
                    target.outputStream().use { out ->
                        while (remaining > 0L) {
                            if (remaining < 4L) throw IllegalStateException("invalid encrypted frame")
                            val encLen = raf.readInt()
                            if (encLen <= 0) throw IllegalStateException("invalid encrypted block length")
                            val encryptedChunk = ByteArray(encLen)
                            raf.readFully(encryptedChunk)
                            val plainChunk = engine(context).decrypt(encryptedChunk)
                            out.write(plainChunk)
                            remaining -= (4L + encLen.toLong())
                        }
                    }
                    target.inputStream().use { stream ->
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        while (true) {
                            val read = stream.read(digestBuffer)
                            if (read <= 0) break
                            digest.update(digestBuffer, 0, read)
                        }
                        val hash = digest.digest().joinToString("") { b -> "%02x".format(b) }
                        if (hash != asset.sha256Hex) throw IllegalStateException("asset checksum mismatch")
                    }
                }
                restored += 1
            }.onFailure { failed += 1 }
        }
        val result = RestoreExecutionResult.success(restored, skipped, failed, currentManifest.backupId)
        BackupRuntimeState.lastRestoreResult = result
        AppLogger.d(
            TAG,
            "restoreLatest done id=${shortId(currentManifest.backupId)} restored=$restored skipped=$skipped failed=$failed",
        )
        result
    }

    private fun writeVolumes(
        context: Context,
        backupFolder: File,
        assets: MutableList<BackupAsset>,
        vaultRoot: File,
    ): List<BackupVolume> {
        if (assets.isEmpty()) return emptyList()
        val volumes = mutableListOf<BackupVolume>()
        var volumeIndex = -1
        var out: DataOutputStream? = null
        var currentFile: File? = null
        var currentSize = 0L
        var currentDigest: java.security.MessageDigest? = null

        fun closeCurrentVolumeIfNeeded() {
            if (out != null && currentFile != null && currentDigest != null) {
                out!!.flush()
                out!!.close()
                val encryptedSize = currentFile!!.length()
                val checksum = currentDigest!!.digest().joinToString("") { b -> "%02x".format(b) }
                volumes += BackupVolume(
                    fileName = currentFile!!.name,
                    encryptedSizeBytes = encryptedSize,
                    checksumHex = checksum,
                )
            }
            out = null
            currentFile = null
            currentDigest = null
            currentSize = 0L
        }

        fun openNextVolume() {
            closeCurrentVolumeIfNeeded()
            volumeIndex += 1
            currentFile = File(backupFolder, "volume_${volumeIndex.toString().padStart(3, '0')}.bin.enc")
            out = DataOutputStream(currentFile!!.outputStream().buffered())
            currentSize = 0L
            currentDigest = java.security.MessageDigest.getInstance("SHA-256")
        }

        openNextVolume()
        assets.forEachIndexed { idx, asset ->
            val source = File(vaultRoot, asset.relativePath)
            val estimated = source.length()
            if (currentSize > 0 && currentSize + estimated > VOLUME_MAX_BYTES) openNextVolume()

            val startOffset = currentSize
            source.inputStream().buffered().use { input ->
                val buffer = ByteArray(STREAM_CHUNK_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                    val encryptedChunk = engine(context).encrypt(chunk)
                    out!!.writeInt(encryptedChunk.size)
                    out!!.write(encryptedChunk)
                    currentDigest!!.update(intToBytes(encryptedChunk.size))
                    currentDigest!!.update(encryptedChunk)
                    currentSize += 4L + encryptedChunk.size.toLong()
                }
            }
            assets[idx] = asset.copy(
                backupId = backupFolder.name,
                volumeFileName = currentFile!!.name,
                offsetInVolume = startOffset,
                encryptedLengthInVolume = currentSize - startOffset,
            )
            AppLogger.d(
                TAG,
                "writeVolumes asset=${asset.relativePath.substringAfterLast('/')} size=${asset.sizeBytes} volume=${currentFile!!.name}",
            )
        }
        closeCurrentVolumeIfNeeded()
        AppLogger.d(TAG, "writeVolumes done volumes=${volumes.size}")
        return volumes
    }

    private fun writeManifest(context: Context, folder: File, manifest: BackupManifest) {
        val encrypted = engine(context).encrypt(manifest.toJson().toByteArray(Charsets.UTF_8))
        File(folder, MANIFEST_FILE_NAME).writeBytes(encrypted)
    }

    private fun readManifest(context: Context, folder: File): BackupManifest? {
        val encrypted = File(folder, MANIFEST_FILE_NAME)
        if (!encrypted.exists()) return null
        return runCatching {
            val plain = engine(context).decrypt(encrypted.readBytes())
            BackupManifest.fromJson(String(plain, Charsets.UTF_8))
        }.getOrNull()
    }

    private fun scanVaultAssets(vaultRoot: File): List<BackupAsset> {
        return vaultRoot.walkTopDown().filter { it.isFile }.map { file ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buf = ByteArray(STREAM_CHUNK_SIZE)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            BackupAsset(
                backupId = "",
                relativePath = file.relativeTo(vaultRoot).invariantSeparatorsPath,
                sizeBytes = file.length(),
                modifiedAtMs = file.lastModified(),
                sha256Hex = digest.digest().joinToString("") { b -> "%02x".format(b) },
                volumeFileName = "",
                offsetInVolume = 0L,
                encryptedLengthInVolume = 0L,
            )
        }.toList()
    }

    private fun buildManifestChain(context: Context, target: BackupManifest): List<BackupManifest> {
        val byId = loadIndex(context).associateBy { it.backupId }
        val ordered = mutableListOf<BackupManifest>()
        var cursor: BackupManifest? = target
        while (cursor != null) {
            ordered.add(cursor)
            cursor = cursor.baseBackupId?.let { parentId ->
                val meta = byId[parentId] ?: return@let null
                readManifest(context, File(backupRoot(context), meta.folderName))
            }
        }
        return ordered.reversed()
    }

    private suspend fun upsertBackupRecords(context: Context) {
        val db = Room.databaseBuilder(
            context.applicationContext,
            PhotoVaultDatabase::class.java,
            PhotoVaultDatabase.NAME,
        ).build()
        runCatching {
            val dao = db.backupRecordDao()
            val metas = loadIndex(context).sortedByDescending { it.createdAtMs }.take(MAX_BACKUP_VERSIONS)
            metas.forEach { meta ->
                val folder = File(backupRoot(context), meta.folderName)
                dao.insert(
                    BackupRecordEntity(
                        filePath = folder.absolutePath,
                        createdAtEpochMs = meta.createdAtMs,
                        version = if (meta.kind == BackupKind.FULL) 1 else 2,
                        checksumHex = null,
                    ),
                )
            }
            val latest = dao.latest(200)
            if (latest.size > MAX_BACKUP_VERSIONS) {
                dao.deleteByIds(latest.drop(MAX_BACKUP_VERSIONS).map { it.id })
            }
            AppLogger.d(TAG, "backupRecord synced count=${metas.size}")
        }.onFailure {
            AppLogger.e(TAG, "backupRecord sync failed: ${it.message}", it)
        }
        db.close()
    }

    private fun updateIndexAfterCreate(context: Context, manifest: BackupManifest, folder: File) {
        val current = loadIndex(context).toMutableList()
        current.add(
            BackupIndexMeta(
                backupId = manifest.backupId,
                folderName = folder.name,
                createdAtMs = manifest.createdAtMs,
                kind = manifest.kind,
                baseBackupId = manifest.baseBackupId,
            ),
        )
        saveIndex(context, current)
    }

    private fun pruneOldBackups(context: Context) {
        val all = loadIndex(context).sortedByDescending { it.createdAtMs }
        if (all.size <= MAX_BACKUP_VERSIONS) return
        val keep = all.take(MAX_BACKUP_VERSIONS)
        all.drop(MAX_BACKUP_VERSIONS).forEach { File(backupRoot(context), it.folderName).deleteRecursively() }
        saveIndex(context, keep)
    }

    private fun loadLatestBackupMeta(context: Context): BackupIndexMeta? =
        loadIndex(context).maxByOrNull { it.createdAtMs }

    private fun loadIndex(context: Context): List<BackupIndexMeta> {
        val file = File(backupRoot(context), INDEX_FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    add(
                        BackupIndexMeta(
                            backupId = item.getString("backupId"),
                            folderName = item.getString("folderName"),
                            createdAtMs = item.getLong("createdAtMs"),
                            kind = BackupKind.valueOf(item.getString("kind")),
                            baseBackupId = item.optString("baseBackupId").ifBlank { null },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveIndex(context: Context, metas: List<BackupIndexMeta>) {
        val arr = JSONArray()
        metas.sortedByDescending { it.createdAtMs }.forEach { meta ->
            arr.put(
                JSONObject()
                    .put("backupId", meta.backupId)
                    .put("folderName", meta.folderName)
                    .put("createdAtMs", meta.createdAtMs)
                    .put("kind", meta.kind.name)
                    .put("baseBackupId", meta.baseBackupId ?: ""),
            )
        }
        backupRoot(context).mkdirs()
        File(backupRoot(context), INDEX_FILE_NAME).writeText(arr.toString())
    }

    private fun backupRoot(context: Context): File = File(context.filesDir, BACKUP_ROOT_DIR)

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun shortId(backupId: String): String = backupId.takeLast(8)

    private const val TAG = "BackupMVP"
}

enum class BackupKind { FULL, INCREMENTAL }

data class BackupVolume(
    val fileName: String,
    val encryptedSizeBytes: Long,
    val checksumHex: String,
)

data class BackupAsset(
    val backupId: String,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
    val sha256Hex: String,
    val volumeFileName: String,
    val offsetInVolume: Long,
    val encryptedLengthInVolume: Long,
)

data class BackupManifest(
    val backupId: String,
    val createdAtMs: Long,
    val kind: BackupKind,
    val baseBackupId: String?,
    val assets: List<BackupAsset>,
    val volumes: List<BackupVolume>,
    val totalAssetCount: Int,
) {
    fun toJson(): String {
        val assetsJson = JSONArray()
        assets.forEach { a ->
            assetsJson.put(
                JSONObject()
                    .put("backupId", a.backupId)
                    .put("relativePath", a.relativePath)
                    .put("sizeBytes", a.sizeBytes)
                    .put("modifiedAtMs", a.modifiedAtMs)
                    .put("sha256Hex", a.sha256Hex)
                    .put("volumeFileName", a.volumeFileName)
                    .put("offsetInVolume", a.offsetInVolume)
                    .put("encryptedLengthInVolume", a.encryptedLengthInVolume),
            )
        }
        val volumesJson = JSONArray()
        volumes.forEach { v ->
            volumesJson.put(
                JSONObject()
                    .put("fileName", v.fileName)
                    .put("encryptedSizeBytes", v.encryptedSizeBytes)
                    .put("checksumHex", v.checksumHex),
            )
        }
        return JSONObject()
            .put("backupId", backupId)
            .put("createdAtMs", createdAtMs)
            .put("kind", kind.name)
            .put("baseBackupId", baseBackupId ?: "")
            .put("totalAssetCount", totalAssetCount)
            .put("assets", assetsJson)
            .put("volumes", volumesJson)
            .toString()
    }

    companion object {
        fun fromJson(json: String): BackupManifest {
            val root = JSONObject(json)
            val assetsArray = root.getJSONArray("assets")
            val assets = buildList {
                for (i in 0 until assetsArray.length()) {
                    val a = assetsArray.getJSONObject(i)
                    add(
                        BackupAsset(
                            backupId = a.getString("backupId"),
                            relativePath = a.getString("relativePath"),
                            sizeBytes = a.getLong("sizeBytes"),
                            modifiedAtMs = a.getLong("modifiedAtMs"),
                            sha256Hex = a.getString("sha256Hex"),
                            volumeFileName = a.getString("volumeFileName"),
                            offsetInVolume = a.getLong("offsetInVolume"),
                            encryptedLengthInVolume = a.getLong("encryptedLengthInVolume"),
                        ),
                    )
                }
            }
            val volumesArray = root.getJSONArray("volumes")
            val volumes = buildList {
                for (i in 0 until volumesArray.length()) {
                    val v = volumesArray.getJSONObject(i)
                    add(
                        BackupVolume(
                            fileName = v.getString("fileName"),
                            encryptedSizeBytes = v.getLong("encryptedSizeBytes"),
                            checksumHex = v.getString("checksumHex"),
                        ),
                    )
                }
            }
            return BackupManifest(
                backupId = root.getString("backupId"),
                createdAtMs = root.getLong("createdAtMs"),
                kind = BackupKind.valueOf(root.getString("kind")),
                baseBackupId = root.optString("baseBackupId").ifBlank { null },
                assets = assets,
                volumes = volumes,
                totalAssetCount = root.optInt("totalAssetCount", assets.size),
            )
        }
    }
}

data class BackupIndexMeta(
    val backupId: String,
    val folderName: String,
    val createdAtMs: Long,
    val kind: BackupKind,
    val baseBackupId: String?,
)

data class BackupExecutionResult(
    val success: Boolean,
    val message: String,
    val backupId: String = "",
    val backupKind: BackupKind = BackupKind.FULL,
    val outputPath: String = "",
    val outputSizeBytes: Long = 0,
    val assetCount: Int = 0,
    val totalAssetCount: Int = 0,
    val volumeCount: Int = 0,
) {
    companion object {
        fun success(
            backupId: String,
            backupKind: BackupKind,
            outputPath: String,
            outputSizeBytes: Long,
            assetCount: Int,
            totalAssetCount: Int,
            volumeCount: Int,
        ) = BackupExecutionResult(
            success = true,
            message = "备份完成",
            backupId = backupId,
            backupKind = backupKind,
            outputPath = outputPath,
            outputSizeBytes = outputSizeBytes,
            assetCount = assetCount,
            totalAssetCount = totalAssetCount,
            volumeCount = volumeCount,
        )

        fun failure(message: String) = BackupExecutionResult(success = false, message = message)
    }
}

data class RestoreExecutionResult(
    val success: Boolean,
    val message: String,
    val restored: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val sourceBackupId: String = "",
) {
    companion object {
        fun success(restored: Int, skipped: Int, failed: Int, sourceBackupId: String) = RestoreExecutionResult(
            success = true,
            message = "恢复完成",
            restored = restored,
            skipped = skipped,
            failed = failed,
            sourceBackupId = sourceBackupId,
        )

        fun failure(message: String) = RestoreExecutionResult(success = false, message = message)
    }
}

private data class ValidationResult(
    val success: Boolean,
    val message: String,
)
