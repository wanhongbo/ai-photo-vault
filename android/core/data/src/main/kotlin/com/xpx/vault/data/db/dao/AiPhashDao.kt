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

    @Query("DELETE FROM ai_phash WHERE photo_id = :photoId")
    suspend fun deleteByPhoto(photoId: Long)
}
