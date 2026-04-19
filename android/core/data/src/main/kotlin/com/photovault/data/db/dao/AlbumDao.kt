package com.photovault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.photovault.data.db.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AlbumEntity): Long

    @Query("SELECT * FROM albums ORDER BY updated_at_ms DESC")
    fun observeAll(): Flow<List<AlbumEntity>>
}
