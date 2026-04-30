package com.xpx.vault.data.crypto

import android.content.Context
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 备份密钥管理器：
 * - 通过 Argon2id（回退策略可替换）从 PIN 口令 + 设备盐派生 AES-256 BackupKey。
 * - 计算 keyFingerprint = HMAC-SHA256(backupKey, "aivault.fp.v1")[:16]，用于判断备份密码是否变更。
 *
 * 盐值 Salt 首次安装时随机生成 32 字节，持久化在 [EncryptedSharedPreferences]。
 * 导出的 [KdfParams] 仅包含参数，不包含任何密钥/口令字节，可以安全写入备份包头。
 */
class BackupKeyManager(
    private val context: Context,
) {
    /** Kdf 参数。可随备份包头一同持久化。 */
    data class KdfParams(
        val algorithm: String = ALGO_ARGON2ID,
        val saltHex: String,
        val iterations: Int,
        val memoryKb: Int,
        val parallelism: Int,
    )

    /** 派生结果：密钥 + 指纹 + 所用参数（供写入包头）。 */
    data class BackupKeyMaterial(
        val key: SecretKey,
        val fingerprintHex: String,
        val kdfParams: KdfParams,
    )

    /**
     * 读取或初始化 kdf 参数。salt 随机 32B，按设备自适应降级 memory/iterations。
     * 多次调用返回同一 salt。
     */
    fun getOrCreateKdfParams(): KdfParams {
        val prefs = prefs()
        val existingSalt = prefs.getString(KEY_SALT_HEX, null)
        val saltHex = existingSalt ?: run {
            val bytes = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
            val hex = bytes.toHex()
            prefs.edit().putString(KEY_SALT_HEX, hex).apply()
            hex
        }
        val existingMem = prefs.getInt(KEY_MEMORY_KB, 0)
        val existingIter = prefs.getInt(KEY_ITERATIONS, 0)
        val existingPar = prefs.getInt(KEY_PARALLELISM, 0)
        return if (existingMem > 0 && existingIter > 0 && existingPar > 0) {
            KdfParams(
                algorithm = prefs.getString(KEY_ALGORITHM, ALGO_ARGON2ID) ?: ALGO_ARGON2ID,
                saltHex = saltHex,
                iterations = existingIter,
                memoryKb = existingMem,
                parallelism = existingPar,
            )
        } else {
            val (memKb, iterations) = Argon2idKdf.chooseParams(context)
            val params = KdfParams(
                algorithm = ALGO_ARGON2ID,
                saltHex = saltHex,
                iterations = iterations,
                memoryKb = memKb,
                parallelism = Argon2idKdf.DEFAULT_PARALLELISM,
            )
            prefs.edit()
                .putString(KEY_ALGORITHM, params.algorithm)
                .putInt(KEY_MEMORY_KB, params.memoryKb)
                .putInt(KEY_ITERATIONS, params.iterations)
                .putInt(KEY_PARALLELISM, params.parallelism)
                .apply()
            params
        }
    }

    /**
     * 派生 BackupKey。[password] 调用方负责用完后 fill(0)。
     */
    fun deriveKey(password: CharArray, params: KdfParams): BackupKeyMaterial {
        val salt = params.saltHex.hexToBytes()
        val keyBytes = Argon2idKdf.derive(
            password = password,
            salt = salt,
            iterations = params.iterations,
            memoryKb = params.memoryKb,
            parallelism = params.parallelism,
            outLen = Argon2idKdf.DEFAULT_KEY_LENGTH_BYTES,
        )
        val key = SecretKeySpec(keyBytes, "AES")
        // 立即清零本地副本；SecretKeySpec 内部会持有自己的 copy。
        keyBytes.fill(0)
        return BackupKeyMaterial(
            key = key,
            fingerprintHex = fingerprint(key),
            kdfParams = params,
        )
    }

    /** keyFingerprint = HMAC-SHA256(backupKey, "aivault.fp.v1")[:16] hex。 */
    fun fingerprint(key: SecretKey): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val full = mac.doFinal(FP_DOMAIN_SEPARATOR.toByteArray(Charsets.UTF_8))
        return full.copyOf(FINGERPRINT_LENGTH_BYTES).toHex()
    }

    // Salt 与 kdf 参数本身会随备份包头明文写出，不属于秘密，故使用普通 SharedPreferences，避免 AndroidX Security Crypto 的 MasterKey API 兼容性问题。
    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val ALGO_ARGON2ID: String = "Argon2id"
        private const val PREFS_NAME = "backup_kdf_prefs"
        private const val KEY_SALT_HEX = "salt_hex"
        private const val KEY_ALGORITHM = "algorithm"
        private const val KEY_MEMORY_KB = "memory_kb"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_PARALLELISM = "parallelism"
        private const val SALT_LENGTH_BYTES = 32
        private const val FINGERPRINT_LENGTH_BYTES = 16
        private const val FP_DOMAIN_SEPARATOR = "aivault.fp.v1"

        fun ByteArray.toHex(): String = joinToString("") { b -> "%02x".format(b) }

        fun String.hexToBytes(): ByteArray {
            require(length % 2 == 0) { "hex string length must be even" }
            val out = ByteArray(length / 2)
            for (i in out.indices) {
                out[i] = substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return out
        }
    }
}
