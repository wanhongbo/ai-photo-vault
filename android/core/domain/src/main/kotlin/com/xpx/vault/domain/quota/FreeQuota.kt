package com.xpx.vault.domain.quota

/**
 * 免费版配额常量。Premium 用户不受这些限制约束。
 *
 * 修改此处常量时需同步更新：
 *  - PaywallScreen 文案中的展示数字
 *  - 设置页的剩余额度进度条
 */
object FreeQuota {
    /** 加密保险箱最大存储条目数（照片+视频）。 */
    const val MAX_VAULT_ITEMS = 50

    /** 免费备份次数上限（含手动与自动）。 */
    const val MAX_BACKUP_COUNT = 1

    /** 每自然月 AI 功能调用次数上限。 */
    const val MAX_AI_MONTHLY = 10
}
