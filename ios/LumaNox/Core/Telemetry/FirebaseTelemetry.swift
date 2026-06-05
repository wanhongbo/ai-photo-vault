import FirebaseAnalytics
import FirebaseCore
import FirebaseCrashlytics
import Foundation
import os

enum FirebaseTelemetry {
    private static let log = Logger(subsystem: "com.xpx.vault", category: "FirebaseTelemetry")
    private(set) static var isConfigured = false

    static func configure() {
        guard !isConfigured else { return }

        if FirebaseApp.app() == nil {
            guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
                log.info("Firebase skipped because GoogleService-Info.plist is not bundled.")
                return
            }
            FirebaseApp.configure()
        }

        Analytics.setAnalyticsCollectionEnabled(!isDebugBuild)
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(!isDebugBuild)
        isConfigured = true
    }

    static func logEvent(_ name: String, parameters: [String: String] = [:]) {
        guard isConfigured else { return }
        let payload = parameters.reduce(into: [String: Any]()) { result, item in
            result[sanitizeName(item.key)] = sanitizeValue(item.value)
        }
        Analytics.logEvent(sanitizeName(name), parameters: payload.isEmpty ? nil : payload)
    }

    static func breadcrumb(_ tag: String, _ message: String) {
        guard isConfigured else { return }
        Crashlytics.crashlytics().log("[\(sanitizeName(tag))] \(sanitizeValue(message))")
    }

    private static var isDebugBuild: Bool {
        #if DEBUG
        true
        #else
        false
        #endif
    }

    private static func sanitizeName(_ value: String) -> String {
        let filtered = value.filter { $0.isLetter || $0.isNumber || $0 == "_" }
        return String((filtered.isEmpty ? "event" : filtered).prefix(40))
    }

    private static func sanitizeValue(_ value: String) -> String {
        String(value.prefix(100))
    }
}
