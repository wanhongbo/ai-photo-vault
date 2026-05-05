package com.xpx.vault.ai.algo

import com.xpx.vault.ai.core.ClassifyCategory

/**
 * 把 ML Kit Image Labeler 输出的英文标签映射到归一化的 6 大类。
 *
 * 匹配规则：按非字母字符将 label 拆成 token，以**整词**命中关键词集合；
 * 避免子串误匹配（例如 "chair" 含 "hair" 会被误判为 PORTRAIT）。
 *
 * - SCREENSHOT 主要由扫描层基于分辨率启发式兜底（见 AiLocalScanUseCase），
 *   这里额外支持 ML Kit 偶尔输出的 "screenshot" / "screen" 直接命中。
 * - ID_CARD / DOCUMENT 主要依赖敏感通道和 OCR 辅助，这里仅做兜底。
 */
object ClassifyCategoryMapper {

    private val PORTRAIT = setOf(
        "person", "people", "face", "selfie", "hair", "smile", "skin",
        "portrait", "man", "woman", "boy", "girl", "child", "baby",
    )
    private val LANDSCAPE = setOf(
        "sky", "cloud", "mountain", "tree", "beach", "sea", "ocean",
        "sunset", "sunrise", "plant", "flower", "forest", "landscape",
        "river", "lake", "snow",
    )
    private val FOOD = setOf(
        "food", "dish", "cuisine", "meal", "fruit", "dessert", "drink",
        "beverage", "cake", "bread", "meat", "vegetable", "coffee", "tea",
    )
    private val DOCUMENT = setOf(
        "document", "paper", "text", "receipt", "book", "newspaper",
        "menu", "letter",
    )
    private val SCREENSHOT = setOf(
        "screenshot", "screen",
    )

    fun map(label: String): ClassifyCategory {
        val tokens = label.lowercase().split(NON_ALPHA).filter { it.isNotEmpty() }.toSet()
        if (tokens.isEmpty()) return ClassifyCategory.OTHER
        return when {
            tokens.any { it in SCREENSHOT } -> ClassifyCategory.SCREENSHOT
            tokens.any { it in PORTRAIT } -> ClassifyCategory.PORTRAIT
            tokens.any { it in FOOD } -> ClassifyCategory.FOOD
            tokens.any { it in LANDSCAPE } -> ClassifyCategory.LANDSCAPE
            tokens.any { it in DOCUMENT } -> ClassifyCategory.DOCUMENT
            else -> ClassifyCategory.OTHER
        }
    }

    private val NON_ALPHA = Regex("[^a-z]+")
}
