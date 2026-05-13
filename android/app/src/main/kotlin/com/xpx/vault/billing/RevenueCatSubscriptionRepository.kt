package com.xpx.vault.billing

import androidx.fragment.app.FragmentActivity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import com.xpx.vault.domain.billing.PaywallOfferingsState
import com.xpx.vault.domain.billing.PaywallPackageOffer
import com.xpx.vault.domain.billing.PaywallPlanKind
import com.xpx.vault.domain.billing.PurchaseActivityHost
import com.xpx.vault.domain.repo.SubscriptionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Singleton
class RevenueCatSubscriptionRepository @Inject constructor() : SubscriptionRepository {

    companion object {
        /** 与 UI 层 [stringResource] 映射，表示未配置 RevenueCat Key。 */
        const val ERROR_CODE_RC_KEY_MISSING: String = "RC_KEY_MISSING"
    }

    private val _offeringsState = MutableStateFlow<PaywallOfferingsState>(PaywallOfferingsState.Loading)
    override val offeringsState: StateFlow<PaywallOfferingsState> = _offeringsState.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val packageCache = LinkedHashMap<String, Package>()

    init {
        if (BillingBootstrap.isConfigured) {
            Purchases.sharedInstance.updatedCustomerInfoListener =
                UpdatedCustomerInfoListener { info -> applyCustomerInfo(info) }
            // 冷启动拉一次权益，避免仅依赖缓存时 UI 空白过久
            Purchases.sharedInstance.getCustomerInfo(
                object : ReceiveCustomerInfoCallback {
                    override fun onReceived(customerInfo: CustomerInfo) {
                        applyCustomerInfo(customerInfo)
                    }

                    override fun onError(error: PurchasesError) {
                        // 忽略首次拉取失败，等待 refreshCatalog
                    }
                },
            )
        }
    }

    override fun isSdkConfigured(): Boolean = BillingBootstrap.isConfigured

    override suspend fun refreshCatalog() {
        if (!BillingBootstrap.isConfigured) {
            _offeringsState.value = PaywallOfferingsState.Error(ERROR_CODE_RC_KEY_MISSING)
            return
        }
        _offeringsState.value = PaywallOfferingsState.Loading
        withContext(Dispatchers.IO) {
            try {
                val offerings = Purchases.sharedInstance.awaitOfferings()
                val current = offerings.current
                if (current == null) {
                    _offeringsState.value =
                        PaywallOfferingsState.Error("RevenueCat offering `current` is null. Set a current offering in Dashboard.")
                    return@withContext
                }
                val mapped = current.availablePackages
                    .sortedBy { sortOrder(it) }
                    .map { it.toPaywallOffer() }
                // 计算年订相对于月订的节省百分比
                val monthlyPriceMicros = current.availablePackages
                    .firstOrNull { it.packageType == PackageType.MONTHLY }
                    ?.product?.price?.amountMicros
                val enriched = mapped.map { offer ->
                    if (offer.kind == PaywallPlanKind.ANNUAL && monthlyPriceMicros != null && monthlyPriceMicros > 0) {
                        val annualPkg = current.availablePackages.firstOrNull { it.packageType == PackageType.ANNUAL }
                        val annualMicros = annualPkg?.product?.price?.amountMicros ?: 0
                        val yearlyEquiv = monthlyPriceMicros * 12
                        val savings = ((yearlyEquiv - annualMicros) * 100 / yearlyEquiv).toInt()
                        if (savings > 0) offer.copy(savingsPercent = savings) else offer
                    } else offer
                }
                packageCache.clear()
                current.availablePackages.forEach { pkg ->
                    packageCache[pkg.identifier] = pkg
                }
                val defaultIdx = enriched.indexOfFirst { it.kind == PaywallPlanKind.ANNUAL }
                    .let { if (it >= 0) it else 0 }
                _offeringsState.value = PaywallOfferingsState.Ready(
                    packages = enriched,
                    defaultSelectedIndex = defaultIdx,
                    isPremium = _isPremium.value,
                )
            } catch (e: OfferingsFetchException) {
                _offeringsState.value = PaywallOfferingsState.Error(e.error.message)
            } catch (e: Exception) {
                _offeringsState.value = PaywallOfferingsState.Error(e.message)
            }
        }
    }

    override suspend fun restorePurchases(): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            if (!BillingBootstrap.isConfigured) {
                cont.resume(Result.failure(IllegalStateException("Billing not configured")))
                return@suspendCancellableCoroutine
            }
            Purchases.sharedInstance.restorePurchases(
                object : ReceiveCustomerInfoCallback {
                    override fun onReceived(customerInfo: CustomerInfo) {
                        applyCustomerInfo(customerInfo)
                        cont.resume(Result.success(Unit))
                    }

                    override fun onError(error: PurchasesError) {
                        cont.resume(Result.failure(Exception(error.message)))
                    }
                },
            )
        }

    override suspend fun purchase(
        packageIdentifier: String,
        host: PurchaseActivityHost,
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        if (!BillingBootstrap.isConfigured) {
            cont.resume(Result.failure(IllegalStateException("Billing not configured")))
            return@suspendCancellableCoroutine
        }
        val activity = host.obtain() as? FragmentActivity
        if (activity == null) {
            cont.resume(Result.failure(IllegalStateException("Invalid activity")))
            return@suspendCancellableCoroutine
        }
        val pkg = packageCache[packageIdentifier]
        if (pkg == null) {
            cont.resume(Result.failure(IllegalStateException("Unknown package $packageIdentifier")))
            return@suspendCancellableCoroutine
        }
        Purchases.sharedInstance.purchase(
            PurchaseParams.Builder(activity, pkg).build(),
            object : PurchaseCallback {
                override fun onCompleted(
                    storeTransaction: StoreTransaction,
                    customerInfo: CustomerInfo,
                ) {
                    applyCustomerInfo(customerInfo)
                    cont.resume(Result.success(Unit))
                }

                override fun onError(
                    error: PurchasesError,
                    userCancelled: Boolean,
                ) {
                    if (userCancelled) {
                        cont.resume(Result.failure(PurchaseCancelledException()))
                    } else {
                        cont.resume(Result.failure(Exception(error.message)))
                    }
                }
            },
        )
    }

    private fun applyCustomerInfo(info: CustomerInfo) {
        val active = info.entitlements[LumaVaultBillingIds.ENTITLEMENT_PREMIUM]?.isActive == true
        _isPremium.value = active
        val cur = _offeringsState.value
        if (cur is PaywallOfferingsState.Ready) {
            _offeringsState.value = cur.copy(isPremium = active)
        }
    }

    private fun sortOrder(pkg: Package): Int =
        when (pkg.packageType) {
            PackageType.WEEKLY -> -1
            PackageType.MONTHLY -> 0
            PackageType.ANNUAL -> 1
            PackageType.SIX_MONTH,
            PackageType.THREE_MONTH,
            PackageType.TWO_MONTH,
            -> 2
            PackageType.LIFETIME -> 3
            else -> 9
        }

    private fun Package.toPlanKind(): PaywallPlanKind =
        when (packageType) {
            PackageType.MONTHLY -> PaywallPlanKind.MONTHLY
            PackageType.ANNUAL,
            PackageType.SIX_MONTH,
            PackageType.THREE_MONTH,
            PackageType.TWO_MONTH,
            -> PaywallPlanKind.ANNUAL
            PackageType.LIFETIME -> PaywallPlanKind.LIFETIME
            else -> PaywallPlanKind.OTHER
        }

    private fun Package.toPaywallOffer(): PaywallPackageOffer {
        val sp = product
        val trialLabel = sp.subscriptionOptions
            ?.freeTrial
            ?.freePhase
            ?.billingPeriod
            ?.let { period ->
                when (period.unit) {
                    com.revenuecat.purchases.models.Period.Unit.YEAR -> "${period.value} 年免费试用"
                    com.revenuecat.purchases.models.Period.Unit.MONTH -> "${period.value} 个月免费试用"
                    com.revenuecat.purchases.models.Period.Unit.WEEK -> "${period.value} 周免费试用"
                    com.revenuecat.purchases.models.Period.Unit.DAY -> "${period.value} 天免费试用"
                    com.revenuecat.purchases.models.Period.Unit.UNKNOWN -> null
                }
            }
        return PaywallPackageOffer(
            kind = toPlanKind(),
            packageIdentifier = identifier,
            title = sp.name,
            description = sp.description,
            pricePrimary = sp.price.formatted,
            priceSecondary = null,
            periodShortLabel = null,
            showBestValueBadge = packageType == PackageType.ANNUAL,
            freeTrialLabel = trialLabel,
        )
    }
}

/** 用户在系统结算弹窗中取消购买。 */
class PurchaseCancelledException : Exception("cancelled")
