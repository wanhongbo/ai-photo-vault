import SwiftUI

struct MainTabView: View {
    @EnvironmentObject private var router: AppRouter
    @StateObject private var vaultHomeViewModel = VaultHomeViewModel()
    @State private var didApplyDebugStartRoute = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch router.selectedTab {
                case .vault:
                    NavigationStack(path: $router.vaultPath) {
                        VaultHomeView(viewModel: vaultHomeViewModel)
                            .routeNavigationDestinations()
                            .toolbar(.hidden, for: .navigationBar)
                    }
                case .camera:
                    CameraHomeView()
                case .ai:
                    NavigationStack(path: $router.aiPath) {
                        AIHomeView()
                            .routeNavigationDestinations()
                            .toolbar(.hidden, for: .navigationBar)
                    }
                case .settings:
                    NavigationStack(path: $router.settingsPath) {
                        SettingsHomeView()
                            .routeNavigationDestinations()
                            .toolbar(.hidden, for: .navigationBar)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            if router.shouldShowBottomTabBar {
                LNBottomTabBar(selected: $router.selectedTab, onCameraTap: {
                    router.openPrivateCamera()
                })
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .lnScreenBackground()
        .animation(.easeInOut(duration: 0.18), value: router.shouldShowBottomTabBar)
        .fullScreenCover(isPresented: Binding(
            get: { router.presentedRoute != nil },
            set: { if !$0 { router.dismissPresented() } }
        )) {
            if let route = router.presentedRoute {
                NavigationStack {
                    RouteDestinationView(route: route)
                        .toolbar(.hidden, for: .navigationBar)
                }
            }
        }
        .onAppear {
            scheduleOnboardingPaywallIfNeeded()
            applyDebugStartRouteIfNeeded()
        }
        .accessibilityIdentifier("main_tab_view")
    }

    /// 首启软墙：进入主页 5s 后展示可关闭 Paywall（对齐 Android MainActivity）。
    private func scheduleOnboardingPaywallIfNeeded() {
        #if DEBUG
        return
        #endif
        guard BillingBootstrap.isConfigured else { return }
        guard OnboardingPaywallManager.shouldShow else { return }
        Task {
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            guard OnboardingPaywallManager.shouldShow else { return }
            await MainActor.run {
                OnboardingPaywallManager.markSeen()
                router.present(.paywall(dismissable: true, source: PaywallSource.onboarding))
            }
        }
    }

    private func applyDebugStartRouteIfNeeded() {
        #if DEBUG
        guard !didApplyDebugStartRoute else { return }
        didApplyDebugStartRoute = true
        switch ProcessInfo.processInfo.environment["LUMANOX_DEBUG_START_ROUTE"] {
        case "albumList":
            router.selectedTab = .vault
            router.pushVault(.albumList)
        case "recentList":
            router.selectedTab = .vault
            router.pushVault(.recentList)
        case "albumDefault":
            router.selectedTab = .vault
            router.pushVault(.album(name: "Default"))
        default:
            break
        }
        #endif
    }
}
