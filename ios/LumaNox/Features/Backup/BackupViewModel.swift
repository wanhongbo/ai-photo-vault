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
            localURL = try PlaintextTempFileManager.shared.copyFileToTemporary(
                sourceURL: inputURL,
                scene: .restore,
                preferredName: "restore_in_\(UUID().uuidString).aivb"
            )
        } catch {
            pinError = L10n.tr("restore_error_cannot_read_file")
            return nil
        }
        pendingRestoreURL = nil
        return localURL
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
    @Published var progress = LongRunningTaskProgress.initial(phase: .preparing)
    @Published var finished = false
    @Published var failed = false
    @Published var errorMessage: String?

    let outputURL: URL
    private var task: Task<Void, Never>?

    init(outputURL: URL) {
        self.outputURL = outputURL
    }

    func start(onFinished: @escaping @MainActor () -> Void) {
        guard task == nil else { return }
        task = Task {
            let result = await LocalBackupService.shared.createManualBackup(to: outputURL) { [weak self] next in
                self?.progress = next
            }
            guard !Task.isCancelled else { return }
            if result.success {
                BackupFlowState.lastBackup = result
                finished = true
                progress = LongRunningTaskProgress(
                    phase: .completed,
                    current: result.assetCount,
                    total: result.assetCount,
                    currentFileName: outputURL.lastPathComponent,
                    bytesWritten: result.outputSizeBytes,
                    totalBytes: result.outputSizeBytes,
                    cancellable: false
                )
                onFinished()
            } else if result.cancelled {
                finished = false
            } else {
                failed = true
                errorMessage = result.message
            }
        }
    }

    func cancel() {
        task?.cancel()
        task = nil
        try? FileManager.default.removeItem(at: outputURL)
    }
}

@MainActor
final class RestoreProgressViewModel: ObservableObject {
    @Published var progress = LongRunningTaskProgress.initial(phase: .verifying)
    @Published var finished = false
    @Published var failed = false
    @Published var errorMessage: String?

    let inputURL: URL
    let pin: String
    private var task: Task<Void, Never>?

    init(inputURL: URL, pin: String) {
        self.inputURL = inputURL
        self.pin = pin
    }

    func start(onFinished: @escaping @MainActor () -> Void) {
        guard task == nil else { return }
        task = Task {
            let result = await LocalBackupService.shared.restore(from: inputURL, pin: pin) { [weak self] next in
                self?.progress = next
            }
            PlaintextTempFileManager.shared.removeItem(inputURL)
            guard !Task.isCancelled else { return }
            if result.success {
                await VaultStore.shared.loadSnapshot()
                BackupFlowState.lastRestore = result
                finished = true
                progress = LongRunningTaskProgress(
                    phase: .completed,
                    current: result.restored + result.skipped + result.failed,
                    total: result.restored + result.skipped + result.failed,
                    currentFileName: nil,
                    bytesWritten: progress.totalBytes,
                    totalBytes: progress.totalBytes,
                    cancellable: false
                )
                await AutoBackupScheduler.runOnceNow(reason: .manualRestoreSync)
                onFinished()
            } else if result.cancelled {
                finished = false
            } else {
                failed = true
                errorMessage = result.message
            }
        }
    }

    func cancel() {
        task?.cancel()
        task = nil
        PlaintextTempFileManager.shared.removeItem(inputURL)
    }
}

extension UTType {
    static let aivaultBackup = UTType(filenameExtension: "aivb") ?? .data
}
