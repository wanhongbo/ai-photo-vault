package com.xpx.vault.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 在 Android Keystore 中生成/读取 AES 主密钥，私钥材料不可导出。
 */
class KeystoreSecretKeyProvider(
    private val keyAlias: String = DEFAULT_ALIAS,
) {
    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun getOrCreateAesSecretKey(): SecretKey {
        if (keyStore.containsAlias(keyAlias)) {
            val existing = runCatching {
                (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            }.getOrNull()
            if (existing != null) return existing
            runCatching { keyStore.deleteEntry(keyAlias) }
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "photo_vault_master_aes"
    }
}
