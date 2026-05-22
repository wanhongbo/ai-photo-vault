import Foundation
import SwiftUI

@MainActor
final class SettingsBackupSyncViewModel: ObservableObject {
    @Published var autoBackupEnabled: Bool
    @Published var folderWritable = false
    @Published var folderPath: String?
    @Published var lastBackupText: String?
    @Published var isRunning = false
    @Published var statusMessage: String?

    init() {
        autoBackupEnabled = AutoBackupScheduler.isEnabled
        refresh()
    }

    var statusTitle: String {
        if !autoBackupEnabled {
            return L10n.tr("backup_auto_status_disabled")
        }
        if !folderWritable {
            return L10n.tr("backup_auto_status_needs_folder")
        }
        if !BackupSecretsStore.hasCached {
            return L10n.tr("backup_auto_status_locked")
        }
        return L10n.tr("backup_auto_status_ready_title")
    }

    var statusDetail: String {
        if !autoBackupEnabled {
            return L10n.tr("backup_auto_status_disabled_desc")
        }
        if !folderWritable {
            return L10n.tr("backup_auto_status_needs_folder_desc")
        }
        if !BackupSecretsStore.hasCached {
            return L10n.tr("backup_auto_status_locked_desc")
        }
        return folderPath ?? L10n.tr("backup_auto_status_ready")
    }

    var statusIconName: String {
        if !autoBackupEnabled {
            return "pause.circle.fill"
        }
        if folderWritable && BackupSecretsStore.hasCached {
            return "checkmark.shield.fill"
        }
        return "exclamationmark.triangle.fill"
    }

    var statusTint: Color {
        if !autoBackupEnabled {
            return LNColor.subtitle
        }
        if folderWritable && BackupSecretsStore.hasCached {
            return LNColor.success
        }
        return LNColor.amberWarning
    }

    func refresh() {
        folderWritable = ExternalBackupLocation.isWritable()
        folderPath = ExternalBackupLocation.displayPath
        if let ms = BackupMeta.load().auto?.lastBackupAtMs, ms > 0 {
            let date = Date(timeIntervalSince1970: TimeInterval(ms) / 1000)
            let fmt = DateFormatter()
            fmt.dateStyle = .medium
            fmt.timeStyle = .short
            lastBackupText = fmt.string(from: date)
        } else {
            lastBackupText = nil
        }
    }

    func setAutoBackupEnabled(_ enabled: Bool) {
        autoBackupEnabled = enabled
        AutoBackupScheduler.isEnabled = enabled
    }

    func onFolderPicked(_ url: URL, router: AppRouter) {
        guard url.startAccessingSecurityScopedResource() else {
            statusMessage = L10n.tr("backup_folder_pick_failed")
            return
        }
        defer { url.stopAccessingSecurityScopedResource() }
        do {
            try ExternalBackupLocation.persistFolder(url)
            ExternalBackupLocation.sanitizeOnStartup()
            AutoBackupScheduler.ensureScheduled()
            refresh()
            statusMessage = L10n.tr("backup_folder_linked")
            Task { await runAutoBackupNow(router: router) }
        } catch {
            statusMessage = error.localizedDescription
        }
    }

    func clearFolder() {
        ExternalBackupLocation.clearFolder()
        refresh()
        statusMessage = L10n.tr("backup_folder_cleared")
    }

    func runAutoBackupNow(router: AppRouter) async {
        guard router.guardProFeature(.backupCreate) else { return }
        guard folderWritable else {
            statusMessage = L10n.tr("backup_error_no_saf_dir")
            return
        }
        guard BackupSecretsStore.hasCached else {
            statusMessage = L10n.tr("backup_error_no_key")
            return
        }
        isRunning = true
        defer { isRunning = false }
        if let result = await AutoBackupScheduler.runOnceNow(reason: .userManualButton) {
            refresh()
            if result.success {
                statusMessage = L10n.tr(
                    "backup_auto_success_fmt",
                    result.assetCount,
                    ByteCountFormatter.string(fromByteCount: result.outputSizeBytes, countStyle: .file)
                )
            } else {
                statusMessage = result.message
            }
        }
    }
}
