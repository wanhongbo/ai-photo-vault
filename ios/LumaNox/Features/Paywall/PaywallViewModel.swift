import Combine
import Foundation

@MainActor
final class PaywallViewModel: ObservableObject {
    @Published private(set) var offeringsState: PaywallOfferingsState = .loading
    @Published private(set) var isPremium = false
    @Published private(set) var isRefreshingCatalog = false
    @Published private(set) var isNetworkAvailable = true
    @Published var selectedIndex = 0
    @Published var purchasing = false
    @Published var surfaceError: String?
    @Published var purchaseToast: String?
    @Published var shouldDismissAfterSuccess = false

    let source: String

    private let subscription = SubscriptionService.shared
    private var cancellables = Set<AnyCancellable>()

    init(source: String) {
        self.source = source
        subscription.$offeringsState
            .sink { [weak self] state in
                guard let self else { return }
                self.offeringsState = state
                if case .ready(_, let defaultIdx, _) = state, self.selectedIndex == 0 {
                    self.selectedIndex = defaultIdx
                }
            }
            .store(in: &cancellables)
        subscription.$isPremium
            .sink { [weak self] in self?.isPremium = $0 }
            .store(in: &cancellables)
        subscription.$isRefreshingCatalog
            .sink { [weak self] in self?.isRefreshingCatalog = $0 }
            .store(in: &cancellables)
        NetworkReachability.shared.$isOnline
            .sink { [weak self] in self?.isNetworkAvailable = $0 }
            .store(in: &cancellables)
        syncFromService()
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
        guard isNetworkAvailable else {
            surfaceError = L10n.tr("paywall_error_offline")
            return
        }
        guard case .ready(let packages, _, _) = offeringsState else { return }
        guard !packages.isEmpty else { return }
        let idx = min(max(selectedIndex, 0), max(packages.count - 1, 0))
        let pkg = packages[idx]
        purchasing = true
        surfaceError = nil
        PaywallAnalytics.trackPurchaseStart(packageId: pkg.packageIdentifier, source: source)
        defer { purchasing = false }
        let lockToken = AppLockManager.shared.beginSystemInteraction(timeout: 60)
        let result = await subscription.purchase(packageIdentifier: pkg.packageIdentifier)
        AppLockManager.shared.endSystemInteraction(lockToken)
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
                surfaceError = displayPurchaseError(error)
            }
        }
    }

    func restore() async {
        guard isNetworkAvailable else {
            surfaceError = L10n.tr("paywall_error_offline")
            return
        }
        purchasing = true
        surfaceError = nil
        defer { purchasing = false }
        let lockToken = AppLockManager.shared.beginSystemInteraction(timeout: 60)
        let result = await subscription.restorePurchases()
        AppLockManager.shared.endSystemInteraction(lockToken)
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
            surfaceError = displayPurchaseError(error)
        }
    }

    private func syncFromService() {
        offeringsState = subscription.offeringsState
        isPremium = subscription.isPremium
        isRefreshingCatalog = subscription.isRefreshingCatalog
        isNetworkAvailable = subscription.isNetworkAvailable
        if case .ready(_, let defaultIdx, _) = offeringsState, selectedIndex == 0 {
            selectedIndex = defaultIdx
        }
    }

    private func displayPurchaseError(_ error: Error) -> String {
        let message = error.localizedDescription
        switch message {
        case SubscriptionService.errorCodeNetworkOffline:
            return L10n.tr("paywall_error_offline")
        case "PURCHASE_TIMEOUT":
            return L10n.tr("paywall_purchase_timeout")
        default:
            return message
        }
    }
}
