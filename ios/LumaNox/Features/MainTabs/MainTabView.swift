import SwiftUI

struct MainTabView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var vaultHomeViewModel = VaultHomeViewModel()
    @StateObject private var privateCameraViewModel = PrivateCameraViewModel()
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
                    .swipeBackEnabled()
                case .camera:
                    CameraHomeView()
                case .ai:
                    NavigationStack(path: $router.aiPath) {
                        AIHomeView()
                            .routeNavigationDestinations()
                            .toolbar(.hidden, for: .navigationBar)
                    }
                    .swipeBackEnabled()
                case .settings:
                    NavigationStack(path: $router.settingsPath) {
                        SettingsHomeView()
                            .routeNavigationDestinations()
                            .toolbar(.hidden, for: .navigationBar)
                    }
                    .swipeBackEnabled()
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
                    switch route {
                    case .privateCamera:
                        PrivateCameraView(viewModel: privateCameraViewModel)
                    default:
                        RouteDestinationView(route: route)
                    }
                }
                .toolbar(.hidden, for: .navigationBar)
                .swipeBackEnabled()
            }
        }
        .onChange(of: scenePhase) { phase in
            handleScenePhase(phase)
        }
        .onChange(of: router.presentedRoute) { route in
            guard route == nil else { return }
            prewarmCameraIfPossible()
        }
        .onChange(of: router.phase) { _ in
            prewarmCameraIfPossible()
        }
        .onAppear {
            scheduleOnboardingPaywallIfNeeded()
            applyDebugStartRouteIfNeeded()
            prewarmCameraIfPossible()
        }
        .accessibilityIdentifier("main_tab_view")
    }

    private func handleScenePhase(_ phase: ScenePhase) {
        switch phase {
        case .active:
            prewarmCameraIfPossible()
        case .inactive, .background:
            if router.presentedRoute != .privateCamera {
                privateCameraViewModel.controller.stop(discardPendingRecording: true)
            }
        @unknown default:
            break
        }
    }

    private func prewarmCameraIfPossible() {
        guard scenePhase == .active,
              router.phase == .main,
              router.presentedRoute != .privateCamera
        else { return }
        privateCameraViewModel.prepareForFastStart()
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
