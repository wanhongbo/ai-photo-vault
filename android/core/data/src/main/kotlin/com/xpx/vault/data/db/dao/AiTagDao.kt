package com.xpx.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xpx.vault.data.db.entity.AiTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AiTagEntity>)

    @Query("DELETE FROM ai_tag WHERE photo_id = :photoId")
    suspend fun deleteByPhoto(photoId: Long)

    @Query("SELECT * FROM ai_tag WHERE category = :category ORDER BY confidence DESC")
    fun observeByCategory(category: String): Flow<List<AiTagEntity>>
}
