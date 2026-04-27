package com.xpx.vault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_state")
data class SubscriptionStateEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    @ColumnInfo(name = "plan") val plan: String,
    @ColumnInfo(name = "expire_at_ms") val expireAtEpochMs: Long?,
    @ColumnInfo(name = "entitlements_json") val entitlementsJson: String?,
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
