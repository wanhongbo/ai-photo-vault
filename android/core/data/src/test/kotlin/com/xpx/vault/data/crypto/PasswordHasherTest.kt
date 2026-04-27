package com.xpx.vault.data.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun sha256Hex_matchesKnownVector() {
        val empty = PasswordHasher.sha256HexOfUtf8("")
        assertThat(empty).isEqualTo(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        )
    }

    @Test
    fun sha256HexWithSalt_isDeterministic() {
        val salt = byteArrayOf(1, 2, 3)
        val a = PasswordHasher.sha256HexWithSalt("1234", salt)
        val b = PasswordHasher.sha256HexWithSalt("1234", salt)
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(PasswordHasher.sha256HexWithSalt("1235", salt))
    }
}
