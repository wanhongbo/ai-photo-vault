package com.photovault.domain.model

/**
 * 备份包记录（加密 ZIP 路径与校验）。
 */
data class BackupRecord(
    val id: Long,
    val filePath: String,
    val createdAtEpochMs: Long,
    val version: Int,
    val checksumHex: String?,
)
