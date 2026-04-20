package com.photovault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.photovault.data.db.entity.SecuritySettingEntity

@Dao
interface SecuritySettingDao {
    @Query("SELECT * FROM security_settings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long = SecuritySettingEntity.SINGLETON_ID): SecuritySettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SecuritySettingEntity)
}
