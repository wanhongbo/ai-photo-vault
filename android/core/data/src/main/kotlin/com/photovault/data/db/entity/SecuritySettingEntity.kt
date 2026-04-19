package com.photovault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_settings")
data class SecuritySettingEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    @ColumnInfo(name = "lock_type") val lockType: String,
    @ColumnInfo(name = "pin_hash_hex") val pinHashHex: String?,
    @ColumnInfo(name = "biometric_enabled") val biometricEnabled: Boolean,
    @ColumnInfo(name = "fail_count") val failCount: Int,
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
