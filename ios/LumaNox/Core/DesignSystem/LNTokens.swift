import SwiftUI

enum LNRadius {
    static let dialog: CGFloat = 22
    static let homeCard: CGFloat = 20
    static let homeThumb: CGFloat = 12
    static let homeAlbumCard: CGFloat = 14
    static let homeNavBar: CGFloat = 24
    static let homeNavItem: CGFloat = 16
    static let settingsRow: CGFloat = 12
    static let paywallCard: CGFloat = 18
    static let topBarButton: CGFloat = 14
    static let vaultEmptyIconWrap: CGFloat = 28
}

enum LNSpacing {
    static let minTouchTarget: CGFloat = 44
    static let screenHorizontal: CGFloat = 16
    static let cardPadding: CGFloat = 16
    static let gridGap: CGFloat = 8
    static let sectionGap: CGFloat = 16
    static let albumCardWidth: CGFloat = 124
    static let albumCoverHeight: CGFloat = 92
    static let dialogMaxWidth: CGFloat = 308
    static let dialogPadding: CGFloat = 22
    static let dialogBodyTopGap: CGFloat = 11
    static let dialogButtonTopGap: CGFloat = 22
    static let dialogButtonHeight: CGFloat = 54
    static let dialogButtonGap: CGFloat = 12
    static let buttonHeightPrimary: CGFloat = 54
    static let buttonHeightSecondary: CGFloat = 48
    static let homeNavBarHeight: CGFloat = 88
}

enum LNTypography {
    static func displayLarge() -> Font { .system(size: 32, weight: .bold) }
    static func displaySmall() -> Font { .system(size: 24, weight: .bold) }
    static func headlineMedium() -> Font { .system(size: 22, weight: .bold) }
    static func headlineSmall() -> Font { .system(size: 20, weight: .semibold) }
    static func titleLarge() -> Font { .system(size: 18, weight: .semibold) }
    static func titleMedium() -> Font { .system(size: 16, weight: .semibold) }
    static func bodyLarge() -> Font { .system(size: 15, weight: .regular) }
    static func bodyMedium() -> Font { .system(size: 14, weight: .regular) }
    static func labelMedium() -> Font { .system(size: 12, weight: .medium) }
    static func button() -> Font { .system(size: 16, weight: .semibold) }
    static func dialogTitle() -> Font { .system(size: 18, weight: .bold) }
    static func dialogBody() -> Font { .system(size: 14, weight: .regular) }
    static func pinTitle() -> Font { .system(size: 24, weight: .bold) }
    static func pinDigit() -> Font { .system(size: 22, weight: .regular) }
}

struct LNGradientBackground: View {
    let top: Color
    let bottom: Color

    var body: some View {
        LinearGradient(
            colors: [top, bottom],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

struct LNCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(LNSpacing.cardPadding)
            .background(LNColor.sectionBg)
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
            .overlay(
                RoundedRectangle(cornerRadius: LNRadius.homeCard)
                    .stroke(LNColor.stroke, lineWidth: 1)
            )
    }
}

extension View {
    func lnCard() -> some View {
        modifier(LNCardModifier())
    }

    func lnScreenBackground() -> some View {
        background(LNGradientBackground(top: LNColor.bgTop, bottom: LNColor.bgBottom))
    }

    func lnOutlinedCard(
        cornerRadius: CGFloat = LNRadius.homeCard,
        fill: Color = LNColor.sectionBg,
        stroke: Color = LNColor.stroke
    ) -> some View {
        background(fill)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(stroke, lineWidth: 1)
            )
    }
}
