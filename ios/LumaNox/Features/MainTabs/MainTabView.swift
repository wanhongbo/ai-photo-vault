import Combine
import SwiftUI
import UIKit

struct MainTabView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var vaultHomeViewModel = VaultHomeViewModel()
    @ObservedObject var privateCameraViewModel: PrivateCameraViewModel
    @State private var didApplyDebugStartRoute = false
    @State private var isKeyboardVisible = false

    var body: some View {
        ZStack(alignment: .bottom) {
            mainContent
                .padding(.bottom, bottomTabReservedHeight)

            if shouldShowBottomTabBar {
                LNBottomTabBar(selected: $router.selectedTab, onCameraTap: {
                    router.openPrivateCamera()
                })
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .lnScreenBackground()
        .animation(.easeInOut(duration: 0.18), value: shouldShowBottomTabBar)
        .onChange(of: scenePhase) { phase in
            handleScenePhase(phase)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            isKeyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            isKeyboardVisible = false
        }
        .onChange(of: router.presentedRoute) { route in
            guard route == nil else { return }
            prewarmCameraIfPossible()
        }
        .onChange(of: router.phase) { _ in
            prewarmCameraIfPossible()
        }
        .onAppear {
            applyDebugStartRouteIfNeeded()
            prewarmCameraIfPossible()
        }
        .accessibilityIdentifier("main_tab_view")
    }

    @ViewBuilder
    private var mainContent: some View {
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
    }

    private var shouldShowBottomTabBar: Bool {
        router.shouldShowBottomTabBar &&
            !isKeyboardVisible &&
            !(router.selectedTab == .vault && vaultHomeViewModel.shouldHideBottomTabBar)
    }

    private var bottomTabReservedHeight: CGFloat {
        shouldShowBottomTabBar ? LNSpacing.homeNavBarHeight + 8 : 0
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
