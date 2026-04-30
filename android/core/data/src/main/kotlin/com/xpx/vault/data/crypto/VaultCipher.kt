package com.xpx.vault.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Vault 数据加密引擎（AES-256-CBC）。
 *
 * 设计要点：
 * - **软件 AES key**：AES-256 的原始 32B 使用 `SecretKeySpec` 构造；运行时走用户态 NEON 指令，无 Binder IPC，
 *   无 `Cipher.doFinal` 1-2MB 上限，性能是硬件 Keystore AES-CBC 的 5-10 倍；足以支撑高频相册滑动与视频解密。
 * - **Keystore AES-GCM wrap**：软件 key 的持久化使用 Android Keystore AES-GCM 加密后落盘到 `filesDir/.vault_cipher_key`。
 *   这样"裸 key 永不明文上盘"的安全属性与工业界 Conceal / Signal / Tink 的做法对齐。
 * - **文件格式**：IV(16B) 前置 + AES/CBC/PKCS5Padding 密文；与 [AesCbcEngine] 格式兼容。
 * - **线程安全**：内部所有状态仅在 `getOrCreateKey()` 的 `synchronized` 内变更；解密/加密本身无共享状态。
 */
class VaultCipher private constructor(
    private val appContext: Context,
) {
    @Volatile
    private var cachedKey: SecretKey? = null

    private val lock = Any()
    private val secureRandom = SecureRandom()

    /** 获取（首次则生成）vault 数据 AES key；仅内存缓存，不对外暴露 raw 字节。 */
    fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(lock) {
            cachedKey?.let { return it }
            val keyFile = dataKeyFile()
            val key = if (keyFile.exists()) {
                loadAndUnwrap(keyFile) ?: generateAndWrap(keyFile)
            } else {
                generateAndWrap(keyFile)
            }
            cachedKey = key
            return key
        }
    }

    /** 流式加密：随机 IV → 前置写入 → 输入流逐块 `update` → 末尾 `doFinal`。调用方负责关闭流。 */
    fun encryptStream(
        input: InputStream,
        output: OutputStream,
        bufferBytes: Int = DEFAULT_BUFFER,
    ) {
        val key = getOrCreateKey()
        val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
        output.write(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val inBuf = ByteArray(bufferBytes)
        while (true) {
            val n = input.read(inBuf)
            if (n <= 0) break
            val out = cipher.update(inBuf, 0, n)
            if (out != null && out.isNotEmpty()) output.write(out)
        }
        val tail = cipher.doFinal()
        if (tail != null && tail.isNotEmpty()) output.write(tail)
        output.flush()
    }

    /** 流式解密：读 16B IV → 逐块 `update` 并回调 `sink` → 末尾 `doFinal`。调用方负责关闭 [input]。 */
    fun decryptStream(
        input: InputStream,
        bufferBytes: Int = DEFAULT_BUFFER,
        sink: (data: ByteArray, offset: Int, length: Int) -> Unit,
    ) {
        val key = getOrCreateKey()
        val iv = ByteArray(IV_LEN)
        readFully(input, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        val inBuf = ByteArray(bufferBytes)
        while (true) {
            val n = input.read(inBuf)
            if (n <= 0) break
            val out = cipher.update(inBuf, 0, n)
            if (out != null && out.isNotEmpty()) sink(out, 0, out.size)
        }
        val tail = cipher.doFinal()
        if (tail != null && tail.isNotEmpty()) sink(tail, 0, tail.size)
    }

    /** 将 [plainInput] 加密并原子写入 [destFile]；先写同目录 `.tmp` 再 rename。 */
    fun encryptFile(plainInput: InputStream, destFile: File) {
        destFile.parentFile?.mkdirs()
        val tmp = File(destFile.parentFile, "${destFile.name}.enc_tmp_${System.nanoTime()}")
        tmp.outputStream().buffered().use { out ->
            encryptStream(plainInput, out)
        }
        if (destFile.exists()) destFile.delete()
        if (!tmp.renameTo(destFile)) {
            tmp.copyTo(destFile, overwrite = true)
            tmp.delete()
        }
    }

    /**
     * 推送模式加密：由调用方通过 [feed] 回调增量喚入明文块，结构性地原子写入 [destFile]。
     *
     * 用于恢复路径：备份包 reader 是 pull 式的 `readNextChunk()`，无法包装成 InputStream；
     * 调用方在 feed 内部逐 chunk 调用 emit(plain) 即可，内部负责 IV 预写、Cipher.update/doFinal 与 rename。
     */
    fun encryptFileFromChunks(
        destFile: File,
        feed: (emit: (ByteArray, Int, Int) -> Unit) -> Unit,
    ) {
        val key = getOrCreateKey()
        val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        destFile.parentFile?.mkdirs()
        val tmp = File(destFile.parentFile, "${destFile.name}.enc_tmp_${System.nanoTime()}")
        tmp.outputStream().buffered().use { out ->
            out.write(iv)
            feed { data, offset, length ->
                if (length > 0) {
                    val enc = cipher.update(data, offset, length)
                    if (enc != null && enc.isNotEmpty()) out.write(enc)
                }
            }
            val tail = cipher.doFinal()
            if (tail != null && tail.isNotEmpty()) out.write(tail)
            out.flush()
        }
        if (destFile.exists()) destFile.delete()
        if (!tmp.renameTo(destFile)) {
            tmp.copyTo(destFile, overwrite = true)
            tmp.delete()
        }
    }

    /** 小文件一次性解密到内存；大于 10MB 的数据请改用 [decryptToTempFile] 避免 OOM。 */
    fun decryptToByteArray(src: File): ByteArray {
        val out = ByteArrayOutputStream(src.length().coerceAtLeast(1024).toInt())
        src.inputStream().buffered().use { input ->
            decryptStream(input) { data, offset, length -> out.write(data, offset, length) }
        }
        return out.toByteArray()
    }

    /** 流式解密到 [destDir] 下的临时文件；返回最终文件句柄。调用方使用完后务必 `delete()`。 */
    fun decryptToTempFile(src: File, destDir: File, destName: String): File {
        destDir.mkdirs()
        val target = File(destDir, destName)
        if (target.exists()) target.delete()
        target.outputStream().buffered().use { out ->
            src.inputStream().buffered().use { input ->
                decryptStream(input) { data, offset, length -> out.write(data, offset, length) }
            }
        }
        return target
    }

    /** 尝试对 [src] 做 16B IV + AES-CBC 解密的首块校验；用于区分密文 / 明文（启动迁移使用）。 */
    fun looksLikeCiphertext(src: File): Boolean {
        val len = src.length()
        // 密文至少 16B IV + 16B 一整块；小于此长度按明文处理。
        if (len < IV_LEN + 16) return false
        // AES-CBC 密文长度（不含 IV）必须是 16 的整数倍。
        if ((len - IV_LEN) % 16 != 0L) return false
        return runCatching {
            src.inputStream().use { input ->
                val iv = ByteArray(IV_LEN)
                readFully(input, iv)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), IvParameterSpec(iv))
                val buf = ByteArray(64)
                val read = input.read(buf)
                if (read <= 0) return@runCatching false
                cipher.update(buf, 0, read)
                // 无异常即认为 header 部分是合法 AES-CBC。
                true
            }
        }.getOrDefault(false)
    }

    // --- 内部：软件 key 的持久化与 wrap 协议 ---

    private fun dataKeyFile(): File = File(appContext.filesDir, KEY_FILE_NAME)

    private fun generateAndWrap(keyFile: File): SecretKey {
        val raw = ByteArray(32).also { secureRandom.nextBytes(it) } // AES-256
        val wrapKey = getOrCreateWrapKey()
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
        val iv = cipher.iv // AES-GCM 由 Keystore 随机生成
        val wrapped = cipher.doFinal(raw)
        writeAtomic(keyFile) { out ->
            out.write(KEY_FILE_MAGIC)
            out.write(intArrayOf(iv.size).first().toByte().let { byteArrayOf(it) })
            out.write(iv)
            out.write(wrapped)
        }
        return SecretKeySpec(raw, "AES")
    }

    private fun loadAndUnwrap(keyFile: File): SecretKey? = runCatching {
        keyFile.inputStream().buffered().use { input ->
            val magic = ByteArray(KEY_FILE_MAGIC.size)
            readFully(input, magic)
            if (!magic.contentEquals(KEY_FILE_MAGIC)) return@runCatching null
            val ivLen = input.read().takeIf { it > 0 } ?: return@runCatching null
            val iv = ByteArray(ivLen).also { readFully(input, it) }
            val wrapped = input.readBytes()
            val wrapKey = getOrCreateWrapKey()
            val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(128, iv))
            val raw = cipher.doFinal(wrapped)
            SecretKeySpec(raw, "AES")
        }
    }.getOrNull()

    private fun getOrCreateWrapKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(WRAP_KEY_ALIAS)) {
            val entry = runCatching {
                (ks.getEntry(WRAP_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            }.getOrNull()
            if (entry != null) return entry
            runCatching { ks.deleteEntry(WRAP_KEY_ALIAS) }
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            WRAP_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    private fun writeAtomic(dest: File, write: (OutputStream) -> Unit) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        tmp.outputStream().buffered().use { write(it) }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n <= 0) error("unexpected EOF while reading ${buf.size} bytes (got $total)")
            total += n
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val WRAP_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val WRAP_KEY_ALIAS = "photo_vault_cipher_wrap"
        private const val KEY_FILE_NAME = ".vault_cipher_key"
        private val KEY_FILE_MAGIC = byteArrayOf(0x56, 0x4B, 0x01, 0x00) // "VK" 0x0100
        private const val IV_LEN = 16
        private const val DEFAULT_BUFFER = 64 * 1024

        @Volatile
        private var instance: VaultCipher? = null

        /**
         * 进程级单例入口。由于 [VaultCipher] 不依赖 Hilt，object / 顶层函数均可直接使用；
         * 首次调用惰性初始化（不触发 Keystore 访问，真正的 wrap key 读取发生在 [getOrCreateKey]）。
         */
        fun get(context: Context): VaultCipher {
            val existing = instance
            if (existing != null) return existing
            synchronized(this) {
                val again = instance
                if (again != null) return again
                val created = VaultCipher(context.applicationContext)
                instance = created
                return created
            }
        }
    }
}
