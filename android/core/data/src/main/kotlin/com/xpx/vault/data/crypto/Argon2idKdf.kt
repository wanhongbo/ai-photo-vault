package com.xpx.vault.data.crypto

import android.app.ActivityManager
import android.content.Context
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/**
 * Argon2id KDF wrapper backed by BouncyCastle's pure-Java implementation.
 *
 * 输入口令以 [CharArray] 传入，函数返回后调用方负责 [CharArray.fill] 清零；
 * 本工具在内部会将 char 转 UTF-8 字节，执行完毕立即将中间字节数组清零。
 */
object Argon2idKdf {
    /** 默认 time cost（iterations）。 */
    const val DEFAULT_ITERATIONS: Int = 3

    /** 默认 memory cost，单位 KB，对应 64 MB。 */
    const val DEFAULT_MEMORY_KB: Int = 64 * 1024

    /** 默认 parallelism。 */
    const val DEFAULT_PARALLELISM: Int = 1

    /** 低端机降级后的 memory cost，单位 KB，对应 32 MB。 */
    const val LOW_END_MEMORY_KB: Int = 32 * 1024

    /** 低端机降级后的 iterations。 */
    const val LOW_END_ITERATIONS: Int = 4

    /** 输出密钥长度（字节）。AES-256 需要 32 字节。 */
    const val DEFAULT_KEY_LENGTH_BYTES: Int = 32

    /** 根据设备内存判定是否使用低端机参数。 */
    fun chooseParams(context: Context): Pair<Int, Int> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClass = am?.memoryClass ?: 0
        return if (memoryClass in 1 until 128) {
            LOW_END_MEMORY_KB to LOW_END_ITERATIONS
        } else {
            DEFAULT_MEMORY_KB to DEFAULT_ITERATIONS
        }
    }

    /**
     * 使用 Argon2id 从口令派生定长密钥。
     *
     * @param password  口令 CharArray；调用方负责使用完毕后清零。
     * @param salt      盐字节数组，建议 ≥ 16B。
     * @param iterations time cost。
     * @param memoryKb   memory cost，单位 KB。
     * @param parallelism 线程并行数。
     * @param outLen     输出长度。
     */
    fun derive(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        memoryKb: Int = DEFAULT_MEMORY_KB,
        parallelism: Int = DEFAULT_PARALLELISM,
        outLen: Int = DEFAULT_KEY_LENGTH_BYTES,
    ): ByteArray {
        require(password.isNotEmpty()) { "password must not be empty" }
        require(salt.isNotEmpty()) { "salt must not be empty" }

        val passwordBytes = charsToUtf8Bytes(password)
        return try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKb)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build()
            val generator = Argon2BytesGenerator().apply { init(params) }
            val out = ByteArray(outLen)
            generator.generateBytes(passwordBytes, out)
            out
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun charsToUtf8Bytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        // 清理 ByteBuffer 底层数组中残留的字节
        if (byteBuffer.hasArray()) {
            val arr = byteBuffer.array()
            val offset = byteBuffer.arrayOffset()
            for (i in offset until arr.size) arr[i] = 0
        }
        return bytes
    }
}
