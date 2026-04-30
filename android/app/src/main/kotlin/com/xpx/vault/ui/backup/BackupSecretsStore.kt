package com.xpx.vault.ui.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.xpx.vault.AppLogger
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 用 Android Keystore 中独立的 AES-GCM 包裹密钥，把 BackupKey 材料缓存到私有目录。
 *
 * Worker 无法弹解锁页，启动时读取缓存即可快速获得可用的 BackupKey；
 * 修改 PIN 时调用 [cache] 刷新；登出/清除会话时调用 [clear]。
 *
 * 缓存文件格式：`[IV(12B)] [cipherText || tag]`。
 */
object BackupSecretsStore {
    private const val WRAP_ALIAS = "photo_vault_backup_wrap_aes"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val CACHE_FILE_NAME = "backup_secret.bin"
    private const val TAG = "BackupSecretsStore"

    /** 是否存在有效的缓存。 */
    fun hasCached(context: Context): Boolean = cacheFile(context).exists()

    /** 覆盖写入 BackupKey 缓存。 */
    fun cache(context: Context, backupKey: SecretKey) {
        val keyBytes = backupKey.encoded
            ?: error("backupKey not extractable; expected raw SecretKeySpec")
        try {
            val wrapKey = getOrCreateWrapKey()
            val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, wrapKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val combined = cipher.doFinal(keyBytes)
            val out = cacheFile(context)
            out.parentFile?.mkdirs()
            out.outputStream().use { stream ->
                stream.write(iv)
                stream.write(combined)
                stream.flush()
            }
        } finally {
            // 擦除本地中间副本
            java.util.Arrays.fill(keyBytes, 0.toByte())
        }
    }

    /** 读取缓存并返回 SecretKey；解密失败返回 null 并自动清空。 */
    fun loadCached(context: Context): SecretKey? {
        val file = cacheFile(context)
        if (!file.exists()) return null
        return runCatching {
            val bytes = file.readBytes()
            require(bytes.size > IV_LENGTH) { "corrupt cache file" }
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val body = bytes.copyOfRange(IV_LENGTH, bytes.size)
            val wrapKey = getOrCreateWrapKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val keyBytes = cipher.doFinal(body)
            SecretKeySpec(keyBytes, "AES")
        }.onFailure {
            AppLogger.w(TAG, "loadCached failed, clearing: ${it.message}")
            clear(context)
        }.getOrNull()
    }

    /** 清空缓存文件。 */
    fun clear(context: Context) {
        cacheFile(context).delete()
    }

    /** 清空缓存并删除 Keystore 中的包裹密钥（用于完整重置）。 */
    fun destroy(context: Context) {
        clear(context)
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(WRAP_ALIAS)) ks.deleteEntry(WRAP_ALIAS)
        }
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE_NAME)

    private fun getOrCreateWrapKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(WRAP_ALIAS)) {
            val existing = runCatching {
                (ks.getEntry(WRAP_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            }.getOrNull()
            if (existing != null) return existing
            runCatching { ks.deleteEntry(WRAP_ALIAS) }
        }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            WRAP_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }
}
