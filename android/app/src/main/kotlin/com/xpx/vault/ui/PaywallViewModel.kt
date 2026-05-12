package com.xpx.vault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.billing.PaywallAnalytics
import com.xpx.vault.billing.PurchaseCancelledException
import com.xpx.vault.domain.billing.PurchaseActivityHost
import com.xpx.vault.domain.repo.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 购买结果事件，UI 层据此做一次性反馈（Toast / 自动关闭）。
 */
sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Failed(val message: String?) : PurchaseResult
    data class RestoreSuccess(val isPremium: Boolean) : PurchaseResult
    data class RestoreFailed(val message: String?) : PurchaseResult
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val analytics: PaywallAnalytics,
) : ViewModel() {

    val offeringsState = subscriptionRepository.offeringsState
    val isPremium = subscriptionRepository.isPremium

    private val _purchasing = MutableStateFlow(false)
    val purchasing: StateFlow<Boolean> = _purchasing.asStateFlow()

    private val _surfaceError = MutableStateFlow<String?>(null)
    val surfaceError: StateFlow<String?> = _surfaceError.asStateFlow()

    /** 一次性购买结果事件流，UI 用 collectAsEffect 消费。 */
    private val _purchaseResult = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 1)
    val purchaseResult: SharedFlow<PurchaseResult> = _purchaseResult.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        _surfaceError.value = null
        viewModelScope.launch {
            subscriptionRepository.refreshCatalog()
        }
    }

    fun clearError() {
        _surfaceError.value = null
    }

    fun purchase(host: PurchaseActivityHost, packageIdentifier: String) {
        viewModelScope.launch {
            _purchasing.value = true
            _surfaceError.value = null
            analytics.trackPurchaseStart(packageIdentifier, "paywall")
            try {
                val result = subscriptionRepository.purchase(packageIdentifier, host)
                result.onSuccess {
                    analytics.trackPurchaseSuccess(packageIdentifier)
                    _purchaseResult.tryEmit(PurchaseResult.Success)
                }.onFailure { e ->
                    if (e is PurchaseCancelledException) {
                        analytics.trackPurchaseCancel()
                        _purchaseResult.tryEmit(PurchaseResult.Cancelled)
                    } else {
                        analytics.trackPurchaseFail(e.message)
                        _surfaceError.value = e.message
                        _purchaseResult.tryEmit(PurchaseResult.Failed(e.message))
                    }
                }
            } catch (e: Exception) {
                analytics.trackPurchaseFail(e.message)
                _surfaceError.value = e.message
                _purchaseResult.tryEmit(PurchaseResult.Failed(e.message))
            } finally {
                _purchasing.value = false
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _purchasing.value = true
            _surfaceError.value = null
            try {
                val result = subscriptionRepository.restorePurchases()
                result.onSuccess {
                    val premium = subscriptionRepository.isPremium.value
                    analytics.trackRestore(success = true)
                    _purchaseResult.tryEmit(PurchaseResult.RestoreSuccess(isPremium = premium))
                }.onFailure {
                    analytics.trackRestore(success = false)
                    _surfaceError.value = it.message
                    _purchaseResult.tryEmit(PurchaseResult.RestoreFailed(it.message))
                }
            } catch (e: Exception) {
                analytics.trackRestore(success = false)
                _surfaceError.value = e.message
                _purchaseResult.tryEmit(PurchaseResult.RestoreFailed(e.message))
            } finally {
                _purchasing.value = false
            }
        }
    }
}
