import Foundation

/// 首启软墙：PIN 解锁后延迟展示一次可关闭 Paywall — 对齐 Android [OnboardingPaywallManager]。
enum OnboardingPaywallManager {
    private static let key = "has_seen_onboarding_paywall"

    static var shouldShow: Bool {
        !UserDefaults.standard.bool(forKey: key)
    }

    static func markSeen() {
        UserDefaults.standard.set(true, forKey: key)
    }
}
