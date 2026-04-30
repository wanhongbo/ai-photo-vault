package com.xpx.vault.data.crypto

import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * AES-256-CBC + PKCS7，IV 前置（16 字节），与一期产品「照片 AES-256-CBC」约定一致。
 * 主密钥由 [KeystoreSecretKeyProvider] 托管于 Android Keystore。
 *
 * 注意：Android Keystore 的 HW-backed 密钥对单次 [Cipher.doFinal] 有大小上限（~1-2MB），
 * 超过该阈值可能抛出 NullPointerException("Attempt to get length of null array")；
 * 对大文件请用 [decryptStream]/[encryptStream] 流式 API。
 *
 * TODO(vault-encryption)：一期 MVP 未将 [VaultStore] / 相机写入接入本引擎，因此目前 vault_albums/ 实际
 * 为明文存储；备份/恢复为了配合这个现实已替换为明文直通路径，待写/读路径接入后再恢复调用。
 */
class AesCbcEngine(
    private val secretKey: SecretKey,
) {
    private val secureRandom = SecureRandom()

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val cipherText = cipher.doFinal(plain)
        return iv + cipherText
    }

    fun decrypt(ivAndCipherText: ByteArray): ByteArray {
        require(ivAndCipherText.size > IV_LENGTH) { "invalid payload" }
        val iv = ivAndCipherText.copyOfRange(0, IV_LENGTH)
        val cipherBytes = ivAndCipherText.copyOfRange(IV_LENGTH, ivAndCipherText.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        return cipher.doFinal(cipherBytes)
    }

    /**
     * 流式解密：先读 16B IV，再按 [readBufferBytes] 为单位调用 cipher.update，
     * 最后一次 doFinal 输出尾块。每输出一块明文就回调 [sink]。
     *
     * @param input 未关闭；由调用方自行关闭。
     */
    fun decryptStream(
        input: InputStream,
        readBufferBytes: Int = DEFAULT_STREAM_BUFFER,
        sink: (data: ByteArray, offset: Int, length: Int) -> Unit,
    ) {
        val ivBuf = ByteArray(IV_LENGTH)
        readFully(input, ivBuf)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ivBuf))
        val inBuf = ByteArray(readBufferBytes)
        while (true) {
            val n = input.read(inBuf)
            if (n <= 0) break
            val out = cipher.update(inBuf, 0, n)
            if (out != null && out.isNotEmpty()) sink(out, 0, out.size)
        }
        val tail = cipher.doFinal()
        if (tail != null && tail.isNotEmpty()) sink(tail, 0, tail.size)
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
        /** PKCS5Padding 与 AES 在 JVM / Android 上均可用，等价于 PKCS7。 */
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val IV_LENGTH = 16
        private const val DEFAULT_STREAM_BUFFER = 64 * 1024
    }
}
