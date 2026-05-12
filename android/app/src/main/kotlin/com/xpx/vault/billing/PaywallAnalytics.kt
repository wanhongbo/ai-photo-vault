package com.xpx.vault.billing

import com.xpx.vault.AppLogger
import com.xpx.vault.domain.quota.PaywallTrigger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 支付墙埋点事件追踪器。
 *
 * 当前使用日志输出，后续接入 Firebase Analytics 或其他 SDK 时只需替换内部实现。
 * 所有事件通过 [logEvent] 统一落盘，便于 debug 和 CI 截取日志验证。
 */
@Singleton
class PaywallAnalytics @Inject constructor() {

    companion object {
        private const val TAG = "PaywallAnalytics"

        // 事件名常量
        const val EVENT_PAYWALL_SHOWN = "paywall_shown"
        const val EVENT_PAYWALL_DISMISSED = "paywall_dismissed"
        const val EVENT_PAYWALL_PURCHASE_START = "paywall_purchase_start"
        const val EVENT_PAYWALL_PURCHASE_SUCCESS = "paywall_purchase_success"
        const val EVENT_PAYWALL_PURCHASE_CANCEL = "paywall_purchase_cancel"
        const val EVENT_PAYWALL_PURCHASE_FAIL = "paywall_purchase_fail"
        const val EVENT_PAYWALL_RESTORE = "paywall_restore"
        const val EVENT_GATE_TRIGGERED = "gate_triggered"
    }

    /** 记录支付墙展示 */
    fun trackPaywallShown(source: String, trigger: PaywallTrigger) {
        logEvent(EVENT_PAYWALL_SHOWN, mapOf("source" to source, "trigger" to trigger.name))
    }

    /** 记录用户关闭/跳过支付墙 */
    fun trackPaywallDismissed(source: String) {
        logEvent(EVENT_PAYWALL_DISMISSED, mapOf("source" to source))
    }

    /** 记录开始购买 */
    fun trackPurchaseStart(packageId: String, source: String) {
        logEvent(EVENT_PAYWALL_PURCHASE_START, mapOf("package" to packageId, "source" to source))
    }

    /** 记录购买成功 */
    fun trackPurchaseSuccess(packageId: String) {
        logEvent(EVENT_PAYWALL_PURCHASE_SUCCESS, mapOf("package" to packageId))
    }

    /** 记录用户取消购买 */
    fun trackPurchaseCancel() {
        logEvent(EVENT_PAYWALL_PURCHASE_CANCEL, emptyMap())
    }

    /** 记录购买失败 */
    fun trackPurchaseFail(error: String?) {
        logEvent(EVENT_PAYWALL_PURCHASE_FAIL, mapOf("error" to (error ?: "unknown")))
    }

    /** 记录恢复购买 */
    fun trackRestore(success: Boolean) {
        logEvent(EVENT_PAYWALL_RESTORE, mapOf("success" to success.toString()))
    }

    /** 记录门控触发 */
    fun trackGateTriggered(feature: String, reason: String) {
        logEvent(EVENT_GATE_TRIGGERED, mapOf("feature" to feature, "reason" to reason))
    }

    private fun logEvent(event: String, params: Map<String, String>) {
        val paramStr = params.entries.joinToString(", ") { "${it.key}=${it.value}" }
        AppLogger.d(TAG, "[$event] $paramStr")
        // TODO: 接入 Firebase Analytics
        // FirebaseAnalytics.getInstance(context).logEvent(event, bundleOf(*params.map { it.key to it.value }.toTypedArray()))
    }
}
