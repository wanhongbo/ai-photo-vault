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

    var body: some View {
        GeometryReader { proxy in
            let contentWidth = max(0, proxy.size.width - LNSpacing.screenHorizontal * 2)
            let cellSize = max(0, floor((contentWidth - LNSpacing.gridGap * 2) / 3))

            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    penNavigation
                    summaryCard
                    stateContent(contentWidth: contentWidth, cellSize: cellSize)
                }
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.bottom, 24)
                .frame(width: proxy.size.width, alignment: .topLeading)
            }
            .background(LNColor.bgBottom.ignoresSafeArea())
        }
        .task { await loadRecords() }
    }

    private var penNavigation: some View {
        HStack(spacing: 0) {
            Button(action: { dismiss() }) {
                Text("‹")
                    .font(.system(size: 30, weight: .medium))
                    .foregroundStyle(LNColor.title)
                    .frame(width: LNSpacing.minTouchTarget, height: 52, alignment: .leading)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L10n.commonBack)
            .accessibilityIdentifier("bulk_export_back")

            Text(L10n.tr("bulk_export_title"))
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(LNColor.title)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 52)
    }

    private var summaryCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.tr("bulk_export_select_title"))
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(LNColor.title)
            Text(L10n.tr("bulk_export_selected_count", selectedIds.count))
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(LNColor.subtitle)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(LNColor.stroke, lineWidth: 1)
        )
    }

    @ViewBuilder
    private func stateContent(contentWidth: CGFloat, cellSize: CGFloat) -> some View {
        if isLoading {
            ProgressView()
                .tint(LNColor.brandBlue)
                .frame(width: contentWidth)
                .frame(minHeight: 180)
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
            exportGrid(cellSize: cellSize)
            LNButton(
                title: L10n.tr("bulk_export_start"),
                variant: .primary,
                enabled: !selectedIds.isEmpty
            ) {
                let selected = records.filter { selectedIds.contains($0.id) }
                ExportRuntimeState.enqueue(records: selected)
                router.pushInCurrentTab(.exportProgress)
            }
            .frame(width: contentWidth)
        }
    }

    private func exportGrid(cellSize: CGFloat) -> some View {
        let fixedColumns = Array(
            repeating: GridItem(.fixed(cellSize), spacing: LNSpacing.gridGap),
            count: 3
        )

        return LazyVGrid(columns: fixedColumns, spacing: LNSpacing.gridGap) {
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
                    .frame(width: cellSize, height: cellSize)
                    .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeThumb))
                    .overlay(
                        RoundedRectangle(cornerRadius: LNRadius.homeThumb)
                            .stroke(selected ? LNColor.brandBlue : LNColor.stroke, lineWidth: selected ? 2 : 1)
                    )
                    .overlay(alignment: .topTrailing) {
                        if selected {
                            ZStack {
                                Circle().fill(LNColor.brandBlue)
                                Image(systemName: "checkmark")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(LNColor.bgBottom)
                            }
                            .frame(width: 24, height: 24)
                            .padding(8)
                        }
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(record.originalFileName ?? record.fileName)
                .accessibilityAddTraits(selected ? [.isSelected] : [])
            }
        }
        .frame(width: cellSize * 3 + LNSpacing.gridGap * 2, alignment: .leading)
        .frame(maxWidth: .infinity, alignment: .leading)
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

    @State private var progress = LongRunningTaskProgress(
        phase: .exporting,
        current: 0,
        total: 1,
        currentFileName: nil,
        bytesWritten: 0,
        totalBytes: 0,
        cancellable: true
    )
    @State private var showCancel = false
    @State private var exportTask: Task<Void, Never>?
    @State private var didCancel = false

    var body: some View {
        LNScreenScaffold(title: L10n.tr("export_progress_title"), onBack: { showCancel = true }) {
            VStack(spacing: 16) {
                LNProgressCard(title: progressTitle, progress: progress.fraction)

                Text(L10n.tr(progress.phase.localizationKey))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lnCard()

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
        L10n.tr("export_progress_count", progress.current, progress.total)
    }

    private func startExport() {
        guard exportTask == nil else { return }
        let records = ExportRuntimeState.pendingRecords
        progress = LongRunningTaskProgress(
            phase: .exporting,
            current: 0,
            total: max(records.count, 1),
            currentFileName: nil,
            bytesWritten: 0,
            totalBytes: records.reduce(Int64(0)) { $0 + max(0, $1.encryptedSizeBytes) },
            cancellable: true
        )
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
