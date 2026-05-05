package com.xpx.vault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 图像质量表（每张照片唯一一条）。
 * photoId 由 path hash 生成（sha1(encryptedPath).toLong()），不依赖 photo_assets。
 */
@Entity(
    tableName = "ai_quality",
    indices = [
        Index(value = ["is_blurry"]),
        Index(value = ["is_duplicate"]),
        Index(value = ["duplicate_group_id"]),
    ],
)
data class AiQualityEntity(
    @PrimaryKey @ColumnInfo(name = "photo_id") val photoId: Long,
    @ColumnInfo(name = "sharpness") val sharpness: Float,
    @ColumnInfo(name = "brightness") val brightness: Float,
    @ColumnInfo(name = "is_blurry") val isBlurry: Boolean,
    @ColumnInfo(name = "is_over_exposed") val isOverExposed: Boolean,
    @ColumnInfo(name = "is_duplicate") val isDuplicate: Boolean,
    @ColumnInfo(name = "duplicate_group_id") val duplicateGroupId: Long?,
)
