import SwiftUI
import UniformTypeIdentifiers

struct BackupRestoreView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = BackupRestoreViewModel()
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var exportDocument = BackupExportDocument()

    var body: some View {
        LNScreenScaffold(title: L10n.backupRestoreTitle, onBack: { dismiss() }) {
            autoBackupStatusCard

            Text(L10n.tr("backup_restore_hint"))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .frame(maxWidth: .infinity, alignment: .leading)

            LNButton(title: L10n.tr("backup_manual_export"), variant: .primary) {
                guard router.guardProFeature(.backupCreate) else { return }
                if BackupSecretsStore.hasCached {
                    showExporter = true
                } else {
                    viewModel.errorMessage = L10n.tr("backup_error_no_key")
                }
            }
            LNButton(title: L10n.tr("backup_manual_import"), variant: .secondary) {
                showImporter = true
            }
        }
        .overlay { dialogs }
        .fileExporter(
            isPresented: $showExporter,
            document: exportDocument,
            contentType: .data,
            defaultFilename: defaultBackupFilename
        ) { result in
            switch result {
            case .success(let url):
                BackupFlowState.backupOutputURL = url
                router.pushSettings(.backupProgress(outputUri: url.path))
            case .failure(let error):
                viewModel.errorMessage = error.localizedDescription
            }
        }
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.data, .item],
            allowsMultipleSelection: false
        ) { result in
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
        VStack(alignment: .leading, spacing: 8) {
            Text(L10n.tr("backup_auto_status_title"))
                .font(LNTypography.titleMedium())
                .foregroundStyle(LNColor.title)
            Text(
                ExternalBackupLocation.isWritable()
                    ? L10n.tr("backup_auto_status_ready")
                    : L10n.tr("backup_auto_status_unlinked")
            )
            .font(LNTypography.bodyMedium())
            .foregroundStyle(LNColor.subtitle)
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
    }

    @ViewBuilder
    private var dialogs: some View {
        if viewModel.showPinDialog {
            LNPinDialog(
                title: L10n.tr("backup_restore_pin_title"),
                subtitle: L10n.tr("lock_subtitle"),
                confirmTitle: L10n.commonConfirm,
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

    private var defaultBackupFilename: String {
        "LumaNox_backup_\(Int(Date().timeIntervalSince1970)).aivb"
    }
}

/// Empty writable document for `.fileExporter` target selection.
struct BackupExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    static var writableContentTypes: [UTType] { [.data] }

    init() {}
    init(configuration: ReadConfiguration) throws {}
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data())
    }
}

struct BackupProgressView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    let outputUri: String
    @StateObject private var viewModel: BackupProgressViewModel

    init(outputUri: String) {
        self.outputUri = outputUri
        let url = URL(fileURLWithPath: outputUri)
        _viewModel = StateObject(wrappedValue: BackupProgressViewModel(outputURL: url))
    }

    var body: some View {
        LNScreenScaffold(title: L10n.tr("backup_progress_title"), onBack: { dismiss() }) {
            LNProgressCard(title: outputUri, progress: viewModel.progress)
            Text(viewModel.statusText)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
        }
        .task {
            guard router.guardProFeature(.backupCreate) else {
                dismiss()
                return
            }
            await viewModel.start()
            if viewModel.finished {
                router.pushSettings(.backupResult)
            }
        }
        .overlay {
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
}

struct BackupResultView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let result = BackupFlowState.lastBackup
        LNScreenScaffold(title: L10n.tr("backup_result_success"), onBack: { dismiss() }) {
            LNEmptyStateCard(
                title: L10n.tr("backup_result_success"),
                message: result.map {
                    L10n.tr("backup_success_message_fmt", $0.assetCount, ByteCountFormatter.string(fromByteCount: $0.outputSizeBytes, countStyle: .file))
                } ?? L10n.tr("backup_result_success"),
                actionTitle: L10n.commonOk,
                action: { dismiss() }
            )
        }
    }
}

struct RestoreProgressView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    let inputUri: String
    let pin: String
    @StateObject private var viewModel: RestoreProgressViewModel

    init(inputUri: String, pin: String = "") {
        self.inputUri = inputUri
        self.pin = pin
        _viewModel = StateObject(wrappedValue: RestoreProgressViewModel(
            inputURL: URL(fileURLWithPath: inputUri),
            pin: pin
        ))
    }

    var body: some View {
        LNScreenScaffold(title: L10n.tr("restore_progress_title"), onBack: { dismiss() }) {
            LNProgressCard(title: inputUri, progress: viewModel.progress)
        }
        .task {
            await viewModel.start()
            if viewModel.finished {
                router.pushSettings(.restoreResult)
            }
        }
        .overlay {
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
}

struct RestoreResultView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let result = BackupFlowState.lastRestore
        LNScreenScaffold(title: L10n.tr("restore_result_title"), onBack: { dismiss() }) {
            LNEmptyStateCard(
                title: L10n.tr("restore_result_success"),
                message: result.map {
                    L10n.tr("restore_result_detail_fmt", $0.restored, $0.skipped, $0.failed)
                } ?? L10n.tr("backup_restore_success_message"),
                actionTitle: L10n.commonOk,
                action: { dismiss() }
            )
        }
    }
}
