package com.xpx.vault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 照片 AI 标签表。同一 photoId 可能有多个标签（不同分类）。
 * photoId 由 path hash 生成（sha1(encryptedPath).toLong()），不依赖 photo_assets 表。
 */
@Entity(
    tableName = "ai_tag",
    indices = [
        Index(value = ["photo_id"]),
        Index(value = ["category"]),
        Index(value = ["photo_id", "label"], unique = true),
    ],
)
data class AiTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "photo_id") val photoId: Long,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "confidence") val confidence: Float,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "created_at_ms") val createdAtEpochMs: Long,
)
