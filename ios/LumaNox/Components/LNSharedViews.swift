import SwiftUI

struct LNMediaGrid: View {
    let items: [LNMediaItem]
    let onSelect: (LNMediaItem) -> Void

    private let columns = [
        GridItem(.flexible(), spacing: LNSpacing.gridGap),
        GridItem(.flexible(), spacing: LNSpacing.gridGap),
        GridItem(.flexible(), spacing: LNSpacing.gridGap),
    ]

    var body: some View {
        LazyVGrid(columns: columns, spacing: LNSpacing.gridGap) {
            ForEach(items) { item in
                Button { onSelect(item) } label: {
                    VaultMediaThumbnailView(
                        encryptedPath: item.path,
                        isVideo: item.isVideo,
                        contentMode: .fill,
                        targetPixelSize: 360
                    )
                    .aspectRatio(1, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeThumb))
                    .overlay(
                        RoundedRectangle(cornerRadius: LNRadius.homeThumb)
                            .stroke(LNColor.stroke, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct LNEmptyStateCard: View {
    let title: String
    let message: String
    let actionTitle: String
    let action: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "lock.shield")
                .font(.system(size: 36))
                .foregroundStyle(LNColor.brandBlue)
                .padding(20)
                .background(LNColor.brandBlue.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 28))
            Text(title).font(LNTypography.headlineMedium()).foregroundStyle(LNColor.title)
            Text(message).font(LNTypography.bodyMedium()).foregroundStyle(LNColor.subtitle).multilineTextAlignment(.center)
            LNButton(title: actionTitle, variant: .primary, action: action)
        }
        .lnCard()
    }
}

struct LNProgressCard: View {
    let title: String
    let progress: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(LNTypography.titleLarge()).foregroundStyle(LNColor.title)
            ProgressView(value: progress)
                .tint(LNColor.brandBlue)
            Text("\(Int(progress * 100))%")
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle)
        }
        .lnCard()
    }
}

struct LNAISuggestCard: View {
    let state: AISuggestState
    let onPrimary: () -> Void
    let onSecondary: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(badge).font(LNTypography.labelMedium()).foregroundStyle(accent)
            Text(title).font(LNTypography.headlineSmall()).foregroundStyle(LNColor.title)
            Text(subtitle).font(LNTypography.bodyMedium()).foregroundStyle(LNColor.subtitle)
            HStack(spacing: 8) {
                LNButton(title: primaryTitle, variant: .primary, action: onPrimary)
                LNButton(title: L10n.commonCancel, variant: .secondary, action: onSecondary)
            }
        }
        .padding(LNSpacing.cardPadding)
        .background(
            LinearGradient(colors: [gradStart, LNColor.aiGradEnd], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
        .overlay(RoundedRectangle(cornerRadius: LNRadius.homeCard).stroke(LNColor.stroke, lineWidth: 1))
    }

    private var badge: String {
        switch state {
        case .scanning: return "Scanning"
        case .sensitive: return "Alert"
        case .cleanup: return "Cleanup"
        case .allClear: return "All Clear"
        case .idle: return "AI"
        }
    }

    private var title: String {
        switch state {
        case .scanning: return "Analyzing photos"
        case .sensitive: return "3 sensitive photos found"
        case .cleanup: return "5 photos can be cleaned"
        case .allClear: return "All clear"
        case .idle: return "Start AI scan"
        }
    }

    private var subtitle: String { L10n.tr("placeholder_feature") }
    private var primaryTitle: String {
        switch state {
        case .cleanup: return L10n.tr("ai_cleanup_confirm_clean")
        case .sensitive: return L10n.tr("ai_feat_blur")
        default: return L10n.tr("ai_feat_classify")
        }
    }

    private var accent: Color {
        switch state {
        case .sensitive: return LNColor.amberWarning
        case .cleanup: return LNColor.cleanupOrange
        case .allClear: return LNColor.allClearTeal
        default: return LNColor.brandBlue
        }
    }

    private var gradStart: Color {
        switch state {
        case .sensitive, .cleanup: return LNColor.aiSensitiveStart
        case .allClear: return LNColor.aiAllClearStart
        default: return LNColor.aiGradStart
        }
    }
}
