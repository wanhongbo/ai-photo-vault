package com.xpx.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xpx.vault.data.db.entity.SecuritySettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecuritySettingDao {
    @Query("SELECT * FROM security_settings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long = SecuritySettingEntity.SINGLETON_ID): SecuritySettingEntity?

    @Query("SELECT * FROM security_settings WHERE id = :id LIMIT 1")
    fun observeSingleton(id: Long = SecuritySettingEntity.SINGLETON_ID): Flow<SecuritySettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SecuritySettingEntity)
}
