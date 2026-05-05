package com.xpx.vault.ai.util

import java.security.MessageDigest

/**
 * 照片稳定 ID 生成器。
 *
 * 项目当前照片走 VaultStore 文件系统（非 Room），因此 AI 表不再维护 FK 到 photo_assets。
 * 通过 sha1(encryptedPath).toLong() 将文件路径哈希为 64-bit 稳定 ID：
 *  - 同一文件（路径不变）→ 每次计算结果一致，可作为 upsert 主键；
 *  - 不同文件碰撞概率极低（~2^-64）。
 *
 * 说明：此映射不可逆，反查需要由调用方同时保存 path 字符串（UI 展示使用 path）。
 */
object PhotoIdentity {

    fun fromPath(path: String): Long {
        val digest = MessageDigest.getInstance("SHA-1").digest(path.toByteArray(Charsets.UTF_8))
        // 取前 8 字节当作 Long（保持正负号，不刻意 abs 以保留完整 64bit 区分度）。
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (digest[i].toLong() and 0xFF)
        }
        return result
    }
}
