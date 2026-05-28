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
    private var systemInteractionTokens = Set<UUID>()
    private var systemInteractionGraceUntil: Date?
    private let securityStore = SecuritySettingsStore.shared
    private let systemInteractionGraceInterval: TimeInterval = 1.0

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

    @discardableResult
    func beginSystemInteraction(timeout: TimeInterval = 120) -> UUID {
        let token = UUID()
        systemInteractionTokens.insert(token)
        systemInteractionGraceUntil = nil
        scheduleSystemInteractionTimeout(token, timeout: timeout)
        return token
    }

    func endSystemInteraction(_ token: UUID?) {
        guard let token else { return }
        guard systemInteractionTokens.remove(token) != nil else { return }
        systemInteractionGraceUntil = Date().addingTimeInterval(systemInteractionGraceInterval)
    }

    func handleScenePhase(_ phase: ScenePhase, lockScreenVisible: Bool = false) {
        switch phase {
        case .inactive:
            if securityStore.hasPinConfigured {
                guard !lockScreenVisible else {
                    protectsAppSwitcherSnapshot = false
                    return
                }
                guard !isSystemInteractionActiveOrGrace else { return }
                wasBackgrounded = true
                requireUnlock = true
                protectsAppSwitcherSnapshot = true
                hasPendingForegroundBiometricRequest = true
            }
        case .background:
            if securityStore.hasPinConfigured {
                guard !lockScreenVisible else {
                    protectsAppSwitcherSnapshot = false
                    return
                }
                wasBackgrounded = true
                requireUnlock = true
                protectsAppSwitcherSnapshot = true
                hasPendingForegroundBiometricRequest = true
            }
        case .active:
            if !wasBackgrounded, !systemInteractionTokens.isEmpty {
                systemInteractionTokens.removeAll()
                systemInteractionGraceUntil = Date().addingTimeInterval(systemInteractionGraceInterval)
            }
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

    private var isSystemInteractionActiveOrGrace: Bool {
        if !systemInteractionTokens.isEmpty { return true }
        guard let systemInteractionGraceUntil else { return false }
        return Date() < systemInteractionGraceUntil
    }

    private func scheduleSystemInteractionTimeout(_ token: UUID, timeout: TimeInterval) {
        Task { @MainActor in
            let nanoseconds = UInt64(max(1, timeout) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: nanoseconds)
            if systemInteractionTokens.remove(token) != nil {
                systemInteractionGraceUntil = Date().addingTimeInterval(systemInteractionGraceInterval)
            }
        }
    }
}

extension View {
    func appLockSystemInteraction(timeout: TimeInterval = 120) -> some View {
        simultaneousGesture(
            TapGesture().onEnded {
                AppLockManager.shared.beginSystemInteraction(timeout: timeout)
            }
        )
    }
}
