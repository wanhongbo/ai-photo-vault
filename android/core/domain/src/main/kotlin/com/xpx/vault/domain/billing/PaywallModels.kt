package com.xpx.vault.domain.billing

/** 用于 Paywall 结构文案（周期标签等），**不含**具体金额。 */
enum class PaywallPlanKind {
    MONTHLY,
    ANNUAL,
    LIFETIME,
    OTHER,
}

/**
 * 单条可购套餐在 Paywall 上的展示数据；**价签与标题**须来自商店 / RevenueCat，勿在客户端写死金额。
 */
data class PaywallPackageOffer(
    val kind: PaywallPlanKind,
    val packageIdentifier: String,
    val title: String,
    val description: String,
    val pricePrimary: String,
    val priceSecondary: String?,
    val periodShortLabel: String?,
    val showBestValueBadge: Boolean,
)

sealed interface PaywallOfferingsState {
    data object Loading : PaywallOfferingsState

    data class Ready(
        val packages: List<PaywallPackageOffer>,
        val defaultSelectedIndex: Int,
        val isPremium: Boolean,
    ) : PaywallOfferingsState

    data class Error(val message: String?) : PaywallOfferingsState
}

/** 由 UI 提供当前 [android.app.Activity]，供 Play 结算弹窗使用；domain 层仅持有 [Any] 避免依赖 Android API。 */
fun interface PurchaseActivityHost {
    fun obtain(): Any?
}
