package com.xpx.vault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 感知哈希表，每张照片仅一条（photoId 为主键）。
 * photoId 由 path hash 生成（sha1(encryptedPath).toLong()），不依赖 photo_assets。
 */
@Entity(tableName = "ai_phash")
data class AiPhashEntity(
    @PrimaryKey @ColumnInfo(name = "photo_id") val photoId: Long,
    @ColumnInfo(name = "phash") val phash: Long,
    @ColumnInfo(name = "dhash") val dhash: Long,
)
