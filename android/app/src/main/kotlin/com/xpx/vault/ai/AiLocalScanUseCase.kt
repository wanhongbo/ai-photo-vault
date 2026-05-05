package com.xpx.vault.ai

import android.content.Context
import android.graphics.BitmapFactory
import com.xpx.vault.ai.algo.DuplicateClusterer
import com.xpx.vault.ai.core.AiAnalysisResult
import com.xpx.vault.ai.core.AiEngine
import com.xpx.vault.ai.core.AiFeatureRegistry
import com.xpx.vault.ai.core.AiTag as CoreAiTag
import com.xpx.vault.ai.core.ClassifyCategory
import com.xpx.vault.ai.core.ImageAnalyzer
import com.xpx.vault.ai.util.PhotoIdentity
import com.xpx.vault.data.crypto.VaultCipher
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
import kotlinx.coroutines.SupervisorJob
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

    /** 供自动触发场景（解锁后、导入后、拍照后）在后台 launch 扫描。 */
    // 使用 IO 调度器：扫描的主要成本是解密 / 磁盘 IO / ML Kit inference（内部自管线程），
    // 不是纯 CPU 密集型计算，放到 Dispatchers.IO 和 UI 关键的 Default 池解耦，提高实际吞吐。
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 当前扫描这一轮结束后是否要再补一轮。通过 [requestScan] 累加：
     * 正在扫描时多次请求被合并为“在当前轮次之后继续补扫一轮”，避免漏掉正在过程中新增的照片。
     */
    @Volatile private var rescanRequested = false

    /**
     * 异步触发一次扫描。无论当前是否在扫，进程生命周期内常驻执行。
     *
     * @param force 为 true 时强制全量重扫（忽略增量跳过），对应 UI 手动 “扫描” 按钮的语义；
     *   false 时走增量扫描，已扫过的 photoId 会被跳过。
     */
    fun requestScan(force: Boolean = false) {
        appScope.launch { run(force) }
    }

    fun launch(scope: CoroutineScope) {
        scope.launch { run() }
    }

    suspend fun run(force: Boolean = false) {
        if (!mutex.tryLock()) {
            // 已有一轮在运行：记下请求，当前轮结束后自动再补一轮（合并多次触发）。
            rescanRequested = true
            return
        }
        try {
            do {
                rescanRequested = false
                runOnePass(force)
            } while (rescanRequested)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun runOnePass(force: Boolean) {
        val allPhotos = VaultStore.listRecentPhotos(appContext, limit = Int.MAX_VALUE)
            .filter { isVaultImage(it.path) }
        // 增量扫描：通过 phash 表已有的 photoId 集合做差集（LOCAL_ALGO analyzer 总是 ready，所以 phash 能视为“已扫过”的标志）。
        // 只拉 photoId 列，避免把完整 phash 表（每条 ~16B*2）拉进内存。
        val scanned: Set<Long> = if (force) emptySet() else runCatching {
            repository.listAllScannedPhotoIds().toSet()
        }.getOrDefault(emptySet())
        val photos = allPhotos.filter { PhotoIdentity.fromPath(it.path) !in scanned }
        _progress.value = AiScanProgress(running = true, total = photos.size, done = 0)
        if (photos.isEmpty()) {
            _progress.value = AiScanProgress(running = false, total = 0, done = 0)
            return
        }

        val readyAnalyzers: List<ImageAnalyzer> = registry.all().filter { runCatching { it.isReady() }.getOrDefault(false) }
        val recordedHashes = mutableListOf<AiPerceptualHash>()

        for ((index, photo) in photos.withIndex()) {
            val decoded = VaultThumbnailCache.loadDecoded(appContext, photo.path, targetMaxPx = 256)
            val bitmap = decoded?.bitmap
            if (bitmap != null && readyAnalyzers.isNotEmpty()) {
                val photoId = PhotoIdentity.fromPath(photo.path)
                var merged = analyzeAll(readyAnalyzers, photoId, bitmap)
                val isScreenshot = isScreenshotByDimensions(decoded.originalWidth, decoded.originalHeight) ||
                    merged.tags.any { it.category == ClassifyCategory.SCREENSHOT }
                merged = if (isScreenshot) {
                    // 截图与其他分类互斥：清除 ML Kit 对截图内容的错标（例如纯白截图被误判为 Selfie）。
                    merged.copy(
                        tags = listOf(
                            CoreAiTag(
                                label = "Screenshot",
                                category = ClassifyCategory.SCREENSHOT,
                                confidence = 0.9f,
                                source = AiEngine.LOCAL_ALGO,
                            ),
                        ),
                    )
                } else {
                    // 非截图：过滤低置信度 tag，避免 ML Kit 在模棱两可的误判污染分类 Tab。
                    merged.copy(tags = merged.tags.filter { it.confidence >= MIN_TAG_CONFIDENCE })
                }
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
        // 把一张照片的多条 upsert 合并成单事务：N 次 fsync → 1 次，纯写入延迟降低 30~60ms/张。
        repository.runInTransaction {
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
    }

    /**
     * 通过原图分辨率与设备屏幕尺寸匹配，启发式判断是否为截图。
     *
     * 宽高从 [VaultThumbnailCache.loadDecoded] 的解码过程顺带带出，避免对原图再做一次解密 + inJustDecodeBounds。
     * 当宽高为 0（旧版 `.dim` 边信息文件缺失）时回落到原有路径 [detectScreenshotByResolution]。
     *
     * 规则：(w, h) 或 (h, w) 与屏幕物理像素近似相等（容差 ±3%）。
     */
    private fun isScreenshotByDimensions(w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0) return false
        val dm = appContext.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        if (screenW <= 0 || screenH <= 0) return false
        val tolerance = 0.03f
        return approxEqual(w, screenW, tolerance) && approxEqual(h, screenH, tolerance) ||
            approxEqual(w, screenH, tolerance) && approxEqual(h, screenW, tolerance)
    }

    /**
     * 旧路径：当 loadDecoded 从旧磁盘缓存命中且缺 `.dim` 边信息时 fallback。
     * 成本高（会解密一次原图），故仅做兼容兆底。
     */
    private suspend fun detectScreenshotByResolution(path: String): Boolean = withContext(Dispatchers.IO) {
        val dm = appContext.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        if (screenW <= 0 || screenH <= 0) return@withContext false
        val bytes = runCatching { VaultCipher.get(appContext).decryptToByteArray(java.io.File(path)) }
            .getOrNull() ?: return@withContext false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return@withContext false
        val tolerance = 0.03f
        val matchPortrait = approxEqual(w, screenW, tolerance) && approxEqual(h, screenH, tolerance)
        val matchLandscape = approxEqual(w, screenH, tolerance) && approxEqual(h, screenW, tolerance)
        matchPortrait || matchLandscape
    }

    private fun approxEqual(a: Int, b: Int, tolerance: Float): Boolean {
        val diff = kotlin.math.abs(a - b)
        val base = kotlin.math.max(a, b)
        return base > 0 && diff.toFloat() / base <= tolerance
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

        /**
         * Tag 最低置信度阈值。ML Kit ImageLabeler 返回在 0.5 附近的标签常常接近随机猜测，
         * 容易把纯色/截图类照片误标为 Selfie/Portrait。提高门槛避免污染分类 Tab。
         */
        const val MIN_TAG_CONFIDENCE = 0.6f
    }
}
