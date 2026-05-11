package com.xpx.vault.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.data.db.PhotoVaultDatabase
import com.xpx.vault.ui.setup.FirstLaunchRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 与 [SplashScreen] 最短展示时间一致，避免首启路由判断与动画不同步。 */
internal const val SplashHoldMs = 1_600L

data class SplashNavState(
    val ready: Boolean = false,
    /** true：从未设置 PIN 且无外部备份包，直接进入主页；否则进入锁屏/恢复流程。 */
    val skipLockToMain: Boolean = false,
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val db: PhotoVaultDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(SplashNavState())
    val state: StateFlow<SplashNavState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val branch = withContext(Dispatchers.IO) {
                FirstLaunchRouter.detect(appContext, db)
            }
            delay(SplashHoldMs)
            val skipLock = branch == FirstLaunchRouter.Branch.Fresh
            _state.value = SplashNavState(ready = true, skipLockToMain = skipLock)
        }
    }
}
