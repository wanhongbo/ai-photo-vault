import Foundation

/// 支付墙门控 — 对齐 Android [PaywallGatekeeper]。
@MainActor
final class PaywallGatekeeper {
    static let shared = PaywallGatekeeper()

    private let subscription = SubscriptionService.shared
    private let quota = QuotaManager.shared

    private init() {}

    func checkAccess(_ feature: ProFeature) -> GateResult {
        if subscription.isPremium {
            return .allowed
        }

        let result: GateResult = switch feature {
        case .vaultImport:
            if quota.isVaultFull(isPremium: false) {
                .hardWall(trigger: .quotaHard, reason: "vault_full")
            } else {
                .allowed
            }
        case .backupCreate:
            if quota.isBackupExhausted(isPremium: false) {
                .hardWall(trigger: .quotaHard, reason: "backup_exhausted")
            } else {
                .allowed
            }
        case .aiCleanup, .aiSensitive, .aiClassify, .aiPrivacy:
            if quota.isAiExhausted(isPremium: false) {
                .hardWall(trigger: .quotaHard, reason: "ai_monthly_exhausted")
            } else {
                .allowed
            }
        case .exportNoWatermark:
            .hardWall(trigger: .proFeatureHard, reason: "pro_only_export")
        }

        if case .hardWall(_, let reason) = result {
            PaywallAnalytics.trackGateTriggered(feature: feature.rawValue, reason: reason)
        }
        return result
    }
}
