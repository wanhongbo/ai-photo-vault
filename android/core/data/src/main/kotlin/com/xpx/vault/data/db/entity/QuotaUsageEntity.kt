package com.xpx.vault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 配额月度使用计数。每月一行，自然月切换时由 QuotaManagerImpl 新建记录。
 */
@Entity(
    tableName = "quota_usage",
    indices = [Index(value = ["year_month"], unique = true)],
)
data class QuotaUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 格式: "2026-05" 表示 2026 年 5 月。 */
    @ColumnInfo(name = "year_month") val yearMonth: String,
    /** 当月 AI 操作已使用次数。 */
    @ColumnInfo(name = "ai_count") val aiCount: Int = 0,
)
