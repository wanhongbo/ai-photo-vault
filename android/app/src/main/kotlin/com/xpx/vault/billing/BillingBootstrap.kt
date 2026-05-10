package com.xpx.vault.billing

import android.app.Application
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.xpx.vault.BuildConfig

/**
 * RevenueCat 初始化；占位 API Key 时跳过，避免开发环境崩溃。
 */
object BillingBootstrap {
    @Volatile
    var isConfigured: Boolean = false
        private set

    fun init(application: Application) {
        val key = BuildConfig.REVENUECAT_API_KEY
        if (key.isBlank() || key.startsWith("REPLACE_")) {
            return
        }
        Purchases.logLevel = if (BuildConfig.DEV_TOOLS) LogLevel.DEBUG else LogLevel.ERROR
        Purchases.configure(
            PurchasesConfiguration.Builder(application, key).build(),
        )
        isConfigured = true
    }
}
