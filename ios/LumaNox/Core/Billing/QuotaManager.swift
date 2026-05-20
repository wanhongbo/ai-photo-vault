import Foundation

/// 免费版配额跟踪 — 行为对齐 Android [QuotaManagerImpl]。
@MainActor
final class QuotaManager {
    static let shared = QuotaManager()

    private let defaults = UserDefaults.standard
    private let backupCountKey = "quota_backup_count"
    private let aiYearMonthKey = "quota_ai_year_month"
    private let aiCountKey = "quota_ai_count"

    private(set) var vaultCount = 0

    private init() {}

    func updateVaultCount(_ count: Int) {
        vaultCount = count
    }

    var backupCount: Int {
        defaults.integer(forKey: backupCountKey)
    }

    var aiMonthlyCount: Int {
        let ym = currentYearMonth()
        if defaults.string(forKey: aiYearMonthKey) != ym {
            return 0
        }
        return defaults.integer(forKey: aiCountKey)
    }

    func recordSuccessfulBackup() {
        let next = backupCount + 1
        defaults.set(next, forKey: backupCountKey)
    }

    func incrementAiUsage() {
        let ym = currentYearMonth()
        if defaults.string(forKey: aiYearMonthKey) != ym {
            defaults.set(ym, forKey: aiYearMonthKey)
            defaults.set(0, forKey: aiCountKey)
        }
        defaults.set(aiMonthlyCount + 1, forKey: aiCountKey)
    }

    func isVaultFull(isPremium: Bool) -> Bool {
        if isPremium { return false }
        return vaultCount >= FreeQuota.maxVaultItems
    }

    func isBackupExhausted(isPremium: Bool) -> Bool {
        if isPremium { return false }
        return backupCount >= FreeQuota.maxBackupCount
    }

    func isAiExhausted(isPremium: Bool) -> Bool {
        if isPremium { return false }
        return aiMonthlyCount >= FreeQuota.maxAiMonthly
    }

    private func currentYearMonth() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f.string(from: Date())
    }
}
