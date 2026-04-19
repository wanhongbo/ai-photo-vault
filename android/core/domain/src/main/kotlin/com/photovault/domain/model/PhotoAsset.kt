package com.photovault.domain.model

/**
 * 私密照片元数据（密文路径在应用私有目录，不入系统相册）。
 */
data class PhotoAsset(
    val id: Long,
    val albumId: Long,
    val encryptedPath: String,
    val thumbPath: String?,
    val metadataJson: String?,
    val createdAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
)
