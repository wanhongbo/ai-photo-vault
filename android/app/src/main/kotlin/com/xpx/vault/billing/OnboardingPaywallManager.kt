package com.xpx.vault.billing

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理首启软墙（Onboarding Paywall）的展示状态。
 *
 * 规则：用户首次完成 PIN 设置/解锁后，展示一次可跳过的 PaywallScreen。
 * 之后不再自动弹出。
 */
@Singleton
class OnboardingPaywallManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 是否需要展示 onboarding 软墙。 */
    fun shouldShow(): Boolean =
        !prefs.getBoolean(KEY_HAS_SEEN, false)

    /** 标记已展示（无论用户是否跳过或购买）。 */
    fun markSeen() {
        prefs.edit().putBoolean(KEY_HAS_SEEN, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "onboarding_paywall_prefs"
        private const val KEY_HAS_SEEN = "has_seen_onboarding_paywall"
    }
}
