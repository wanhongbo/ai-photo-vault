package com.xpx.vault.domain.repo

import com.xpx.vault.domain.billing.PaywallOfferingsState
import com.xpx.vault.domain.billing.PurchaseActivityHost
import kotlinx.coroutines.flow.StateFlow

/**
 * 订阅 + 买断 catalog 与购买；实现位于 `app` 模块（RevenueCat）。
 */
interface SubscriptionRepository {
    val offeringsState: StateFlow<PaywallOfferingsState>
    val isPremium: StateFlow<Boolean>

    /** RevenueCat SDK 是否已在 Application 中完成 [configure]。 */
    fun isSdkConfigured(): Boolean

    suspend fun refreshCatalog()

    suspend fun restorePurchases(): Result<Unit>

    /**
     * 发起 Google Play 购买流程；[host] 须返回 [android.app.Activity]。
     */
    suspend fun purchase(
        packageIdentifier: String,
        host: PurchaseActivityHost,
    ): Result<Unit>
}
