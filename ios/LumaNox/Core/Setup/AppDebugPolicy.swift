import Foundation

enum AppDebugPolicy {
    static var skipsPinGate: Bool {
        #if DEBUG && targetEnvironment(simulator)
        ProcessInfo.processInfo.environment["LUMANOX_DEBUG_SKIP_PIN"] == "1"
        #else
        false
        #endif
    }
}
