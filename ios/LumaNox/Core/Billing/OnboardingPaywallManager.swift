import Foundation

enum SoftPaywallReason {
    case vaultImportValue
    case vaultNearLimit
    case backupSuccess
    case aiNearLimit

    var source: String {
        switch self {
        case .vaultImportValue: return PaywallSource.importValue
        case .vaultNearLimit: return PaywallSource.vaultNearLimit
        case .backupSuccess: return PaywallSource.backupSuccess
        case .aiNearLimit: return PaywallSource.aiNearLimit
        }
    }

    fileprivate var seenKey: String {
        switch self {
        case .vaultImportValue: return "seen_import_value"
        case .vaultNearLimit: return "seen_vault_near_limit"
        case .backupSuccess: return "seen_backup_success"
        case .aiNearLimit: return "seen_ai_near_limit"
        }
    }
}

@MainActor
enum PaywallPromptManager {
    private static let defaults = UserDefaults.standard
    private static let lastShownAtKey = "paywall_prompt_last_shown_at"
    private static let totalShowCountKey = "paywall_prompt_total_show_count"
    private static let maxLifetimeSoftPrompts = 2
    private static let cooldown: TimeInterval = 7 * 24 * 60 * 60
    private static let vaultValueMinItems = 5
    private static let vaultNearLimitItems = FreeQuota.maxVaultItems * 4 / 5
    private static let aiNearLimitCount = 7

    static func promptAfterVaultImport(currentVaultCount: Int) -> SoftPaywallReason? {
        guard !SubscriptionService.shared.isPremium else { return nil }
        let reason: SoftPaywallReason?
        if currentVaultCount >= vaultNearLimitItems {
            reason = .vaultNearLimit
        } else if currentVaultCount >= vaultValueMinItems {
            reason = .vaultImportValue
        } else {
            reason = nil
        }
        return reason.flatMap { shouldShow($0) ? $0 : nil }
    }

    static func promptBeforeAiFeature(aiMonthlyCount: Int) -> SoftPaywallReason? {
        guard !SubscriptionService.shared.isPremium,
              aiMonthlyCount >= aiNearLimitCount,
              aiMonthlyCount < FreeQuota.maxAiMonthly,
              shouldShow(.aiNearLimit)
        else { return nil }
        return .aiNearLimit
    }

    static func promptAfterBackupSuccess() -> SoftPaywallReason? {
        guard shouldShow(.backupSuccess) else { return nil }
        return .backupSuccess
    }

    static func markShown(_ reason: SoftPaywallReason) {
        defaults.set(true, forKey: reason.seenKey)
        defaults.set(Date().timeIntervalSince1970, forKey: lastShownAtKey)
        defaults.set(defaults.integer(forKey: totalShowCountKey) + 1, forKey: totalShowCountKey)
    }

    private static func shouldShow(_ reason: SoftPaywallReason) -> Bool {
        #if DEBUG
        return false
        #else
        guard BillingBootstrap.isConfigured else { return false }
        guard !SubscriptionService.shared.isPremium else { return false }
        guard !defaults.bool(forKey: reason.seenKey) else { return false }
        guard defaults.integer(forKey: totalShowCountKey) < maxLifetimeSoftPrompts else { return false }
        let lastShownAt = defaults.double(forKey: lastShownAtKey)
        return lastShownAt == 0 || Date().timeIntervalSince1970 - lastShownAt >= cooldown
        #endif
    }
}
