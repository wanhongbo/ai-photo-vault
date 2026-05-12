package com.xpx.vault.billing

import com.xpx.vault.domain.quota.PaywallTrigger
import com.xpx.vault.domain.quota.ProFeature
import com.xpx.vault.domain.quota.QuotaManager
import com.xpx.vault.domain.repo.SubscriptionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 支付墙门控器：各业务入口在执行受限操作前调用 [checkAccess]，
 * 根据用户权益状态 + 配额用量返回放行/软墙/硬墙结果。
 *
 * UI 层根据 [GateResult] 决定是否导航到 PaywallScreen。
 */
@Singleton
class PaywallGatekeeper @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val quotaManager: QuotaManager,
    private val analytics: PaywallAnalytics,
) {

    /**
     * 判定用户对 [feature] 的访问权限。
     */
    fun checkAccess(feature: ProFeature): GateResult {
        // Premium 用户始终放行
        if (subscriptionRepository.isPremium.value) return GateResult.Allowed

        val result = when (feature) {
            ProFeature.VAULT_IMPORT -> {
                if (quotaManager.isVaultFull()) {
                    GateResult.HardWall(
                        trigger = PaywallTrigger.QUOTA_HARD,
                        reason = "vault_full",
                    )
                } else {
                    GateResult.Allowed
                }
            }

            ProFeature.BACKUP_CREATE -> {
                if (quotaManager.isBackupExhausted()) {
                    GateResult.HardWall(
                        trigger = PaywallTrigger.QUOTA_HARD,
                        reason = "backup_exhausted",
                    )
                } else {
                    GateResult.Allowed
                }
            }

            ProFeature.AI_CLEANUP,
            ProFeature.AI_SENSITIVE,
            ProFeature.AI_CLASSIFY,
            ProFeature.AI_PRIVACY,
            -> {
                if (quotaManager.isAiExhausted()) {
                    GateResult.HardWall(
                        trigger = PaywallTrigger.QUOTA_HARD,
                        reason = "ai_monthly_exhausted",
                    )
                } else {
                    GateResult.Allowed
                }
            }

            ProFeature.EXPORT_NO_WATERMARK -> {
                GateResult.HardWall(
                    trigger = PaywallTrigger.PRO_FEATURE_HARD,
                    reason = "pro_only_export",
                )
            }
        }
        // 埋点
        if (result is GateResult.HardWall) {
            analytics.trackGateTriggered(feature.name, result.reason)
        }
        return result
    }
}

/**
 * 门控结果。
 */
sealed interface GateResult {
    /** 放行，用户可继续操作。 */
    data object Allowed : GateResult

    /** 软墙：展示支付墙但用户可跳过。 */
    data class SoftWall(
        val trigger: PaywallTrigger,
        val reason: String,
    ) : GateResult

    /** 硬墙：必须购买或返回，不可跳过。 */
    data class HardWall(
        val trigger: PaywallTrigger,
        val reason: String,
    ) : GateResult
}
