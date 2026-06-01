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
    @State private var cameraPreparationTask: Task<Void, Never>?

    var body: some View {
        ZStack(alignment: .bottom) {
            mainContent
                .padding(.bottom, bottomTabReservedHeight)

            if shouldShowBottomTabBar {
                LNBottomTabBar(
                    selected: $router.selectedTab,
                    onCameraPressBegan: {
                        router.openPrivateCamera()
                    },
                    onCameraTap: {
                        router.openPrivateCamera()
                    }
                )
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
            scheduleCameraPreparationIfPossible()
        }
        .onChange(of: router.phase) { _ in
            scheduleCameraPreparationIfPossible()
        }
        .onChange(of: router.selectedTab) { _ in
            scheduleCameraPreparationIfPossible()
        }
        .onChange(of: router.vaultPath.count) { _ in
            scheduleCameraPreparationIfPossible()
        }
        .onAppear {
            applyDebugStartRouteIfNeeded()
            scheduleCameraPreparationIfPossible()
        }
        .onDisappear {
            cancelCameraPreparation()
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
            scheduleCameraPreparationIfPossible()
        case .inactive, .background:
            cancelCameraPreparation()
            if router.presentedRoute != .privateCamera {
                privateCameraViewModel.controller.stop(discardPendingRecording: true)
            }
        @unknown default:
            break
        }
    }

    private var shouldPrepareCameraOnVaultHome: Bool {
        guard scenePhase == .active,
              router.phase == .main,
              router.presentedRoute != .privateCamera,
              router.selectedTab == .vault,
              router.vaultPath.count == 0
        else { return false }
        return true
    }

    private func scheduleCameraPreparationIfPossible() {
        cancelCameraPreparation()
        guard shouldPrepareCameraOnVaultHome else { return }
        cameraPreparationTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled, shouldPrepareCameraOnVaultHome else { return }
            privateCameraViewModel.prepareSessionForVaultHome()
        }
    }

    private func cancelCameraPreparation() {
        cameraPreparationTask?.cancel()
        cameraPreparationTask = nil
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
