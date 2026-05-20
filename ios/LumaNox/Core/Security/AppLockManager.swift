import Foundation
import SwiftUI

/// 与 Android `AppLockManager` 对齐：冷启动需解锁；后台超时回前台再上锁。
@MainActor
final class AppLockManager: ObservableObject {
    static let shared = AppLockManager()

    @Published private(set) var requireUnlock = false

    private let backgroundTimeout: TimeInterval = 60
    private var lastBackgroundAt: Date?
    private let securityStore = SecuritySettingsStore.shared

    private init() {
        requireUnlock = securityStore.hasPinConfigured
    }

    func onUnlockSucceeded() {
        requireUnlock = false
        lastBackgroundAt = nil
    }

    func refreshPinConfigured() {
        securityStore.reload()
        if !securityStore.hasPinConfigured {
            requireUnlock = false
        }
    }

    func handleScenePhase(_ phase: ScenePhase) {
        switch phase {
        case .background, .inactive:
            if securityStore.hasPinConfigured {
                lastBackgroundAt = Date()
            }
        case .active:
            guard securityStore.hasPinConfigured, let last = lastBackgroundAt else { return }
            if Date().timeIntervalSince(last) >= backgroundTimeout {
                requireUnlock = true
            }
            lastBackgroundAt = nil
        @unknown default:
            break
        }
    }
}
