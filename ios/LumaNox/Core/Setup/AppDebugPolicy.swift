enum AppDebugPolicy {
    static var skipsPinGate: Bool {
        #if DEBUG && targetEnvironment(simulator)
        true
        #else
        false
        #endif
    }
}
