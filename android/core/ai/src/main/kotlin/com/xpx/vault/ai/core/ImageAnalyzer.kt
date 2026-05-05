package com.xpx.vault.ai.core

import android.graphics.Bitmap

/**
 * 单一分析能力的策略接口。不同引擎（Noop / LocalAlgo / MLKit / TFLite）实现此接口，
 * 由 `AiFeatureRegistry` 根据运行环境（如 GMS 是否可用）决定注入哪个实现。
 *
 * 推理输入统一为缩略图（通常 256 边长），以控制能耗；输出聚合到 [AiAnalysisResult]。
 * 实现方可只填充自己负责的字段，让上层合并各分析器结果。
 */
interface ImageAnalyzer {
    val engine: AiEngine
    val capability: AnalyzerCapability

    /** 是否已具备推理条件（如模型已下载、GMS 可用）。 */
    suspend fun isReady(): Boolean = true

    /** 预热：可选，首次使用前触发模型加载 / 下载。 */
    suspend fun warmUp() {}

    suspend fun analyze(photoId: Long, bitmap: Bitmap): AiAnalysisResult

    /** 释放底层资源（关闭 MLKit client 等）。 */
    fun close() {}
}

/**
 * 声明一个 Analyzer 能产出哪些能力，用于 [AiFeatureRegistry] 做路由。
 */
data class AnalyzerCapability(
    val canClassify: Boolean = false,
    val canDetectSensitive: Boolean = false,
    val canAssessQuality: Boolean = false,
    val canHashPerceptually: Boolean = false,
    val canDetectFace: Boolean = false,
    val canRecognizeText: Boolean = false,
    val canDecodeBarcode: Boolean = false,
)
