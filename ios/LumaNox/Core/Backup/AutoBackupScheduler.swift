import BackgroundTasks
import Foundation

enum BackupTriggerReason: String {
    case passwordChanged = "PASSWORD_CHANGED"
    case manualRestoreSync = "MANUAL_RESTORE_SYNC"
    case userManualButton = "USER_MANUAL_BUTTON"
    case coldStartDue = "COLD_START_DUE"
    case backgroundRefresh = "BACKGROUND_REFRESH"
}

/// 自动备份调度 — 对齐 Android [AutoBackupScheduler] + [AutoIncrementalBackupWorker]。
enum AutoBackupScheduler {
    static let taskIdentifier = "com.xpx.vault.auto-backup"
    private static let enabledKey = "auto_backup_enabled"
    private static let coldStartMinInterval: TimeInterval = 8 * 60 * 60
    private static let refreshInterval: TimeInterval = 24 * 60 * 60

    static var isEnabled: Bool {
        get {
            if UserDefaults.standard.object(forKey: enabledKey) == nil { return true }
            return UserDefaults.standard.bool(forKey: enabledKey)
        }
        set {
            UserDefaults.standard.set(newValue, forKey: enabledKey)
            ensureScheduled()
        }
    }

    static func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier,
            using: nil
        ) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handleAppRefresh(task: refresh)
        }
    }

    static func ensureScheduled() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
        guard isEnabled else { return }
        scheduleNextAppRefresh()
    }

    static func scheduleNextAppRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: refreshInterval)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// 冷启：距上次自动备份超过 8h 则延迟补跑一次。
    static func scheduleColdStartIfDue() {
        guard isEnabled else { return }
        let last = BackupMeta.load().auto?.lastBackupAtMs ?? 0
        let elapsed = last > 0
            ? Date().timeIntervalSince1970 * 1000 - Double(last)
            : .infinity
        guard elapsed >= coldStartMinInterval * 1000 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 30) {
            Task { await runOnceNow(reason: .coldStartDue) }
        }
    }

    @discardableResult
    static func runOnceNow(reason: BackupTriggerReason) async -> BackupExecutionResult? {
        guard isEnabled else { return nil }
        guard BackupSecretsStore.hasCached else { return nil }
        guard ExternalBackupLocation.isWritable() else { return nil }
        let result = await LocalBackupService.shared.createAutoBackup()
        if result.success {
            scheduleNextAppRefresh()
        }
        return result
    }

    private static func handleAppRefresh(task: BGAppRefreshTask) {
        scheduleNextAppRefresh()
        let work = Task {
            await runOnceNow(reason: .backgroundRefresh)
        }
        task.expirationHandler = { work.cancel() }
        Task {
            _ = await work.value
            task.setTaskCompleted(success: true)
        }
    }
}
