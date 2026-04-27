package com.xpx.vault.domain.model

/**
 * 自定义相册（与一期《开发计划与实施方案》2.3 数据模型对齐）。
 */
data class Album(
    val id: Long,
    val name: String,
    val coverPhotoId: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
