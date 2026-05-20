import Foundation
import RevenueCat

struct PurchaseCancelledError: Error {}

/// RevenueCat 订阅仓库 — 对齐 Android [RevenueCatSubscriptionRepository]。
@MainActor
final class SubscriptionService: ObservableObject {
    static let shared = SubscriptionService()

    static let errorCodeRcKeyMissing = "RC_KEY_MISSING"

    @Published private(set) var offeringsState: PaywallOfferingsState = .loading
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

    func refreshCatalog() async {
        guard BillingBootstrap.isConfigured else {
            offeringsState = .error(Self.errorCodeRcKeyMissing)
            return
        }
        offeringsState = .loading
        do {
            let offerings = try await Purchases.shared.offerings()
            guard let current = offerings.current else {
                offeringsState = .error(
                    "RevenueCat offering `current` is null. Set a current offering in Dashboard."
                )
                return
            }
            packageCache.removeAll()
            for pkg in current.availablePackages {
                packageCache[pkg.identifier] = pkg
            }
            let sorted = current.availablePackages.sorted { sortOrder($0) < sortOrder($1) }
            let mapped = sorted.map { mapPackage($0) }
            let enriched = applySavingsIfPossible(packages: mapped, current: current)
            let defaultIdx = enriched.firstIndex { $0.kind == .annual } ?? 0
            offeringsState = .ready(
                packages: enriched,
                defaultSelectedIndex: defaultIdx,
                isPremium: isPremium
            )
        } catch {
            offeringsState = .error(error.localizedDescription)
        }
    }

    func restorePurchases() async -> Result<Void, Error> {
        guard BillingBootstrap.isConfigured else {
            return .failure(NSError(domain: "billing", code: 1, userInfo: [NSLocalizedDescriptionKey: "Billing not configured"]))
        }
        do {
            let info = try await Purchases.shared.restorePurchases()
            applyCustomerInfo(info)
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    func purchase(packageIdentifier: String) async -> Result<Void, Error> {
        guard BillingBootstrap.isConfigured else {
            return .failure(NSError(domain: "billing", code: 1, userInfo: [NSLocalizedDescriptionKey: "Billing not configured"]))
        }
        guard let pkg = packageCache[packageIdentifier] else {
            return .failure(NSError(domain: "billing", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unknown package"]))
        }
        do {
            let result = try await Purchases.shared.purchase(package: pkg)
            if result.userCancelled {
                return .failure(PurchaseCancelledError())
            }
            applyCustomerInfo(result.customerInfo)
            return .success(())
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

    private func mapPackage(_ pkg: Package) -> PaywallPackageOffer {
        let product = pkg.storeProduct
        let trialLabel: String? = nil
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

    private func planKind(_ type: PackageType) -> PaywallPlanKind {
        switch type {
        case .monthly: return .monthly
        case .annual, .sixMonth, .threeMonth, .twoMonth: return .annual
        case .lifetime: return .lifetime
        default: return .other
        }
    }

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
