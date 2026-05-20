import SwiftUI

struct AIHomeView: View {
    @EnvironmentObject private var router: AppRouter
    @State private var suggestState: AISuggestState = .sensitive

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L10n.aiTitle)
                .font(LNTypography.displaySmall())
                .foregroundStyle(LNColor.title)
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.top, 8)

            VStack(alignment: .leading, spacing: 16) {
                aiSuggestCard
                HStack {
                    Text(L10n.aiFeaturesTitle)
                        .font(LNTypography.titleLarge())
                        .foregroundStyle(LNColor.title)
                    Spacer()
                    Text(L10n.tr("ai_features_count_fmt", aiFeatureCards.count))
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(LNColor.subtitle)
                }
                aiFeatureGrid
            }
            .padding(LNSpacing.screenHorizontal)
            .padding(.top, 16)
            .padding(.bottom, LNSpacing.homeNavBarHeight + 16)
        }
        .accessibilityIdentifier("ai_home_view")
    }

    private var aiSuggestCard: some View {
        HStack(alignment: .top, spacing: 12) {
            iconWell(systemName: "eye.slash", foreground: LNColor.amberWarning, background: LNColor.aiBlurIconBg, size: 44)
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Text(L10n.tr("ai_suggest_badge_sensitive"))
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(LNColor.amberWarning)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(LNColor.amberWarning.opacity(0.18))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    Text(L10n.tr("ai_suggest_sensitive_title"))
                        .font(LNTypography.titleMedium())
                        .foregroundStyle(LNColor.title)
                        .lineLimit(1)
                }
                Text(L10n.tr("ai_suggest_sensitive_desc"))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.title.opacity(0.78))
                    .lineLimit(3)
                HStack(spacing: 10) {
                    Button { openAIFeature(.privacyRedact(path: ""), router: router) } label: {
                        Text(L10n.tr("ai_suggest_redact_now"))
                            .font(LNTypography.labelMedium().weight(.bold))
                            .foregroundStyle(Color.black)
                            .frame(width: 116, height: 38)
                            .background(LNColor.amberWarning)
                            .clipShape(RoundedRectangle(cornerRadius: 11))
                    }
                    .buttonStyle(.plain)
                    Button { suggestState = .idle } label: {
                        Text(L10n.tr("ai_suggest_snooze"))
                            .font(LNTypography.labelMedium())
                            .foregroundStyle(LNColor.title.opacity(0.68))
                            .frame(width: 96, height: 38)
                            .background(Color.white.opacity(0.06))
                            .clipShape(RoundedRectangle(cornerRadius: 11))
                            .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color.white.opacity(0.18), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(LNSpacing.cardPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [LNColor.aiSensitiveStart, LNColor.aiGradEnd], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
        .overlay(RoundedRectangle(cornerRadius: LNRadius.homeCard).stroke(LNColor.stroke, lineWidth: 1))
    }

    private var aiFeatureGrid: some View {
        VStack(spacing: 12) {
            featureCard(aiFeatureCards[0], wide: true)
                .frame(maxWidth: .infinity)
                .frame(height: 154)
            HStack(spacing: 12) {
                featureCard(aiFeatureCards[1])
                featureCard(aiFeatureCards[2])
            }
            .frame(height: 142)
        }
    }

    private func featureCard(_ feature: AIFeatureCardModel, wide: Bool = false) -> some View {
        Button { openAIFeature(feature.route, router: router) } label: {
            VStack(spacing: 10) {
                iconWell(
                    systemName: feature.systemImage,
                    foreground: feature.foreground,
                    background: feature.background,
                    size: wide ? 56 : 52
                )
                Text(feature.title)
                    .font(LNTypography.titleMedium())
                    .foregroundStyle(LNColor.title)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .lnOutlinedCard(fill: LNColor.sectionBg)
        }
        .buttonStyle(.plain)
    }

    private func iconWell(systemName: String, foreground: Color, background: Color, size: CGFloat) -> some View {
        Image(systemName: systemName)
            .font(.system(size: size * 0.46, weight: .semibold))
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeAlbumCard))
    }

    private func openAIFeature(_ route: AppRoute, router: AppRouter) {
        let feature: ProFeature = switch route {
        case .aiCleanup: .aiCleanup
        case .aiSensitive, .recentList: .aiSensitive
        case .aiClassify, .aiClassifyDetail: .aiClassify
        case .privacyRedact: .aiPrivacy
        default: .aiClassify
        }
        guard router.guardProFeature(feature) else { return }
        QuotaManager.shared.incrementAiUsage()
        router.pushAI(route)
    }

    private var aiFeatureCards: [AIFeatureCardModel] {
        [
            AIFeatureCardModel(
                title: L10n.tr("ai_feat_blur"),
                systemImage: "eye.slash",
                foreground: LNColor.amberWarning,
                background: LNColor.aiBlurIconBg,
                route: .privacyRedact(path: "")
            ),
            AIFeatureCardModel(
                title: L10n.tr("ai_feat_classify"),
                systemImage: "square.stack.3d.up",
                foreground: LNColor.brandBlue,
                background: LNColor.aiClassifyIconBg,
                route: .aiClassify
            ),
            AIFeatureCardModel(
                title: L10n.tr("ai_feat_dedup"),
                systemImage: "rectangle.on.rectangle",
                foreground: LNColor.aiDedup,
                background: LNColor.aiDedupIconBg,
                route: .aiCleanup
            ),
        ]
    }
}

private struct AIFeatureCardModel {
    let title: String
    let systemImage: String
    let foreground: Color
    let background: Color
    let route: AppRoute
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
