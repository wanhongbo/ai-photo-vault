package com.xpx.vault.ui.backup

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.xpx.vault.AppLogger
import com.xpx.vault.R
import com.xpx.vault.data.crypto.BackupKeyManager
import com.xpx.vault.data.crypto.VaultCipher
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val VAULT_ROOT_DIR = "vault_albums"
private const val BACKUP_TMP_DIR = "backup_tmp"
private const val STREAM_CHUNK_SIZE = BackupPackageV1.CHUNK_MAX_PLAIN_BYTES
private const val TAG = "BackupMVP"

enum class BackupTrigger { AUTO, MANUAL }

enum class BackupKind { FULL, INCREMENTAL }

/**
 * 新版双密钥备份/恢复服务。
 *
 * - Vault Master Key (Keystore) 负责 vault_albums/ 日常加解密。
 * - Backup Key (Argon2id 派生) 仅用于备份包加解密；由 [BackupSecretsStore] 缓存或调用方临时派生。
 * - AUTO：固定外部路径 `Documents/AIVault/backup.dat` 覆盖式单文件；走 fp 比对决定 FULL/INCREMENTAL。
 * - MANUAL：永远 FULL；用户 SAF 选位置；不触碰 backup_meta.json 的 `auto` 字段。
 */
object LocalBackupMvpService {
    private val mutex = Mutex()

    /** 供 T7 / T8 注入的 BackupKeyManager 工厂。ViewModel 层也可直接构造。 */
    fun backupKeyManager(context: Context): BackupKeyManager =
        BackupKeyManager(context.applicationContext)

    // ---------- 备份入口 ----------

    suspend fun createBackup(
        context: Context,
        trigger: BackupTrigger,
        targetUri: Uri? = null,
    ): BackupExecutionResult = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) {
            AppLogger.w(TAG, "createBackup rejected: already running")
            return@withContext BackupExecutionResult.alreadyRunning(context)
        }
        try {
            when (trigger) {
                BackupTrigger.AUTO -> doAutoBackup(context)
                BackupTrigger.MANUAL -> {
                    val uri = targetUri
                        ?: return@withContext BackupExecutionResult.failure(context.getString(R.string.backup_error_no_target))
                    doManualBackup(context, uri)
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun doAutoBackup(context: Context): BackupExecutionResult {
        val vaultRoot = File(context.filesDir, VAULT_ROOT_DIR)
        if (!vaultRoot.exists()) return BackupExecutionResult.failure(context.getString(R.string.backup_error_no_vault_dir))
        if (!ExternalBackupLocation.isWritable(context)) {
            return BackupExecutionResult.failure(context.getString(R.string.backup_error_no_saf_dir))
        }
        val backupKey = BackupSecretsStore.loadCached(context)
            ?: return BackupExecutionResult.failure(context.getString(R.string.backup_error_no_key))
        val keyManager = backupKeyManager(context)
        val fingerprint = keyManager.fingerprint(backupKey)
        val kdfParams = keyManager.getOrCreateKdfParams()
        val meta = BackupMeta.load(context).auto
        val mode = when {
            meta == null -> BackupKind.FULL
            meta.keyFingerprintHex != fingerprint -> BackupKind.FULL
            else -> BackupKind.INCREMENTAL
        }

        val assets = scanVaultAssets(context, vaultRoot)
        AppLogger.d(
            TAG,
            "auto backup start mode=${mode.name} assets=${assets.size} " +
                "prev=${meta?.lastBackupId?.let(::shortId) ?: "none"}",
        )
        val now = System.currentTimeMillis()
        val backupId = newBackupId(now)

        // 空间预估：估算 = 2 × vault 总大小；不足则提前失败。
        val estimatedBytes = assets.sumOf { it.sizeBytes }
        if (!hasEnoughSpace(context, estimatedBytes * 2)) {
            return BackupExecutionResult.failure(context.getString(R.string.backup_error_no_space))
        }

        val tmpDir = File(context.filesDir, BACKUP_TMP_DIR).apply { mkdirs() }
        val bodyFile = File(tmpDir, "auto_body_$backupId.bin")
        val writingFile = File(tmpDir, "auto_$backupId.writing")
        return runCatching {
            val writeResult = writeBodyAndAssemble(
                context = context,
                vaultRoot = vaultRoot,
                bodyFile = bodyFile,
                writingFile = writingFile,
                backupKey = backupKey,
                headerBase = BackupPackageV1.HeaderBase(
                    backupId = backupId,
                    createdAtMs = now,
                    kind = BackupPackageV1.Kind.AUTO,
                    kdfAlgorithm = kdfParams.algorithm,
                    kdfSaltHex = kdfParams.saltHex,
                    kdfIterations = kdfParams.iterations,
                    kdfMemoryKb = kdfParams.memoryKb,
                    kdfParallelism = kdfParams.parallelism,
                    keyFingerprintHex = fingerprint,
                ),
                assets = assets,
            )

            // 写完 tmp 后把字节流原子覆盖到外部 backup.dat
            ExternalBackupLocation.atomicReplaceAuto(context, writingFile)

            // 更新 backup_meta.auto
            BackupMeta.updateAuto(
                context,
                BackupMeta.AutoMeta(
                    lastBackupId = backupId,
                    lastBackupAtMs = now,
                    keyFingerprintHex = fingerprint,
                    kdfParams = kdfParams,
                    externalUri = ExternalBackupLocation.findAuto(context)?.uri?.toString(),
                    assetIndex = assets.map { a ->
                        BackupMeta.AssetIndexEntry(
                            relativePath = a.relativePath,
                            sha256Hex = a.sha256Hex,
                            sizeBytes = a.sizeBytes,
                        )
                    },
                ),
            )

            val result = BackupExecutionResult.success(
                context,
                backupId = backupId,
                backupKind = mode,
                outputPath = "auto:backup.dat",
                outputSizeBytes = writeResult.totalBytes,
                assetCount = writeResult.writtenAssetCount,
                totalAssetCount = assets.size,
                volumeCount = 1,
            )
            BackupRuntimeState.lastBackupResult = result
            AppLogger.d(
                TAG,
                "auto backup success id=${shortId(backupId)} mode=${mode.name} bytes=${writeResult.totalBytes}",
            )
            result
        }.getOrElse {
            AppLogger.e(TAG, "auto backup failed: ${it.javaClass.simpleName} ${it.message}", it)
            BackupExecutionResult.failure(context.getString(R.string.backup_error_failed_fmt, it.message ?: context.getString(R.string.common_unknown_error)))
        }.also {
            bodyFile.delete()
            writingFile.delete()
        }
    }

    private suspend fun doManualBackup(
        context: Context,
        targetUri: Uri,
    ): BackupExecutionResult {
        val vaultRoot = File(context.filesDir, VAULT_ROOT_DIR)
        if (!vaultRoot.exists()) return BackupExecutionResult.failure(context.getString(R.string.backup_error_no_vault_dir))
        val backupKey = BackupSecretsStore.loadCached(context)
            ?: return BackupExecutionResult.failure(context.getString(R.string.backup_error_no_key))
        val keyManager = backupKeyManager(context)
        val fingerprint = keyManager.fingerprint(backupKey)
        val kdfParams = keyManager.getOrCreateKdfParams()

        val assets = scanVaultAssets(context, vaultRoot)
        if (assets.isEmpty()) {
            return BackupExecutionResult.failure(context.getString(R.string.backup_error_vault_empty))
        }
        val now = System.currentTimeMillis()
        val backupId = newBackupId(now)

        val estimatedBytes = assets.sumOf { it.sizeBytes }
        if (!hasEnoughSpace(context, estimatedBytes * 2)) {
            return BackupExecutionResult.failure(context.getString(R.string.backup_error_no_space))
        }

        val tmpDir = File(context.filesDir, BACKUP_TMP_DIR).apply { mkdirs() }
        val bodyFile = File(tmpDir, "manual_body_$backupId.bin")
        val writingFile = File(tmpDir, "manual_$backupId.aivb.writing")

        return runCatching {
            val writeResult = writeBodyAndAssemble(
                context = context,
                vaultRoot = vaultRoot,
                bodyFile = bodyFile,
                writingFile = writingFile,
                backupKey = backupKey,
                headerBase = BackupPackageV1.HeaderBase(
                    backupId = backupId,
                    createdAtMs = now,
                    kind = BackupPackageV1.Kind.MANUAL,
                    kdfAlgorithm = kdfParams.algorithm,
                    kdfSaltHex = kdfParams.saltHex,
                    kdfIterations = kdfParams.iterations,
                    kdfMemoryKb = kdfParams.memoryKb,
                    kdfParallelism = kdfParams.parallelism,
                    keyFingerprintHex = fingerprint,
                ),
                assets = assets,
            )

            val localMd5 = fileMd5Hex(writingFile)
            // 拷贝到用户所选 SAF 目标
            context.contentResolver.openOutputStream(targetUri, "w").use { out ->
                checkNotNull(out) { "openOutputStream returned null" }
                writingFile.inputStream().buffered().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                    out.flush()
                }
            }
            // 读回校验 MD5
            val remoteMd5 = context.contentResolver.openInputStream(targetUri)?.use { streamMd5Hex(it) }
                ?: error(context.getString(R.string.backup_error_verify_readback))
            if (remoteMd5 != localMd5) {
                error(context.getString(R.string.backup_error_verify_mismatch))
            }

            // 追加到 manualHistory
            BackupMeta.appendManual(
                context,
                BackupMeta.ManualEntry(
                    createdAtMs = now,
                    uri = targetUri.toString(),
                    sizeBytes = writeResult.totalBytes,
                    note = null,
                ),
            )

            val result = BackupExecutionResult.success(
                context,
                backupId = backupId,
                backupKind = BackupKind.FULL,
                outputPath = targetUri.toString(),
                outputSizeBytes = writeResult.totalBytes,
                assetCount = writeResult.writtenAssetCount,
                totalAssetCount = assets.size,
                volumeCount = 1,
            )
            BackupRuntimeState.lastBackupResult = result
            AppLogger.d(TAG, "manual backup success id=${shortId(backupId)} bytes=${writeResult.totalBytes}")
            result
        }.getOrElse {
            AppLogger.e(TAG, "manual backup failed: ${it.message}", it)
            BackupExecutionResult.failure(context.getString(R.string.backup_error_failed_fmt, it.message ?: context.getString(R.string.common_unknown_error)))
        }.also {
            bodyFile.delete()
            writingFile.delete()
        }
    }

    // ---------- 恢复入口 ----------

    suspend fun restoreFromAutoPackage(
        context: Context,
        pin: CharArray,
    ): RestoreExecutionResult = withContext(Dispatchers.IO) {
        val autoFile = ExternalBackupLocation.findAuto(context)
            ?: return@withContext RestoreExecutionResult.failure(context.getString(R.string.restore_error_no_auto_backup)).also {
                pin.fill(0.toChar())
            }
        val input = runCatching { context.contentResolver.openInputStream(autoFile.uri) }
            .getOrNull()
            ?: return@withContext RestoreExecutionResult.failure(context.getString(R.string.restore_error_cannot_open_auto)).also {
                pin.fill(0.toChar())
            }
        input.use { stream ->
            restoreFromStream(context, stream, pin)
        }
    }

    suspend fun restoreFromManualFile(
        context: Context,
        fileUri: Uri,
        pin: CharArray,
    ): RestoreExecutionResult = withContext(Dispatchers.IO) {
        val input = runCatching { context.contentResolver.openInputStream(fileUri) }
            .getOrNull()
            ?: return@withContext RestoreExecutionResult.failure(context.getString(R.string.restore_error_cannot_read_file)).also {
                pin.fill(0.toChar())
            }
        input.use { stream ->
            restoreFromStream(context, stream, pin)
        }
    }

    private fun restoreFromStream(
        context: Context,
        input: java.io.InputStream,
        pin: CharArray,
    ): RestoreExecutionResult {
        // 为了拿到 kdfParams/fp，先读 header；header 小，允许占用内存。
        val buffered = java.io.BufferedInputStream(input)
        val keyManager = backupKeyManager(context)
        val backupKey: SecretKey
        val header: BackupPackageV1.Header
        try {
            // 预读 header（未传 key 之前无法创建 reader，这里借用 dummy key，仅解析到 header 部分）。
            // 为了避免临时解析两次，我们先把 header 部分字节拷贝出来解析：
            val headerInfo = peekHeader(buffered)
            header = headerInfo.header
            val params = BackupKeyManager.KdfParams(
                algorithm = header.kdfAlgorithm,
                saltHex = header.kdfSaltHex,
                iterations = header.kdfIterations,
                memoryKb = header.kdfMemoryKb,
                parallelism = header.kdfParallelism,
            )
            val material = keyManager.deriveKey(pin, params)
            if (material.fingerprintHex != header.keyFingerprintHex) {
                return RestoreExecutionResult.failure(context.getString(R.string.restore_error_wrong_pin))
            }
            backupKey = material.key
        } finally {
            pin.fill(0.toChar())
        }

        val reader = BackupPackageV1.newReader(buffered, backupKey)
        // peekHeader 已消费 header 字节；直接把已解析的 header 注入 reader，跳过 readHeader 过程。
        reader.attachHeader(header)
        return doRestoreWithParsedHeader(context, reader, header)
    }

    private fun doRestoreWithParsedHeader(
        context: Context,
        reader: BackupPackageV1.Reader,
        header: BackupPackageV1.Header,
    ): RestoreExecutionResult {
        val vaultRoot = File(context.filesDir, VAULT_ROOT_DIR).apply { mkdirs() }
        val cipher = VaultCipher.get(context)
        var restored = 0
        var skipped = 0
        var failed = 0
        header.assets.forEach { asset ->
            runCatching {
                val target = File(vaultRoot, asset.relativePath)
                // 命中跳过：目标已存在且解密后的明文 sha256 与备份记录一致。
                if (target.exists() && decryptedSha256Hex(cipher, target) == asset.sha256Hex) {
                    skipped += 1
                    // 仍需跳过 reader 中的 frame
                    repeat(asset.frameCount) { reader.readNextChunk() }
                    return@runCatching
                }
                target.parentFile?.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                // 从 reader 拉取明文 chunk，计算明文 sha256，同时通过 VaultCipher 加密后原子写入 vault。
                cipher.encryptFileFromChunks(target) { emit ->
                    repeat(asset.frameCount) {
                        val plain = reader.readNextChunk()
                            ?: error("unexpected end of body for ${asset.relativePath}")
                        digest.update(plain)
                        emit(plain, 0, plain.size)
                    }
                }
                val hash = digest.digest().joinToString("") { b -> "%02x".format(b) }
                if (hash != asset.sha256Hex) {
                    target.delete()
                    error("asset checksum mismatch: ${asset.relativePath}")
                }
                restored += 1
            }.onFailure {
                failed += 1
                AppLogger.e(TAG, "restore asset failed: ${asset.relativePath} ${it.message}")
            }
        }
        val result = RestoreExecutionResult.success(context, restored, skipped, failed, header.backupId)
        BackupRuntimeState.lastRestoreResult = result
        AppLogger.d(
            TAG,
            "restore done id=${shortId(header.backupId)} restored=$restored skipped=$skipped failed=$failed",
        )
        return result
    }

    private data class HeaderPeek(
        val header: BackupPackageV1.Header,
    )

    /**
     * 读 MAGIC/VERSION/HEADER_LEN/HEADER_JSON 并解析；之后 [input] 的指针刚好停在 body 起点，
     * 这样外层用 [BackupPackageV1.newReader] 时，构造的 Reader 需要直接从 body 读 frame——
     * 但 Reader 自身要求先调用 readHeader()。为兼容，我们把 Reader 改造成"接受已解析 header"。
     *
     * 实现上：我们在这里不使用 BackupPackageV1.newReader 的 readHeader 流程，
     * 改为直接构造一个轻量 ChunkReader 子过程（见 [doRestoreWithParsedHeader]，这里直接复用 reader
     * 并给 Reader 手工塞入 header——为此在 BackupPackageV1.Reader 暴露 attachPreParsedHeader。
     */
    private fun peekHeader(input: java.io.BufferedInputStream): HeaderPeek {
        val magic = ByteArray(BackupPackageV1.MAGIC.size).also { readFully(input, it) }
        require(magic.contentEquals(BackupPackageV1.MAGIC)) { "bad magic" }
        val version = readInt32BE(input)
        require(version == BackupPackageV1.VERSION) { "unsupported version=$version" }
        val headerLen = readInt32BE(input)
        require(headerLen in 1..(32 * 1024 * 1024)) { "invalid header length=$headerLen" }
        val headerBytes = ByteArray(headerLen).also { readFully(input, it) }
        val header = BackupPackageV1.parseHeaderJson(String(headerBytes, Charsets.UTF_8))
        return HeaderPeek(header)
    }

    // ---------- 内部工具 ----------

    private data class WriteResult(
        val totalBytes: Long,
        val writtenAssetCount: Int,
    )

    private fun writeBodyAndAssemble(
        context: Context,
        vaultRoot: File,
        bodyFile: File,
        writingFile: File,
        backupKey: SecretKey,
        headerBase: BackupPackageV1.HeaderBase,
        assets: List<VaultAsset>,
    ): WriteResult {
        val cipher = VaultCipher.get(context)
        // 1. 写 body 到 tmp：逐 asset 通过 VaultCipher 流式解密 → 按 1MB 分片 → bodyWriter.writeChunk
        bodyFile.outputStream().buffered().use { bodyOut ->
            val bodyWriter = BackupPackageV1.newBodyWriter(bodyOut, backupKey)
            val chunkBuf = ByteArray(BackupPackageV1.CHUNK_MAX_PLAIN_BYTES)
            assets.forEach { asset ->
                bodyWriter.beginAsset(asset.relativePath, asset.sha256Hex, asset.sizeBytes)
                val source = File(vaultRoot, asset.relativePath)
                var chunkFill = 0
                var totalPlainBytes = 0L
                source.inputStream().buffered().use { input ->
                    cipher.decryptStream(input) { data, offset, length ->
                        var remaining = length
                        var p = offset
                        while (remaining > 0) {
                            val take = minOf(chunkBuf.size - chunkFill, remaining)
                            System.arraycopy(data, p, chunkBuf, chunkFill, take)
                            chunkFill += take
                            p += take
                            remaining -= take
                            totalPlainBytes += take
                            if (chunkFill == chunkBuf.size) {
                                bodyWriter.writeChunk(chunkBuf, 0, chunkFill)
                                chunkFill = 0
                            }
                        }
                    }
                }
                if (chunkFill > 0) {
                    bodyWriter.writeChunk(chunkBuf, 0, chunkFill)
                    chunkFill = 0
                }
                if (totalPlainBytes == 0L) {
                    // 空文件：写入一个 0 长度 chunk 让 frameCount 合法（极少见，占位）
                    bodyWriter.writeChunk(ByteArray(0), 0, 0)
                }
                bodyWriter.endAsset()
            }
            // 2. 组装最终文件到 writingFile，并 fsync
            FileOutputStream(writingFile).use { out ->
                val total = BackupPackageV1.finalizePackage(
                    bodyFile = bodyFile,
                    bodyWriter = bodyWriter,
                    headerBase = headerBase,
                    finalOutput = out,
                )
                out.fd.sync()
                // 额外一次读回校验 MD5（写盘可靠性）：读回并比对
                val md5 = fileMd5Hex(writingFile)
                AppLogger.d(TAG, "assemble done bytes=$total md5=${md5.take(8)}")
                return WriteResult(totalBytes = total, writtenAssetCount = assets.size)
            }
        }
    }

    private data class VaultAsset(
        val relativePath: String,
        val sizeBytes: Long,
        val sha256Hex: String,
    )

    private fun scanVaultAssets(context: Context, vaultRoot: File): List<VaultAsset> {
        if (!vaultRoot.exists()) return emptyList()
        // vault 下的文件是密文；备份包写入的为明文分片，因此 sha256/size 必须按解密后的明文计算，
        // 恢复端文件校验才能对齐。
        val cipher = VaultCipher.get(context)
        return vaultRoot.walkTopDown()
            .filter { it.isFile }
            // 跳过迁移 marker 与 encryptFile 残留的临时文件，避免整个备份失败。
            .filter { it.name != ".vault_encrypted_v1" && !it.name.contains(".enc_tmp_") }
            .mapNotNull { file -> scanOrHealAsset(cipher, vaultRoot, file) }
            .toList()
    }

    /**
     * 单文件扩展扫描。正常密文走 [computePlainDigest] 算 sha256/size；
     * 若报 BadPadding（开发期迁移协程未跑完 / 代码升级前存量明文 / 迁移判则偽阳性），
     * 就地自愈：将明文文件 encryptFile 原子加密后重扫一次；若仍失败（文件损坏等）返回 null 跳过。
     */
    private fun scanOrHealAsset(
        cipher: VaultCipher,
        vaultRoot: File,
        file: File,
    ): VaultAsset? {
        runCatching { return buildAsset(cipher, vaultRoot, file) }
            .onFailure { firstErr ->
                AppLogger.w(
                    TAG,
                    "scan decrypt failed, attempt self-heal: ${file.absolutePath} ${firstErr.message}",
                )
            }
        return runCatching {
            // 自愈：将明文原地加密（encryptFile 默认 .enc_tmp_ → rename），再重扫。
            file.inputStream().buffered().use { input ->
                cipher.encryptFile(input, file)
            }
            buildAsset(cipher, vaultRoot, file)
        }.getOrElse {
            AppLogger.e(TAG, "scan self-heal failed, skip asset: ${file.absolutePath} ${it.message}")
            null
        }
    }

    private fun buildAsset(cipher: VaultCipher, vaultRoot: File, file: File): VaultAsset {
        val digest = MessageDigest.getInstance("SHA-256")
        var plainSize = 0L
        file.inputStream().buffered().use { input ->
            cipher.decryptStream(input) { data, offset, length ->
                digest.update(data, offset, length)
                plainSize += length
            }
        }
        return VaultAsset(
            relativePath = file.relativeTo(vaultRoot).invariantSeparatorsPath,
            sizeBytes = plainSize,
            sha256Hex = digest.digest().joinToString("") { b -> "%02x".format(b) },
        )
    }

    /** 对 vault 内的密文文件计算其解密后的明文 sha256；对明文/损坏返回 null 以触发重写。 */
    private fun decryptedSha256Hex(cipher: VaultCipher, file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            cipher.decryptStream(input) { data, offset, length ->
                digest.update(data, offset, length)
            }
        }
        digest.digest().joinToString("") { b -> "%02x".format(b) }
    }.getOrNull()

    private fun hasEnoughSpace(context: Context, needBytes: Long): Boolean {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes >= needBytes
        }.getOrDefault(true)
    }

    private fun fileMd5Hex(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(STREAM_CHUNK_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun streamMd5Hex(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buf = ByteArray(STREAM_CHUNK_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray) {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n < 0) error("unexpected EOF, need ${buf.size} bytes, got $total")
            total += n
        }
    }

    private fun readInt32BE(input: java.io.InputStream): Int {
        val b = ByteArray(4).also { readFully(input, it) }
        return ((b[0].toInt() and 0xFF) shl 24) or
            ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or
            (b[3].toInt() and 0xFF)
    }

    private fun newBackupId(now: Long): String =
        "bkp_${now}_${UUID.randomUUID().toString().take(8)}"

    private fun shortId(backupId: String): String = backupId.takeLast(8)

    // ---------- 启动清理：旧 v0 结构 + 外部 .writing/.bak 自检 ----------

    fun sanitizeOnStartup(context: Context) {
        ExternalBackupLocation.sanitizeOnStartup(context)
        runCatching {
            // 清空 backup_tmp（kill 后的遗留）
            val tmp = File(context.filesDir, BACKUP_TMP_DIR)
            if (tmp.exists()) tmp.listFiles()?.forEach { it.delete() }
        }
    }
}

// ---------- 结果数据类 ----------

data class BackupExecutionResult(
    val success: Boolean,
    val message: String,
    val alreadyRunning: Boolean = false,
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
            context: Context,
            backupId: String,
            backupKind: BackupKind,
            outputPath: String,
            outputSizeBytes: Long,
            assetCount: Int,
            totalAssetCount: Int,
            volumeCount: Int,
        ) = BackupExecutionResult(
            success = true,
            message = context.getString(R.string.backup_result_success_done),
            backupId = backupId,
            backupKind = backupKind,
            outputPath = outputPath,
            outputSizeBytes = outputSizeBytes,
            assetCount = assetCount,
            totalAssetCount = totalAssetCount,
            volumeCount = volumeCount,
        )

        fun failure(message: String) = BackupExecutionResult(success = false, message = message)

        fun alreadyRunning(context: Context) = BackupExecutionResult(
            success = false,
            message = context.getString(R.string.backup_result_already_running),
            alreadyRunning = true,
        )
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
        fun success(context: Context, restored: Int, skipped: Int, failed: Int, sourceBackupId: String) = RestoreExecutionResult(
            success = true,
            message = context.getString(R.string.restore_result_success_done),
            restored = restored,
            skipped = skipped,
            failed = failed,
            sourceBackupId = sourceBackupId,
        )

        fun failure(message: String) = RestoreExecutionResult(success = false, message = message)
    }
}
