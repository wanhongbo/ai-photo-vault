import Foundation
import RevenueCat

/// RevenueCat 初始化；未配置 API Key 时跳过（与 Android [BillingBootstrap] 一致）。
enum BillingBootstrap {
    private(set) static var isConfigured = false

    static func configure() {
        guard !isConfigured else { return }
        guard let key = apiKey, !key.isEmpty else { return }
        #if DEBUG
        Purchases.logLevel = .debug
        #else
        Purchases.logLevel = .error
        #endif
        Purchases.configure(withAPIKey: key)
        isConfigured = true
    }

    private static var apiKey: String? {
        if let env = ProcessInfo.processInfo.environment["REVENUECAT_API_KEY"]?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !env.isEmpty {
            return env
        }
        if let plist = Bundle.main.object(forInfoDictionaryKey: "REVENUECAT_API_KEY") as? String {
            let trimmed = plist.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty, trimmed != "$(REVENUECAT_API_KEY)" {
                return trimmed
            }
        }
        return nil
    }
}
