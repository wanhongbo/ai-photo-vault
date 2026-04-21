package com.photovault.app.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photovault.data.crypto.PasswordHasher
import com.photovault.data.db.PhotoVaultDatabase
import com.photovault.data.db.entity.SecuritySettingEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 6
private const val LOCK_TYPE_PIN = "PIN"

@HiltViewModel
class LockViewModel @Inject constructor(
    private val db: PhotoVaultDatabase,
) : ViewModel() {
    private val dao = db.securitySettingDao()

    private val _state = MutableStateFlow(LockUiState(loading = true))
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val setting = dao.getById()
            _state.value = if (setting == null) {
                LockUiState(
                    stage = LockStage.SETUP_ENTER,
                    loading = false,
                    title = "设置 PIN 码",
                    subtitle = "请设置一个 6 位 PIN 码用于快速解锁",
                    stepLabel = "1 / 2",
                )
            } else {
                toUnlockState(setting)
            }
        }
    }

    fun onNumber(number: Int) {
        val s = _state.value
        if (s.loading || s.success) return
        if (s.enteredPin.length >= PIN_LENGTH) return
        val next = s.enteredPin + number
        _state.value = s.copy(enteredPin = next, error = null)
        if (next.length == PIN_LENGTH) {
            if (s.stage == LockStage.SETUP_ENTER) {
                _state.value = s.copy(
                    stage = LockStage.SETUP_CONFIRM,
                    firstPinOrPattern = next,
                    enteredPin = "",
                    title = "再次输入 PIN 码",
                    subtitle = "请重新输入以确认您的 PIN 码",
                    stepLabel = "2 / 2",
                    helper = "第一次 PIN 已设置",
                )
            } else if (s.stage == LockStage.SETUP_CONFIRM) {
                if (next == s.firstPinOrPattern) {
                    persistSetting(LOCK_TYPE_PIN, next, onSaved = {
                        _state.value = s.copy(
                            success = true,
                            title = "PIN 码设置成功",
                            subtitle = "您的 PIN 码已安全加密存储在本设备",
                            enteredPin = "",
                            error = null,
                            biometricPromptAfterSetup = true,
                        )
                    })
                } else {
                    _state.value = s.copy(
                        stage = LockStage.SETUP_CONFIRM_ERROR,
                        enteredPin = "",
                        error = "两次 PIN 码输入不一致，请重新输入",
                        title = "PIN 码不匹配",
                        subtitle = "两次输入的 PIN 码不一致，请重新设置",
                    )
                }
            } else if (s.stage == LockStage.UNLOCK) {
                verifyUnlockPin(next)
            }
        }
    }

    fun deleteLast() {
        val s = _state.value
        if (s.enteredPin.isNotEmpty()) {
            _state.value = s.copy(enteredPin = s.enteredPin.dropLast(1), error = null)
        }
    }

    fun resetSetup() {
        val s = _state.value
        _state.value = s.copy(
            stage = LockStage.SETUP_ENTER,
            enteredPin = "",
            firstPinOrPattern = null,
            helper = null,
            error = null,
            success = false,
            title = "设置 PIN 码",
            subtitle = "请设置一个 6 位 PIN 码用于快速解锁",
            stepLabel = "1 / 2",
        )
    }

    fun consumeUnlockEvent() {
        val s = _state.value
        if (s.unlockSuccess) {
            _state.value = s.copy(unlockSuccess = false)
        }
    }

    fun onBiometricUnlockSuccess() {
        viewModelScope.launch {
            val setting = dao.getById() ?: return@launch
            dao.upsert(setting.copy(failCount = 0))
            _state.value = _state.value.copy(
                unlockSuccess = true,
                enteredPin = "",
                error = null,
            )
        }
    }

    fun onBiometricUnlockFailed(errorMessage: String) {
        val s = _state.value
        _state.value = s.copy(error = errorMessage)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val setting = dao.getById() ?: return@launch
            dao.upsert(setting.copy(biometricEnabled = enabled))
            _state.value = _state.value.copy(
                biometricEnabled = enabled,
                biometricPromptAfterSetup = false,
                error = null,
            )
        }
    }

    fun dismissBiometricSetupPrompt() {
        val s = _state.value
        if (s.biometricPromptAfterSetup) {
            _state.value = s.copy(biometricPromptAfterSetup = false)
        }
    }

    private fun persistSetting(lockType: String, rawValue: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            val hashed = PasswordHasher.sha256HexOfUtf8(rawValue)
            dao.upsert(
                SecuritySettingEntity(
                    lockType = lockType,
                    pinHashHex = hashed,
                    biometricEnabled = false,
                    failCount = 0,
                ),
            )
            onSaved()
        }
    }

    private fun verifyUnlockPin(pin: String) {
        viewModelScope.launch {
            val setting = dao.getById() ?: return@launch
            val hashed = PasswordHasher.sha256HexOfUtf8(pin)
            if (setting.pinHashHex == hashed) {
                dao.upsert(setting.copy(failCount = 0))
                _state.value = _state.value.copy(unlockSuccess = true, enteredPin = "", error = null)
            } else {
                val nextFail = setting.failCount + 1
                dao.upsert(setting.copy(failCount = nextFail))
                _state.value = _state.value.copy(
                    enteredPin = "",
                    error = "PIN 错误，请重试（已失败 $nextFail 次）",
                )
            }
        }
    }

    private fun toUnlockState(setting: SecuritySettingEntity): LockUiState {
        return LockUiState(
            stage = LockStage.UNLOCK,
            loading = false,
            isSetup = false,
            biometricEnabled = setting.biometricEnabled,
            title = "输入 PIN 解锁",
            subtitle = "请输入 6 位 PIN 码解锁",
            stepLabel = null,
        )
    }
}

enum class LockStage {
    SETUP_ENTER,
    SETUP_CONFIRM,
    SETUP_CONFIRM_ERROR,
    UNLOCK,
}

data class LockUiState(
    val stage: LockStage = LockStage.SETUP_ENTER,
    val loading: Boolean = false,
    val isSetup: Boolean = true,
    val success: Boolean = false,
    val unlockSuccess: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricPromptAfterSetup: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val stepLabel: String? = null,
    val helper: String? = null,
    val enteredPin: String = "",
    val firstPinOrPattern: String? = null,
    val error: String? = null,
)
