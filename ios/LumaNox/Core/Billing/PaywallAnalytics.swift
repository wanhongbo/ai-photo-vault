import Foundation
import os

/// 支付墙埋点（日志占位，后续可接 Firebase）。
enum PaywallAnalytics {
    private static let log = Logger(subsystem: "com.xpx.vault", category: "PaywallAnalytics")

    static func trackGateTriggered(feature: String, reason: String) {
        log.debug("gate_triggered feature=\(feature, privacy: .public) reason=\(reason, privacy: .public)")
    }

    static func trackPurchaseStart(packageId: String, source: String) {
        log.debug("paywall_purchase_start package=\(packageId, privacy: .public) source=\(source, privacy: .public)")
    }

    static func trackPurchaseSuccess(packageId: String) {
        log.debug("paywall_purchase_success package=\(packageId, privacy: .public)")
    }

    static func trackPurchaseCancel() {
        log.debug("paywall_purchase_cancel")
    }

    static func trackPurchaseFail(_ error: String?) {
        log.debug("paywall_purchase_fail error=\(error ?? "unknown", privacy: .public)")
    }

    static func trackRestore(success: Bool) {
        log.debug("paywall_restore success=\(success, privacy: .public)")
    }
}
