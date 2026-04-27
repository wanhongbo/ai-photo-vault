package com.xpx.vault.domain.model

/**
 * 解锁与安全相关持久化（口令仅存哈希）。
 */
data class SecuritySetting(
    val id: Long,
    val lockType: String,
    val pinHashHex: String?,
    val biometricEnabled: Boolean,
    val failCount: Int,
)
