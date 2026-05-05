package com.xpx.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xpx.vault.data.db.entity.AiPhashEntity

@Dao
interface AiPhashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiPhashEntity)

    @Query("SELECT * FROM ai_phash")
    suspend fun listAll(): List<AiPhashEntity>

    /**
     * 只拉 photoId 列用于增量扫描的差集判定，避免把整张 phash 表拉进内存。
     */
    @Query("SELECT photo_id FROM ai_phash")
    suspend fun listAllPhotoIds(): List<Long>

    @Query("DELETE FROM ai_phash WHERE photo_id = :photoId")
    suspend fun deleteByPhoto(photoId: Long)
}
