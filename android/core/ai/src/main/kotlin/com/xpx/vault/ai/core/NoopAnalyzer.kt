package com.xpx.vault.ai.core

import android.graphics.Bitmap

/**
 * 占位分析器：不做任何推理，仅返回空结果。用于：
 *  - GMS 不可用时作为 ML Kit 能力的降级实现；
 *  - 开发期尚未接入真实引擎前，保证调用链路可通。
 */
class NoopAnalyzer(
    override val capability: AnalyzerCapability = AnalyzerCapability(),
) : ImageAnalyzer {
    override val engine: AiEngine = AiEngine.NOOP

    override suspend fun analyze(photoId: Long, bitmap: Bitmap): AiAnalysisResult =
        AiAnalysisResult(photoId = photoId, engineVersion = "noop-1")
}
