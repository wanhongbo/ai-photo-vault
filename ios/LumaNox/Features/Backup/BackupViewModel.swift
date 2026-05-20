import Foundation
import SwiftUI
import UniformTypeIdentifiers

@MainActor
final class BackupRestoreViewModel: ObservableObject {
    @Published var showPinDialog = false
    @Published var pinError: String?
    @Published var isBusy = false
    @Published var errorMessage: String?
    @Published var successMessage: String?

    private var pendingRestoreURL: URL?

    func beginRestore(url: URL) {
        pendingRestoreURL = url
        pinError = nil
        showPinDialog = true
    }

    func cancelPin() {
        showPinDialog = false
        pendingRestoreURL = nil
        pinError = nil
    }

    func confirmRestorePin(_ pin: String) async -> URL? {
        guard let inputURL = pendingRestoreURL else { return nil }
        pinError = nil
        isBusy = true
        defer {
            isBusy = false
            showPinDialog = false
        }
        let localURL: URL
        do {
            let tmp = FileManager.default.temporaryDirectory
                .appendingPathComponent("restore_in_\(UUID().uuidString).aivb")
            if FileManager.default.fileExists(atPath: tmp.path) {
                try FileManager.default.removeItem(at: tmp)
            }
            try FileManager.default.copyItem(at: inputURL, to: tmp)
            localURL = tmp
        } catch {
            pinError = L10n.tr("restore_error_cannot_read_file")
            return nil
        }
        let result = await LocalBackupService.shared.restore(from: localURL, pin: pin)
        if result.success {
            await VaultStore.shared.loadSnapshot()
            BackupFlowState.lastRestore = result
            pendingRestoreURL = nil
            await AutoBackupScheduler.runOnceNow(reason: .manualRestoreSync)
            return localURL
        }
        pinError = result.message.isEmpty ? L10n.tr("restore_error_wrong_pin") : result.message
        return nil
    }

    func runBackup(to outputURL: URL, router: AppRouter) async -> BackupExecutionResult? {
        guard router.guardProFeature(.backupCreate) else { return nil }
        isBusy = true
        defer { isBusy = false }
        let result = await LocalBackupService.shared.createManualBackup(to: outputURL)
        if result.success {
            BackupFlowState.lastBackup = result
        } else {
            errorMessage = result.message
        }
        return result
    }
}

@MainActor
enum BackupFlowState {
    static var lastBackup: BackupExecutionResult?
    static var lastRestore: RestoreExecutionResult?
    static var backupOutputURL: URL?
    static var restoreInputURL: URL?
}

@MainActor
final class BackupProgressViewModel: ObservableObject {
    @Published var progress: Double = 0
    @Published var statusText = ""
    @Published var finished = false
    @Published var failed = false
    @Published var errorMessage: String?

    let outputURL: URL

    init(outputURL: URL) {
        self.outputURL = outputURL
    }

    func start() async {
        statusText = L10n.tr("backup_progress_title")
        progress = 0.1
        let result = await LocalBackupService.shared.createManualBackup(to: outputURL)
        progress = 1
        if result.success {
            BackupFlowState.lastBackup = result
            finished = true
            statusText = L10n.tr("backup_result_success")
        } else {
            failed = true
            errorMessage = result.message
        }
    }
}

@MainActor
final class RestoreProgressViewModel: ObservableObject {
    @Published var progress: Double = 0
    @Published var statusText = ""
    @Published var finished = false
    @Published var failed = false
    @Published var errorMessage: String?

    let inputURL: URL
    let pin: String

    init(inputURL: URL, pin: String) {
        self.inputURL = inputURL
        self.pin = pin
    }

    func start() async {
        statusText = L10n.tr("restore_progress_title")
        progress = 0.15
        let result = await LocalBackupService.shared.restore(from: inputURL, pin: pin)
        progress = 1
        if result.success {
            await VaultStore.shared.loadSnapshot()
            BackupFlowState.lastRestore = result
            finished = true
            statusText = L10n.tr("restore_result_success")
        } else {
            failed = true
            errorMessage = result.message
        }
    }
}

extension UTType {
    static let aivaultBackup = UTType(filenameExtension: "aivb") ?? .data
}
