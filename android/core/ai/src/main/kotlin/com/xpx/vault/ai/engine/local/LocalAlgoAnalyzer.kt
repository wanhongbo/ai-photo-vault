package com.xpx.vault.ai.engine.local

import android.graphics.Bitmap
import com.xpx.vault.ai.algo.PerceptualHasher
import com.xpx.vault.ai.algo.SharpnessAnalyzer
import com.xpx.vault.ai.core.AiAnalysisResult
import com.xpx.vault.ai.core.AiEngine
import com.xpx.vault.ai.core.AiQuality
import com.xpx.vault.ai.core.AnalyzerCapability
import com.xpx.vault.ai.core.ImageAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 零依赖的本地算法 Analyzer：负责"图像质量"+ "感知哈希"两类能力。
 * 不依赖 GMS，始终可用，是"垃圾清理"功能的基础实现。
 */
class LocalAlgoAnalyzer(
    private val versionTag: String = ENGINE_VERSION,
) : ImageAnalyzer {

    override val engine: AiEngine = AiEngine.LOCAL_ALGO

    override val capability: AnalyzerCapability = AnalyzerCapability(
        canAssessQuality = true,
        canHashPerceptually = true,
    )

    override suspend fun analyze(photoId: Long, bitmap: Bitmap): AiAnalysisResult =
        withContext(Dispatchers.Default) {
            val quality = SharpnessAnalyzer.analyze(bitmap)
            val phash = PerceptualHasher.pHash(bitmap)
            val dhash = PerceptualHasher.dHash(bitmap)
            AiAnalysisResult(
                photoId = photoId,
                engineVersion = versionTag,
                quality = AiQuality(
                    sharpness = quality.sharpness,
                    brightness = quality.brightness,
                    isBlurry = quality.isBlurry,
                    isOverExposed = quality.isOverExposed,
                ),
                phash = phash,
                dhash = dhash,
            )
        }

    companion object {
        const val ENGINE_VERSION = "local-algo-v1"
    }
}
