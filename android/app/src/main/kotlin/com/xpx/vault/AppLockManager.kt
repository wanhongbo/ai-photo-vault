package com.xpx.vault

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.xpx.vault.data.db.PhotoVaultDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用级锁屏策略：
 * - 冷启动默认需要解锁（_requireUnlock 初始 true）；若本机尚未配置 PIN，异步读库后会置为无需解锁。
 * - 进入后台(onStop)不立即上锁，只记录时间戳；只有再次回前台(onStart)且后台时长超过阈值才上锁。
 *   这样 SAF/系统相册/相机/分享 等短时间外部 Activity 回来不会弹 PIN。
 * - 未设置 PIN 时，不因后台超时触发锁屏（无 PIN 可验证）。
 * - 解锁成功调用 [onUnlockSucceeded] 清空状态。
 */
@Singleton
class AppLockManager @Inject constructor(
    private val db: PhotoVaultDatabase,
) : DefaultLifecycleObserver {

    private val _requireUnlock = MutableStateFlow(true)
    val requireUnlock: StateFlow<Boolean> = _requireUnlock.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var started = false

    /**
     * null：尚未完成首次读库；true：已配置 PIN，适用后台锁；false：未配置 PIN，不弹应用锁。
     */
    @Volatile
    private var pinConfigured: Boolean? = null

    /** 最近一次 onStop 的时间戳（ms）。0 表示"当前并未在后台"。 */
    @Volatile
    private var lastStopAtMs: Long = 0L

    /** 后台超时阈值：小于该值的后台切换不触发锁屏。可在后续接入设置项改为可配置。 */
    private val backgroundTimeoutMs: Long = DEFAULT_BACKGROUND_TIMEOUT_MS

    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appScope.launch {
            val hasPin = withContext(Dispatchers.IO) {
                db.securitySettingDao().getById() != null
            }
            pinConfigured = hasPin
            if (!hasPin) {
                _requireUnlock.value = false
            }
        }
    }

    fun onUnlockSucceeded() {
        _requireUnlock.value = false
        lastStopAtMs = 0L
    }

    /** 与数据库同步：是否已配置 PIN（用于后台锁与导航）。 */
    fun refreshPinConfiguredFromDb() {
        appScope.launch {
            val hasPin = withContext(Dispatchers.IO) {
                db.securitySettingDao().getById() != null
            }
            pinConfigured = hasPin
            if (!hasPin) {
                _requireUnlock.value = false
            }
        }
    }

    /** 在写入/清除 PIN 后立即更新内存态，避免等下一次读库才生效。 */
    fun setPinConfigured(configured: Boolean) {
        pinConfigured = configured
        if (!configured) {
            _requireUnlock.value = false
        }
    }

    /**
     * 主动要求下次回前台时上锁（如修改 PIN 成功后想强制重新验证）。
     * 目前未使用，作为公开 API 保留。
     */
    fun forceLockNow() {
        if (pinConfigured != true) return
        _requireUnlock.value = true
        lastStopAtMs = 0L
    }

    override fun onStop(owner: LifecycleOwner) {
        // 只记录时间戳，避免短暂切换(SAF/相机/选择器/分享)回来立刻弹 PIN。
        lastStopAtMs = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (pinConfigured != true) return
        val stopAt = lastStopAtMs
        if (stopAt <= 0L) return
        val elapsed = System.currentTimeMillis() - stopAt
        lastStopAtMs = 0L
        if (elapsed >= backgroundTimeoutMs) {
            _requireUnlock.value = true
            AppLogger.d(
                TAG,
                "lock triggered: background elapsedMs=$elapsed thresholdMs=$backgroundTimeoutMs",
            )
        } else {
            AppLogger.d(
                TAG,
                "lock skipped: background elapsedMs=$elapsed < thresholdMs=$backgroundTimeoutMs",
            )
        }
    }

    companion object {
        private const val TAG = "AppLockManager"

        /** 默认后台超时 60 秒：短于该时间从外部 Activity 回来不重新上锁。 */
        const val DEFAULT_BACKGROUND_TIMEOUT_MS: Long = 60_000L
    }
}
