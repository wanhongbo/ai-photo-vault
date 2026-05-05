package com.xpx.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xpx.vault.data.db.entity.AiQualityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiQualityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiQualityEntity)

    @Query("SELECT * FROM ai_quality WHERE is_blurry = 1")
    fun observeBlurry(): Flow<List<AiQualityEntity>>

    @Query("SELECT * FROM ai_quality WHERE is_duplicate = 1")
    fun observeDuplicates(): Flow<List<AiQualityEntity>>

    @Query("SELECT * FROM ai_quality WHERE photo_id = :photoId")
    suspend fun findByPhoto(photoId: Long): AiQualityEntity?

    @Query("DELETE FROM ai_quality WHERE photo_id = :photoId")
    suspend fun deleteByPhoto(photoId: Long)
}
