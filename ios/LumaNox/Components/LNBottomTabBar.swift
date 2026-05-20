import SwiftUI

struct LNBottomTabBar: View {
    @Binding var selected: MainTab
    let onCameraTap: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(MainTab.allCases) { tab in
                tabButton(tab)
            }
        }
        .padding(8)
        .frame(height: LNSpacing.homeNavBarHeight)
        .background(LNColor.navBarBg.opacity(0.8))
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeNavBar))
        .overlay(
            RoundedRectangle(cornerRadius: LNRadius.homeNavBar)
                .stroke(LNColor.stroke, lineWidth: 1)
        )
        .padding(.horizontal, LNSpacing.screenHorizontal)
        .padding(.bottom, 8)
        .accessibilityIdentifier("ln_bottom_tab_bar")
    }

    @ViewBuilder
    private func tabButton(_ tab: MainTab) -> some View {
        let isSelected = selected == tab
        Button {
            if tab == .camera {
                onCameraTap()
            } else {
                selected = tab
            }
        } label: {
            VStack(spacing: 4) {
                Image(systemName: icon(for: tab))
                    .font(.system(size: 21, weight: isSelected ? .semibold : .medium))
                Text(label(for: tab))
                    .font(LNTypography.labelMedium())
            }
            .foregroundStyle(isSelected ? LNColor.navItemActive : LNColor.navItemIdle)
            .frame(maxWidth: .infinity)
            .frame(maxHeight: .infinity)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: LNRadius.homeNavItem)
                    .fill(isSelected ? LNColor.brandBlue.opacity(0.12) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: LNRadius.homeNavItem)
                    .stroke(isSelected ? LNColor.brandBlue : Color.clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("ln_tab_\(tab.rawValue)")
    }

    private func icon(for tab: MainTab) -> String {
        switch tab {
        case .vault: return "lock.shield"
        case .camera: return "camera.fill"
        case .ai: return "sparkles"
        case .settings: return "gearshape"
        }
    }

    private func label(for tab: MainTab) -> String {
        switch tab {
        case .vault: return L10n.homeNavVault
        case .camera: return L10n.homeNavCamera
        case .ai: return L10n.homeNavAI
        case .settings: return L10n.homeNavSettings
        }
    }
}
