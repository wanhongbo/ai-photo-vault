package com.photovault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trash_items",
    foreignKeys = [
        ForeignKey(
            entity = PhotoAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["photo_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["expire_at_ms"])],
)
data class TrashItemEntity(
    @PrimaryKey @ColumnInfo(name = "photo_id") val photoId: Long,
    @ColumnInfo(name = "expire_at_ms") val expireAtEpochMs: Long,
)
