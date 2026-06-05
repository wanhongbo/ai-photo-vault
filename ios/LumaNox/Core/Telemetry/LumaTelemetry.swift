import Foundation
import os

enum LumaTelemetry {
    private static let log = Logger(subsystem: "com.xpx.vault", category: "Telemetry")

    static func logEvent(_ name: String, parameters: [String: String] = [:]) {
        let cleaned = parameters.mapValues(sanitizeValue)
        log.debug("\(name, privacy: .public) \(cleaned.description, privacy: .public)")
        FirebaseTelemetry.logEvent(name, parameters: cleaned)
    }

    static func breadcrumb(_ tag: String, _ message: String) {
        FirebaseTelemetry.breadcrumb(tag, sanitizeValue(message))
    }

    static func trackAppStart(version: String, build: String) {
        logEvent("app_start", parameters: [
            "version_name": version,
            "version_code": build,
            "build_type": isDebugBuild ? "debug" : "release",
        ])
    }

    static func trackLock(event: String, method: String, result: String) {
        logEvent("lock_\(event)", parameters: ["method": method, "result": result])
    }

    static func trackVaultImport(source: String, mediaType: String, result: String) {
        logEvent("vault_import", parameters: ["source": source, "media_type": mediaType, "result": result])
    }

    static func trackVaultItem(action: String, result: String) {
        logEvent("vault_item", parameters: ["action": action, "result": result])
    }

    static func trackCameraCapture(mediaType: String, result: String) {
        logEvent("camera_capture", parameters: ["media_type": mediaType, "result": result])
    }

    static func trackBackup(trigger: String, kind: String, result: String, assetCount: Int) {
        logEvent("backup_create", parameters: [
            "trigger": trigger.lowercased(),
            "kind": kind.lowercased(),
            "result": result,
            "asset_count": String(max(0, assetCount)),
        ])
    }

    static func trackRestore(source: String, result: String, restored: Int = 0, skipped: Int = 0, failed: Int = 0) {
        logEvent("backup_restore", parameters: [
            "source": source,
            "result": result,
            "restored": String(max(0, restored)),
            "skipped": String(max(0, skipped)),
            "failed": String(max(0, failed)),
        ])
    }

    static func trackAiScan(result: String, total: Int, processed: Int) {
        logEvent("ai_scan", parameters: [
            "result": result,
            "total": String(max(0, total)),
            "processed": String(max(0, processed)),
        ])
    }

    private static var isDebugBuild: Bool {
        #if DEBUG
        true
        #else
        false
        #endif
    }

    private static func sanitizeValue(_ value: String) -> String {
        String(value.replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
            .prefix(100))
    }
}
