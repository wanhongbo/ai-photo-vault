import Foundation

/// 首启分叉 — 对齐 Android [FirstLaunchRouter]。
enum FirstLaunchBranch: Equatable {
    /// 从未设置 PIN，外部无 `backup.dat`
    case fresh
    /// 从未设置 PIN，但已授权目录中存在 `backup.dat`
    case restoreLogin
    /// 已有 PIN
    case unlock
}

enum FirstLaunchRouter {
    static func detect(securityStore: SecuritySettingsStore = .shared) -> FirstLaunchBranch {
        securityStore.reload()
        if securityStore.hasPinConfigured {
            return .unlock
        }
        if ExternalBackupLocation.findAutoBackup() {
            return .restoreLogin
        }
        return .fresh
    }
}
