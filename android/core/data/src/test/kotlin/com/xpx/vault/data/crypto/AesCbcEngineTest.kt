package com.xpx.vault.data.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class AesCbcEngineTest {
    @Test
    fun encryptThenDecrypt_roundTrips() {
        val keyBytes = ByteArray(32) { it.toByte() }
        val key = SecretKeySpec(keyBytes, "AES")
        val engine = AesCbcEngine(key)
        val plain = "phase-1 vault payload".toByteArray(Charsets.UTF_8)
        val cipher = engine.encrypt(plain)
        assertThat(cipher.size).isGreaterThan(16)
        assertThat(engine.decrypt(cipher)).isEqualTo(plain)
    }
}
