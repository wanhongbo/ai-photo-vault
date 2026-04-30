package com.xpx.vault.ui.backup

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.xpx.vault.AppLogger
import com.xpx.vault.data.crypto.AesCbcEngine
import com.xpx.vault.data.crypto.BackupKeyManager
import com.xpx.vault.data.crypto.KeystoreSecretKeyProvider
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
            return@withContext BackupExecutionResult.alreadyRunning()
        }
        try {
            when (trigger) {
                BackupTrigger.AUTO -> doAutoBackup(context)
                BackupTrigger.MANUAL -> {
                    val uri = targetUri
                        ?: return@withContext BackupExecutionResult.failure("手动备份缺少目标位置。")
                    doManualBackup(context, uri)
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun doAutoBackup(context: Context): BackupExecutionResult {
        val vaultRoot = File(context.filesDir, VAULT_ROOT_DIR)
        if (!vaultRoot.exists()) return BackupExecutionResult.failure("保险箱目录不存在，无法创建备份。")
        if (!ExternalBackupLocation.isWritable(context)) {
            return BackupExecutionResult.failure("未授权外部备份目录，无法写入。")
        }
        val backupKey = BackupSecretsStore.loadCached(context)
            ?: return BackupExecutionResult.failure("备份密钥尚未缓存，请先解锁后再试。")
        val keyManager = backupKeyManager(context)
        val fingerprint = keyManager.fingerprint(backupKey)
        val kdfParams = keyManager.getOrCreateKdfParams()
        val meta = BackupMeta.load(context).auto
        val mode = when {
            meta == null -> BackupKind.FULL
            meta.keyFingerprintHex != fingerprint -> BackupKind.FULL
            else -> BackupKind.INCREMENTAL
        }

        val assets = scanVaultAssets(vaultRoot)
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
            return BackupExecutionResult.failure("本机磁盘空间不足，无法生成备份临时文件。")
        }

        val tmpDir = File(context.filesDir, BACKUP_TMP_DIR).apply { mkdirs() }
        val bodyFile = File(tmpDir, "auto_body_$backupId.bin")
        val writingFile = File(tmpDir, "auto_$backupId.writing")
        return runCatching {
            val writeResult = writeBodyAndAssemble(
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
            BackupExecutionResult.failure("备份失败：${it.message ?: "未知异常"}")
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
        if (!vaultRoot.exists()) return BackupExecutionResult.failure("保险箱目录不存在，无法创建备份。")
        val backupKey = BackupSecretsStore.loadCached(context)
            ?: return BackupExecutionResult.failure("备份密钥尚未缓存，请先解锁后再试。")
        val keyManager = backupKeyManager(context)
        val fingerprint = keyManager.fingerprint(backupKey)
        val kdfParams = keyManager.getOrCreateKdfParams()

        val assets = scanVaultAssets(vaultRoot)
        if (assets.isEmpty()) {
            return BackupExecutionResult.failure("保险箱为空，没有可备份的内容。")
        }
        val now = System.currentTimeMillis()
        val backupId = newBackupId(now)

        val estimatedBytes = assets.sumOf { it.sizeBytes }
        if (!hasEnoughSpace(context, estimatedBytes * 2)) {
            return BackupExecutionResult.failure("本机磁盘空间不足，无法生成备份临时文件。")
        }

        val tmpDir = File(context.filesDir, BACKUP_TMP_DIR).apply { mkdirs() }
        val bodyFile = File(tmpDir, "manual_body_$backupId.bin")
        val writingFile = File(tmpDir, "manual_$backupId.aivb.writing")

        return runCatching {
            val writeResult = writeBodyAndAssemble(
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
                ?: error("无法读回目标文件进行校验")
            if (remoteMd5 != localMd5) {
                error("目标文件校验不一致，请重试。")
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
            BackupExecutionResult.failure("备份失败：${it.message ?: "未知异常"}")
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
            ?: return@withContext RestoreExecutionResult.failure("外部未发现可恢复的自动备份。").also {
                pin.fill(0.toChar())
            }
        val input = runCatching { context.contentResolver.openInputStream(autoFile.uri) }
            .getOrNull()
            ?: return@withContext RestoreExecutionResult.failure("无法打开外部自动备份文件。").also {
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
            ?: return@withContext RestoreExecutionResult.failure("无法读取所选备份文件。").also {
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
                return RestoreExecutionResult.failure("密码不正确，请重试。")
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
        val masterKey = KeystoreSecretKeyProvider().getOrCreateAesSecretKey()
        val engine = AesCbcEngine(masterKey)
        var restored = 0
        var skipped = 0
        var failed = 0
        header.assets.forEach { asset ->
            runCatching {
                val target = File(vaultRoot, asset.relativePath)
                if (target.exists() && fileSha256Hex(target) == asset.sha256Hex) {
                    skipped += 1
                    // 仍需跳过 reader 中的 frame
                    repeat(asset.frameCount) { reader.readNextChunk() }
                    return@runCatching
                }
                target.parentFile?.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                target.outputStream().buffered().use { out ->
                    repeat(asset.frameCount) {
                        val plain = reader.readNextChunk()
                            ?: error("unexpected end of body for ${asset.relativePath}")
                        // 用 Keystore 主密钥重加密后落地
                        val enc = engine.encrypt(plain)
                        out.write(enc)
                        digest.update(plain)
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
        val result = RestoreExecutionResult.success(restored, skipped, failed, header.backupId)
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
        vaultRoot: File,
        bodyFile: File,
        writingFile: File,
        backupKey: SecretKey,
        headerBase: BackupPackageV1.HeaderBase,
        assets: List<VaultAsset>,
    ): WriteResult {
        val masterKey = KeystoreSecretKeyProvider().getOrCreateAesSecretKey()
        val engine = AesCbcEngine(masterKey)

        // 1. 写 body 到 tmp（逐 asset 读 vault 密文 → 解密 → 明文 → GCM 加密 chunk 写入 body）
        bodyFile.outputStream().buffered().use { bodyOut ->
            val bodyWriter = BackupPackageV1.newBodyWriter(bodyOut, backupKey)
            assets.forEach { asset ->
                bodyWriter.beginAsset(asset.relativePath, asset.sha256Hex, asset.sizeBytes)
                val source = File(vaultRoot, asset.relativePath)
                val encrypted = source.readBytes()
                val plain = engine.decrypt(encrypted)
                // 分片写入：每个 chunk 不超过 CHUNK_MAX_PLAIN_BYTES
                var offset = 0
                while (offset < plain.size) {
                    val len = minOf(BackupPackageV1.CHUNK_MAX_PLAIN_BYTES, plain.size - offset)
                    bodyWriter.writeChunk(plain, offset, len)
                    offset += len
                }
                if (plain.isEmpty()) {
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

    private fun scanVaultAssets(vaultRoot: File): List<VaultAsset> {
        if (!vaultRoot.exists()) return emptyList()
        val masterKey = KeystoreSecretKeyProvider().getOrCreateAesSecretKey()
        val engine = AesCbcEngine(masterKey)
        return vaultRoot.walkTopDown().filter { it.isFile }.map { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            // 计算明文 sha256，用于恢复端校验
            runCatching {
                val encrypted = file.readBytes()
                val plain = engine.decrypt(encrypted)
                digest.update(plain)
            }.onFailure {
                // 无法解密则回退为密文 sha256（避免崩溃；恢复端会在 restore 侧失败）
                file.inputStream().buffered().use { input ->
                    val buf = ByteArray(STREAM_CHUNK_SIZE)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        digest.update(buf, 0, n)
                    }
                }
            }
            VaultAsset(
                relativePath = file.relativeTo(vaultRoot).invariantSeparatorsPath,
                sizeBytes = file.length(),
                sha256Hex = digest.digest().joinToString("") { b -> "%02x".format(b) },
            )
        }.toList()
    }

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

    private fun fileSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
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

        fun alreadyRunning() = BackupExecutionResult(
            success = false,
            message = "已有备份任务在进行中。",
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
