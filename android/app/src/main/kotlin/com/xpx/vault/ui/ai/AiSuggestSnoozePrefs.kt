package com.xpx.vault.ui.ai

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI 建议卡片相关的轻量本地偏好。
 *
 * 两类键：
 *  - "忽略 7 天"：按建议类型（敏感 / 清理）记录到期时间戳，到期前对应卡片不展示；
 *  - "首次扫描完成时间"：用于区分 [AiSuggestion.Idle]（从未扫过）与 [AiSuggestion.AllClear]（扫过、无事）。
 *
 * 通过 [versionFlow] 对外暴露写事件，让订阅方（ViewModel 的 combine）在用户操作后重新派生状态。
 */
@Singleton
class AiSuggestSnoozePrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    enum class Kind { SENSITIVE, CLEANUP }

    /** 每次写操作自增，供 Flow 消费者感知状态变化（SharedPreferences 本身不是 Flow）。 */
    private val _version = MutableStateFlow(0L)
    val versionFlow: StateFlow<Long> = _version.asStateFlow()

    fun isSnoozed(kind: Kind, nowMs: Long = System.currentTimeMillis()): Boolean {
        val until = prefs.getLong(keyOf(kind), 0L)
        return nowMs < until
    }

    fun snooze(kind: Kind, durationMs: Long = DEFAULT_SNOOZE_MS) {
        prefs.edit().putLong(keyOf(kind), System.currentTimeMillis() + durationMs).apply()
        bumpVersion()
    }

    /**
     * 清除所有类型的忽略到期时间。用户主动触发“重新扫描”时调用：
     * 重扫是用户明确要求重新评估的信号，之前的 snooze 应被 revoke，
     * 否则重扫出的真实结果会被旧 snooze 隔断，却给用户呈现一切良好的假象。
     */
    fun clearAllSnoozes() {
        prefs.edit()
            .remove(KEY_SENSITIVE_UNTIL_MS)
            .remove(KEY_CLEANUP_UNTIL_MS)
            .apply()
        bumpVersion()
    }

    /** 是否完成过至少一次扫描。用于区分 Idle / AllClear。 */
    fun hasEverScanned(): Boolean = prefs.contains(KEY_LAST_SCAN_END_MS)

    fun markScanCompleted() {
        prefs.edit().putLong(KEY_LAST_SCAN_END_MS, System.currentTimeMillis()).apply()
        bumpVersion()
    }

    private fun bumpVersion() {
        _version.value = _version.value + 1
    }

    private fun keyOf(kind: Kind): String = when (kind) {
        Kind.SENSITIVE -> KEY_SENSITIVE_UNTIL_MS
        Kind.CLEANUP -> KEY_CLEANUP_UNTIL_MS
    }

    companion object {
        private const val FILE_NAME = "ai_suggest_prefs"
        private const val KEY_SENSITIVE_UNTIL_MS = "sensitive_snooze_until_ms"
        private const val KEY_CLEANUP_UNTIL_MS = "cleanup_snooze_until_ms"
        private const val KEY_LAST_SCAN_END_MS = "last_scan_end_ms"
        private const val DEFAULT_SNOOZE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
