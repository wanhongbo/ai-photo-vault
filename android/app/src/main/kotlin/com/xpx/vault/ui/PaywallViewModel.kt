package com.xpx.vault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.billing.PurchaseCancelledException
import com.xpx.vault.domain.billing.PurchaseActivityHost
import com.xpx.vault.domain.repo.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    val offeringsState = subscriptionRepository.offeringsState
    val isPremium = subscriptionRepository.isPremium

    private val _purchasing = MutableStateFlow(false)
    val purchasing: StateFlow<Boolean> = _purchasing.asStateFlow()

    private val _surfaceError = MutableStateFlow<String?>(null)
    val surfaceError: StateFlow<String?> = _surfaceError.asStateFlow()

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
            try {
                subscriptionRepository.purchase(packageIdentifier, host).onFailure { e ->
                    if (e !is PurchaseCancelledException) {
                        _surfaceError.value = e.message
                    }
                }
            } finally {
                _purchasing.value = false
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _purchasing.value = true
            _surfaceError.value = null
            subscriptionRepository.restorePurchases().onFailure {
                _surfaceError.value = it.message
            }
            _purchasing.value = false
        }
    }
}
