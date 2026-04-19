package com.photovault.domain.model

/**
 * 回收站条目（30 天过期，与产品约定一致）。
 */
data class TrashItem(
    val photoId: Long,
    val expireAtEpochMs: Long,
)
