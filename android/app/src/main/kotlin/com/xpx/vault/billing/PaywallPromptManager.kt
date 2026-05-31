package com.xpx.vault.billing

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaywallPromptManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldPromptAfterVaultImport(currentVaultCount: Int, isPremium: Boolean): SoftPaywallReason? =
        when {
            isPremium -> null
            currentVaultCount >= VAULT_NEAR_LIMIT_ITEMS -> SoftPaywallReason.VAULT_NEAR_LIMIT
            currentVaultCount >= VAULT_VALUE_MIN_ITEMS -> SoftPaywallReason.VAULT_IMPORT_VALUE
            else -> null
        }?.takeIf { shouldShow(it, isPremium) }

    fun shouldPromptBeforeAiFeature(aiMonthlyCount: Int, isPremium: Boolean): SoftPaywallReason? =
        SoftPaywallReason.AI_NEAR_LIMIT.takeIf {
            !isPremium && aiMonthlyCount >= AI_NEAR_LIMIT_COUNT && shouldShow(it, isPremium)
        }

    fun shouldPromptAfterBackupSuccess(isPremium: Boolean): SoftPaywallReason? =
        SoftPaywallReason.BACKUP_SUCCESS.takeIf { shouldShow(it, isPremium) }

    fun markShown(reason: SoftPaywallReason) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(reason.seenKey, true)
            .putLong(KEY_LAST_SHOWN_AT_MS, now)
            .putInt(KEY_TOTAL_SHOW_COUNT, prefs.getInt(KEY_TOTAL_SHOW_COUNT, 0) + 1)
            .apply()
    }

    private fun shouldShow(reason: SoftPaywallReason, isPremium: Boolean): Boolean {
        if (isPremium) return false
        if (prefs.getBoolean(reason.seenKey, false)) return false
        if (prefs.getInt(KEY_TOTAL_SHOW_COUNT, 0) >= MAX_LIFETIME_SOFT_PROMPTS) return false
        val lastShownAt = prefs.getLong(KEY_LAST_SHOWN_AT_MS, 0L)
        val elapsed = System.currentTimeMillis() - lastShownAt
        return lastShownAt == 0L || elapsed >= SOFT_PROMPT_COOLDOWN_MS
    }

    companion object {
        private const val PREFS_NAME = "paywall_prompt_prefs"
        private const val KEY_LAST_SHOWN_AT_MS = "last_shown_at_ms"
        private const val KEY_TOTAL_SHOW_COUNT = "total_show_count"
        private const val MAX_LIFETIME_SOFT_PROMPTS = 2
        private const val SOFT_PROMPT_COOLDOWN_MS = 7L * 24L * 60L * 60L * 1000L
        private const val VAULT_VALUE_MIN_ITEMS = 5
        private const val VAULT_NEAR_LIMIT_ITEMS = 40
        private const val AI_NEAR_LIMIT_COUNT = 7
    }
}

enum class SoftPaywallReason(
    val source: String,
    internal val seenKey: String,
) {
    VAULT_IMPORT_VALUE("import_value", "seen_import_value"),
    VAULT_NEAR_LIMIT("vault_near_limit", "seen_vault_near_limit"),
    BACKUP_SUCCESS("backup_success", "seen_backup_success"),
    AI_NEAR_LIMIT("ai_near_limit", "seen_ai_near_limit"),
}
