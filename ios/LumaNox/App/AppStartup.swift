import UIKit

enum AppStartup {
    private static var didBootstrap = false
    private static let bootstrapLock = NSLock()

    /// 仅设置 UIWindow 底色，避免 Launch Screen → SwiftUI 首帧之间闪白。
    /// 切勿对 UIWindow 内所有 UIView 设置 appearance，否则会破坏 NavigationStack 内容渲染。
    static func configureWindowAppearance() {
        UIWindow.appearance().backgroundColor = UIColor(
            red: 5 / 255,
            green: 8 / 255,
            blue: 13 / 255,
            alpha: 1
        )
    }

    static func performBootstrapIfNeeded() {
        bootstrapLock.lock()
        defer { bootstrapLock.unlock() }
        guard !didBootstrap else { return }
        didBootstrap = true

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
}
