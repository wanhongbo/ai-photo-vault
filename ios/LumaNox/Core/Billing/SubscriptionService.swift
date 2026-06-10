import Foundation
import RevenueCat

struct PurchaseCancelledError: Error {}
struct PurchaseTimeoutError: Error {}
private struct CatalogTimeoutError: Error {}

/// RevenueCat 订阅仓库 — 对齐 Android [RevenueCatSubscriptionRepository]。
@MainActor
final class SubscriptionService: ObservableObject {
    static let shared = SubscriptionService()

    static let errorCodeRcKeyMissing = "RC_KEY_MISSING"
    static let errorCodeOfferingMissing = "RC_OFFERING_MISSING"
    static let errorCodeOfferingEmpty = "RC_OFFERING_EMPTY"
    static let errorCodeCatalogTimeout = "RC_CATALOG_TIMEOUT"
    static let errorCodeNetworkOffline = "RC_NETWORK_OFFLINE"

    private static let catalogFetchTimeoutSeconds: UInt64 = 8
    private static let purchaseTimeoutSeconds: UInt64 = 45

    @Published private(set) var offeringsState: PaywallOfferingsState = .loading
    @Published private(set) var isRefreshingCatalog = false
    @Published private(set) var isPremium = false

    private var packageCache: [String: Package] = [:]

    private let purchasesDelegate = SubscriptionPurchasesDelegate()

    private init() {
        if BillingBootstrap.isConfigured {
            purchasesDelegate.owner = self
            Purchases.shared.delegate = purchasesDelegate
            Task { await refreshCustomerInfo() }
        }
    }

    var isSdkConfigured: Bool { BillingBootstrap.isConfigured }

    var isNetworkAvailable: Bool { NetworkReachability.shared.isOnline }

    func refreshCatalog() async {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-uiTestPaywallPreview")
            || (ProcessInfo.processInfo.arguments.contains("-uiTestPaywall") && !BillingBootstrap.isConfigured) {
            offeringsState = .ready(
                packages: Self.debugPreviewPackages,
                defaultSelectedIndex: 1,
                isPremium: isPremium
            )
            return
        }
        #endif
        guard BillingBootstrap.isConfigured else {
            offeringsState = .error(Self.errorCodeRcKeyMissing)
            return
        }

        let hasCachedCatalog = if case .ready = offeringsState { true } else { false }
        if hasCachedCatalog {
            isRefreshingCatalog = true
        } else {
            offeringsState = .loading
        }
        defer { isRefreshingCatalog = false }

        guard await NetworkReachability.isReachable() else {
            if !hasCachedCatalog {
                offeringsState = .error(Self.errorCodeNetworkOffline)
            }
            return
        }

        do {
            let offerings = try await Self.fetchOfferingsWithTimeout()
            guard let current = offerings.current ?? offerings.offering(identifier: LumaNoxBillingIds.offeringDefault) else {
                if !hasCachedCatalog {
                    offeringsState = .error(Self.errorCodeOfferingMissing)
                }
                return
            }
            guard !current.availablePackages.isEmpty else {
                if !hasCachedCatalog {
                    offeringsState = .error(Self.errorCodeOfferingEmpty)
                }
                return
            }
            packageCache.removeAll()
            for pkg in current.availablePackages {
                packageCache[pkg.identifier] = pkg
            }
            let sorted = current.availablePackages.sorted { sortOrder($0) < sortOrder($1) }
            let eligibility = await Purchases.shared.checkTrialOrIntroDiscountEligibility(
                productIdentifiers: sorted.map(\.storeProduct.productIdentifier)
            )
            let mapped = sorted.map { pkg in
                mapPackage(
                    pkg,
                    introEligibility: eligibility[pkg.storeProduct.productIdentifier]?.status
                )
            }
            let enriched = applySavingsIfPossible(packages: mapped, current: current)
            let defaultIdx = enriched.firstIndex { $0.kind == .annual } ?? 0
            offeringsState = .ready(
                packages: enriched,
                defaultSelectedIndex: defaultIdx,
                isPremium: isPremium
            )
        } catch {
            if !hasCachedCatalog {
                offeringsState = .error(Self.catalogErrorCode(from: error))
            }
        }
    }

    func restorePurchases() async -> Result<Void, Error> {
        guard BillingBootstrap.isConfigured else {
            return .failure(NSError(domain: "billing", code: 1, userInfo: [NSLocalizedDescriptionKey: "Billing not configured"]))
        }
        guard await NetworkReachability.isReachable() else {
            return .failure(SubscriptionService.offlineError())
        }
        do {
            let info = try await Self.withTimeout(seconds: Self.purchaseTimeoutSeconds) {
                try await Purchases.shared.restorePurchases()
            }
            applyCustomerInfo(info)
            return .success(())
        } catch is PurchaseTimeoutError {
            return .failure(SubscriptionService.timeoutError())
        } catch {
            return .failure(error)
        }
    }

    func purchase(packageIdentifier: String) async -> Result<Void, Error> {
        guard BillingBootstrap.isConfigured else {
            return .failure(NSError(domain: "billing", code: 1, userInfo: [NSLocalizedDescriptionKey: "Billing not configured"]))
        }
        guard await NetworkReachability.isReachable() else {
            return .failure(SubscriptionService.offlineError())
        }
        guard let pkg = packageCache[packageIdentifier] else {
            return .failure(NSError(domain: "billing", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unknown package"]))
        }
        do {
            let result = try await Self.withTimeout(seconds: Self.purchaseTimeoutSeconds) {
                try await Purchases.shared.purchase(package: pkg)
            }
            if result.userCancelled {
                return .failure(PurchaseCancelledError())
            }
            applyCustomerInfo(result.customerInfo)
            return .success(())
        } catch is PurchaseTimeoutError {
            return .failure(SubscriptionService.timeoutError())
        } catch {
            if let code = error as? ErrorCode, code == .purchaseCancelledError {
                return .failure(PurchaseCancelledError())
            }
            return .failure(error)
        }
    }

    private func refreshCustomerInfo() async {
        guard BillingBootstrap.isConfigured else { return }
        do {
            let info = try await Purchases.shared.customerInfo()
            applyCustomerInfo(info)
        } catch {
            // 首次拉取失败可忽略
        }
    }

    private static func offlineError() -> NSError {
        NSError(
            domain: "billing",
            code: 3,
            userInfo: [NSLocalizedDescriptionKey: errorCodeNetworkOffline]
        )
    }

    private static func timeoutError() -> NSError {
        NSError(
            domain: "billing",
            code: 4,
            userInfo: [NSLocalizedDescriptionKey: "PURCHASE_TIMEOUT"]
        )
    }

    private static func withTimeout<T>(
        seconds: UInt64,
        operation: @escaping () async throws -> T
    ) async throws -> T {
        try await withThrowingTaskGroup(of: T.self) { group in
            group.addTask { try await operation() }
            group.addTask {
                try await Task.sleep(nanoseconds: seconds * 1_000_000_000)
                throw PurchaseTimeoutError()
            }
            guard let result = try await group.next() else {
                throw PurchaseTimeoutError()
            }
            group.cancelAll()
            return result
        }
    }

    private func applySavingsIfPossible(packages: [PaywallPackageOffer], current: Offering) -> [PaywallPackageOffer] {
        guard let monthlyPkg = current.availablePackages.first(where: { $0.packageType == .monthly }),
              let annualPkg = current.availablePackages.first(where: { $0.packageType == .annual })
        else { return packages }
        let monthly = NSDecimalNumber(decimal: monthlyPkg.storeProduct.price)
        let annual = NSDecimalNumber(decimal: annualPkg.storeProduct.price)
        guard monthly.doubleValue > 0 else { return packages }
        let yearlyEquiv = monthly.multiplying(by: 12)
        let savings = yearlyEquiv.subtracting(annual)
            .multiplying(by: 100)
            .dividing(by: yearlyEquiv)
            .intValue
        guard savings > 0 else { return packages }
        return packages.map { offer in
            guard offer.kind == .annual else { return offer }
            return PaywallPackageOffer(
                kind: offer.kind,
                packageIdentifier: offer.packageIdentifier,
                title: offer.title,
                description: offer.description,
                pricePrimary: offer.pricePrimary,
                priceSecondary: offer.priceSecondary,
                periodShortLabel: offer.periodShortLabel,
                showBestValueBadge: offer.showBestValueBadge,
                freeTrialLabel: offer.freeTrialLabel,
                savingsPercent: savings
            )
        }
    }

    private func sortOrder(_ pkg: Package) -> Int {
        switch pkg.packageType {
        case .weekly: return -1
        case .monthly: return 0
        case .annual: return 1
        case .sixMonth, .threeMonth, .twoMonth: return 2
        case .lifetime: return 3
        case .custom, .unknown: return 9
        @unknown default: return 9
        }
    }

    private func mapPackage(_ pkg: Package, introEligibility: IntroEligibilityStatus?) -> PaywallPackageOffer {
        let product = pkg.storeProduct
        let trialLabel = freeTrialLabel(for: product, introEligibility: introEligibility)
        return PaywallPackageOffer(
            kind: planKind(pkg.packageType),
            packageIdentifier: pkg.identifier,
            title: product.localizedTitle,
            description: product.localizedDescription,
            pricePrimary: product.localizedPriceString,
            priceSecondary: nil,
            periodShortLabel: nil,
            showBestValueBadge: pkg.packageType == .annual,
            freeTrialLabel: trialLabel,
            savingsPercent: nil
        )
    }

    private func freeTrialLabel(for product: StoreProduct, introEligibility: IntroEligibilityStatus?) -> String? {
        guard introEligibility?.isEligible == true,
              let discount = product.introductoryDiscount,
              discount.paymentMode == .freeTrial
        else { return nil }
        return localizedTrialLabel(for: discount.subscriptionPeriod)
    }

    private func localizedTrialLabel(for period: SubscriptionPeriod) -> String? {
        switch period.unit {
        case .year:
            return L10n.tr("paywall_trial_year", period.value)
        case .month:
            return L10n.tr("paywall_trial_month", period.value)
        case .week:
            return L10n.tr("paywall_trial_week", period.value)
        case .day:
            return L10n.tr("paywall_trial_day", period.value)
        @unknown default:
            return nil
        }
    }

    private static func catalogErrorCode(from error: Error) -> String {
        if error is CatalogTimeoutError {
            return errorCodeCatalogTimeout
        }
        if error is PurchaseTimeoutError {
            return errorCodeCatalogTimeout
        }
        if isNetworkError(error) {
            return errorCodeNetworkOffline
        }
        let message = error.localizedDescription.lowercased()
        if message.contains("network")
            || message.contains("internet")
            || message.contains("offline")
            || message.contains("connection")
            || message.contains("timed out")
            || message.contains("timeout") {
            return errorCodeNetworkOffline
        }
        if message.contains("offering") || message.contains("product") || message.contains("storekit") || message.contains("app store connect") {
            return errorCodeOfferingEmpty
        }
        return error.localizedDescription
    }

    private static func isNetworkError(_ error: Error) -> Bool {
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost, .dataNotAllowed, .cannotFindHost, .cannotConnectToHost, .dnsLookupFailed, .internationalRoamingOff:
                return true
            default:
                return false
            }
        }
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain {
            return [
                NSURLErrorNotConnectedToInternet,
                NSURLErrorNetworkConnectionLost,
                NSURLErrorCannotFindHost,
                NSURLErrorCannotConnectToHost,
                NSURLErrorDNSLookupFailed,
                NSURLErrorDataNotAllowed,
            ].contains(ns.code)
        }
        return false
    }

    private static func fetchOfferingsWithTimeout() async throws -> Offerings {
        try await withTimeout(seconds: catalogFetchTimeoutSeconds) {
            try await Purchases.shared.offerings()
        }
    }

    private func planKind(_ type: PackageType) -> PaywallPlanKind {
        switch type {
        case .monthly: return .monthly
        case .annual, .sixMonth, .threeMonth, .twoMonth: return .annual
        case .lifetime: return .lifetime
        default: return .other
        }
    }

    #if DEBUG
    private static var debugPreviewPackages: [PaywallPackageOffer] {
        [
        PaywallPackageOffer(
            kind: .monthly,
            packageIdentifier: "debug_monthly",
            title: L10n.tr("paywall_preview_monthly"),
            description: L10n.tr("paywall_preview_description"),
            pricePrimary: "$4.99",
            priceSecondary: nil,
            periodShortLabel: nil,
            showBestValueBadge: false,
            freeTrialLabel: L10n.tr("paywall_trial_week", 1),
            savingsPercent: nil
        ),
        PaywallPackageOffer(
            kind: .annual,
            packageIdentifier: "debug_annual",
            title: L10n.tr("paywall_preview_annual"),
            description: L10n.tr("paywall_preview_description"),
            pricePrimary: "$29.99",
            priceSecondary: nil,
            periodShortLabel: nil,
            showBestValueBadge: true,
            freeTrialLabel: L10n.tr("paywall_trial_week", 2),
            savingsPercent: 50
        )
        ]
    }
    #endif

}

/// RevenueCat `PurchasesDelegate` 须基于 `NSObject`。
private final class SubscriptionPurchasesDelegate: NSObject, PurchasesDelegate {
    weak var owner: SubscriptionService?

    func purchases(_ purchases: Purchases, receivedUpdated customerInfo: CustomerInfo) {
        Task { @MainActor in
            owner?.applyCustomerInfo(customerInfo)
        }
    }
}

extension SubscriptionService {
    fileprivate func applyCustomerInfo(_ info: CustomerInfo) {
        let active = info.entitlements[LumaNoxBillingIds.entitlementPremium]?.isActive == true
        isPremium = active
        if case .ready(let packages, let idx, _) = offeringsState {
            offeringsState = .ready(packages: packages, defaultSelectedIndex: idx, isPremium: active)
        }
    }
}
