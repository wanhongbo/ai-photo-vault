package com.xpx.vault.billing

/**
 * 与 RevenueCat Dashboard、Play Console 商品 ID 对齐（见 `doc/android/支付功能-开发计划与配置清单.md` §3.0）。
 */
object LumaNoxBillingIds {
    const val ENTITLEMENT_PREMIUM = "premium"
    const val OFFERING_DEFAULT = "default"
    const val PACKAGE_MONTHLY = "\$rc_monthly"
    const val PACKAGE_ANNUAL = "\$rc_annual"
    const val PACKAGE_LIFETIME = "\$rc_lifetime"
    const val PRODUCT_MONTHLY = "luma_vault_premium_monthly"
    const val PRODUCT_ANNUAL = "luma_vault_premium_annual"
    const val PRODUCT_LIFETIME = "luma_vault_premium_lifetime"
}
