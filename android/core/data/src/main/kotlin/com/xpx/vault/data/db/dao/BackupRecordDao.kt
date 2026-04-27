package com.xpx.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xpx.vault.data.db.entity.BackupRecordEntity

@Dao
interface BackupRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BackupRecordEntity): Long

    @Query("SELECT * FROM backup_records ORDER BY created_at_ms DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<BackupRecordEntity>

    @Query("DELETE FROM backup_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
