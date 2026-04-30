package com.xpx.vault.ui.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * v1 备份包读写器（AUTO / MANUAL 共用）。
 *
 * 包结构（所有整型为大端序）：
 * ```
 * | MAGIC(8B "AIVAULT\x01") | VERSION(int32) | HEADER_LEN(int32) |
 * | HEADER_JSON(HEADER_LEN bytes) |
 * | BODY: N x ChunkFrame |
 * | TRAILER: sha256(header_raw || body_raw) (32B raw) |
 * ```
 *
 * ChunkFrame：
 * ```
 * | IV(12B) | CIPHER_LEN(int32，不含 tag) | CIPHER_TEXT(CIPHER_LEN B) | GCM_TAG(16B) |
 * ```
 *
 * HeaderJson 字段（明文，不含任何密钥字节）：
 * ```json
 * {
 *   "version": 1,
 *   "backupId": "bkp_...",
 *   "createdAtMs": 123,
 *   "kind": "AUTO|MANUAL",
 *   "kdfParams": { "algorithm": "Argon2id", "saltHex": "...", "iterations": 3, "memoryKb": 65536, "parallelism": 1 },
 *   "keyFingerprintHex": "...",
 *   "cipher": "AES-256-GCM",
 *   "assets": [
 *     { "relativePath": "...", "sha256Hex": "...", "sizeBytes": 123, "chunkRange": { "fromFrame": 0, "count": 3 } }
 *   ]
 * }
 * ```
 */
object BackupPackageV1 {
    const val VERSION: Int = 1
    const val CHUNK_MAX_PLAIN_BYTES: Int = 1 * 1024 * 1024 // 每 chunk 明文上限 1MB
    const val CIPHER_SUITE: String = "AES-256-GCM"
    const val IV_LENGTH: Int = 12
    const val GCM_TAG_LENGTH_BITS: Int = 128
    const val TRAILER_LENGTH: Int = 32

    private const val GCM_TAG_LENGTH_BYTES: Int = GCM_TAG_LENGTH_BITS / 8
    private const val TRANSFORMATION: String = "AES/GCM/NoPadding"

    /** MAGIC: ASCII "AIVAULT" + 0x01。 */
    val MAGIC: ByteArray = byteArrayOf(
        'A'.code.toByte(), 'I'.code.toByte(), 'V'.code.toByte(),
        'A'.code.toByte(), 'U'.code.toByte(), 'L'.code.toByte(),
        'T'.code.toByte(), 0x01,
    )

    enum class Kind { AUTO, MANUAL }

    /** HeaderJson 的构造输入；调用方先行收集 body 结构后再组装。 */
    data class HeaderBase(
        val backupId: String,
        val createdAtMs: Long,
        val kind: Kind,
        val kdfAlgorithm: String,
        val kdfSaltHex: String,
        val kdfIterations: Int,
        val kdfMemoryKb: Int,
        val kdfParallelism: Int,
        val keyFingerprintHex: String,
    )

    /** 单个 asset 的头描述。 */
    data class AssetHeader(
        val relativePath: String,
        val sha256Hex: String,
        val sizeBytes: Long,
        val fromFrame: Int,
        val frameCount: Int,
    )

    /** 解析后的包头。 */
    data class Header(
        val version: Int,
        val backupId: String,
        val createdAtMs: Long,
        val kind: Kind,
        val kdfAlgorithm: String,
        val kdfSaltHex: String,
        val kdfIterations: Int,
        val kdfMemoryKb: Int,
        val kdfParallelism: Int,
        val keyFingerprintHex: String,
        val cipher: String,
        val assets: List<AssetHeader>,
    )

    // ---------- 写入侧 ----------

    /**
     * Body 写入器。调用方在本地临时文件先写 body 部分，并累积每个 asset 的 frame 范围，
     * 最后调用 [finalizePackage] 把 header 和 body 拼成最终 v1 包文件。
     */
    class BodyWriter internal constructor(
        private val bodyOutput: OutputStream,
        private val backupKey: SecretKey,
    ) {
        private val random = SecureRandom()
        private var frameIndex = 0
        private var assetStartFrame = -1
        private val finalizedAssets = mutableListOf<AssetHeader>()
        private var currentAsset: AssetHeaderBuilder? = null

        private data class AssetHeaderBuilder(
            val relativePath: String,
            val sha256Hex: String,
            val sizeBytes: Long,
            val fromFrame: Int,
        )

        fun beginAsset(relativePath: String, sha256Hex: String, sizeBytes: Long) {
            check(currentAsset == null) { "previous asset not ended" }
            assetStartFrame = frameIndex
            currentAsset = AssetHeaderBuilder(
                relativePath = relativePath,
                sha256Hex = sha256Hex,
                sizeBytes = sizeBytes,
                fromFrame = frameIndex,
            )
        }

        /** 写入单个 chunk 明文；调用方按 [CHUNK_MAX_PLAIN_BYTES] 分片。 */
        fun writeChunk(plain: ByteArray, offset: Int = 0, length: Int = plain.size) {
            checkNotNull(currentAsset) { "call beginAsset first" }
            require(length in 1..CHUNK_MAX_PLAIN_BYTES) { "invalid chunk length=$length" }
            val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, backupKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val combined = cipher.doFinal(plain, offset, length)
            // combined = cipherText(length bytes) || tag(16 bytes)
            check(combined.size == length + GCM_TAG_LENGTH_BYTES) {
                "unexpected GCM output size=${combined.size}"
            }
            bodyOutput.write(iv)
            writeInt32BE(bodyOutput, length)
            bodyOutput.write(combined)
            frameIndex += 1
        }

        fun endAsset(): AssetHeader {
            val b = checkNotNull(currentAsset) { "no asset in progress" }
            val count = frameIndex - b.fromFrame
            val header = AssetHeader(
                relativePath = b.relativePath,
                sha256Hex = b.sha256Hex,
                sizeBytes = b.sizeBytes,
                fromFrame = b.fromFrame,
                frameCount = count,
            )
            finalizedAssets += header
            currentAsset = null
            return header
        }

        internal fun snapshot(): List<AssetHeader> = finalizedAssets.toList()
    }

    /** 构造一个 BodyWriter。调用方负责 [bodyOutput] 的关闭。 */
    fun newBodyWriter(bodyOutput: OutputStream, backupKey: SecretKey): BodyWriter =
        BodyWriter(bodyOutput, backupKey)

    /**
     * 把 [bodyFile] 作为包体拼装成 v1 格式写入 [finalOutput]，并在尾部附 sha256 trailer。
     * @return 产出的字节数（含 header + body + trailer）。
     */
    fun finalizePackage(
        bodyFile: File,
        bodyWriter: BodyWriter,
        headerBase: HeaderBase,
        finalOutput: OutputStream,
    ): Long {
        val headerJson = buildHeaderJson(headerBase, bodyWriter.snapshot())
        val headerBytes = headerJson.toByteArray(Charsets.UTF_8)

        val digest = MessageDigest.getInstance("SHA-256")
        val countingOut = CountingOutputStream(finalOutput)

        // 写入 MAGIC 与 version/header_len，这部分也纳入 sha256 计算（等价于 header_raw）。
        fun writeAndDigest(bytes: ByteArray, off: Int = 0, len: Int = bytes.size) {
            countingOut.write(bytes, off, len)
            digest.update(bytes, off, len)
        }

        writeAndDigest(MAGIC)
        writeAndDigest(int32ToBytes(VERSION))
        writeAndDigest(int32ToBytes(headerBytes.size))
        writeAndDigest(headerBytes)

        // 拷贝 body 文件内容
        bodyFile.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                writeAndDigest(buf, 0, n)
            }
        }

        // TRAILER = sha256(header_raw || body_raw)
        val trailer = digest.digest()
        check(trailer.size == TRAILER_LENGTH) { "sha256 size mismatch" }
        countingOut.write(trailer)
        finalOutput.flush()
        return countingOut.bytesWritten
    }

    // ---------- 读取侧 ----------

    /** 顺序流式 Reader：先读 header，再按 [AssetHeader.frameCount] 连续调用 [readNextChunk]。 */
    class Reader internal constructor(
        private val input: InputStream,
        private val backupKey: SecretKey,
    ) {
        var header: Header? = null
            private set
        private var framesRead = 0

        /** 读取并校验包头，但不校验 trailer（trailer 需要读完 body 后再校验）。 */
        fun readHeader(): Header {
            header?.let { return it }
            val magic = readFullyOrThrow(MAGIC.size)
            require(magic.contentEquals(MAGIC)) { "bad magic" }
            val version = readInt32BE()
            require(version == VERSION) { "unsupported version=$version" }
            val headerLen = readInt32BE()
            require(headerLen in 1..MAX_HEADER_BYTES) { "invalid header length=$headerLen" }
            val headerBytes = readFullyOrThrow(headerLen)
            val parsed = parseHeaderJson(String(headerBytes, Charsets.UTF_8))
            header = parsed
            return parsed
        }

        /**
         * 注入调用方已经预解析的 header，并跳过 [readHeader] 中的字节消费。
         * 使用场景：调用方先用固定 key 派生流程读 header 拿 kdfParams，再用正确密码派生出 backupKey，
         * 此时 [input] 的位置已经越过 header，直接从 body 继续读。
         */
        fun attachHeader(h: Header) {
            check(header == null) { "header already parsed" }
            header = h
        }

        /**
         * 顺序读取下一个 chunk 的明文。
         * 返回 null 表示已经读完所有 frame。
         */
        fun readNextChunk(): ByteArray? {
            val h = header ?: error("call readHeader() first")
            val totalFrames = h.assets.sumOf { it.frameCount }
            if (framesRead >= totalFrames) return null
            val iv = readFullyOrThrow(IV_LENGTH)
            val cipherLen = readInt32BE()
            require(cipherLen in 0..CHUNK_MAX_PLAIN_BYTES) { "invalid cipher len=$cipherLen" }
            val combined = readFullyOrThrow(cipherLen + GCM_TAG_LENGTH_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, backupKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plain = cipher.doFinal(combined)
            framesRead += 1
            return plain
        }

        private fun readFullyOrThrow(size: Int): ByteArray {
            val buf = ByteArray(size)
            var total = 0
            while (total < size) {
                val n = input.read(buf, total, size - total)
                if (n < 0) throw EOFException("unexpected EOF, need $size bytes, got $total")
                total += n
            }
            return buf
        }

        private fun readInt32BE(): Int {
            val b = readFullyOrThrow(4)
            return ((b[0].toInt() and 0xFF) shl 24) or
                ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or
                (b[3].toInt() and 0xFF)
        }
    }

    /** 构造一个 Reader。调用方负责关闭 [input]。 */
    fun newReader(input: InputStream, backupKey: SecretKey): Reader = Reader(input, backupKey)

    // ---------- 辅助 ----------

    private const val MAX_HEADER_BYTES: Int = 32 * 1024 * 1024

    internal fun buildHeaderJson(base: HeaderBase, assets: List<AssetHeader>): String {
        val kdf = JSONObject()
            .put("algorithm", base.kdfAlgorithm)
            .put("saltHex", base.kdfSaltHex)
            .put("iterations", base.kdfIterations)
            .put("memoryKb", base.kdfMemoryKb)
            .put("parallelism", base.kdfParallelism)
        val assetArr = JSONArray()
        assets.forEach { a ->
            val range = JSONObject()
                .put("fromFrame", a.fromFrame)
                .put("count", a.frameCount)
            assetArr.put(
                JSONObject()
                    .put("relativePath", a.relativePath)
                    .put("sha256Hex", a.sha256Hex)
                    .put("sizeBytes", a.sizeBytes)
                    .put("chunkRange", range),
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("backupId", base.backupId)
            .put("createdAtMs", base.createdAtMs)
            .put("kind", base.kind.name)
            .put("kdfParams", kdf)
            .put("keyFingerprintHex", base.keyFingerprintHex)
            .put("cipher", CIPHER_SUITE)
            .put("assets", assetArr)
            .toString()
    }

    internal fun parseHeaderJson(raw: String): Header {
        val root = JSONObject(raw)
        val version = root.getInt("version")
        val kdf = root.getJSONObject("kdfParams")
        val assetsArr = root.getJSONArray("assets")
        val assets = buildList {
            for (i in 0 until assetsArr.length()) {
                val a = assetsArr.getJSONObject(i)
                val range = a.getJSONObject("chunkRange")
                add(
                    AssetHeader(
                        relativePath = a.getString("relativePath"),
                        sha256Hex = a.getString("sha256Hex"),
                        sizeBytes = a.getLong("sizeBytes"),
                        fromFrame = range.getInt("fromFrame"),
                        frameCount = range.getInt("count"),
                    ),
                )
            }
        }
        return Header(
            version = version,
            backupId = root.getString("backupId"),
            createdAtMs = root.getLong("createdAtMs"),
            kind = Kind.valueOf(root.getString("kind")),
            kdfAlgorithm = kdf.getString("algorithm"),
            kdfSaltHex = kdf.getString("saltHex"),
            kdfIterations = kdf.getInt("iterations"),
            kdfMemoryKb = kdf.getInt("memoryKb"),
            kdfParallelism = kdf.getInt("parallelism"),
            keyFingerprintHex = root.getString("keyFingerprintHex"),
            cipher = root.optString("cipher", CIPHER_SUITE),
            assets = assets,
        )
    }

    private fun int32ToBytes(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(),
        (v ushr 16).toByte(),
        (v ushr 8).toByte(),
        v.toByte(),
    )

    private fun writeInt32BE(out: OutputStream, v: Int) {
        out.write(int32ToBytes(v))
    }

    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytesWritten += 1
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytesWritten += len.toLong()
        }

        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }
}
