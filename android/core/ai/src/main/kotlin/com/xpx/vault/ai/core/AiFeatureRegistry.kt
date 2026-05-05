package com.xpx.vault.ai.core

/**
 * 注册所有可用的 [ImageAnalyzer]，按能力索引，供上层 UseCase 按需组合调用。
 *
 * 用法：上层传入一张 bitmap，根据需要的能力通过 [analyzersFor] 拿到合适的
 * Analyzer 列表；同一能力下可能有多个实现（如 ML Kit + LocalAlgo），调用方决定
 * 是"择优取一个"还是"合并所有结果"。
 */
class AiFeatureRegistry(
    private val analyzers: List<ImageAnalyzer>,
) {
    fun analyzersFor(selector: (AnalyzerCapability) -> Boolean): List<ImageAnalyzer> =
        analyzers.filter { selector(it.capability) }

    fun all(): List<ImageAnalyzer> = analyzers

    fun close() {
        analyzers.forEach { runCatching { it.close() } }
    }
}
