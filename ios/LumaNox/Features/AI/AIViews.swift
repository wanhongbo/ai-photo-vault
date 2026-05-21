import SwiftUI

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
                    aiStatsRow
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

    private var aiStatsRow: some View {
        HStack(spacing: 10) {
            AIStatPill(value: "\(aiService.summary.scannedCount)/\(aiService.summary.totalCount)", label: L10n.tr("ai_stat_scanned"), color: LNColor.brandBlue)
            AIStatPill(value: "\(aiService.summary.sensitiveCount)", label: L10n.tr("ai_stat_sensitive"), color: LNColor.amberWarning)
            AIStatPill(value: "\(aiService.summary.cleanupCount)", label: L10n.tr("ai_stat_cleanup"), color: LNColor.cleanupOrange)
        }
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
        guard router.guardProFeature(proFeature) else { return }
        QuotaManager.shared.incrementAiUsage()
        router.pushAI(route)
    }

    private func startScan() {
        guard router.guardProFeature(.aiClassify) else { return }
        QuotaManager.shared.incrementAiUsage()
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
                route: .privacyRedact(path: ""),
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
        LNScreenScaffold(title: L10n.aiSensitiveTitle, onBack: { dismiss() }) {
            AIActionHeaderCard(
                icon: "eye.slash",
                tint: LNColor.amberWarning,
                title: L10n.tr("ai_sensitive_count_fmt", sensitiveRecords.count),
                message: L10n.tr("ai_sensitive_live_desc"),
                primaryTitle: aiService.progress.running ? L10n.commonLoading : L10n.tr("ai_cleanup_scan_now"),
                primaryLoading: aiService.progress.running,
                secondaryTitle: nil,
                primaryAction: { Task { await aiService.scanVault() } },
                secondaryAction: {}
            )

            if sensitiveRecords.isEmpty {
                AIEmptyActionView(
                    systemImage: "checkmark.shield",
                    title: L10n.tr("ai_sensitive_empty_title"),
                    message: L10n.tr("ai_sensitive_empty_desc")
                )
            } else {
                AISectionHeader(title: L10n.tr("ai_sensitive_candidates"), value: "\(sensitiveRecords.count)")
                LNMediaGrid(items: sensitiveRecords.map(mediaItem)) { item in
                    router.pushAI(.privacyRedact(path: item.path))
                }
            }
        }
        .task { aiService.refreshSummary() }
        .accessibilityIdentifier("ai_sensitive_review_view")
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
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var aiService = VaultAIAnalysisService.shared
    let category: String

    private var categoryRecords: [VaultMediaRecord] {
        aiService.records.filter { $0.ai.category == category }
    }

    var body: some View {
        LNScreenScaffold(title: localizedCategory(category), onBack: { dismiss() }) {
            if categoryRecords.isEmpty {
                AIEmptyActionView(
                    systemImage: "tray",
                    title: L10n.tr("ai_classify_detail_empty"),
                    message: L10n.tr("ai_classify_empty_desc")
                )
            } else {
                LNMediaGrid(items: categoryRecords.map(mediaItem)) { _ in }
            }
        }
        .task { aiService.refreshSummary() }
        .accessibilityIdentifier("ai_classify_detail_view")
    }
}

struct PrivacyRedactView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var aiService = VaultAIAnalysisService.shared
    @ObservedObject private var redactionService = PrivacyRedactionService.shared
    @State private var selectedStyle: PrivacyRedactionStyle = .mosaic
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

    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                privacyRedactTopBar
                    .padding(.horizontal, 16)
                    .padding(.top, 8)

                PrivacyRedactCanvas(
                    path: activePath,
                    isVideo: activeIsVideo,
                    selectedStyle: selectedStyle
                )
                .frame(height: min(400, max(360, proxy.size.height * 0.46)))
                .padding(.horizontal, 20)
                .padding(.top, 12)

                VStack(spacing: 8) {
                    privacyRedactStatusLine
                    privacyRedactManualOps
                    PrivacyRedactStylePicker(selectedStyle: $selectedStyle)
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
        .task { aiService.refreshSummary() }
        .accessibilityIdentifier("privacy_redact_view")
    }

    private var privacyRedactTopBar: some View {
        HStack(spacing: 12) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(LNColor.title)
                    .frame(width: 44, height: 44)
                    .background(LNColor.navBarBg)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(LNColor.stroke, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L10n.commonBack)
            .accessibilityIdentifier("privacy_redact_back")

            VStack(alignment: .leading, spacing: 3) {
                Text(L10n.privacyRedactTitle)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(LNColor.title)
                    .accessibilityAddTraits(.isHeader)
                Text(L10n.tr("privacy_redact_subtitle"))
                    .font(.system(size: 12, weight: .regular))
                    .foregroundStyle(LNColor.subtitle)
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)
            }
            Spacer(minLength: 0)
        }
        .frame(height: 52)
    }

    private var privacyRedactStatusLine: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                Circle()
                    .fill(LNColor.brandBlue)
                    .frame(width: 7, height: 7)
                Text(L10n.tr("privacy_redact_detection_status"))
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

            Button {} label: {
                Text(L10n.tr("privacy_redact_manual_done"))
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.white)
                    .frame(width: 88, height: 40)
                    .background(Color(hex: 0x1F4A9E))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(LNColor.brandBlue, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("privacy_redact_manual_done")
        }
    }

    private var privacyRedactManualOps: some View {
        HStack(spacing: 8) {
            Text(L10n.tr("privacy_redact_manual_count"))
                .font(.system(size: 11, weight: .regular))
                .foregroundStyle(LNColor.subtitle)
                .frame(maxWidth: .infinity, alignment: .leading)
            PrivacyRedactSmallChip(title: L10n.tr("privacy_redact_undo"), identifier: "privacy_redact_undo")
            PrivacyRedactSmallChip(title: L10n.tr("privacy_redact_clear"), identifier: "privacy_redact_clear")
        }
        .frame(height: 36)
    }

    private var privacyRedactBottomActions: some View {
        VStack(spacing: 8) {
            Button {
                Task { _ = await redactionService.redactAndImport(path: activePath, style: selectedStyle) }
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
                .background(activeIsVideo ? LNColor.buttonDisabledBg : LNColor.brandBlue)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            .disabled(redactionService.isSaving || activeIsVideo)
            .accessibilityIdentifier("privacy_redact_save_vault")

            HStack(spacing: 10) {
                PrivacyRedactSecondaryAction(
                    title: L10n.tr("privacy_redact_export_system"),
                    systemImage: "square.and.arrow.down",
                    identifier: "privacy_redact_export_system"
                ) {
                    redactionService.updateMessage(L10n.tr("privacy_redact_export_pending"), isError: false)
                }
                PrivacyRedactSecondaryAction(
                    title: L10n.tr("privacy_redact_share_redacted"),
                    systemImage: "square.and.arrow.up",
                    identifier: "privacy_redact_share_redacted"
                ) {
                    redactionService.updateMessage(L10n.tr("privacy_redact_share_pending"), isError: false)
                }
            }
            .frame(height: 48)
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
                            .foregroundStyle(selectedStyle == style ? Color.white : LNColor.navItemActive)
                            .frame(maxWidth: .infinity)
                            .frame(height: 36)
                            .background(selectedStyle == style ? LNColor.brandBlue : LNColor.sectionBg)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(selectedStyle == style ? LNColor.brandBlue : LNColor.stroke, lineWidth: 1))
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
    let selectedStyle: PrivacyRedactionStyle

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 18)
                .fill(Color(hex: 0x020409))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(LNColor.stroke, lineWidth: 1))

            Group {
                if path.isEmpty {
                    PrivacyRedactMockPhoto(selectedStyle: selectedStyle)
                } else {
                    VaultMediaThumbnailView(
                        encryptedPath: path,
                        isVideo: isVideo,
                        contentMode: .fit,
                        targetPixelSize: 1200
                    )
                    .background(Color(hex: 0x07101C))
                    .overlay(PrivacyRedactRegionOverlay(selectedStyle: selectedStyle))
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(hex: 0x344761), lineWidth: 1))
            .padding(.horizontal, 21)
            .padding(.vertical, 18)

            Text(L10n.tr("privacy_redact_drag_hint"))
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(LNColor.title)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color(hex: 0x101722, alpha: 0.80))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .padding(.leading, 72)
                .padding(.top, 34)
        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .accessibilityIdentifier("privacy_redact_canvas")
    }
}

private struct PrivacyRedactMockPhoto: View {
    let selectedStyle: PrivacyRedactionStyle

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
                PrivacyRedactRegionOverlay(selectedStyle: selectedStyle)
            }
        }
    }
}

private struct PrivacyRedactRegionOverlay: View {
    let selectedStyle: PrivacyRedactionStyle

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let height = proxy.size.height
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 9)
                    .stroke(Color(hex: 0xFF6B8F), style: StrokeStyle(lineWidth: 2, dash: [8, 6]))
                    .frame(width: width * 0.27, height: height * 0.15)
                    .offset(x: width * 0.06, y: height * 0.06)

                redactionShape
                    .frame(width: width * 0.26, height: height * 0.17)
                    .offset(x: width * 0.57, y: height * 0.13)

                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.black.opacity(0.92))
                    .frame(width: width * 0.66, height: 26)
                    .offset(x: width * 0.15, y: height * 0.77)

                RoundedRectangle(cornerRadius: 5)
                    .fill(Color.white.opacity(0.95))
                    .frame(width: width * 0.52, height: 22)
                    .offset(x: width * 0.17, y: height * 0.86)
            }
        }
        .allowsHitTesting(false)
    }

    @ViewBuilder
    private var redactionShape: some View {
        switch selectedStyle {
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

private struct PrivacyRedactSmallChip: View {
    let title: String
    let identifier: String

    var body: some View {
        Button {} label: {
            Text(title)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Color(hex: 0xB7C6DD))
                .frame(width: 72, height: 36)
                .background(LNColor.sectionBg)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(LNColor.stroke, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(identifier)
    }
}

private struct PrivacyRedactStylePicker: View {
    @Binding var selectedStyle: PrivacyRedactionStyle

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
                        .foregroundStyle(selectedStyle == style ? Color.white : Color(hex: 0xB7C6DD))
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .frame(maxWidth: .infinity)
                        .frame(height: 38)
                        .background(selectedStyle == style ? LNColor.brandBlue : LNColor.sectionBg)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(selectedStyle == style ? LNColor.brandBlue : LNColor.stroke, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("privacy_redact_style_\(style.rawValue)")
            }
        }
        .frame(height: 84)
    }
}

private struct PrivacyRedactSecondaryAction: View {
    let title: String
    let systemImage: String
    let identifier: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(.system(size: 15, weight: .semibold))
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)
            }
            .foregroundStyle(Color(hex: 0xD8DAE0))
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(LNColor.sectionBg)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(LNColor.stroke, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(identifier)
    }
}

private struct AIStatPill: View {
    let value: String
    let label: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(color)
            Text(label)
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(LNColor.stroke, lineWidth: 1))
    }
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
