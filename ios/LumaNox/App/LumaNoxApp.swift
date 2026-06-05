import SwiftUI

@main
struct LumaNoxApp: App {
    init() {
        FirebaseTelemetry.configure()
        LumaTelemetry.trackAppStart(
            version: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown",
            build: Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
        )
        BillingBootstrap.configure()
        AutoBackupScheduler.registerBackgroundTasks()
        ExternalBackupLocation.sanitizeOnStartup()
        VaultMaintenanceService.performStartupCleanup()
    }

    @StateObject private var router = AppRouter()
    @StateObject private var appLock = AppLockManager.shared
    @StateObject private var vaultStore = VaultStore.shared
    @StateObject private var subscription = SubscriptionService.shared
    @StateObject private var languageManager = LanguageManager.shared
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(router)
                .environmentObject(appLock)
                .environmentObject(vaultStore)
                .environmentObject(subscription)
                .environmentObject(languageManager)
                .environment(\.locale, languageManager.effectiveLocale)
                .preferredColorScheme(.dark)
                .onChange(of: scenePhase) { phase in
                    appLock.handleScenePhase(phase, lockScreenVisible: router.phase == .lock)
                    if appLock.requireUnlock, router.phase == .main {
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) {
                            router.phase = .lock
                        }
                    }
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var appLock: AppLockManager
    @StateObject private var privateCameraViewModel = PrivateCameraViewModel()

    var body: some View {
        ZStack {
            LNColor.bgBottom.ignoresSafeArea()
            Group {
                switch router.phase {
                case .splash:
                    SplashView()
                case .lock:
                    LockView()
                case .main:
                    MainTabView(privateCameraViewModel: privateCameraViewModel)
                }
            }
        }
        .animation(.easeInOut(duration: 0.25), value: router.phase)
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
        .overlay {
            if appLock.protectsAppSwitcherSnapshot {
                PrivacySnapshotLockView()
                    .transition(.identity)
            }
        }
        .onAppear {
            router.preparePrivateCameraForPresentation = { [privateCameraViewModel] in
                privateCameraViewModel.startForPresentation()
            }
            #if DEBUG
            applyDebugLaunchRouteIfNeeded()
            #endif
            enforcePinGate()
            AutoBackupScheduler.scheduleColdStartIfDue()
            Task { await VaultMaintenanceService.performUnlockedCleanup() }
        }
        .onChange(of: router.phase) { _ in enforcePinGate() }
    }

    private func enforcePinGate() {
        if AppDebugPolicy.skipsPinGate { return }
        if router.phase == .main, !SecuritySettingsStore.shared.hasPinConfigured {
            router.phase = .lock
        }
    }

    #if DEBUG
    private func applyDebugLaunchRouteIfNeeded() {
        guard AppDebugPolicy.skipsPinGate else { return }
        if ProcessInfo.processInfo.arguments.contains("-uiTestPaywall"),
           router.presentedRoute == nil {
            router.phase = .main
            router.presentedRoute = .paywall(dismissable: true, source: PaywallSource.manual)
            return
        }
        guard ProcessInfo.processInfo.arguments.contains("-uiTestPrivacyRedact"),
              router.aiPath.isEmpty else { return }
        router.phase = .main
        router.selectedTab = .ai
        router.pushAI(.privacyRedact(path: ""))
    }
    #endif
}

private struct PrivacySnapshotLockView: View {
    private let keypadRows = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"]]

    var body: some View {
        ZStack {
            LNColor.lockBg.ignoresSafeArea()
            VStack(spacing: 18) {
                Spacer(minLength: 84)

                Image(systemName: "lock")
                    .font(.system(size: 30, weight: .semibold))
                    .foregroundStyle(LNColor.brandBlue)
                    .frame(width: 86, height: 86)
                    .background(LNColor.brandBlue.opacity(0.18))
                    .clipShape(RoundedRectangle(cornerRadius: 26))
                    .overlay(RoundedRectangle(cornerRadius: 26).stroke(LNColor.brandBlue.opacity(0.85), lineWidth: 1))

                VStack(spacing: 8) {
                    Text(L10n.tr("lock_title"))
                        .font(LNTypography.pinTitle())
                        .foregroundStyle(LNColor.title)
                    Text(L10n.tr("lock_subtitle"))
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.subtitle)
                        .multilineTextAlignment(.center)
                        .lineSpacing(2)
                        .frame(width: 300)
                }

                HStack(spacing: 12) {
                    ForEach(0..<6, id: \.self) { _ in
                        Circle()
                            .stroke(LNColor.brandBlue, lineWidth: 1.5)
                            .frame(width: 12, height: 12)
                    }
                }
                .padding(.top, 4)

                VStack(spacing: 12) {
                    ForEach(keypadRows, id: \.self) { row in
                        HStack(spacing: 16) {
                            ForEach(row, id: \.self) { key in
                                snapshotKey(key)
                            }
                        }
                    }
                    HStack(spacing: 16) {
                        snapshotKey("camera")
                        snapshotKey("0")
                        snapshotKey("delete")
                    }
                }
                .padding(.top, 18)

                Spacer(minLength: 48)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func snapshotKey(_ key: String) -> some View {
        ZStack {
            Circle()
                .fill(Color(hex: 0x131C29))
                .overlay(Circle().stroke(LNColor.brandBlue, lineWidth: 1.5))
            if key == "camera" {
                Image(systemName: "camera")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(LNColor.title)
            } else if key == "delete" {
                Image(systemName: "delete.left")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(LNColor.title)
            } else {
                Text(key)
                    .font(LNTypography.pinDigit())
                    .foregroundStyle(LNColor.title)
            }
        }
        .frame(width: 78, height: 78)
    }
}
