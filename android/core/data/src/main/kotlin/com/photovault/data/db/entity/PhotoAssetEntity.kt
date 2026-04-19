package com.photovault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photo_assets",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["album_id"]),
        Index(value = ["deleted_at_ms"]),
    ],
)
data class PhotoAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "encrypted_path") val encryptedPath: String,
    @ColumnInfo(name = "thumb_path") val thumbPath: String?,
    @ColumnInfo(name = "metadata_json") val metadataJson: String?,
    @ColumnInfo(name = "created_at_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "deleted_at_ms") val deletedAtEpochMs: Long?,
)
