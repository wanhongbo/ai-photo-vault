import SwiftUI

struct LNNavigationBar: View {
    let title: String
    let onBack: () -> Void

    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(LNColor.title)
                    .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
                    .background(LNColor.navBarBg.opacity(0.8))
                    .clipShape(RoundedRectangle(cornerRadius: LNRadius.topBarButton))
            }
            .accessibilityLabel(L10n.commonBack)
            .accessibilityIdentifier("ln_nav_back")

            Text(title)
                .font(LNTypography.titleMedium())
                .foregroundStyle(LNColor.title)
                .frame(maxWidth: .infinity)
                .accessibilityAddTraits(.isHeader)

            Color.clear
                .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
        }
        .padding(.horizontal, LNSpacing.screenHorizontal)
    }
}

struct LNScreenScaffold<Content: View>: View {
    let title: String
    let onBack: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            LNNavigationBar(title: title, onBack: onBack)
            ScrollView {
                content()
                    .padding(LNSpacing.screenHorizontal)
                    .padding(.bottom, 24)
            }
        }
        .lnScreenBackground()
    }
}
