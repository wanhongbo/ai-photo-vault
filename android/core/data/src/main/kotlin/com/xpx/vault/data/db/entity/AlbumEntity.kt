package com.xpx.vault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [Index(value = ["updated_at_ms"])],
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "cover_photo_id") val coverPhotoId: Long?,
    @ColumnInfo(name = "created_at_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtEpochMs: Long,
)
