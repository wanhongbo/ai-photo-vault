import SwiftUI

@main
struct LumaNoxApp: App {
    init() {
        BillingBootstrap.configure()
        AutoBackupScheduler.registerBackgroundTasks()
        ExternalBackupLocation.sanitizeOnStartup()
        VaultMaintenanceService.performStartupCleanup()
    }

    @StateObject private var router = AppRouter()
    @StateObject private var appLock = AppLockManager.shared
    @StateObject private var vaultStore = VaultStore.shared
    @StateObject private var subscription = SubscriptionService.shared
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(router)
                .environmentObject(appLock)
                .environmentObject(vaultStore)
                .environmentObject(subscription)
                .preferredColorScheme(.dark)
                .onChange(of: scenePhase) { phase in
                    appLock.handleScenePhase(phase)
                    #if DEBUG
                    return
                    #endif
                    if appLock.requireUnlock, router.phase == .main {
                        router.phase = .lock
                    }
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var appLock: AppLockManager

    var body: some View {
        Group {
            switch router.phase {
            case .splash:
                SplashView()
            case .lock:
                LockView()
            case .main:
                MainTabView()
            }
        }
        .animation(.easeInOut(duration: 0.25), value: router.phase)
        .onAppear {
            enforcePinGate()
            AutoBackupScheduler.scheduleColdStartIfDue()
            Task { await VaultMaintenanceService.performUnlockedCleanup() }
        }
        .onChange(of: router.phase) { _ in enforcePinGate() }
    }

    private func enforcePinGate() {
        #if DEBUG
        return
        #endif
        if router.phase == .main, !SecuritySettingsStore.shared.hasPinConfigured {
            router.phase = .lock
        }
    }
}
