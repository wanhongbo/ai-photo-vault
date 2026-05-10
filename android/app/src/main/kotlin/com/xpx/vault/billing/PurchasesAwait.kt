package com.xpx.vault.billing

import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class OfferingsFetchException(val error: PurchasesError) : Exception(error.message)

internal suspend fun Purchases.awaitOfferings(): Offerings =
    suspendCancellableCoroutine { cont ->
        getOfferings(
            object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) {
                    cont.resume(offerings)
                }

                override fun onError(error: PurchasesError) {
                    cont.resumeWithException(OfferingsFetchException(error))
                }
            },
        )
    }
