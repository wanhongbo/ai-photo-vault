package com.photovault.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private enum class BackgroundLockPolicy {
    ON_STOP,
    ON_PAUSE,
}

@Singleton
class AppLockManager @Inject constructor() : DefaultLifecycleObserver {
    private val backgroundLockPolicy = BackgroundLockPolicy.ON_STOP

    private val _requireUnlock = MutableStateFlow(true)
    val requireUnlock: StateFlow<Boolean> = _requireUnlock.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun onUnlockSucceeded() {
        _requireUnlock.value = false
    }

    override fun onPause(owner: LifecycleOwner) {
        if (backgroundLockPolicy == BackgroundLockPolicy.ON_PAUSE) {
            _requireUnlock.value = true
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (backgroundLockPolicy == BackgroundLockPolicy.ON_STOP) {
            _requireUnlock.value = true
        }
    }
}
