package com.photovault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "backup_records",
    indices = [Index(value = ["created_at_ms"])],
)
data class BackupRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "created_at_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "version") val version: Int,
    @ColumnInfo(name = "checksum_hex") val checksumHex: String?,
)
