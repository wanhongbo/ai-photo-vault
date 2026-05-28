import Foundation
import os

/// 支付墙埋点；仅记录功能、来源、商品 ID、结果这类低敏字段。
enum PaywallAnalytics {
    private static let log = Logger(subsystem: "com.xpx.vault", category: "PaywallAnalytics")

    static func trackGateTriggered(feature: String, reason: String) {
        log.debug("gate_triggered feature=\(feature, privacy: .public) reason=\(reason, privacy: .public)")
        FirebaseTelemetry.logEvent("gate_triggered", parameters: ["feature": feature, "reason": reason])
    }

    static func trackPurchaseStart(packageId: String, source: String) {
        log.debug("paywall_purchase_start package=\(packageId, privacy: .public) source=\(source, privacy: .public)")
        FirebaseTelemetry.logEvent("paywall_purchase_start", parameters: ["package": packageId, "source": source])
    }

    static func trackPurchaseSuccess(packageId: String) {
        log.debug("paywall_purchase_success package=\(packageId, privacy: .public)")
        FirebaseTelemetry.logEvent("paywall_purchase_success", parameters: ["package": packageId])
    }

    static func trackPurchaseCancel() {
        log.debug("paywall_purchase_cancel")
        FirebaseTelemetry.logEvent("paywall_purchase_cancel")
    }

    static func trackPurchaseFail(_ error: String?) {
        log.debug("paywall_purchase_fail error=\(error ?? "unknown", privacy: .public)")
        FirebaseTelemetry.logEvent("paywall_purchase_fail", parameters: ["error": error ?? "unknown"])
    }

    static func trackRestore(success: Bool) {
        log.debug("paywall_restore success=\(success, privacy: .public)")
        FirebaseTelemetry.logEvent("paywall_restore", parameters: ["success": success ? "true" : "false"])
    }
}
