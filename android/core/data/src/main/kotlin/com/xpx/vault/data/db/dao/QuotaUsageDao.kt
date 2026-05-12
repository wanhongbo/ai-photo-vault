package com.xpx.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xpx.vault.data.db.entity.QuotaUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuotaUsageDao {

    @Query("SELECT * FROM quota_usage WHERE year_month = :yearMonth LIMIT 1")
    suspend fun findByMonth(yearMonth: String): QuotaUsageEntity?

    @Query("SELECT COALESCE(ai_count, 0) FROM quota_usage WHERE year_month = :yearMonth")
    fun observeAiCount(yearMonth: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: QuotaUsageEntity): Long

    @Query("UPDATE quota_usage SET ai_count = ai_count + 1 WHERE year_month = :yearMonth")
    suspend fun incrementAiCount(yearMonth: String): Int
}
