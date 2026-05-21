import SwiftUI
import UIKit

@MainActor
enum ExportRuntimeState {
    static var sourceAlbumName: String?
    static var pendingRecords: [VaultMediaRecord] = []
    static var lastResult: MediaExportBatchResult?

    static func prepareSource(albumName: String?) {
        sourceAlbumName = albumName
        pendingRecords = []
        cleanupResultFiles()
        lastResult = nil
    }

    static func enqueue(records: [VaultMediaRecord]) {
        pendingRecords = records
        cleanupResultFiles()
        lastResult = nil
    }

    static func finish(_ result: MediaExportBatchResult) {
        lastResult = result
    }

    static func cleanupResultFiles() {
        MediaExportService.shared.cleanup(lastResult?.outputDirectory)
    }

    static func reset() {
        sourceAlbumName = nil
        pendingRecords = []
        cleanupResultFiles()
        lastResult = nil
    }
}

struct BulkExportView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var vaultStore: VaultStore
    @Environment(\.dismiss) private var dismiss

    @State private var records: [VaultMediaRecord] = []
    @State private var selectedIds = Set<String>()
    @State private var isLoading = true
    @State private var errorMessage: String?

    private let columns = [
        GridItem(.flexible(), spacing: LNSpacing.gridGap),
        GridItem(.flexible(), spacing: LNSpacing.gridGap),
        GridItem(.flexible(), spacing: LNSpacing.gridGap),
    ]

    var body: some View {
        LNScreenScaffold(title: L10n.tr("bulk_export_title"), onBack: { dismiss() }) {
            VStack(spacing: 16) {
                summaryCard

                if isLoading {
                    ProgressView()
                        .tint(LNColor.brandBlue)
                        .frame(maxWidth: .infinity, minHeight: 180)
                } else if let errorMessage {
                    LNEmptyStateCard(
                        title: L10n.tr("bulk_export_error_title"),
                        message: errorMessage,
                        actionTitle: L10n.tr("bulk_export_retry"),
                        action: { Task { await loadRecords() } }
                    )
                } else if records.isEmpty {
                    LNEmptyStateCard(
                        title: L10n.tr("bulk_export_empty_title"),
                        message: L10n.tr("bulk_export_empty_message"),
                        actionTitle: L10n.commonOk,
                        action: { dismiss() }
                    )
                } else {
                    exportGrid
                    LNButton(
                        title: L10n.tr("bulk_export_start"),
                        variant: .primary,
                        enabled: !selectedIds.isEmpty
                    ) {
                        let selected = records.filter { selectedIds.contains($0.id) }
                        ExportRuntimeState.enqueue(records: selected)
                        router.pushInCurrentTab(.exportProgress)
                    }
                }
            }
        }
        .task { await loadRecords() }
    }

    private var summaryCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.tr("bulk_export_select_title"))
                .font(LNTypography.titleMedium())
                .foregroundStyle(LNColor.title)
            Text(L10n.tr("bulk_export_selected_count", selectedIds.count))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .lnCard()
    }

    private var exportGrid: some View {
        LazyVGrid(columns: columns, spacing: LNSpacing.gridGap) {
            ForEach(records) { record in
                let selected = selectedIds.contains(record.id)
                Button {
                    toggle(record)
                } label: {
                    VaultMediaThumbnailView(
                        encryptedPath: mediaPath(for: record),
                        isVideo: record.isVideo,
                        contentMode: .fill,
                        targetPixelSize: 360
                    )
                    .aspectRatio(1, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeThumb))
                    .overlay(
                        RoundedRectangle(cornerRadius: LNRadius.homeThumb)
                            .stroke(selected ? LNColor.brandBlue : LNColor.stroke, lineWidth: selected ? 2 : 1)
                    )
                    .overlay(alignment: .topTrailing) {
                        Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 23, weight: .semibold))
                            .foregroundStyle(selected ? LNColor.brandBlue : LNColor.title.opacity(0.72))
                            .padding(7)
                            .shadow(radius: 3)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(record.originalFileName ?? record.fileName)
                .accessibilityAddTraits(selected ? [.isSelected] : [])
            }
        }
    }

    private func toggle(_ record: VaultMediaRecord) {
        if selectedIds.contains(record.id) {
            selectedIds.remove(record.id)
        } else {
            selectedIds.insert(record.id)
        }
    }

    private func loadRecords() async {
        isLoading = true
        errorMessage = nil
        await vaultStore.loadSnapshot(recentLimit: 200)
        do {
            let root = try vaultStore.rootDirectory()
            let trash = try vaultStore.trashDirectory()
            let snapshot = try VaultMetadataStore.shared.reconcile(vaultRoot: root, trashRoot: trash)
            if let albumName = ExportRuntimeState.sourceAlbumName {
                records = snapshot.activeMedia(in: albumName)
            } else {
                records = snapshot.activeMedia.sorted { $0.modifiedAtMs > $1.modifiedAtMs }
            }
            selectedIds = selectedIds.intersection(Set(records.map(\.id)))
            isLoading = false
        } catch {
            records = []
            selectedIds = []
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }

    private func mediaPath(for record: VaultMediaRecord) -> String {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return record.absoluteURL(documentsDirectory: docs).path
    }
}

struct ExportProgressView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss

    @State private var progress = MediaExportProgress(completed: 0, total: 1, currentFileName: nil)
    @State private var showCancel = false
    @State private var exportTask: Task<Void, Never>?
    @State private var didCancel = false

    var body: some View {
        LNScreenScaffold(title: L10n.tr("export_progress_title"), onBack: { showCancel = true }) {
            VStack(spacing: 16) {
                LNProgressCard(title: progressTitle, progress: progress.fraction)

                if let currentFileName = progress.currentFileName {
                    Text(L10n.tr("export_progress_current", currentFileName))
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.subtitle)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .lnCard()
                }

                LNButton(title: L10n.commonCancel, variant: .secondary) {
                    showCancel = true
                }
            }
        }
        .overlay {
            if showCancel {
                LNDialog(
                    title: L10n.tr("export_cancel_title"),
                    message: L10n.tr("export_cancel_message"),
                    confirmTitle: L10n.tr("export_cancel_confirm"),
                    dismissTitle: L10n.tr("export_cancel_continue"),
                    confirmVariant: .danger,
                    onConfirm: cancelExport,
                    onDismiss: { showCancel = false }
                )
            }
        }
        .onAppear(perform: startExport)
        .onDisappear {
            if didCancel {
                exportTask?.cancel()
            }
        }
    }

    private var progressTitle: String {
        L10n.tr("export_progress_count", progress.completed, progress.total)
    }

    private func startExport() {
        guard exportTask == nil else { return }
        let records = ExportRuntimeState.pendingRecords
        progress = MediaExportProgress(completed: 0, total: max(records.count, 1), currentFileName: nil)
        exportTask = Task {
            let result = await MediaExportService.shared.export(records: records) { next in
                progress = next
            }
            guard !Task.isCancelled, !didCancel, !result.cancelled else { return }
            ExportRuntimeState.finish(result)
            router.pushInCurrentTab(.exportResult)
        }
    }

    private func cancelExport() {
        didCancel = true
        showCancel = false
        exportTask?.cancel()
        ExportRuntimeState.pendingRecords = []
        dismiss()
    }
}

struct ExportResultView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @State private var sharePayload: ExportSharePayload?

    private var result: MediaExportBatchResult {
        ExportRuntimeState.lastResult ?? .empty
    }

    var body: some View {
        LNScreenScaffold(title: L10n.tr("export_result_title"), onBack: close) {
            VStack(spacing: 16) {
                resultCard

                if !result.exportedFiles.isEmpty {
                    LNButton(title: L10n.tr("export_result_share"), variant: .primary) {
                        sharePayload = ExportSharePayload(urls: result.exportedFiles)
                    }
                }

                LNButton(title: L10n.tr("export_result_done"), variant: .secondary) {
                    close()
                }
            }
        }
        .sheet(item: $sharePayload) { payload in
            ExportShareSheet(urls: payload.urls)
        }
    }

    private var resultCard: some View {
        VStack(spacing: 12) {
            Image(systemName: result.failedCount == 0 ? "checkmark.shield.fill" : "exclamationmark.triangle.fill")
                .font(.system(size: 36, weight: .semibold))
                .foregroundStyle(result.failedCount == 0 ? LNColor.success : LNColor.amberWarning)
                .padding(18)
                .background((result.failedCount == 0 ? LNColor.success : LNColor.amberWarning).opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 26))

            Text(L10n.tr("export_result_success_count", result.successCount))
                .font(LNTypography.headlineMedium())
                .foregroundStyle(LNColor.title)

            Text(L10n.tr("export_result_failed_count", result.failedCount))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)

            if let firstFailure = result.failures.first {
                Text(firstFailure.reason)
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
            }
        }
        .frame(maxWidth: .infinity)
        .lnCard()
    }

    private func close() {
        ExportRuntimeState.reset()
        if router.selectedTab == .vault || router.selectedTab == .settings || router.selectedTab == .ai {
            router.popCurrentTab(count: 3)
        } else {
            dismiss()
        }
    }
}

private struct ExportSharePayload: Identifiable {
    let id = UUID()
    let urls: [URL]
}

private struct ExportShareSheet: UIViewControllerRepresentable {
    let urls: [URL]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: urls, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
