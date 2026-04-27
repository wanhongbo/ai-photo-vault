package com.xpx.vault.domain.model

/**
 * 订阅与权益缓存（一期以 RevenueCat 为准，本地仅作展示/离线体验）。
 */
data class SubscriptionState(
    val id: Long,
    val plan: String,
    val expireAtEpochMs: Long?,
    val entitlementsJson: String?,
)
