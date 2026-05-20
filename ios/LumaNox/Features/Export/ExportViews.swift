import SwiftUI

struct BulkExportView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @State private var selected = Set<String>()

    var body: some View {
        LNScreenScaffold(title: L10n.tr("bulk_export_title"), onBack: { dismiss() }) {
            LNMediaGrid(items: MockData.mediaItems) { item in
                if selected.contains(item.id) { selected.remove(item.id) } else { selected.insert(item.id) }
            }
            LNButton(title: L10n.tr("export_progress_title"), variant: .primary, enabled: !selected.isEmpty) {
                router.pushInCurrentTab(.exportProgress)
            }
        }
    }
}

struct ExportProgressView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @State private var progress = 0.35
    @State private var showCancel = false

    var body: some View {
        LNScreenScaffold(title: L10n.tr("export_progress_title"), onBack: { showCancel = true }) {
            LNProgressCard(title: L10n.tr("export_progress_title"), progress: progress)
            LNButton(title: L10n.commonCancel, variant: .secondary) { showCancel = true }
        }
        .overlay {
            if showCancel {
                LNDialog(
                    title: L10n.tr("export_cancel_title"),
                    message: L10n.tr("export_cancel_message"),
                    confirmTitle: L10n.tr("export_cancel_confirm"),
                    dismissTitle: L10n.tr("export_cancel_continue"),
                    confirmVariant: .danger,
                    onConfirm: { showCancel = false; dismiss() },
                    onDismiss: { showCancel = false }
                )
            }
        }
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                router.pushInCurrentTab(.exportResult)
            }
        }
    }
}

struct ExportResultView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        LNScreenScaffold(title: L10n.tr("export_result_title"), onBack: { dismiss() }) {
            LNEmptyStateCard(
                title: L10n.tr("export_result_title"),
                message: L10n.tr("placeholder_feature"),
                actionTitle: L10n.commonOk,
                action: { dismiss() }
            )
        }
    }
}
