package com.xpx.vault.data.crypto

import java.security.MessageDigest

/** 口令哈希：SHA-256；可配合安装级 salt，不落明文口令。 */
object PasswordHasher {
    private const val SHA_256 = "SHA-256"

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(SHA_256).digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun sha256HexOfUtf8(string: String): String =
        sha256Hex(string.toByteArray(Charsets.UTF_8))

    /**
     * SHA-256(salt || passwordBytes)，用于 PIN 等口令存储。
     */
    fun sha256HexWithSalt(passwordUtf8: String, salt: ByteArray): String {
        val pwd = passwordUtf8.toByteArray(Charsets.UTF_8)
        val combined = salt + pwd
        return sha256Hex(combined)
    }
}
