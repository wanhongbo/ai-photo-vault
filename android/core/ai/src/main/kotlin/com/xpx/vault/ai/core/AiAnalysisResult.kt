package com.xpx.vault.ai.core

/**
 * 单张图像的聚合分析结果。不同引擎按需填充字段，未处理的字段保持 null / 空列表。
 */
data class AiAnalysisResult(
    val photoId: Long,
    val engineVersion: String,
    val tags: List<AiTag> = emptyList(),
    val quality: AiQuality? = null,
    val sensitive: List<AiSensitiveHit> = emptyList(),
    val phash: Long? = null,
    val dhash: Long? = null,
)

data class AiTag(
    val label: String,
    val category: ClassifyCategory,
    val confidence: Float,
    val source: AiEngine,
)

data class AiQuality(
    val sharpness: Float,      // Laplacian 方差（越大越清晰）
    val brightness: Float,     // 0..1
    val isBlurry: Boolean,
    val isOverExposed: Boolean,
)

/**
 * 单个敏感命中点位，regions 用于脱敏渲染，坐标相对于原始图像尺寸归一化（0..1）。
 */
data class AiSensitiveHit(
    val kind: SensitiveKind,
    val confidence: Float,
    val regions: List<RectF> = emptyList(),
)

/**
 * 轻量 RectF 避免绑定 Android 框架类型；左上角原点，归一化 0..1。
 */
data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)
