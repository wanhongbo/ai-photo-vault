import SwiftUI

struct AIHomeView: View {
    @EnvironmentObject private var router: AppRouter

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
        .accessibilityIdentifier("ai_home_view")
    }

    private var aiScanSummaryCard: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack(spacing: 12) {
                iconWell(systemName: "sparkles", foreground: LNColor.brandBlue, background: LNColor.brandBlue.opacity(0.20), size: 44)
                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.tr("ai_summary_label"))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(LNColor.navItemActive)
                    Text(L10n.tr("ai_summary_title"))
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(LNColor.title)
                        .lineLimit(1)
                }
            }
            Text(L10n.tr("ai_summary_desc"))
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(LNColor.subtitle)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 12) {
                Button { openAIFeature(.aiSensitive, proFeature: .aiSensitive, router: router) } label: {
                    Text(L10n.tr("ai_summary_review_now"))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .background(LNColor.brandBlue)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                Button {} label: {
                    Text(L10n.tr("ai_summary_later"))
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
        .padding(LNSpacing.cardPadding)
        .frame(height: 166)
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
            .frame(maxWidth: .infinity, maxHeight: .infinity)
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

    private var aiToolRows: [AIToolRowModel] {
        [
            AIToolRowModel(
                title: L10n.tr("ai_feat_blur"),
                subtitle: L10n.tr("ai_tool_blur_desc"),
                status: L10n.tr("ai_tool_status_open"),
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
                status: L10n.tr("ai_tool_status_ready"),
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
                status: L10n.tr("ai_tool_status_ready"),
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
    @State private var showConfirm = false

    var body: some View {
        LNScreenScaffold(title: L10n.aiCleanupTitle, onBack: { dismiss() }) {
            LNEmptyStateCard(
                title: L10n.aiCleanupTitle,
                message: L10n.tr("placeholder_feature"),
                actionTitle: L10n.tr("ai_cleanup_confirm_clean")
            ) { showConfirm = true }
        }
        .overlay {
            if showConfirm {
                LNDialog(
                    title: L10n.aiCleanupTitle,
                    message: L10n.tr("ai_cleanup_confirm_message", 5),
                    confirmTitle: L10n.tr("ai_cleanup_confirm_clean"),
                    dismissTitle: L10n.commonCancel,
                    confirmVariant: .danger,
                    onConfirm: { showConfirm = false },
                    onDismiss: { showConfirm = false }
                )
            }
        }
    }
}

struct AISensitiveReviewView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        LNScreenScaffold(title: L10n.aiSensitiveTitle, onBack: { dismiss() }) {
            LNMediaGrid(items: MockData.mediaItems.prefix(6).map { $0 }) { _ in }
        }
    }
}

struct AIClassifyView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        LNScreenScaffold(title: L10n.aiClassifyTitle, onBack: { dismiss() }) {
            ForEach(MockData.classifyCategories, id: \.self) { category in
                LNSettingsRow(title: category, subtitle: "12 items") {
                    router.pushAI(.aiClassifyDetail(category: category))
                }
            }
        }
    }
}

struct AIClassifyDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let category: String

    var body: some View {
        LNScreenScaffold(title: category, onBack: { dismiss() }) {
            LNMediaGrid(items: MockData.mediaItems) { _ in }
        }
    }
}

struct PrivacyRedactView: View {
    @Environment(\.dismiss) private var dismiss
    let path: String

    var body: some View {
        LNScreenScaffold(title: L10n.privacyRedactTitle, onBack: { dismiss() }) {
            RoundedRectangle(cornerRadius: LNRadius.homeCard)
                .fill(LNColor.sectionBg)
                .frame(height: 360)
                .overlay(
                    VStack {
                        Image(systemName: "rectangle.and.pencil.and.ellipsis")
                            .font(.largeTitle)
                            .foregroundStyle(LNColor.amberWarning)
                        Text(path)
                            .font(LNTypography.labelMedium())
                            .foregroundStyle(LNColor.subtitle)
                            .lineLimit(1)
                    }
                )
            LNButton(title: L10n.commonConfirm, variant: .primary) {}
        }
    }
}
