import Foundation
import SwiftUI

/// 冷启动需解锁；iOS 进入后台或 App Switcher 时立即保护内容并要求重新解锁。
@MainActor
final class AppLockManager: ObservableObject {
    static let shared = AppLockManager()

    @Published private(set) var requireUnlock = false
    @Published private(set) var protectsAppSwitcherSnapshot = false
    @Published private(set) var foregroundUnlockBiometricRequestID = 0

    private var wasBackgrounded = false
    private var hasPendingForegroundBiometricRequest = false
    private let securityStore = SecuritySettingsStore.shared

    private init() {
        requireUnlock = securityStore.hasPinConfigured
    }

    func onUnlockSucceeded() {
        requireUnlock = false
        protectsAppSwitcherSnapshot = false
        wasBackgrounded = false
        hasPendingForegroundBiometricRequest = false
    }

    func refreshPinConfigured() {
        securityStore.reload()
        if !securityStore.hasPinConfigured {
            requireUnlock = false
            protectsAppSwitcherSnapshot = false
            wasBackgrounded = false
            hasPendingForegroundBiometricRequest = false
        }
    }

    func handleScenePhase(_ phase: ScenePhase) {
        switch phase {
        case .background, .inactive:
            if securityStore.hasPinConfigured {
                wasBackgrounded = true
                requireUnlock = true
                protectsAppSwitcherSnapshot = true
                hasPendingForegroundBiometricRequest = true
            }
        case .active:
            protectsAppSwitcherSnapshot = false
            guard securityStore.hasPinConfigured, wasBackgrounded else { return }
            wasBackgrounded = false
            requireUnlock = true
            if hasPendingForegroundBiometricRequest {
                foregroundUnlockBiometricRequestID += 1
            }
        @unknown default:
            break
        }
    }

    func consumeForegroundBiometricRequest() {
        hasPendingForegroundBiometricRequest = false
    }
}
