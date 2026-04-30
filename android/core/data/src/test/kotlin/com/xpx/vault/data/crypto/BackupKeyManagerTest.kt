package com.xpx.vault.data.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BackupKeyManager 使用 Context 读写 SharedPreferences 持久化 salt / kdfParams，所以用 Robolectric。
 *
 * 覆盖：
 * - 首次调用会初始化 salt + kdfParams，之后调用返回相同值。
 * - 同 salt/同口令派生出来的 BackupKey + fingerprint 稳定。
 * - 不同口令下 fingerprint 不同。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupKeyManagerTest {

    private lateinit var context: Context
    private lateinit var keyManager: BackupKeyManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 每次测试之间清空 prefs，避免跨测试污染。
        context.getSharedPreferences("backup_kdf_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        keyManager = BackupKeyManager(context)
    }

    @Test
    fun getOrCreateKdfParams_isStableAcrossCalls() {
        val a = keyManager.getOrCreateKdfParams()
        val b = keyManager.getOrCreateKdfParams()
        assertThat(a.saltHex).isEqualTo(b.saltHex)
        assertThat(a.memoryKb).isEqualTo(b.memoryKb)
        assertThat(a.iterations).isEqualTo(b.iterations)
        assertThat(a.parallelism).isEqualTo(b.parallelism)
        assertThat(a.saltHex).hasLength(64) // 32 byte = 64 hex chars
    }

    @Test
    fun deriveKey_sameInputsYieldSameFingerprint() {
        val params = keyManager.getOrCreateKdfParams()
            .copy(iterations = 1, memoryKb = 8 * 1024, parallelism = 1)
        val m1 = keyManager.deriveKey("abcdef".toCharArray(), params)
        val m2 = keyManager.deriveKey("abcdef".toCharArray(), params)
        assertThat(m1.fingerprintHex).isEqualTo(m2.fingerprintHex)
        assertThat(m1.fingerprintHex).hasLength(32) // 16 byte = 32 hex chars
    }

    @Test
    fun deriveKey_differentPinsYieldDifferentFingerprints() {
        val params = keyManager.getOrCreateKdfParams()
            .copy(iterations = 1, memoryKb = 8 * 1024, parallelism = 1)
        val m1 = keyManager.deriveKey("111111".toCharArray(), params)
        val m2 = keyManager.deriveKey("222222".toCharArray(), params)
        assertThat(m1.fingerprintHex).isNotEqualTo(m2.fingerprintHex)
    }
}
