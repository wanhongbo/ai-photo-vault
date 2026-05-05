package com.xpx.vault.ui.ai

/**
 * AI Tab 顶部「建议卡片」的状态机。
 *
 * 状态派生优先级（见 [AiHomeViewModel]）：
 *  1. 扫描进行中 → [Scanning]（展示进度，覆盖其他分支）
 *  2. 有待处理敏感 且 未被用户忽略 → [Sensitive]（最高业务优先）
 *  3. 有可清理（模糊/重复）且 未被用户忽略 → [Cleanup]
 *  4. 已完成过至少一次扫描且以上均不满足 → [AllClear]
 *  5. 从未扫过 → [Idle]（短暂，ViewModel 初始化会立即触发自动扫描）
 */
sealed class AiSuggestion {
    /** 从未扫过、且当前未启动扫描。通常只在冷启后一瞬间出现。 */
    object Idle : AiSuggestion()

    /**
     * 扫描进行中。[total] 为本轮计划处理张数；[done] 为已处理张数。
     * total == 0 时表示进度不确定（比如刚启动还未枚举 vault）。
     */
    data class Scanning(val done: Int, val total: Int) : AiSuggestion()

    /**
     * 检测到敏感内容待处理。
     * [cleanupCount] 若 > 0，卡片会在描述尾部追加"另有 X 张可清理"的副行入口。
     */
    data class Sensitive(val count: Int, val cleanupCount: Int) : AiSuggestion()

    /** 可清理内容（模糊 + 重复）。 */
    data class Cleanup(val count: Int) : AiSuggestion()

    /** 已扫过且当前无任何待处理项。展示状态良好 + 重新扫描入口。 */
    object AllClear : AiSuggestion()
}
