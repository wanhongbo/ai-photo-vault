package com.xpx.vault.data.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Argon2id 的最小可用性测试：
 * - 同输入产出稳定的 32 字节密钥。
 * - 不同 salt / 不同 password 产出不同密钥。
 * - 入参校验。
 */
class Argon2idKdfTest {

    // 使用 low-cost 参数让单测可以快速完成。
    private val iterations = 1
    private val memoryKb = 8 * 1024 // 8 MB
    private val parallelism = 1

    @Test
    fun derive_isDeterministic_forSameInputs() {
        val salt = ByteArray(16) { it.toByte() }
        val a = Argon2idKdf.derive("hello123".toCharArray(), salt, iterations, memoryKb, parallelism)
        val b = Argon2idKdf.derive("hello123".toCharArray(), salt, iterations, memoryKb, parallelism)
        assertThat(a).isEqualTo(b)
        assertThat(a.size).isEqualTo(Argon2idKdf.DEFAULT_KEY_LENGTH_BYTES)
    }

    @Test
    fun derive_differsForDifferentPasswords() {
        val salt = ByteArray(16) { it.toByte() }
        val a = Argon2idKdf.derive("foo".toCharArray(), salt, iterations, memoryKb, parallelism)
        val b = Argon2idKdf.derive("bar".toCharArray(), salt, iterations, memoryKb, parallelism)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun derive_differsForDifferentSalts() {
        val saltA = ByteArray(16) { 1 }
        val saltB = ByteArray(16) { 2 }
        val a = Argon2idKdf.derive("abc".toCharArray(), saltA, iterations, memoryKb, parallelism)
        val b = Argon2idKdf.derive("abc".toCharArray(), saltB, iterations, memoryKb, parallelism)
        assertThat(a).isNotEqualTo(b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun derive_rejectsEmptyPassword() {
        Argon2idKdf.derive(CharArray(0), ByteArray(8) { 1 }, iterations, memoryKb, parallelism)
    }

    @Test(expected = IllegalArgumentException::class)
    fun derive_rejectsEmptySalt() {
        Argon2idKdf.derive("abc".toCharArray(), ByteArray(0), iterations, memoryKb, parallelism)
    }
}
