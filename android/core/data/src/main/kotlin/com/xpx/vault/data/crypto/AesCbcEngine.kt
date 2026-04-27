package com.xpx.vault.data.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * AES-256-CBC + PKCS7，IV 前置（16 字节），与一期产品「照片 AES-256-CBC」约定一致。
 * 主密钥由 [KeystoreSecretKeyProvider] 托管于 Android Keystore。
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

    companion object {
        /** PKCS5Padding 与 AES 在 JVM / Android 上均可用，等价于 PKCS7。 */
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val IV_LENGTH = 16
    }
}
