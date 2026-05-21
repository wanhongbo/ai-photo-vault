import SwiftUI

@MainActor
final class AppRouter: ObservableObject {
    @Published var phase: AppPhase = .splash
    @Published var selectedTab: MainTab = .vault
    @Published var vaultPath = NavigationPath()
    @Published var settingsPath = NavigationPath()
    @Published var aiPath = NavigationPath()
    @Published var presentedRoute: AppRoute?

    var shouldShowBottomTabBar: Bool {
        switch selectedTab {
        case .vault, .camera:
            return vaultPath.count == 0
        case .ai:
            return aiPath.count == 0
        case .settings:
            return settingsPath.count == 0
        }
    }

    func finishSplash(goToLock: Bool) {
        phase = goToLock ? .lock : .main
    }

    func unlock() {
        AppLockManager.shared.onUnlockSucceeded()
        phase = .main
    }

    func openPrivateCamera() {
        presentedRoute = .privateCamera
    }

    func present(_ route: AppRoute) {
        presentedRoute = route
    }

    func dismissPresented() {
        presentedRoute = nil
    }

    func pushVault(_ route: AppRoute) {
        vaultPath.append(route)
    }

    func pushSettings(_ route: AppRoute) {
        settingsPath.append(route)
    }

    func pushAI(_ route: AppRoute) {
        aiPath.append(route)
    }

    /// Pushes onto the navigation stack for the currently selected tab.
    func pushInCurrentTab(_ route: AppRoute) {
        switch selectedTab {
        case .vault, .camera:
            pushVault(route)
        case .ai:
            pushAI(route)
        case .settings:
            pushSettings(route)
        }
    }

    func popCurrentTab(count: Int = 1) {
        switch selectedTab {
        case .vault, .camera:
            vaultPath.removeLast(min(count, vaultPath.count))
        case .ai:
            aiPath.removeLast(min(count, aiPath.count))
        case .settings:
            settingsPath.removeLast(min(count, settingsPath.count))
        }
    }
}
