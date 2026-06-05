import Foundation

/// 支付墙埋点；仅记录功能、来源、商品 ID、结果这类低敏字段。
enum PaywallAnalytics {
    static func trackGateTriggered(feature: String, reason: String) {
        LumaTelemetry.logEvent("gate_triggered", parameters: ["feature": feature, "reason": reason])
    }

    static func trackPurchaseStart(packageId: String, source: String) {
        LumaTelemetry.logEvent("paywall_purchase_start", parameters: ["package": packageId, "source": source])
    }

    static func trackPurchaseSuccess(packageId: String) {
        LumaTelemetry.logEvent("paywall_purchase_success", parameters: ["package": packageId])
    }

    static func trackPurchaseCancel() {
        LumaTelemetry.logEvent("paywall_purchase_cancel")
    }

    static func trackPurchaseFail(_ error: String?) {
        LumaTelemetry.logEvent("paywall_purchase_fail", parameters: ["error": error ?? "unknown"])
    }

    static func trackRestore(success: Bool) {
        LumaTelemetry.logEvent("paywall_restore", parameters: ["success": success ? "true" : "false"])
    }
}
