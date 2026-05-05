package com.xpx.vault.ai.algo

import com.xpx.vault.ai.core.ClassifyCategory

/**
 * 把 ML Kit Image Labeler 输出的英文标签映射到归一化的 6 大类。
 * 仅做离散关键词匹配，未命中默认 [ClassifyCategory.OTHER]。
 *
 * - SCREENSHOT 由扫描层的文件名启发式（"screenshot"/"截屏"）兜底，此处只处理图像内容。
 * - ID_CARD / DOCUMENT 主要依赖敏感通道和 OCR 辅助，这里仅做兜底。
 */
object ClassifyCategoryMapper {

    private val PORTRAIT = setOf(
        "person", "face", "selfie", "hair", "smile", "skin",
    )
    private val LANDSCAPE = setOf(
        "sky", "cloud", "mountain", "tree", "beach", "sea", "ocean",
        "sunset", "sunrise", "plant", "flower", "forest", "landscape",
    )
    private val FOOD = setOf(
        "food", "dish", "cuisine", "meal", "fruit", "dessert", "drink",
        "beverage", "cake", "bread", "meat", "vegetable",
    )
    private val DOCUMENT = setOf(
        "document", "paper", "text", "receipt", "book", "newspaper",
    )

    fun map(label: String): ClassifyCategory {
        val key = label.lowercase()
        return when {
            PORTRAIT.any { key.contains(it) } -> ClassifyCategory.PORTRAIT
            FOOD.any { key.contains(it) } -> ClassifyCategory.FOOD
            LANDSCAPE.any { key.contains(it) } -> ClassifyCategory.LANDSCAPE
            DOCUMENT.any { key.contains(it) } -> ClassifyCategory.DOCUMENT
            else -> ClassifyCategory.OTHER
        }
    }
}
