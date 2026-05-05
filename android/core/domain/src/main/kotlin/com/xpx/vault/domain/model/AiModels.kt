package com.xpx.vault.domain.model

/**
 * AI 标签（照片多标签，用于 Virtual Album 分类展示）。
 */
data class AiTag(
    val id: Long,
    val photoId: Long,
    val label: String,
    val category: String,   // 归一化 6 大类（SCREENSHOT / ID_CARD / PORTRAIT / ...）
    val confidence: Float,
    val source: String,     // NOOP / LOCAL_ALGO / MLKIT / TFLITE
    val createdAtEpochMs: Long,
)

/**
 * 感知哈希记录（去重/重复聚类用）。
 */
data class AiPerceptualHash(
    val photoId: Long,
    val phash: Long,
    val dhash: Long,
)

/**
 * 图像质量评估。
 */
data class AiQualityRecord(
    val photoId: Long,
    val sharpness: Float,
    val brightness: Float,
    val isBlurry: Boolean,
    val isOverExposed: Boolean,
    val isDuplicate: Boolean,
    val duplicateGroupId: Long?,
)

/**
 * 敏感命中记录。status 三态：
 *  - pending：待处理（UI 徽章消费）
 *  - moved：已"移入敏感分组"（打标完成，不会再次提醒）
 *  - ignored：用户忽略，后续不再对该照片同类标签弹提醒
 */
data class AiSensitiveRecord(
    val id: Long,
    val photoId: Long,
    val kind: String,        // ID_CARD / BANK_CARD / PHONE_NUMBER / QR_CODE / FACE_CLEAR / PRIVATE_CHAT
    val confidence: Float,
    val regionsJson: String?, // 归一化矩形列表，供脱敏渲染
    val status: String,       // pending / moved / ignored
    val createdAtEpochMs: Long,
)
