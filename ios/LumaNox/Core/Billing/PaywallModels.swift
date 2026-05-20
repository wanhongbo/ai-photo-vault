import Foundation

enum PaywallPlanKind: String, Equatable {
    case monthly
    case annual
    case lifetime
    case other
}

struct PaywallPackageOffer: Identifiable, Equatable {
    var id: String { packageIdentifier }
    let kind: PaywallPlanKind
    let packageIdentifier: String
    let title: String
    let description: String
    let pricePrimary: String
    let priceSecondary: String?
    let periodShortLabel: String?
    let showBestValueBadge: Bool
    let freeTrialLabel: String?
    let savingsPercent: Int?
}

enum PaywallOfferingsState: Equatable {
    case loading
    case ready(packages: [PaywallPackageOffer], defaultSelectedIndex: Int, isPremium: Bool)
    case error(String?)

    static func == (lhs: PaywallOfferingsState, rhs: PaywallOfferingsState) -> Bool {
        switch (lhs, rhs) {
        case (.loading, .loading): return true
        case let (.ready(lp, li, lprem), .ready(rp, ri, rprem)):
            return lp == rp && li == ri && lprem == rprem
        case let (.error(le), .error(re)): return le == re
        default: return false
        }
    }
}

enum PurchaseResult: Equatable {
    case success
    case cancelled
    case failed(String?)
    case restoreSuccess(isPremium: Bool)
    case restoreFailed(String?)
}
