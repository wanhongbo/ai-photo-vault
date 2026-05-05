package com.xpx.vault.domain.repo

import com.xpx.vault.domain.model.AiPerceptualHash
import com.xpx.vault.domain.model.AiQualityRecord
import com.xpx.vault.domain.model.AiSensitiveRecord
import com.xpx.vault.domain.model.AiTag
import kotlinx.coroutines.flow.Flow

/**
 * AI 分析结果的持久化入口。data 层实现，feature/app 层依赖接口。
 *
 * 约定：所有写操作幂等——对同一 photoId 覆盖而非追加，避免重复分析造成数据膨胀。
 * UI 侧通过 `observe*` 返回 [Flow] 订阅实时更新。
 */
interface AiAnalysisRepository {

    // ---- Tags ----
    suspend fun upsertTags(photoId: Long, tags: List<AiTag>)
    fun observeTagsByCategory(category: String): Flow<List<AiTag>>

    // ---- Perceptual Hash ----
    suspend fun upsertPerceptualHash(hash: AiPerceptualHash)
    suspend fun listAllPerceptualHashes(): List<AiPerceptualHash>

    /** 仅拉取已扫过的 photoId 集合，用于增量扫描差集判定（比完整 phash 便宜得多）。 */
    suspend fun listAllScannedPhotoIds(): List<Long>

    /**
     * 在单个数据库事务中执行 [block]。用于把一次扫描结果的多条 upsert 合并成单事务，
     * 把 N 次 fsync 压到 1 次，显著降低写入延迟。
     */
    suspend fun <R> runInTransaction(block: suspend () -> R): R

    // ---- Quality ----
    suspend fun upsertQuality(record: AiQualityRecord)
    fun observeBlurry(): Flow<List<AiQualityRecord>>
    fun observeDuplicates(): Flow<List<AiQualityRecord>>
    suspend fun findQualityByPhoto(photoId: Long): AiQualityRecord?
    suspend fun clearQualityForPhoto(photoId: Long)

    /** 批量清除全表 is_duplicate 标记。重新聚类前调用，避免悬挂的脏标记。 */
    suspend fun clearAllDuplicateFlags()

    /**
     * 彻底清除一张照片的所有 AI 分析遗留（phash / quality / tag / sensitive）。
     * 用于孤儿数据清理：照片已在 Vault 中删除，但旧的 AI 表记录残留，
     * 导致聚类 / 分类 / 清理页拿到查不到文件的 photoId。
     */
    suspend fun purgePhoto(photoId: Long)

    // ---- Sensitive ----
    suspend fun upsertSensitive(record: AiSensitiveRecord): Long
    fun observePendingSensitiveCount(): Flow<Int>
    fun observePendingSensitive(): Flow<List<AiSensitiveRecord>>
    suspend fun updateSensitiveStatus(id: Long, status: String)
    suspend fun clearSensitiveForPhoto(photoId: Long)
}
