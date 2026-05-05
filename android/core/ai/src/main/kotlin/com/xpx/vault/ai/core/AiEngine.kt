package com.xpx.vault.ai.core

/**
 * AI 分析来源标识，用于判断结果由哪个引擎产生、版本追踪。
 */
enum class AiEngine {
    NOOP,           // 占位实现，不做任何推理
    LOCAL_ALGO,     // 纯算法：pHash / dHash / Laplacian 等
    MLKIT,          // Google ML Kit（按需下载模型）
    TFLITE,         // TensorFlow Lite（预留，后续可替换）
}
