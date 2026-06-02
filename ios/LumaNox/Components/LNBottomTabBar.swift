import SwiftUI
import UIKit

struct LNBottomTabBar: View {
    @Binding var selected: MainTab
    let onCameraPressBegan: () -> Void

    private let itemSpacing: CGFloat = 6
    private let itemHeight: CGFloat = 72
    private let iconBoxSize = CGSize(width: 30, height: 28)
    private let labelHeight: CGFloat = 17
    @State private var didBeginTabPress = false
    @State private var pressedTab: MainTab?
    @State private var pressResetTask: Task<Void, Never>?
    @State private var cameraOpenTask: Task<Void, Never>?

    var body: some View {
        HStack(spacing: itemSpacing) {
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
        .onDisappear {
            pressResetTask?.cancel()
            pressResetTask = nil
            cameraOpenTask?.cancel()
            cameraOpenTask = nil
        }
    }

    @ViewBuilder
    private func tabButton(_ tab: MainTab) -> some View {
        let activeTab = pressedTab ?? selected
        let isSelected = activeTab == tab
        Button {
            if tab == .camera {
                guard pressedTab != .camera else { return }
                beginPress(for: tab, opensCamera: true)
            } else {
                selected = tab
            }
        } label: {
            VStack(spacing: 5) {
                Image(systemName: icon(for: tab))
                    .symbolRenderingMode(.monochrome)
                    .font(.system(size: iconSize(for: tab), weight: isSelected ? .semibold : .medium))
                    .frame(width: iconBoxSize.width, height: iconBoxSize.height, alignment: .center)
                Text(label(for: tab))
                    .font(.system(size: 12, weight: isSelected ? .semibold : .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .frame(height: labelHeight, alignment: .center)
            }
            .foregroundStyle(isSelected ? LNColor.navItemActive : LNColor.navItemIdle)
            .frame(maxWidth: .infinity)
            .frame(height: itemHeight)
            .background(
                RoundedRectangle(cornerRadius: LNRadius.homeNavItem)
                    .fill(isSelected ? LNColor.brandBlue.opacity(0.12) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: LNRadius.homeNavItem)
                    .stroke(isSelected ? LNColor.brandBlue : Color.clear, lineWidth: 1)
            )
        }
        .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.78))
        .simultaneousGesture(tabPressGesture(for: tab))
        .accessibilityIdentifier("ln_tab_\(tab.rawValue)")
    }

    private func tabPressGesture(for tab: MainTab) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !didBeginTabPress else { return }
                beginPress(for: tab, opensCamera: tab == .camera)
            }
            .onEnded { _ in
                if tab == .camera {
                    schedulePressReset(after: 180_000_000)
                } else {
                    resetPressState()
                }
            }
    }

    private func beginPress(for tab: MainTab, opensCamera: Bool) {
        didBeginTabPress = true
        pressedTab = tab
        schedulePressReset(after: tab == .camera ? 420_000_000 : 350_000_000)
        UISelectionFeedbackGenerator().selectionChanged()
        if opensCamera {
            requestCameraOpen()
        } else {
            selected = tab
        }
    }

    private func requestCameraOpen() {
        guard cameraOpenTask == nil else { return }
        cameraOpenTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 35_000_000)
            guard !Task.isCancelled else { return }
            onCameraPressBegan()
            try? await Task.sleep(nanoseconds: 320_000_000)
            guard !Task.isCancelled else { return }
            cameraOpenTask = nil
        }
    }

    private func schedulePressReset(after nanoseconds: UInt64) {
        pressResetTask?.cancel()
        pressResetTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: nanoseconds)
            guard !Task.isCancelled else { return }
            resetPressState()
        }
    }

    private func resetPressState() {
        pressResetTask?.cancel()
        pressResetTask = nil
        didBeginTabPress = false
        pressedTab = nil
    }

    private func iconSize(for tab: MainTab) -> CGFloat {
        switch tab {
        case .vault: return 22
        case .camera: return 22
        case .ai: return 22
        case .settings: return 21
        }
    }

    private func icon(for tab: MainTab) -> String {
        switch tab {
        case .vault: return "lock.shield"
        case .camera: return "camera"
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
