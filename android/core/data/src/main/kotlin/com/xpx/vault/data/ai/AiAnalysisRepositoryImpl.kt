package com.xpx.vault.data.ai

import com.xpx.vault.data.db.PhotoVaultDatabase
import com.xpx.vault.data.db.dao.AiPhashDao
import com.xpx.vault.data.db.dao.AiQualityDao
import com.xpx.vault.data.db.dao.AiSensitiveDao
import com.xpx.vault.data.db.dao.AiTagDao
import com.xpx.vault.data.db.entity.AiPhashEntity
import com.xpx.vault.data.db.entity.AiQualityEntity
import com.xpx.vault.data.db.entity.AiSensitiveEntity
import com.xpx.vault.data.db.entity.AiTagEntity
import com.xpx.vault.domain.model.AiPerceptualHash
import com.xpx.vault.domain.model.AiQualityRecord
import com.xpx.vault.domain.model.AiSensitiveRecord
import com.xpx.vault.domain.model.AiTag
import com.xpx.vault.domain.repo.AiAnalysisRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AiAnalysisRepository] 的 Room 实现。做 entity ↔ domain 两端映射，不引入缓存层：
 * DAO 本身已是 suspend + Flow，Room 的查询计划 + 索引足够支撑当前规模。
 */
@Singleton
class AiAnalysisRepositoryImpl @Inject constructor(
    private val database: PhotoVaultDatabase,
    private val tagDao: AiTagDao,
    private val phashDao: AiPhashDao,
    private val qualityDao: AiQualityDao,
    private val sensitiveDao: AiSensitiveDao,
) : AiAnalysisRepository {

    override suspend fun upsertTags(photoId: Long, tags: List<AiTag>) {
        // 先清除该照片旧标签再整批插入，保证幂等。
        tagDao.deleteByPhoto(photoId)
        if (tags.isEmpty()) return
        tagDao.upsertAll(tags.map { it.toEntity() })
    }

    override fun observeTagsByCategory(category: String): Flow<List<AiTag>> =
        tagDao.observeByCategory(category).map { list ->
            // 同一张照片在 ai_tag 表会有多条 label 记录（ML Kit 输出多 label）。
            // 扫描侧已把这些 tag 的 category 统一到代表分类，但 UI 展示应按 photoId 去重，
            // 否则一张照片会在同一 Tab 里按 label 数量重复展示（例 Flower/Flowerpot/Plant/Building）。
            // 每张照片保留 confidence 最高的那一条作为代表 tag，既保留最有代表性的 label 文本，
            // 又确保宫格天然唯一。
            list
                .groupBy { it.photoId }
                .map { (_, group) -> group.maxByOrNull { it.confidence }!! }
                .sortedByDescending { it.confidence }
                .map { it.toDomain() }
        }

    override suspend fun upsertPerceptualHash(hash: AiPerceptualHash) {
        phashDao.upsert(AiPhashEntity(hash.photoId, hash.phash, hash.dhash))
    }

    override suspend fun listAllPerceptualHashes(): List<AiPerceptualHash> =
        phashDao.listAll().map { AiPerceptualHash(it.photoId, it.phash, it.dhash) }

    override suspend fun listAllScannedPhotoIds(): List<Long> = phashDao.listAllPhotoIds()

    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.withTransaction { block() }

    override suspend fun upsertQuality(record: AiQualityRecord) {
        qualityDao.upsert(record.toEntity())
    }

    override fun observeBlurry(): Flow<List<AiQualityRecord>> =
        qualityDao.observeBlurry().map { list -> list.map { it.toDomain() } }

    override fun observeDuplicates(): Flow<List<AiQualityRecord>> =
        qualityDao.observeDuplicates().map { list -> list.map { it.toDomain() } }

    override suspend fun findQualityByPhoto(photoId: Long): AiQualityRecord? =
        qualityDao.findByPhoto(photoId)?.toDomain()

    override suspend fun clearQualityForPhoto(photoId: Long) {
        qualityDao.deleteByPhoto(photoId)
    }

    override suspend fun clearAllDuplicateFlags() {
        qualityDao.clearAllDuplicateFlags()
    }

    override suspend fun purgePhoto(photoId: Long) {
        // 单事务原子清除 4 表，避免半成品状态导致另一次扫描观察到不一致数据。
        database.withTransaction {
            phashDao.deleteByPhoto(photoId)
            qualityDao.deleteByPhoto(photoId)
            tagDao.deleteByPhoto(photoId)
            sensitiveDao.deleteByPhoto(photoId)
        }
    }

    override suspend fun upsertSensitive(record: AiSensitiveRecord): Long =
        sensitiveDao.upsert(record.toEntity())

    override fun observePendingSensitiveCount(): Flow<Int> =
        sensitiveDao.observePendingCount()

    override fun observePendingSensitive(): Flow<List<AiSensitiveRecord>> =
        sensitiveDao.observePending().map { list -> list.map { it.toDomain() } }

    override suspend fun updateSensitiveStatus(id: Long, status: String) {
        sensitiveDao.updateStatus(id, status)
    }

    override suspend fun clearSensitiveForPhoto(photoId: Long) {
        sensitiveDao.deleteByPhoto(photoId)
    }

    // ---- mapping ----
    private fun AiTag.toEntity() = AiTagEntity(
        id = id,
        photoId = photoId,
        label = label,
        category = category,
        confidence = confidence,
        source = source,
        createdAtEpochMs = createdAtEpochMs,
    )

    private fun AiTagEntity.toDomain() = AiTag(
        id = id,
        photoId = photoId,
        label = label,
        category = category,
        confidence = confidence,
        source = source,
        createdAtEpochMs = createdAtEpochMs,
    )

    private fun AiQualityRecord.toEntity() = AiQualityEntity(
        photoId = photoId,
        sharpness = sharpness,
        brightness = brightness,
        isBlurry = isBlurry,
        isOverExposed = isOverExposed,
        isDuplicate = isDuplicate,
        duplicateGroupId = duplicateGroupId,
    )

    private fun AiQualityEntity.toDomain() = AiQualityRecord(
        photoId = photoId,
        sharpness = sharpness,
        brightness = brightness,
        isBlurry = isBlurry,
        isOverExposed = isOverExposed,
        isDuplicate = isDuplicate,
        duplicateGroupId = duplicateGroupId,
    )

    private fun AiSensitiveRecord.toEntity() = AiSensitiveEntity(
        id = id,
        photoId = photoId,
        kind = kind,
        confidence = confidence,
        regionsJson = regionsJson,
        status = status,
        createdAtEpochMs = createdAtEpochMs,
    )

    private fun AiSensitiveEntity.toDomain() = AiSensitiveRecord(
        id = id,
        photoId = photoId,
        kind = kind,
        confidence = confidence,
        regionsJson = regionsJson,
        status = status,
        createdAtEpochMs = createdAtEpochMs,
    )
}
