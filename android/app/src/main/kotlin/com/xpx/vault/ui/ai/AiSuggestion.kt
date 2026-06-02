package com.xpx.vault.ui.ai

/**
 * AI Tab 顶部「建议卡片」的状态机。
 *
 * 状态派生优先级（见 [AiHomeViewModel]）：
 *  1. 扫描进行中 → [Scanning]（展示进度，覆盖其他分支）
 *  2. 有待处理敏感 且 未被用户忽略 → [Sensitive]（最高业务优先）
 *  3. 有可清理（模糊/重复）且 未被用户忽略 → [Cleanup]
 *  4. 保险箱为空 → [Empty]
 *  5. 已完成过至少一次扫描且以上均不满足 → [AllClear]
 *  6. 从未扫过或有未扫描媒体 → [Idle]
 */
sealed class AiSuggestion {
    /** 保险箱为空，提示用户先导入或拍摄私密照片。 */
    object Empty : AiSuggestion()

    /** 从未扫过、或保险箱中存在尚未扫描的媒体。 */
    object Idle : AiSuggestion()

    /**
     * 扫描进行中。[total] 为本轮计划处理张数；[done] 为已处理张数。
     * total == 0 时表示进度不确定（比如刚启动还未枚举 vault）。
     */
    data class Scanning(val done: Int, val total: Int, val cancelling: Boolean) : AiSuggestion()

    /**
     * 检测到敏感内容待处理。
     * [locationRiskCount] 表示其中包含 EXIF GPS 位置风险的照片数。
     * [cleanupCount] 若 > 0，卡片会在描述尾部追加"另有 X 张可清理"的副行入口。
     */
    data class Sensitive(val count: Int, val locationRiskCount: Int, val cleanupCount: Int) : AiSuggestion()

    /** 可清理内容（模糊 + 重复）。 */
    data class Cleanup(val count: Int) : AiSuggestion()

    /** 已扫过且当前无任何待处理项。展示状态良好 + 重新扫描入口。 */
    object AllClear : AiSuggestion()
}
