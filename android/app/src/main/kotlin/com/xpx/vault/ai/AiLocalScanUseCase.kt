package com.xpx.vault.ai

import android.content.Context
import com.xpx.vault.ai.algo.DuplicateClusterer
import com.xpx.vault.ai.core.AiAnalysisResult
import com.xpx.vault.ai.core.AiFeatureRegistry
import com.xpx.vault.ai.core.ImageAnalyzer
import com.xpx.vault.ai.util.PhotoIdentity
import com.xpx.vault.domain.model.AiPerceptualHash
import com.xpx.vault.domain.model.AiQualityRecord
import com.xpx.vault.domain.model.AiSensitiveRecord
import com.xpx.vault.domain.model.AiTag
import com.xpx.vault.domain.repo.AiAnalysisRepository
import com.xpx.vault.ui.components.VaultThumbnailCache
import com.xpx.vault.ui.vault.VaultStore
import com.xpx.vault.ui.vault.isVaultImage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 扫描进度 UI 状态。
 */
data class AiScanProgress(
    val running: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * Vault 内照片的 AI 本地扫描入口。
 *
 * 工作流：
 *  1. 通过 [VaultStore] 列出所有图片（视频当前版本跳过，避免无意义的缩略图提取）；
 *  2. 每张照片用 [VaultThumbnailCache] 解密出 256px 缩略图；
 *  3. 把 bitmap 串联交给 [AiFeatureRegistry] 中所有 ready 的 analyzer；
 *  4. 聚合结果写入 Room（ai_quality / ai_phash / ai_tag / ai_sensitive）；
 *  5. 全量完成后再跑一次 [DuplicateClusterer] 做跨张重复聚类，更新 is_duplicate 标记。
 *
 * photoId 由 [PhotoIdentity.fromPath] 稳定生成，不依赖 photo_assets。
 *
 * 线程模型：
 *  - 通过 [Mutex] 保证同一时刻只有一次扫描；
 *  - 推理循环在 Dispatchers.Default 上；IO 解密走 VaultThumbnailCache 内部的 Dispatchers.IO。
 */
@Singleton
class AiLocalScanUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val registry: AiFeatureRegistry,
    private val repository: AiAnalysisRepository,
) {
    private val _progress = MutableStateFlow(AiScanProgress())
    val progress: StateFlow<AiScanProgress> = _progress.asStateFlow()
    private val mutex = Mutex()

    fun launch(scope: CoroutineScope) {
        scope.launch { run() }
    }

    suspend fun run() {
        if (!mutex.tryLock()) return
        try {
            val photos = VaultStore.listRecentPhotos(appContext, limit = Int.MAX_VALUE)
                .filter { isVaultImage(it.path) }
            _progress.value = AiScanProgress(running = true, total = photos.size, done = 0)
            if (photos.isEmpty()) {
                _progress.value = AiScanProgress(running = false, total = 0, done = 0)
                return
            }

            val readyAnalyzers: List<ImageAnalyzer> = registry.all().filter { runCatching { it.isReady() }.getOrDefault(false) }
            val recordedHashes = mutableListOf<AiPerceptualHash>()

            for ((index, photo) in photos.withIndex()) {
                val bitmap = VaultThumbnailCache.load(appContext, photo.path, targetMaxPx = 256)
                if (bitmap != null && readyAnalyzers.isNotEmpty()) {
                    val photoId = PhotoIdentity.fromPath(photo.path)
                    val merged = analyzeAll(readyAnalyzers, photoId, bitmap)
                    persist(merged)
                    merged.phash?.let { p ->
                        merged.dhash?.let { d ->
                            recordedHashes += AiPerceptualHash(photoId, p, d)
                        }
                    }
                }
                _progress.value = _progress.value.copy(done = index + 1)
            }

            // 重复聚类：把所有 phash 跑一遍聚类，把非代表张标 is_duplicate=true。
            val fullHashes = runCatching { repository.listAllPerceptualHashes() }.getOrDefault(recordedHashes)
            if (fullHashes.size >= 2) markDuplicates(fullHashes)

            _progress.value = _progress.value.copy(running = false)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun analyzeAll(
        analyzers: List<ImageAnalyzer>,
        photoId: Long,
        bitmap: android.graphics.Bitmap,
    ): AiAnalysisResult = withContext(Dispatchers.Default) {
        // 以第一个 analyzer 的结果为基础，其余分析器按能力合并字段（保持 photoId / engineVersion 不变）。
        var merged = AiAnalysisResult(photoId = photoId, engineVersion = "merged")
        for (analyzer in analyzers) {
            val r = runCatching { analyzer.analyze(photoId, bitmap) }.getOrNull() ?: continue
            merged = merged.copy(
                tags = (merged.tags + r.tags).distinctBy { it.label },
                quality = merged.quality ?: r.quality,
                sensitive = (merged.sensitive + r.sensitive).distinctBy { it.kind },
                phash = merged.phash ?: r.phash,
                dhash = merged.dhash ?: r.dhash,
            )
        }
        merged
    }

    private suspend fun persist(result: AiAnalysisResult) {
        val now = System.currentTimeMillis()
        result.quality?.let { q ->
            repository.upsertQuality(
                AiQualityRecord(
                    photoId = result.photoId,
                    sharpness = q.sharpness,
                    brightness = q.brightness,
                    isBlurry = q.isBlurry,
                    isOverExposed = q.isOverExposed,
                    isDuplicate = false,
                    duplicateGroupId = null,
                ),
            )
        }
        if (result.phash != null && result.dhash != null) {
            repository.upsertPerceptualHash(
                AiPerceptualHash(result.photoId, result.phash!!, result.dhash!!),
            )
        }
        if (result.tags.isNotEmpty()) {
            repository.upsertTags(
                photoId = result.photoId,
                tags = result.tags.map { t ->
                    AiTag(
                        id = 0,
                        photoId = result.photoId,
                        label = t.label,
                        category = t.category.name,
                        confidence = t.confidence,
                        source = t.source.name,
                        createdAtEpochMs = now,
                    )
                },
            )
        }
        // 敏感命中：逐条 upsert（DAO 对 (photoId, kind) 有唯一索引 REPLACE）。
        // 默认新记录状态 pending。
        result.sensitive.forEach { hit ->
            repository.upsertSensitive(
                AiSensitiveRecord(
                    id = 0,
                    photoId = result.photoId,
                    kind = hit.kind.name,
                    confidence = hit.confidence,
                    regionsJson = null,
                    status = "pending",
                    createdAtEpochMs = now,
                ),
            )
        }
    }

    private suspend fun markDuplicates(hashes: List<AiPerceptualHash>) {
        val clusters = DuplicateClusterer.cluster(hashes)
        if (clusters.isEmpty()) return
        for (cluster in clusters) {
            val rep = cluster.photoIds.first()
            for (id in cluster.photoIds.drop(1)) {
                val existing = repository.findQualityByPhoto(id) ?: continue
                repository.upsertQuality(
                    existing.copy(isDuplicate = true, duplicateGroupId = rep),
                )
            }
        }
    }

    companion object {
        const val TAG = "AiLocalScanUseCase"
    }
}
