import Foundation

extension AppRouter {
    /// 门控检查；若需付费墙则 present 并返回 `false`。
    @discardableResult
    func guardProFeature(_ feature: ProFeature) -> Bool {
        #if DEBUG
        return true
        #endif
        let gate = PaywallGatekeeper.shared.checkAccess(feature)
        switch gate {
        case .allowed:
            return true
        case .softWall:
            present(.paywall(dismissable: true, source: PaywallSource.forGate(gate)))
            return false
        case .hardWall:
            present(.paywall(dismissable: false, source: PaywallSource.forGate(gate)))
            return false
        }
    }
}
