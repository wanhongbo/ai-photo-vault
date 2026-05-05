package com.xpx.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xpx.vault.data.db.entity.AiSensitiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiSensitiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiSensitiveEntity): Long

    @Query("SELECT COUNT(*) FROM ai_sensitive WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM ai_sensitive WHERE status = 'pending' ORDER BY created_at_ms DESC")
    fun observePending(): Flow<List<AiSensitiveEntity>>

    @Query("UPDATE ai_sensitive SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM ai_sensitive WHERE photo_id = :photoId")
    suspend fun deleteByPhoto(photoId: Long)
}
