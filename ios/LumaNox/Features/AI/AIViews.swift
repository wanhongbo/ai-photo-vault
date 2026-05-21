import SwiftUI
import UIKit

struct AIHomeView: View {
    @EnvironmentObject private var router: AppRouter
    @ObservedObject private var aiService = VaultAIAnalysisService.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L10n.aiTitle)
                .font(LNTypography.displaySmall())
                .foregroundStyle(LNColor.title)
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.top, 8)

            Text(L10n.tr("ai_home_subtitle"))
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(LNColor.subtitle)
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.top, 2)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    aiScanSummaryCard
                    HStack {
                        Text(L10n.tr("ai_tools_title"))
                            .font(LNTypography.titleLarge())
                            .foregroundStyle(LNColor.title)
                        Spacer()
                        Text(L10n.tr("ai_tools_count_fmt", aiToolRows.count))
                            .font(LNTypography.labelMedium())
                            .foregroundStyle(LNColor.subtitle)
                    }
                    aiToolList
                }
                .padding(LNSpacing.screenHorizontal)
                .padding(.top, 14)
                .padding(.bottom, LNSpacing.homeNavBarHeight + 16)
            }
        }
        .task {
            aiService.refreshSummary()
        }
        .accessibilityIdentifier("ai_home_view")
    }

    private var aiScanSummaryCard: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack(spacing: 12) {
                iconWell(systemName: summaryIcon, foreground: summaryAccent, background: summaryAccent.opacity(0.20), size: 44)
                VStack(alignment: .leading, spacing: 4) {
                    Text(summaryBadge)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(summaryAccent)
                    Text(summaryTitle)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(LNColor.title)
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)
                }
            }

            Text(summaryDescription)
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(LNColor.subtitle)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)

            if aiService.progress.running {
                ProgressView(value: aiService.progress.fraction)
                    .tint(LNColor.brandBlue)
                Text(L10n.tr("ai_scan_progress_fmt", aiService.progress.done, aiService.progress.total))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle)
            } else {
                HStack(spacing: 12) {
                    Button { startScan() } label: {
                        Text(primarySummaryAction)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Color.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 40)
                            .background(LNColor.brandBlue)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                    .disabled(aiService.summary.totalCount == 0)

                    Button { router.pushAI(.aiSensitive) } label: {
                        Text(L10n.tr("ai_summary_review_now"))
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color(hex: 0xB7C6DD))
                            .frame(width: 102, height: 40)
                            .background(Color(hex: 0x122033))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(LNColor.stroke, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(LNSpacing.cardPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
        .overlay(RoundedRectangle(cornerRadius: LNRadius.homeCard).stroke(Color(hex: 0x244869), lineWidth: 1))
        .accessibilityIdentifier("ai_scan_summary_card")
    }

    private var aiToolList: some View {
        VStack(spacing: 10) {
            ForEach(aiToolRows) { tool in
                aiToolRow(tool)
            }
        }
    }

    private func aiToolRow(_ tool: AIToolRowModel) -> some View {
        Button { openAIFeature(tool.route, proFeature: tool.proFeature, router: router) } label: {
            HStack(spacing: 12) {
                iconWell(
                    systemName: tool.systemImage,
                    foreground: tool.foreground,
                    background: tool.background,
                    size: 44
                )
                VStack(alignment: .leading, spacing: 4) {
                    Text(tool.title)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(LNColor.title)
                        .lineLimit(1)
                    Text(tool.subtitle)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(LNColor.subtitle)
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                Text(tool.status)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xB7C6DD))
                    .frame(width: 58, height: 30)
                    .background(Color(hex: 0x122033))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(LNColor.stroke, lineWidth: 1))
            }
            .padding(14)
            .frame(maxWidth: .infinity)
            .frame(height: 86)
            .background(LNColor.sectionBg)
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(tool.stroke, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(tool.accessibilityIdentifier)
    }

    private func iconWell(systemName: String, foreground: Color, background: Color, size: CGFloat) -> some View {
        Image(systemName: systemName)
            .font(.system(size: size * 0.5, weight: .semibold))
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 13))
    }

    private func openAIFeature(_ route: AppRoute, proFeature: ProFeature, router: AppRouter) {
        if route == .aiClassify {
            router.pushAI(route)
            return
        }
        guard router.guardProFeature(proFeature) else { return }
        QuotaManager.shared.incrementAiUsage()
        router.pushAI(route)
    }

    private func startScan() {
        Task { await aiService.scanVault() }
    }

    private var summaryBadge: String {
        if aiService.progress.running { return L10n.tr("ai_summary_scanning_badge") }
        if aiService.summary.totalCount == 0 { return L10n.tr("ai_summary_empty_badge") }
        if aiService.summary.sensitiveCount > 0 { return L10n.tr("ai_suggest_badge_sensitive") }
        if aiService.summary.cleanupCount > 0 { return L10n.tr("ai_summary_cleanup_badge") }
        if aiService.summary.hasUnscanned { return L10n.tr("ai_summary_unscanned_badge") }
        return L10n.tr("ai_summary_all_clear_badge")
    }

    private var summaryTitle: String {
        if aiService.progress.running { return L10n.tr("ai_summary_scanning_title") }
        if aiService.summary.totalCount == 0 { return L10n.tr("ai_summary_empty_title") }
        if aiService.summary.sensitiveCount > 0 { return L10n.tr("ai_summary_sensitive_title_fmt", aiService.summary.sensitiveCount) }
        if aiService.summary.cleanupCount > 0 { return L10n.tr("ai_summary_cleanup_title_fmt", aiService.summary.cleanupCount) }
        if aiService.summary.hasUnscanned { return L10n.tr("ai_summary_unscanned_title") }
        return L10n.tr("ai_summary_all_clear_title")
    }

    private var summaryDescription: String {
        if aiService.summary.totalCount == 0 { return L10n.tr("ai_summary_empty_desc") }
        return L10n.tr("ai_summary_live_desc")
    }

    private var primarySummaryAction: String {
        aiService.summary.hasUnscanned ? L10n.tr("ai_action_start_scan") : L10n.tr("ai_action_rescan")
    }

    private var summaryAccent: Color {
        if aiService.summary.sensitiveCount > 0 { return LNColor.amberWarning }
        if aiService.summary.cleanupCount > 0 { return LNColor.cleanupOrange }
        if !aiService.summary.hasUnscanned && aiService.summary.totalCount > 0 { return LNColor.success }
        return LNColor.brandBlue
    }

    private var summaryIcon: String {
        if aiService.summary.sensitiveCount > 0 { return "eye.slash" }
        if aiService.summary.cleanupCount > 0 { return "rectangle.on.rectangle" }
        if !aiService.summary.hasUnscanned && aiService.summary.totalCount > 0 { return "checkmark.shield" }
        return "sparkles"
    }

    private var aiToolRows: [AIToolRowModel] {
        [
            AIToolRowModel(
                title: L10n.tr("ai_feat_blur"),
                subtitle: L10n.tr("ai_tool_blur_desc"),
                status: aiService.summary.sensitiveCount > 0 ? "\(aiService.summary.sensitiveCount)" : L10n.tr("ai_tool_status_pick"),
                systemImage: "eye.slash",
                foreground: LNColor.brandBlue,
                background: LNColor.brandBlue.opacity(0.20),
                stroke: Color(hex: 0x244869),
                route: .aiSensitive,
                proFeature: .aiPrivacy,
                accessibilityIdentifier: "ai_tool_privacy_blur"
            ),
            AIToolRowModel(
                title: L10n.tr("ai_feat_classify"),
                subtitle: L10n.tr("ai_tool_classify_desc"),
                status: aiService.summary.categoryCounts.isEmpty ? L10n.tr("ai_tool_status_scan") : L10n.tr("ai_tool_status_live"),
                systemImage: "square.stack.3d.up",
                foreground: Color(hex: 0x7DBBFF),
                background: Color(hex: 0x18283D),
                stroke: LNColor.stroke,
                route: .aiClassify,
                proFeature: .aiClassify,
                accessibilityIdentifier: "ai_tool_classify"
            ),
            AIToolRowModel(
                title: L10n.tr("ai_tool_dedup_title"),
                subtitle: L10n.tr("ai_tool_dedup_desc"),
                status: aiService.summary.cleanupCount > 0 ? "\(aiService.summary.cleanupCount)" : L10n.tr("ai_tool_status_live"),
                systemImage: "rectangle.on.rectangle",
                foreground: LNColor.success,
                background: LNColor.success.opacity(0.20),
                stroke: Color(hex: 0x214536),
                route: .aiCleanup,
                proFeature: .aiCleanup,
                accessibilityIdentifier: "ai_tool_deduplicate"
            ),
        ]
    }
}

private struct AIToolRowModel: Identifiable {
    var id: String { accessibilityIdentifier }
    let title: String
    let subtitle: String
    let status: String
    let systemImage: String
    let foreground: Color
    let background: Color
    let stroke: Color
    let route: AppRoute
    let proFeature: ProFeature
    let accessibilityIdentifier: String
}

struct AICleanupView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var aiService = VaultAIAnalysisService.shared
    @State private var showConfirm = false
    @State private var isCleaning = false

    private var cleanableRecords: [VaultMediaRecord] {
        aiService.records.filter { VaultAIAnalysisService.isCleanable($0) }
    }

    var body: some View {
        LNScreenScaffold(title: L10n.aiCleanupTitle, onBack: { dismiss() }) {
            AIActionHeaderCard(
                icon: "rectangle.on.rectangle",
                tint: LNColor.cleanupOrange,
                title: L10n.tr("ai_cleanup_detected_count", cleanableRecords.count),
                message: L10n.tr("ai_cleanup_live_desc"),
                primaryTitle: aiService.progress.running ? L10n.commonLoading : L10n.tr("ai_cleanup_scan_now"),
                primaryLoading: aiService.progress.running,
                secondaryTitle: cleanableRecords.isEmpty ? nil : L10n.tr("ai_cleanup_confirm_clean"),
                primaryAction: { Task { await aiService.scanVault() } },
                secondaryAction: { showConfirm = true }
            )

            if cleanableRecords.isEmpty {
                AIEmptyActionView(
                    systemImage: "checkmark.shield",
                    title: L10n.tr("ai_cleanup_empty"),
                    message: L10n.tr("ai_cleanup_scan_hint")
                )
            } else {
                AISectionHeader(title: L10n.tr("ai_cleanup_candidates"), value: "\(cleanableRecords.count)")
                LNMediaGrid(items: cleanableRecords.map(mediaItem)) { _ in }
            }
        }
        .task { aiService.refreshSummary() }
        .overlay {
            if showConfirm {
                LNDialog(
                    title: L10n.aiCleanupTitle,
                    message: L10n.tr("ai_cleanup_confirm_message", cleanableRecords.count),
                    confirmTitle: L10n.tr("ai_cleanup_confirm_clean"),
                    dismissTitle: L10n.commonCancel,
                    confirmVariant: .danger,
                    onConfirm: {
                        showConfirm = false
                        Task { await cleanupCandidates() }
                    },
                    onDismiss: { showConfirm = false }
                )
            }
        }
        .accessibilityIdentifier("ai_cleanup_view")
    }

    private func cleanupCandidates() async {
        guard !isCleaning else { return }
        isCleaning = true
        for item in cleanableRecords.map(mediaItem) {
            _ = await VaultStore.shared.moveToTrash(path: item.path)
        }
        aiService.refreshSummary()
        isCleaning = false
    }
}

struct AISensitiveReviewView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var aiService = VaultAIAnalysisService.shared

    private var sensitiveRecords: [VaultMediaRecord] {
        aiService.records.filter { VaultAIAnalysisService.isSensitive($0) }
    }

    var body: some View {
        LNScreenScaffold(title: L10n.tr("ai_sensitive_files_title"), onBack: { dismiss() }) {
            AISensitiveReviewHeaderCard(
                count: sensitiveRecords.count,
                loading: aiService.progress.running,
                onScan: { Task { await aiService.scanVault() } }
            )

            if sensitiveRecords.isEmpty {
                AIEmptyActionView(
                    systemImage: "checkmark.shield",
                    title: L10n.tr("ai_sensitive_empty_title"),
                    message: L10n.tr("ai_sensitive_empty_desc")
                )
            } else {
                AISensitiveGridCard(records: sensitiveRecords) { item in
                    router.pushAI(.privacyRedact(path: item.path))
                }
            }
        }
        .task { aiService.refreshSummary() }
        .accessibilityIdentifier("ai_sensitive_review_view")
    }
}

private struct AISensitiveReviewHeaderCard: View {
    let count: Int
    let loading: Bool
    let onScan: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack(spacing: 12) {
                Image(systemName: "eye.slash")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(LNColor.brandBlue)
                    .frame(width: 44, height: 44)
                    .background(LNColor.brandBlue.opacity(0.18))
                    .clipShape(RoundedRectangle(cornerRadius: 13))

                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.tr("ai_sensitive_count_may_contain_fmt", count))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color(hex: 0xB7D7FF))
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)
                    Text(L10n.tr("ai_sensitive_review_blur_desc"))
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(LNColor.subtitle)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Button(action: onScan) {
                ZStack {
                    if loading {
                        ProgressView()
                            .tint(.white)
                    } else {
                        Text(L10n.tr("ai_cleanup_scan_now"))
                            .font(.system(size: 13, weight: .bold))
                    }
                }
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(loading ? LNColor.buttonDisabledBg : LNColor.brandBlue)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            .disabled(loading)
            .accessibilityIdentifier("ai_sensitive_scan_now")
        }
        .padding(LNSpacing.cardPadding)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
        .overlay(RoundedRectangle(cornerRadius: LNRadius.homeCard).stroke(Color(hex: 0x244869), lineWidth: 1))
        .accessibilityIdentifier("ai_sensitive_header")
    }
}

private struct AISensitiveGridCard: View {
    let records: [VaultMediaRecord]
    let onSelect: (LNMediaItem) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            AISectionHeader(title: L10n.tr("ai_sensitive_candidates"), value: "\(records.count)")
            LNMediaGrid(items: records.map(mediaItem), onSelect: onSelect)
        }
        .padding(LNSpacing.cardPadding)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
        .overlay(RoundedRectangle(cornerRadius: LNRadius.homeCard).stroke(LNColor.stroke, lineWidth: 1))
        .accessibilityIdentifier("ai_sensitive_candidates_grid")
    }
}

struct AIClassifyView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var aiService = VaultAIAnalysisService.shared

    private var categories: [(String, Int)] {
        aiService.summary.categoryCounts
            .filter { $0.value > 0 }
            .sorted { lhs, rhs in
                if lhs.value == rhs.value { return localizedCategory(lhs.key) < localizedCategory(rhs.key) }
                return lhs.value > rhs.value
            }
    }

    var body: some View {
        LNScreenScaffold(title: L10n.aiClassifyTitle, onBack: { dismiss() }) {
            AIActionHeaderCard(
                icon: "square.stack.3d.up",
                tint: LNColor.brandBlue,
                title: L10n.tr("ai_classify_summary_fmt", categories.count),
                message: L10n.tr("ai_classify_live_desc"),
                primaryTitle: aiService.progress.running ? L10n.commonLoading : L10n.tr("ai_cleanup_scan_now"),
                primaryLoading: aiService.progress.running,
                secondaryTitle: nil,
                primaryAction: { Task { await aiService.scanVault() } },
                secondaryAction: {}
            )

            if categories.isEmpty {
                AIEmptyActionView(
                    systemImage: "square.stack.3d.up",
                    title: L10n.tr("ai_classify_empty_title"),
                    message: L10n.tr("ai_classify_empty_desc")
                )
            } else {
                ForEach(categories, id: \.0) { category, count in
                    LNSettingsRow(title: localizedCategory(category), subtitle: L10n.tr("ai_classify_items_fmt", count)) {
                        router.pushAI(.aiClassifyDetail(category: category))
                    }
                }
            }
        }
        .task { aiService.refreshSummary() }
        .accessibilityIdentifier("ai_classify_view")
    }
}

struct AIClassifyDetailView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var aiService = VaultAIAnalysisService.shared
    let category: String

    private var categoryRecords: [VaultMediaRecord] {
        aiService.records.filter { $0.ai.category == category }
    }

    var body: some View {
        VaultListScreenChrome(title: localizedCategory(category), onBack: { dismiss() }) { availableWidth in
            let cardWidth = max(0, availableWidth - LNSpacing.screenHorizontal * 2)

            if categoryRecords.isEmpty {
                VStack {
                    AIEmptyActionView(
                        systemImage: "tray",
                        title: L10n.tr("ai_classify_detail_empty"),
                        message: L10n.tr("ai_classify_empty_desc")
                    )
                }
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.top, 22)
                .padding(.bottom, 28)
            } else {
                VaultMediaGridCard(
                    items: categoryRecords.map(mediaItem),
                    width: cardWidth,
                    onSelect: open
                )
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.top, 22)
                .padding(.bottom, 28)
            }
        }
        .task { aiService.refreshSummary() }
        .accessibilityIdentifier("ai_classify_detail_view")
    }

    private func open(_ item: LNMediaItem) {
        if item.isVideo {
            router.pushAI(.videoPlayer(path: item.path, isTrash: false))
        } else {
            router.pushAI(.photoViewer(path: item.path, isTrash: false, source: .recent))
        }
    }
}

struct PrivacyRedactView: View {
    enum RedactMode {
        case automatic
        case manual
    }

    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var aiService = VaultAIAnalysisService.shared
    @ObservedObject private var redactionService = PrivacyRedactionService.shared
    @State private var selectedStyle: PrivacyRedactionStyle = .mosaic
    @State private var redactMode: RedactMode = .automatic
    @State private var autoRegions: [PrivacyRedactionRegion] = []
    @State private var manualRegions: [PrivacyRedactionRegion] = []
    @State private var draftRegion: PrivacyRedactionRegion?
    @State private var selectedManualRegionID: UUID?
    @State private var toastMessage: String?
    @State private var isExportingSystem = false
    @State private var isPreparingShare = false
    @State private var shareURL: URL?
    @State private var showShareSheet = false
    @State private var redactedPreviewImage: UIImage?
    @State private var previewTask: Task<Void, Never>?
    @State private var previewRequestID = UUID()
    let path: String

    private var selectableRecords: [VaultMediaRecord] {
        let sensitive = aiService.records.filter { VaultAIAnalysisService.isSensitive($0) }
        return sensitive.isEmpty ? aiService.records : sensitive
    }

    private var selectedRecord: VaultMediaRecord? {
        aiService.records.first { mediaItem($0).path == path }
    }

    private var activeRecord: VaultMediaRecord? {
        selectedRecord ?? selectableRecords.first
    }

    private var activePath: String {
        if !path.isEmpty { return path }
        guard let activeRecord else { return "" }
        return mediaItem(activeRecord).path
    }

    private var activeIsVideo: Bool {
        selectedRecord?.isVideo ?? activeRecord?.isVideo ?? false
    }

    private var displayedRegions: [PrivacyRedactionRegion] {
        autoRegions + manualRegions + (draftRegion.map { [$0] } ?? [])
    }

    private var saveRegions: [PrivacyRedactionRegion] {
        autoRegions + manualRegions
    }

    private var statusText: String {
        if redactionService.isDetecting {
            return L10n.tr("privacy_redact_detecting")
        }
        return L10n.tr("privacy_redact_detection_status_fmt", autoRegions.count, manualRegions.count)
    }

    private var manualCountText: String {
        L10n.tr("privacy_redact_manual_count_fmt", manualRegions.count)
    }

    private var modeButtonTitle: String {
        redactMode == .manual ? L10n.tr("privacy_redact_manual_done") : L10n.tr("privacy_redact_enter_manual")
    }

    private var canEditRedaction: Bool {
        !redactionService.isDetecting && !activeIsVideo
    }

    private var canSelectStyle: Bool {
        canEditRedaction && !saveRegions.isEmpty
    }

    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                privacyRedactTopBar
                    .padding(.top, 8)

                PrivacyRedactCanvas(
                    path: activePath,
                    isVideo: activeIsVideo,
                    previewImage: redactedPreviewImage,
                    isDetecting: redactionService.isDetecting,
                    mode: redactMode,
                    regions: displayedRegions,
                    selectedManualRegionID: selectedManualRegionID,
                    onDraftChanged: updateDraftRegion,
                    onDraftCommitted: commitDraftRegion
                )
                .frame(height: min(400, max(360, proxy.size.height * 0.46)))
                .padding(.horizontal, 20)
                .padding(.top, 12)

                VStack(spacing: 8) {
                    privacyRedactStatusLine
                    if !manualRegions.isEmpty {
                        privacyRedactManualOps
                    }
                    PrivacyRedactStylePicker(selectedStyle: $selectedStyle, isEnabled: canSelectStyle)
                }
                .padding(12)
                .background(Color(hex: 0x0A1320))
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(LNColor.stroke, lineWidth: 1))
                .padding(.horizontal, 20)
                .padding(.top, 12)

                privacyRedactBottomActions
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .top)
        }
        .lnScreenBackground()
        .ignoresSafeArea(.container, edges: .bottom)
        .overlay(alignment: .top) {
            if let toastMessage {
                PrivacyRedactToast(message: toastMessage)
                    .padding(.top, 82)
                    .padding(.horizontal, 24)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: toastMessage)
        .task(id: activePath) {
            await aiService.refreshSummary()
            await reloadDetectedRegions(for: activePath)
        }
        .onChange(of: selectedStyle) { applySelectedStyleToRegions($0) }
        .onChange(of: activePath) { _ in scheduleRedactionPreview() }
        .onChange(of: activeIsVideo) { _ in scheduleRedactionPreview() }
        .onChange(of: displayedRegions) { _ in scheduleRedactionPreview() }
        .onChange(of: redactionService.isDetecting) { _ in scheduleRedactionPreview() }
        .onDisappear {
            previewTask?.cancel()
        }
        .sheet(isPresented: $showShareSheet) {
            if let shareURL {
                PrivacyRedactShareSheet(url: shareURL) {
                    PlaintextTempFileManager.shared.removeItem(shareURL)
                    self.shareURL = nil
                }
                .ignoresSafeArea()
            }
        }
        .onChange(of: showShareSheet) { isPresented in
            if !isPresented, let shareURL {
                PlaintextTempFileManager.shared.removeItem(shareURL)
                self.shareURL = nil
            }
        }
        .accessibilityIdentifier("privacy_redact_view")
    }

    private var privacyRedactTopBar: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 0) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(LNColor.title)
                        .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
                        .background(LNColor.navBarBg.opacity(0.8))
                        .clipShape(RoundedRectangle(cornerRadius: LNRadius.topBarButton))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.commonBack)
                .accessibilityIdentifier("privacy_redact_back")

                Text(L10n.privacyRedactTitle)
                    .font(LNTypography.titleMedium())
                    .foregroundStyle(LNColor.title)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)
                    .accessibilityAddTraits(.isHeader)

                Color.clear
                    .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
            }
            .padding(.horizontal, LNSpacing.screenHorizontal)

            Text(L10n.tr("privacy_redact_subtitle"))
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle)
                .lineLimit(1)
                .minimumScaleFactor(0.82)
                .padding(.horizontal, LNSpacing.screenHorizontal)
        }
    }

    private var privacyRedactStatusLine: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                Circle()
                    .fill(LNColor.brandBlue)
                    .frame(width: 7, height: 7)
                Text(statusText)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color(hex: 0xB7C6DD))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .frame(height: 40)
            .background(LNColor.sectionBg)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(LNColor.stroke, lineWidth: 1))

            Button {
                draftRegion = nil
                if redactMode == .manual {
                    redactMode = .automatic
                    selectedManualRegionID = nil
                } else {
                    redactMode = .manual
                    selectedManualRegionID = nil
                }
            } label: {
                Text(modeButtonTitle)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(redactMode == .manual ? Color(hex: 0xD9FFF0) : Color(hex: 0xDCEBFF))
                    .frame(width: 88, height: 40)
                    .background(redactMode == .manual ? Color(hex: 0x0F5135) : Color(hex: 0x14233A))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(redactMode == .manual ? LNColor.success : LNColor.brandBlue, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(!canEditRedaction)
            .opacity(canEditRedaction ? 1 : 0.55)
            .accessibilityIdentifier("privacy_redact_manual_done")
        }
    }

    private var privacyRedactManualOps: some View {
        HStack(spacing: 8) {
            Text(manualCountText)
                .font(.system(size: 11, weight: .regular))
                .foregroundStyle(LNColor.subtitle)
                .frame(maxWidth: .infinity, alignment: .leading)
            PrivacyRedactSmallChip(title: L10n.tr("privacy_redact_undo"), identifier: "privacy_redact_undo", isEnabled: !manualRegions.isEmpty) {
                undoLastManualRegion()
            }
            PrivacyRedactSmallChip(title: L10n.tr("privacy_redact_clear"), identifier: "privacy_redact_clear", isEnabled: !manualRegions.isEmpty) {
                manualRegions.removeAll()
                selectedManualRegionID = nil
                draftRegion = nil
            }
        }
        .frame(height: 36)
    }

    private var privacyRedactBottomActions: some View {
        VStack(spacing: 8) {
            Button {
                saveRedactedToVault()
            } label: {
                HStack(spacing: 8) {
                    if redactionService.isSaving {
                        ProgressView()
                            .tint(.white)
                    } else {
                        Image(systemName: "checkmark.shield")
                            .font(.system(size: 18, weight: .semibold))
                    }
                    Text(L10n.tr("privacy_redact_save_vault"))
                        .font(.system(size: 16, weight: .bold))
                }
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(activeIsVideo || saveRegions.isEmpty ? LNColor.buttonDisabledBg : LNColor.brandBlue)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            .disabled(redactionService.isSaving || activeIsVideo || saveRegions.isEmpty)
            .accessibilityIdentifier("privacy_redact_save_vault")

            HStack(spacing: 10) {
                PrivacyRedactSecondaryAction(
                    title: L10n.tr("privacy_redact_export_system"),
                    systemImage: "square.and.arrow.down",
                    identifier: "privacy_redact_export_system",
                    isBusy: isExportingSystem,
                    isEnabled: !saveRegions.isEmpty && !activeIsVideo && !isExportingSystem
                ) {
                    exportRedactedToSystemPhotos()
                }
                PrivacyRedactSecondaryAction(
                    title: L10n.tr("privacy_redact_share_redacted"),
                    systemImage: "square.and.arrow.up",
                    identifier: "privacy_redact_share_redacted",
                    isBusy: isPreparingShare,
                    isEnabled: !saveRegions.isEmpty && !activeIsVideo && !isPreparingShare
                ) {
                    prepareRedactedShare()
                }
            }
            .frame(height: 48)
        }
    }

    @MainActor
    private func reloadDetectedRegions(for path: String) async {
        selectedStyle = .mosaic
        redactMode = .automatic
        manualRegions.removeAll()
        draftRegion = nil
        selectedManualRegionID = nil
        toastMessage = nil
        let detectedRegions = await redactionService.detectRegions(path: path)
        autoRegions = detectedRegions
        scheduleRedactionPreview()
        if !path.isEmpty, detectedRegions.isEmpty {
            showToast(L10n.tr("privacy_redact_no_sensitive_toast"))
        }
    }

    private func updateDraftRegion(_ normalizedRect: CGRect?) {
        guard redactMode == .manual, !activeIsVideo else { return }
        guard let normalizedRect else {
            draftRegion = nil
            return
        }
        draftRegion = PrivacyRedactionRegion(
            normalizedRect: normalizedRect,
            style: selectedStyle,
            source: .manual
        )
    }

    private func commitDraftRegion(_ normalizedRect: CGRect?) {
        guard redactMode == .manual, !activeIsVideo else {
            draftRegion = nil
            return
        }
        guard let normalizedRect, normalizedRect.width >= 0.04, normalizedRect.height >= 0.04 else {
            draftRegion = nil
            return
        }
        let region = PrivacyRedactionRegion(
            normalizedRect: normalizedRect,
            style: selectedStyle,
            source: .manual
        )
        manualRegions.append(region)
        selectedManualRegionID = region.id
        draftRegion = nil
    }

    private func applySelectedStyleToRegions(_ style: PrivacyRedactionStyle) {
        autoRegions = autoRegions.map {
            PrivacyRedactionRegion(id: $0.id, normalizedRect: $0.normalizedRect, style: style, source: $0.source)
        }
        manualRegions = manualRegions.map {
            PrivacyRedactionRegion(id: $0.id, normalizedRect: $0.normalizedRect, style: style, source: $0.source)
        }
        if let draftRegion {
            self.draftRegion = PrivacyRedactionRegion(
                id: draftRegion.id,
                normalizedRect: draftRegion.normalizedRect,
                style: style,
                source: draftRegion.source
            )
        }
    }

    private func scheduleRedactionPreview() {
        let requestID = UUID()
        let path = activePath
        let regions = displayedRegions
        previewRequestID = requestID
        previewTask?.cancel()

        guard !path.isEmpty, !activeIsVideo, !regions.isEmpty, !redactionService.isDetecting else {
            redactedPreviewImage = nil
            return
        }

        previewTask = Task {
            let image = await redactionService.renderPreviewImage(path: path, regions: regions)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard previewRequestID == requestID else { return }
                redactedPreviewImage = image
            }
        }
    }

    private func undoLastManualRegion() {
        guard !manualRegions.isEmpty else { return }
        let removed = manualRegions.removeLast()
        if selectedManualRegionID == removed.id {
            selectedManualRegionID = manualRegions.last?.id
        }
        draftRegion = nil
    }

    private func showToast(_ message: String) {
        toastMessage = message
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            if toastMessage == message {
                toastMessage = nil
            }
        }
    }

    private func showRedactionServiceMessage() {
        guard let message = redactionService.lastMessage else { return }
        showToast(message)
    }

    private func saveRedactedToVault() {
        guard !redactionService.isSaving else { return }
        Task {
            _ = await redactionService.redactAndImport(path: activePath, regions: saveRegions)
            await MainActor.run { showRedactionServiceMessage() }
        }
    }

    private func exportRedactedToSystemPhotos() {
        guard !isExportingSystem else { return }
        isExportingSystem = true
        Task {
            _ = await redactionService.exportToSystemPhotos(path: activePath, regions: saveRegions)
            await MainActor.run {
                isExportingSystem = false
                showRedactionServiceMessage()
            }
        }
    }

    private func prepareRedactedShare() {
        guard !isPreparingShare else { return }
        isPreparingShare = true
        Task {
            let url = await redactionService.makeRedactedShareURL(path: activePath, regions: saveRegions)
            await MainActor.run {
                isPreparingShare = false
                if let url {
                    shareURL = url
                    showShareSheet = true
                } else {
                    redactionService.updateMessage(L10n.tr("privacy_redact_share_failed"), isError: true)
                    showRedactionServiceMessage()
                }
            }
        }
    }

    private var redactionPicker: some View {
        VStack(alignment: .leading, spacing: 16) {
            AIActionHeaderCard(
                icon: "shield.lefthalf.filled",
                tint: LNColor.brandBlue,
                title: L10n.tr("privacy_redact_picker_title"),
                message: L10n.tr("privacy_redact_picker_desc"),
                primaryTitle: aiService.progress.running ? L10n.commonLoading : L10n.tr("ai_cleanup_scan_now"),
                primaryLoading: aiService.progress.running,
                secondaryTitle: nil,
                primaryAction: { Task { await aiService.scanVault() } },
                secondaryAction: {}
            )
            if selectableRecords.isEmpty {
                AIEmptyActionView(
                    systemImage: "photo.on.rectangle",
                    title: L10n.tr("privacy_redact_picker_empty_title"),
                    message: L10n.tr("privacy_redact_picker_empty_desc")
                )
            } else {
                AISectionHeader(title: L10n.tr("privacy_redact_picker_grid_title"), value: "\(selectableRecords.count)")
                LNMediaGrid(items: selectableRecords.map(mediaItem)) { item in
                    router.pushAI(.privacyRedact(path: item.path))
                }
            }
        }
    }

    private var redactionEditor: some View {
        VStack(alignment: .leading, spacing: 16) {
            ZStack(alignment: .bottomLeading) {
                VaultMediaThumbnailView(
                    encryptedPath: path,
                    isVideo: selectedRecord?.isVideo ?? false,
                    contentMode: .fit,
                    targetPixelSize: 1200
                )
                .frame(maxWidth: .infinity)
                .frame(height: 360)
                .background(LNColor.sectionBg)
                .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
                .overlay(RoundedRectangle(cornerRadius: LNRadius.homeCard).stroke(LNColor.stroke, lineWidth: 1))
                .overlay(redactionPreviewOverlay)

                Text(L10n.tr("privacy_redact_auto_hint"))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.title)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(LNColor.dialogBg.opacity(0.88))
                    .clipShape(RoundedRectangle(cornerRadius: 9))
                    .padding(12)
            }

            stylePicker

            if let message = redactionService.lastMessage {
                Text(message)
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(redactionService.lastIsError ? LNColor.error : LNColor.success)
                    .fixedSize(horizontal: false, vertical: true)
            }

            LNButton(
                title: L10n.tr("privacy_redact_save_vault"),
                variant: .primary,
                enabled: selectedRecord?.isVideo != true,
                loading: redactionService.isSaving
            ) {
                Task { _ = await redactionService.redactAndImport(path: path, style: selectedStyle) }
            }

            if selectedRecord?.isVideo == true {
                Text(L10n.tr("privacy_redact_video_hint"))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var stylePicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L10n.tr("privacy_redact_style_title"))
                .font(LNTypography.titleMedium())
                .foregroundStyle(LNColor.title)
            HStack(spacing: 8) {
                ForEach(PrivacyRedactionStyle.allCases) { style in
                    Button { selectedStyle = style } label: {
                        Text(styleTitle(style))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(selectedStyle == style ? Color(hex: 0xF3F4FF) : LNColor.navItemActive)
                            .frame(maxWidth: .infinity)
                            .frame(height: 36)
                            .background(selectedStyle == style ? Color(hex: 0x312E81) : LNColor.sectionBg)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(selectedStyle == style ? Color(hex: 0x818CF8) : LNColor.stroke, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(LNSpacing.cardPadding)
        .lnOutlinedCard(cornerRadius: 16)
    }

    @ViewBuilder
    private var redactionPreviewOverlay: some View {
        if selectedRecord?.isVideo != true {
            ZStack {
                switch selectedStyle {
                case .mosaic:
                    MosaicPreview()
                case .blur:
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(hex: 0xB7D7FF, alpha: 0.78))
                case .blackBar:
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color.black.opacity(0.86))
                case .whiteBar:
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color.white.opacity(0.92))
                case .ovalBlur:
                    Ellipse()
                        .fill(Color(hex: 0xB7D7FF, alpha: 0.78))
                case .emoji:
                    Image(systemName: "face.smiling")
                        .font(.system(size: 26, weight: .semibold))
                        .foregroundStyle(Color.white)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.black.opacity(0.25))
                }
            }
            .frame(width: 170, height: 42)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            .allowsHitTesting(false)
        }
    }
}

private struct PrivacyRedactCanvas: View {
    let path: String
    let isVideo: Bool
    let previewImage: UIImage?
    let isDetecting: Bool
    let mode: PrivacyRedactView.RedactMode
    let regions: [PrivacyRedactionRegion]
    let selectedManualRegionID: UUID?
    let onDraftChanged: (CGRect?) -> Void
    let onDraftCommitted: (CGRect?) -> Void

    var body: some View {
        GeometryReader { proxy in
            let imageRect = CGRect(x: 21, y: 18, width: max(1, proxy.size.width - 42), height: max(1, proxy.size.height - 36))
            let contentRect = fittedImageRect(in: imageRect, imageSize: previewImage?.size)
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 18)
                    .fill(Color(hex: 0x020409))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(LNColor.stroke, lineWidth: 1))

                if isDetecting {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.black)
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(hex: 0x344761), lineWidth: 1))
                        .frame(width: imageRect.width, height: imageRect.height)
                        .position(x: imageRect.midX, y: imageRect.midY)

                    VStack(spacing: 12) {
                        ProgressView()
                            .tint(LNColor.brandBlue)
                        Text(L10n.tr("privacy_redact_detecting"))
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Color(hex: 0xB7C6DD))
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 20)
                    .position(x: imageRect.midX, y: imageRect.midY)
                } else {
                    Group {
                        if let previewImage {
                            Image(uiImage: previewImage)
                                .resizable()
                                .scaledToFit()
                                .background(Color(hex: 0x07101C))
                        } else if path.isEmpty {
                            PrivacyRedactMockPhoto()
                        } else {
                            VaultMediaThumbnailView(
                                encryptedPath: path,
                                isVideo: isVideo,
                                contentMode: .fit,
                                targetPixelSize: 1200
                            )
                            .background(Color(hex: 0x07101C))
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(hex: 0x344761), lineWidth: 1))
                    .frame(width: imageRect.width, height: imageRect.height)
                    .position(x: imageRect.midX, y: imageRect.midY)

                    ForEach(regions) { region in
                        PrivacyRedactRegionView(
                            region: region,
                            isSelectedManual: region.id == selectedManualRegionID,
                            drawsEffect: previewImage == nil
                        )
                        .frame(
                            width: max(1, region.normalizedRect.width * contentRect.width),
                            height: max(1, region.normalizedRect.height * contentRect.height)
                        )
                        .position(
                            x: contentRect.minX + region.normalizedRect.midX * contentRect.width,
                            y: contentRect.minY + region.normalizedRect.midY * contentRect.height
                        )
                    }

                    Text(mode == .manual ? L10n.tr("privacy_redact_manual_hint") : L10n.tr("privacy_redact_auto_hint"))
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(LNColor.title)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color(hex: 0x101722, alpha: 0.80))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .padding(.leading, 72)
                        .padding(.top, 34)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 6)
                    .onChanged { value in
                        guard mode == .manual, !isVideo, !isDetecting else { return }
                        onDraftChanged(normalizedRect(from: value.startLocation, to: value.location, in: contentRect))
                    }
                    .onEnded { value in
                        guard mode == .manual, !isVideo, !isDetecting else { return }
                        onDraftCommitted(normalizedRect(from: value.startLocation, to: value.location, in: contentRect))
                    }
            )
        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .accessibilityIdentifier("privacy_redact_canvas")
    }

    private func normalizedRect(from start: CGPoint, to end: CGPoint, in imageRect: CGRect) -> CGRect? {
        let clampedStart = clamp(start, to: imageRect)
        let clampedEnd = clamp(end, to: imageRect)
        let rect = CGRect(
            x: min(clampedStart.x, clampedEnd.x),
            y: min(clampedStart.y, clampedEnd.y),
            width: abs(clampedStart.x - clampedEnd.x),
            height: abs(clampedStart.y - clampedEnd.y)
        )
        guard rect.width > 4, rect.height > 4 else { return nil }
        return CGRect(
            x: (rect.minX - imageRect.minX) / imageRect.width,
            y: (rect.minY - imageRect.minY) / imageRect.height,
            width: rect.width / imageRect.width,
            height: rect.height / imageRect.height
        )
    }

    private func clamp(_ point: CGPoint, to rect: CGRect) -> CGPoint {
        CGPoint(
            x: min(max(point.x, rect.minX), rect.maxX),
            y: min(max(point.y, rect.minY), rect.maxY)
        )
    }

    private func fittedImageRect(in container: CGRect, imageSize: CGSize?) -> CGRect {
        guard let imageSize, imageSize.width > 0, imageSize.height > 0 else {
            return container
        }
        let imageRatio = imageSize.width / imageSize.height
        let containerRatio = container.width / container.height
        if containerRatio > imageRatio {
            let height = container.height
            let width = height * imageRatio
            return CGRect(x: container.midX - width / 2, y: container.minY, width: width, height: height)
        } else {
            let width = container.width
            let height = width / imageRatio
            return CGRect(x: container.minX, y: container.midY - height / 2, width: width, height: height)
        }
    }
}

private struct PrivacyRedactMockPhoto: View {
    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let height = proxy.size.height
            ZStack(alignment: .topLeading) {
                LinearGradient(
                    colors: [Color(hex: 0x1A2F48), Color(hex: 0x30486B), Color(hex: 0x0E1726)],
                    startPoint: .topTrailing,
                    endPoint: .bottomLeading
                )
                Rectangle()
                    .fill(Color(hex: 0x385D83))
                    .frame(height: height * 0.38)
                Circle()
                    .fill(Color(hex: 0xB7D7FF))
                    .frame(width: 34, height: 34)
                    .position(x: width * 0.82, y: height * 0.16)
                Image(systemName: "mountain.2.fill")
                    .resizable()
                    .scaledToFill()
                    .foregroundStyle(Color(hex: 0x13263B))
                    .frame(width: width * 0.92, height: height * 0.46)
                    .offset(x: width * 0.05, y: height * 0.29)
                    .clipped()
                Image(systemName: "mountain.2.fill")
                    .resizable()
                    .scaledToFill()
                    .foregroundStyle(Color(hex: 0x07101C))
                    .frame(width: width * 1.08, height: height * 0.50)
                    .offset(x: -width * 0.04, y: height * 0.40)
                    .clipped()
                Rectangle()
                    .fill(Color(hex: 0x07101C))
                    .frame(height: height * 0.28)
                    .offset(y: height * 0.67)
            }
        }
    }
}

private struct PrivacyRedactRegionView: View {
    let region: PrivacyRedactionRegion
    let isSelectedManual: Bool
    let drawsEffect: Bool

    var body: some View {
        ZStack {
            if drawsEffect {
                redactionShape
            }
            if region.source == .manual {
                RoundedRectangle(cornerRadius: 10)
                    .stroke(
                        isSelectedManual ? Color(hex: 0xE8C547) : Color(hex: 0xFF6B8F),
                        style: StrokeStyle(lineWidth: isSelectedManual ? 2 : 1.5, dash: [7, 5])
                    )
            }
        }
        .allowsHitTesting(false)
    }

    @ViewBuilder
    private var redactionShape: some View {
        switch region.style {
        case .mosaic:
            MosaicPreview()
                .background(LNColor.brandBlue)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        case .blur:
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(hex: 0xB7D7FF, alpha: 0.78))
        case .blackBar:
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.black.opacity(0.92))
        case .whiteBar:
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.white.opacity(0.95))
        case .ovalBlur:
            Ellipse()
                .fill(Color(hex: 0xB7D7FF, alpha: 0.78))
        case .emoji:
            Image(systemName: "face.smiling")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black.opacity(0.25))
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}

private struct PrivacyRedactToast: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(LNColor.title)
            .lineLimit(2)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color(hex: 0x101722, alpha: 0.94))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: 0x2F4564), lineWidth: 1))
            .shadow(color: Color.black.opacity(0.25), radius: 12, y: 8)
            .accessibilityIdentifier("privacy_redact_toast")
    }
}

private struct PrivacyRedactSmallChip: View {
    let title: String
    let identifier: String
    var isEnabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(isEnabled ? Color(hex: 0xB7C6DD) : Color(hex: 0x66758A))
                .frame(width: 72, height: 36)
                .background(isEnabled ? LNColor.sectionBg : Color(hex: 0x0B1420))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(LNColor.stroke, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityIdentifier(identifier)
    }
}

private struct PrivacyRedactStylePicker: View {
    @Binding var selectedStyle: PrivacyRedactionStyle
    let isEnabled: Bool

    private let columns = [
        GridItem(.flexible(), spacing: 7),
        GridItem(.flexible(), spacing: 7),
        GridItem(.flexible(), spacing: 7),
    ]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 8) {
            ForEach(PrivacyRedactionStyle.allCases) { style in
                Button { selectedStyle = style } label: {
                    Text(styleTitle(style))
                        .font(.system(size: 12, weight: selectedStyle == style ? .bold : .semibold))
                        .foregroundStyle(styleTextColor(style))
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .frame(maxWidth: .infinity)
                        .frame(height: 38)
                        .background(styleBackground(style))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(styleStroke(style), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .disabled(!isEnabled)
                .accessibilityIdentifier("privacy_redact_style_\(style.rawValue)")
            }
        }
        .frame(height: 84)
    }

    private func styleTextColor(_ style: PrivacyRedactionStyle) -> Color {
        guard isEnabled else { return Color(hex: 0x66758A) }
        return selectedStyle == style ? Color(hex: 0xF3F4FF) : Color(hex: 0xB7C6DD)
    }

    private func styleBackground(_ style: PrivacyRedactionStyle) -> Color {
        guard isEnabled else { return Color(hex: 0x0B1420) }
        return selectedStyle == style ? Color(hex: 0x312E81) : LNColor.sectionBg
    }

    private func styleStroke(_ style: PrivacyRedactionStyle) -> Color {
        guard isEnabled else { return Color(hex: 0x1A2638) }
        return selectedStyle == style ? Color(hex: 0x818CF8) : LNColor.stroke
    }
}

private struct PrivacyRedactSecondaryAction: View {
    let title: String
    let systemImage: String
    let identifier: String
    var isBusy = false
    var isEnabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if isBusy {
                    ProgressView()
                        .tint(Color(hex: 0xD8DAE0))
                } else {
                    Image(systemName: systemImage)
                        .font(.system(size: 15, weight: .semibold))
                }
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)
            }
            .foregroundStyle(isEnabled ? Color(hex: 0xD8DAE0) : Color(hex: 0x66758A))
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(isEnabled ? LNColor.sectionBg : Color(hex: 0x0B1420))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(LNColor.stroke, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityIdentifier(identifier)
    }
}

private struct PrivacyRedactShareSheet: UIViewControllerRepresentable {
    let url: URL
    let onComplete: () -> Void

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        controller.completionWithItemsHandler = { _, _, _, _ in
            DispatchQueue.main.async {
                onComplete()
            }
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private struct AIActionHeaderCard: View {
    let icon: String
    let tint: Color
    let title: String
    let message: String
    let primaryTitle: String
    var primaryLoading: Bool = false
    let secondaryTitle: String?
    let primaryAction: () -> Void
    let secondaryAction: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 44, height: 44)
                    .background(tint.opacity(0.18))
                    .clipShape(RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(LNTypography.titleLarge())
                        .foregroundStyle(LNColor.title)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(message)
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.subtitle)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            HStack(spacing: 10) {
                LNButton(title: primaryTitle, variant: .primary, loading: primaryLoading, action: primaryAction)
                if let secondaryTitle {
                    LNButton(title: secondaryTitle, variant: .danger, action: secondaryAction)
                }
            }
        }
        .lnCard()
    }
}

private struct AIEmptyActionView: View {
    let systemImage: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(LNColor.brandBlue)
                .frame(width: 62, height: 62)
                .background(LNColor.brandBlue.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 22))
            Text(title)
                .font(LNTypography.titleLarge())
                .foregroundStyle(LNColor.title)
            Text(message)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(22)
        .lnOutlinedCard(cornerRadius: LNRadius.homeCard)
    }
}

private struct AISectionHeader: View {
    let title: String
    let value: String

    var body: some View {
        HStack {
            Text(title)
                .font(LNTypography.titleMedium())
                .foregroundStyle(LNColor.title)
            Spacer()
            Text(value)
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle)
        }
    }
}

private struct MosaicPreview: View {
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 2), count: 8)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 2) {
            ForEach(0..<32, id: \.self) { index in
                Rectangle()
                    .fill(index.isMultiple(of: 2) ? Color.black.opacity(0.88) : LNColor.brandBlue.opacity(0.55))
                    .frame(height: 8)
            }
        }
        .padding(4)
        .background(Color.black.opacity(0.82))
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

private func mediaItem(_ record: VaultMediaRecord) -> LNMediaItem {
    let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    let modified = Date(timeIntervalSince1970: Double(record.modifiedAtMs) / 1000)
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    return LNMediaItem(
        id: record.id,
        path: record.absoluteURL(documentsDirectory: documents).path,
        fileName: record.originalFileName ?? record.fileName,
        isVideo: record.isVideo,
        sizeLabel: ByteCountFormatter.string(fromByteCount: record.encryptedSizeBytes, countStyle: .file),
        createdAt: formatter.string(from: modified)
    )
}

private func localizedCategory(_ category: String) -> String {
    let localized = L10n.tr(VaultAIAnalysisService.categoryLabelKey(category))
    return localized == VaultAIAnalysisService.categoryLabelKey(category) ? L10n.tr("ai_category_other") : localized
}

private func styleTitle(_ style: PrivacyRedactionStyle) -> String {
    switch style {
    case .mosaic: return L10n.tr("privacy_redact_style_mosaic")
    case .blur: return L10n.tr("privacy_redact_style_blur")
    case .blackBar: return L10n.tr("privacy_redact_style_black_bar")
    case .whiteBar: return L10n.tr("privacy_redact_style_white_bar")
    case .ovalBlur: return L10n.tr("privacy_redact_style_oval_blur")
    case .emoji: return L10n.tr("privacy_redact_style_emoji")
    }
}
