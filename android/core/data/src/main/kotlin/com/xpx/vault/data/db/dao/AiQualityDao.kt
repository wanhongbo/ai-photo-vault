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

    /**
     * 批量清除全表重复标记。重扫聚类前先清零，避免上一轮残留的 is_duplicate=1
     * 在本轮已不构成重复簇时仍被保留（典型场景：用户删除了原件、阈值调整、
     * 代表张顺序变化等导致的悬挂脏标记）。
     */
    @Query("UPDATE ai_quality SET is_duplicate = 0, duplicate_group_id = NULL WHERE is_duplicate = 1")
    suspend fun clearAllDuplicateFlags()
}
