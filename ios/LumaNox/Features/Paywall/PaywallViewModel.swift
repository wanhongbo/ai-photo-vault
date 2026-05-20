import Foundation

@MainActor
final class PaywallViewModel: ObservableObject {
    @Published private(set) var offeringsState: PaywallOfferingsState = .loading
    @Published private(set) var isPremium = false
    @Published var selectedIndex = 0
    @Published var purchasing = false
    @Published var surfaceError: String?
    @Published var purchaseToast: String?
    @Published var shouldDismissAfterSuccess = false

    let source: String

    private let subscription = SubscriptionService.shared

    init(source: String) {
        self.source = source
    }

    func onAppear() {
        syncFromService()
        Task { await refresh() }
    }

    func refresh() async {
        surfaceError = nil
        await subscription.refreshCatalog()
        syncFromService()
    }

    func purchaseSelected() async {
        guard case .ready(let packages, _, _) = offeringsState else { return }
        let idx = min(max(selectedIndex, 0), max(packages.count - 1, 0))
        let pkg = packages[idx]
        purchasing = true
        surfaceError = nil
        PaywallAnalytics.trackPurchaseStart(packageId: pkg.packageIdentifier, source: source)
        defer { purchasing = false }
        let result = await subscription.purchase(packageIdentifier: pkg.packageIdentifier)
        switch result {
        case .success:
            PaywallAnalytics.trackPurchaseSuccess(packageId: pkg.packageIdentifier)
            isPremium = subscription.isPremium
            purchaseToast = L10n.tr("paywall_purchase_success")
            shouldDismissAfterSuccess = true
        case .failure(let error):
            if error is PurchaseCancelledError {
                PaywallAnalytics.trackPurchaseCancel()
            } else {
                PaywallAnalytics.trackPurchaseFail(error.localizedDescription)
                surfaceError = error.localizedDescription
            }
        }
    }

    func restore() async {
        purchasing = true
        surfaceError = nil
        defer { purchasing = false }
        let result = await subscription.restorePurchases()
        switch result {
        case .success:
            isPremium = subscription.isPremium
            PaywallAnalytics.trackRestore(success: isPremium)
            if isPremium {
                purchaseToast = L10n.tr("paywall_restore_success")
                shouldDismissAfterSuccess = true
            } else {
                purchaseToast = L10n.tr("paywall_restore_no_purchase")
            }
        case .failure(let error):
            PaywallAnalytics.trackRestore(success: false)
            surfaceError = error.localizedDescription
        }
    }

    private func syncFromService() {
        offeringsState = subscription.offeringsState
        isPremium = subscription.isPremium
        if case .ready(_, let defaultIdx, _) = offeringsState, selectedIndex == 0 {
            selectedIndex = defaultIdx
        }
    }
}
