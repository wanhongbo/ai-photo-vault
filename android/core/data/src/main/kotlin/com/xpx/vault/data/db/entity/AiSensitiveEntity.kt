package com.xpx.vault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 敏感命中记录。同一 photoId 可有多个子类（如同时命中身份证+二维码）。
 * photoId 由 path hash 生成（sha1(encryptedPath).toLong()），不依赖 photo_assets。
 */
@Entity(
    tableName = "ai_sensitive",
    indices = [
        Index(value = ["photo_id", "kind"], unique = true),
        Index(value = ["status"]),
    ],
)
data class AiSensitiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "photo_id") val photoId: Long,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "confidence") val confidence: Float,
    @ColumnInfo(name = "regions_json") val regionsJson: String?,
    @ColumnInfo(name = "status") val status: String, // pending / moved / ignored
    @ColumnInfo(name = "created_at_ms") val createdAtEpochMs: Long,
)
