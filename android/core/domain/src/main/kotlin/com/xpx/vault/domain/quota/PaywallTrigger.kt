package com.xpx.vault.domain.quota

/**
 * 支付墙触发来源，决定 PaywallScreen 的展示模式与是否可关闭。
 */
enum class PaywallTrigger {
    /** 首次启动引导：可跳过（软墙）。 */
    ONBOARDING_SOFT,

    /** 免费配额耗尽（存储/备份/AI）：不可跳过（硬墙），必须购买或返回。 */
    QUOTA_HARD,

    /** 点击 Pro-only 功能入口：不可跳过（硬墙）。 */
    PRO_FEATURE_HARD,

    /** 设置页手动点击「升级 Premium」：可关闭。 */
    MANUAL,
}

/**
 * 标识受 Premium 保护的功能入口，供 PaywallGatekeeper 判定。
 */
enum class ProFeature {
    /** 导入照片到保险箱（受 vault item 配额限制）。 */
    VAULT_IMPORT,

    /** 创建备份（受备份次数限制）。 */
    BACKUP_CREATE,

    /** AI 清理/去重。 */
    AI_CLEANUP,

    /** AI 敏感检测。 */
    AI_SENSITIVE,

    /** AI 分类。 */
    AI_CLASSIFY,

    /** AI 隐私检测与脱敏。 */
    AI_PRIVACY,

    /** 无水印导出。 */
    EXPORT_NO_WATERMARK,
}
