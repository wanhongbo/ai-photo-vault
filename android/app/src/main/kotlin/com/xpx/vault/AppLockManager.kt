package com.xpx.vault

import android.content.Context
import android.content.Intent
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
 * - 进入后台或用户主动切换应用时立即要求重新解锁，避免最近任务/回前台直接暴露保险箱内容。
 * - 由应用主动拉起的系统页面（照片选择、权限弹窗、SAF 文件选择等）临时跳过后台锁。
 * - 未设置 PIN 时，不触发应用锁（无 PIN 可验证）。
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

    @Volatile
    private var externalSystemUiDepth: Int = 0

    @Volatile
    private var appTransientWindowDepth: Int = 0

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
        requestUnlockIfPinConfigured("manual force lock")
    }

    /** Activity 收到用户离开提示时调用，比进程 onStop 更早，可覆盖最近任务切换缩略图时机。 */
    fun onUserLeavingApp(): Boolean {
        return requestUnlockIfPinConfigured("user leaving app")
    }

    fun shouldProtectTaskSnapshot(): Boolean {
        return pinConfigured == true && externalSystemUiDepth <= 0
    }

    fun shouldIgnoreWindowFocusSnapshotCover(): Boolean {
        return externalSystemUiDepth > 0 || appTransientWindowDepth > 0
    }

    fun isUnlockRequired(): Boolean = _requireUnlock.value

    fun beginExternalSystemUi(reason: String) {
        externalSystemUiDepth += 1
        AppLogger.d(TAG, "external system ui started: $reason depth=$externalSystemUiDepth")
    }

    fun endExternalSystemUi(reason: String) {
        externalSystemUiDepth = (externalSystemUiDepth - 1).coerceAtLeast(0)
        AppLogger.d(TAG, "external system ui ended: $reason depth=$externalSystemUiDepth")
    }

    fun clearExternalSystemUi(reason: String) {
        if (externalSystemUiDepth <= 0) return
        AppLogger.d(TAG, "external system ui cleared: $reason depth=$externalSystemUiDepth")
        externalSystemUiDepth = 0
    }

    fun beginAppTransientWindow(reason: String) {
        appTransientWindowDepth += 1
        AppLogger.d(TAG, "app transient window started: $reason depth=$appTransientWindowDepth")
    }

    fun endAppTransientWindow(reason: String) {
        appTransientWindowDepth = (appTransientWindowDepth - 1).coerceAtLeast(0)
        AppLogger.d(TAG, "app transient window ended: $reason depth=$appTransientWindowDepth")
    }

    override fun onStop(owner: LifecycleOwner) {
        requestUnlockIfPinConfigured("process stopped")
    }

    private fun requestUnlockIfPinConfigured(reason: String): Boolean {
        if (pinConfigured != true) return false
        if (externalSystemUiDepth > 0) {
            AppLogger.d(
                TAG,
                "lock skipped: $reason while external system ui active depth=$externalSystemUiDepth",
            )
            return false
        }
        if (!_requireUnlock.value) {
            _requireUnlock.value = true
            AppLogger.d(TAG, "lock required: $reason")
        }
        return true
    }

    companion object {
        private const val TAG = "AppLockManager"
    }
}

fun Context.findAppLockManager(): AppLockManager? =
    (applicationContext as? LumaApp)?.appLockManager

fun Context.startExternalActivityForAppLock(intent: Intent, reason: String) {
    val appLockManager = findAppLockManager()
    appLockManager?.beginExternalSystemUi(reason)
    try {
        startActivity(intent)
    } catch (throwable: Throwable) {
        appLockManager?.endExternalSystemUi("$reason failed")
        throw throwable
    }
}

inline fun AppLockManager.launchExternalSystemUi(reason: String, launch: () -> Unit) {
    beginExternalSystemUi(reason)
    try {
        launch()
    } catch (throwable: Throwable) {
        endExternalSystemUi("$reason failed")
        throw throwable
    }
}
