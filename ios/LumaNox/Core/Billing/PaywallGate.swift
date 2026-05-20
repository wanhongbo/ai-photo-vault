import Foundation

/// 支付墙触发来源 — 对齐 Android [PaywallTrigger]。
enum PaywallTrigger: String, Equatable {
    case onboardingSoft = "ONBOARDING_SOFT"
    case quotaHard = "QUOTA_HARD"
    case proFeatureHard = "PRO_FEATURE_HARD"
    case manual = "MANUAL"
}

/// 受 Premium 保护的功能 — 对齐 Android [ProFeature]。
enum ProFeature: String, Equatable {
    case vaultImport = "VAULT_IMPORT"
    case backupCreate = "BACKUP_CREATE"
    case aiCleanup = "AI_CLEANUP"
    case aiSensitive = "AI_SENSITIVE"
    case aiClassify = "AI_CLASSIFY"
    case aiPrivacy = "AI_PRIVACY"
    case exportNoWatermark = "EXPORT_NO_WATERMARK"
}

enum GateResult: Equatable {
    case allowed
    case softWall(trigger: PaywallTrigger, reason: String)
    case hardWall(trigger: PaywallTrigger, reason: String)
}

/// 免费版配额 — 对齐 Android [FreeQuota]。
enum FreeQuota {
    static let maxVaultItems = 50
    static let maxBackupCount = 1
    static let maxAiMonthly = 10
}

/// 将门控结果映射为 Paywall 路由 `source` 参数（与 Android MainActivity 一致）。
enum PaywallSource {
    static let onboarding = "onboarding"
    static let manual = "manual"
    static let quotaVault = "quota_vault"
    static let quotaBackup = "quota_backup"
    static let quotaAI = "quota_ai"
    static let proExport = "pro_only_export"

    static func forGate(_ gate: GateResult) -> String {
        switch gate {
        case .allowed: return manual
        case .hardWall(_, let reason), .softWall(_, let reason):
            switch reason {
            case "vault_full": return quotaVault
            case "backup_exhausted": return quotaBackup
            case "ai_monthly_exhausted": return quotaAI
            case "pro_only_export": return proExport
            default: return manual
            }
        }
    }
}
