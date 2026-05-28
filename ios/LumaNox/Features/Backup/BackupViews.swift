import SwiftUI
import UniformTypeIdentifiers

struct BackupRestoreView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = BackupRestoreViewModel()
    @State private var showImporter = false
    @State private var importerLockToken: UUID?

    var body: some View {
        LNScreenScaffold(title: L10n.backupRestoreTitle, onBack: { dismiss() }) {
            autoBackupStatusCard

            Text(L10n.tr("backup_restore_hint"))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .frame(maxWidth: .infinity, alignment: .leading)

            BackupActionCard(
                icon: "square.and.arrow.up",
                title: L10n.tr("backup_manual_export"),
                message: L10n.tr("backup_manual_export_desc"),
                actionTitle: L10n.tr("backup_manual_export_start"),
                variant: .primary,
                loading: viewModel.isBusy
            ) {
                guard !viewModel.isBusy else { return }
                Task {
                    viewModel.isBusy = true
                    defer { viewModel.isBusy = false }
                    guard let outputURL = await viewModel.prepareBackupOutputURL(router: router) else { return }
                    router.pushSettings(.backupProgress(outputUri: outputURL.path))
                }
            }

            BackupActionCard(
                icon: "square.and.arrow.down",
                title: L10n.tr("backup_manual_import"),
                message: L10n.tr("backup_manual_import_desc"),
                actionTitle: L10n.tr("backup_manual_import_start"),
                variant: .secondary,
                loading: false
            ) {
                importerLockToken = AppLockManager.shared.beginSystemInteraction(timeout: 300)
                showImporter = true
            }
        }
        .overlay { dialogs }
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.data, .item],
            allowsMultipleSelection: false
        ) { result in
            AppLockManager.shared.endSystemInteraction(importerLockToken)
            importerLockToken = nil
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                guard url.startAccessingSecurityScopedResource() else {
                    viewModel.errorMessage = L10n.tr("restore_error_cannot_read_file")
                    return
                }
                defer { url.stopAccessingSecurityScopedResource() }
                viewModel.beginRestore(url: url)
            case .failure(let error):
                viewModel.errorMessage = error.localizedDescription
            }
        }
    }

    private var autoBackupStatusCard: some View {
        let ready = ExternalBackupLocation.isWritable()
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: ready ? "checkmark.shield.fill" : "exclamationmark.triangle.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(ready ? LNColor.success : LNColor.amberWarning)
                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.tr("backup_auto_status_title"))
                        .font(LNTypography.titleMedium())
                        .foregroundStyle(LNColor.title)
                    Text(ready ? L10n.tr("backup_auto_status_ready") : L10n.tr("backup_auto_status_unlinked"))
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.subtitle)
                }
            }
            if let path = ExternalBackupLocation.displayPath {
                Text(path)
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle.opacity(0.85))
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(LNSpacing.cardPadding)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
        .overlay(
            RoundedRectangle(cornerRadius: LNRadius.homeCard)
                .stroke(ready ? LNColor.stroke : LNColor.amberWarning.opacity(0.65), lineWidth: 1)
        )
    }

    @ViewBuilder
    private var dialogs: some View {
        if viewModel.showPinDialog {
            LNPinDialog(
                title: L10n.tr("backup_restore_pin_title"),
                subtitle: L10n.tr("backup_restore_pin_subtitle"),
                confirmTitle: L10n.tr("backup_restore_pin_confirm"),
                dismissTitle: L10n.commonCancel,
                errorMessage: viewModel.pinError,
                busy: viewModel.isBusy,
                onConfirm: { pin in
                    Task {
                        if let url = await viewModel.confirmRestorePin(pin) {
                            router.pushSettings(.restoreProgress(inputUri: url.path, pin: pin))
                        }
                    }
                },
                onDismiss: { viewModel.cancelPin() }
            )
        }
        if let msg = viewModel.errorMessage {
            LNDialog(
                title: L10n.tr("settings_pin_error_title"),
                message: msg,
                confirmTitle: L10n.tr("settings_pin_error_action"),
                onConfirm: { viewModel.errorMessage = nil }
            )
        }
    }
}

/// Writable document for completed backup exports.
struct BackupExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    static var writableContentTypes: [UTType] { [.data] }

    private let fileURL: URL?

    init(fileURL: URL? = nil) {
        self.fileURL = fileURL
    }

    init(configuration: ReadConfiguration) throws {
        fileURL = nil
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        guard let fileURL else {
            return FileWrapper(regularFileWithContents: Data())
        }
        return try FileWrapper(url: fileURL, options: .immediate)
    }
}

struct BackupProgressView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    let outputUri: String
    @StateObject private var viewModel: BackupProgressViewModel
    @State private var showCancel = false

    init(outputUri: String) {
        self.outputUri = outputUri
        let url = URL(fileURLWithPath: outputUri)
        _viewModel = StateObject(wrappedValue: BackupProgressViewModel(outputURL: url))
    }

    var body: some View {
        LNScreenScaffold(title: L10n.tr("backup_progress_title"), onBack: backOrConfirmCancel) {
            LongTaskProgressContent(progress: viewModel.progress)
            LNButton(
                title: L10n.commonCancel,
                variant: .secondary,
                enabled: viewModel.progress.cancellable && !viewModel.finished && !viewModel.failed
            ) {
                showCancel = true
            }
        }
        .onAppear {
            guard router.guardProFeature(.backupCreate) else {
                dismiss()
                return
            }
            viewModel.start {
                router.pushSettings(.backupResult)
            }
        }
        .onDisappear {
            if !viewModel.finished && !viewModel.failed {
                viewModel.cancel()
            }
        }
        .overlay {
            if showCancel {
                LNDialog(
                    title: L10n.tr("backup_cancel_title"),
                    message: L10n.tr("backup_cancel_message"),
                    confirmTitle: L10n.tr("backup_cancel_confirm"),
                    dismissTitle: L10n.tr("backup_cancel_continue"),
                    confirmVariant: .danger,
                    onConfirm: {
                        showCancel = false
                        viewModel.cancel()
                        dismiss()
                    },
                    onDismiss: { showCancel = false }
                )
            }
            if viewModel.failed, let msg = viewModel.errorMessage {
                LNDialog(
                    title: L10n.tr("settings_pin_error_title"),
                    message: msg,
                    confirmTitle: L10n.commonOk,
                    onConfirm: { dismiss() }
                )
            }
        }
    }

    private func backOrConfirmCancel() {
        if viewModel.progress.cancellable && !viewModel.finished && !viewModel.failed {
            showCancel = true
        } else {
            dismiss()
        }
    }
}

struct BackupResultView: View {
    @EnvironmentObject private var router: AppRouter
    @State private var showExporter = false
    @State private var exporterLockToken: UUID?
    @State private var exportDocument = BackupExportDocument()
    @State private var saved = false
    @State private var errorMessage: String?

    var body: some View {
        let result = BackupFlowState.lastBackup
        LNScreenScaffold(title: L10n.tr("backup_result_success"), onBack: { dismiss() }) {
            BackupResultCard(
                icon: "checkmark.shield.fill",
                iconTint: LNColor.success,
                title: L10n.tr("backup_result_success"),
                message: result.map {
                    L10n.tr(
                        "backup_success_message_fmt",
                        $0.assetCount,
                        ByteCountFormatter.string(fromByteCount: $0.outputSizeBytes, countStyle: .file)
                    )
                } ?? L10n.tr("backup_result_success"),
                footnote: saved ? L10n.tr("backup_result_saved_hint") : L10n.tr("backup_result_save_required")
            )
            if !saved, let outputURL = BackupFlowState.backupOutputURL {
                LNButton(title: L10n.tr("backup_result_save_file"), variant: .primary) {
                    exportDocument = BackupExportDocument(fileURL: outputURL)
                    exporterLockToken = AppLockManager.shared.beginSystemInteraction(timeout: 300)
                    showExporter = true
                }
            }
            LNButton(title: saved ? L10n.commonOk : L10n.tr("backup_result_skip_save"), variant: .secondary) {
                dismiss()
            }
        }
        .onAppear { saved = BackupFlowState.backupOutputURL == nil }
        .fileExporter(
            isPresented: $showExporter,
            document: exportDocument,
            contentType: .aivaultBackup,
            defaultFilename: defaultBackupFilename
        ) { result in
            AppLockManager.shared.endSystemInteraction(exporterLockToken)
            exporterLockToken = nil
            switch result {
            case .success:
                cleanupExport()
                saved = true
            case .failure(let error):
                errorMessage = error.localizedDescription
            }
            exportDocument = BackupExportDocument()
        }
        .overlay {
            if let errorMessage {
                LNDialog(
                    title: L10n.tr("settings_pin_error_title"),
                    message: errorMessage,
                    confirmTitle: L10n.commonOk,
                    onConfirm: { self.errorMessage = nil }
                )
            }
        }
    }

    private func dismiss() {
        cleanupExport()
        router.popCurrentTab(count: 2)
    }

    private func cleanupExport() {
        if let url = BackupFlowState.backupOutputURL {
            PlaintextTempFileManager.shared.removeItem(url)
            BackupFlowState.backupOutputURL = nil
        }
    }

    private var defaultBackupFilename: String {
        "LumaNox_backup_\(Int(Date().timeIntervalSince1970)).aivb"
    }
}

struct RestoreProgressView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    let inputUri: String
    let pin: String
    @StateObject private var viewModel: RestoreProgressViewModel
    @State private var showCancel = false

    init(inputUri: String, pin: String = "") {
        self.inputUri = inputUri
        self.pin = pin
        _viewModel = StateObject(wrappedValue: RestoreProgressViewModel(
            inputURL: URL(fileURLWithPath: inputUri),
            pin: pin
        ))
    }

    var body: some View {
        LNScreenScaffold(title: L10n.tr("restore_progress_title"), onBack: backOrConfirmCancel) {
            LongTaskProgressContent(progress: viewModel.progress)
            LNButton(
                title: L10n.commonCancel,
                variant: .secondary,
                enabled: viewModel.progress.cancellable && !viewModel.finished && !viewModel.failed
            ) {
                showCancel = true
            }
        }
        .onAppear {
            viewModel.start {
                router.pushSettings(.restoreResult)
            }
        }
        .onDisappear {
            if !viewModel.finished && !viewModel.failed {
                viewModel.cancel()
            }
        }
        .overlay {
            if showCancel {
                LNDialog(
                    title: L10n.tr("restore_cancel_title"),
                    message: L10n.tr("restore_cancel_message"),
                    confirmTitle: L10n.tr("restore_cancel_confirm"),
                    dismissTitle: L10n.tr("restore_cancel_continue"),
                    confirmVariant: .danger,
                    onConfirm: {
                        showCancel = false
                        viewModel.cancel()
                        dismiss()
                    },
                    onDismiss: { showCancel = false }
                )
            }
            if viewModel.failed, let msg = viewModel.errorMessage {
                LNDialog(
                    title: L10n.tr("settings_pin_error_title"),
                    message: msg,
                    confirmTitle: L10n.commonOk,
                    onConfirm: { dismiss() }
                )
            }
        }
    }

    private func backOrConfirmCancel() {
        if viewModel.progress.cancellable && !viewModel.finished && !viewModel.failed {
            showCancel = true
        } else {
            dismiss()
        }
    }
}

struct RestoreResultView: View {
    @EnvironmentObject private var router: AppRouter

    var body: some View {
        let result = BackupFlowState.lastRestore
        LNScreenScaffold(title: L10n.tr("restore_result_title"), onBack: { dismiss() }) {
            BackupResultCard(
                icon: "arrow.down.doc.fill",
                iconTint: LNColor.success,
                title: L10n.tr("restore_result_success"),
                message: result.map {
                    L10n.tr("restore_result_detail_fmt", $0.restored, $0.skipped, $0.failed)
                } ?? L10n.tr("backup_restore_success_message"),
                footnote: L10n.tr("restore_result_sync_hint")
            )
            LNButton(title: L10n.commonOk, variant: .primary) { dismiss() }
        }
    }

    private func dismiss() {
        router.popCurrentTab(count: 2)
    }
}

private struct BackupActionCard: View {
    let icon: String
    let title: String
    let message: String
    let actionTitle: String
    let variant: LNButtonVariant
    let loading: Bool
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(variant == .primary ? LNColor.brandBlue : LNColor.navItemActive)
                    .frame(width: 44, height: 44)
                    .background(LNColor.emptyIconBg)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 5) {
                    Text(title)
                        .font(LNTypography.titleMedium())
                        .foregroundStyle(LNColor.title)
                    Text(message)
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.subtitle)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            LNButton(title: actionTitle, variant: variant, loading: loading, action: action)
        }
        .lnCard()
    }
}

private struct BackupResultCard: View {
    let icon: String
    let iconTint: Color
    let title: String
    let message: String
    let footnote: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(iconTint)
                .frame(width: 68, height: 68)
                .background(iconTint.opacity(0.14))
                .clipShape(RoundedRectangle(cornerRadius: 24))
            Text(title)
                .font(LNTypography.headlineMedium())
                .foregroundStyle(LNColor.title)
                .multilineTextAlignment(.center)
            Text(message)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .multilineTextAlignment(.center)
            Text(footnote)
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle.opacity(0.85))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .lnCard()
    }
}

private struct LongTaskProgressContent: View {
    let progress: LongRunningTaskProgress

    var body: some View {
        VStack(spacing: 14) {
            LNProgressCard(
                title: L10n.tr(progress.phase.localizationKey),
                progress: progress.fraction
            )

            VStack(alignment: .leading, spacing: 8) {
                Text(L10n.tr("long_task_items_fmt", progress.current, progress.total))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)

                if progress.totalBytes > 0 {
                    Text(L10n.tr(
                        "long_task_bytes_fmt",
                        ByteCountFormatter.string(fromByteCount: progress.bytesWritten, countStyle: .file),
                        ByteCountFormatter.string(fromByteCount: progress.totalBytes, countStyle: .file)
                    ))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
                }

                if let fileName = progress.currentFileName {
                    Text(L10n.tr("long_task_current_file_fmt", fileName))
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(LNColor.subtitle.opacity(0.9))
                        .lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .lnCard()
        }
    }
}
