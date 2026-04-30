package com.xpx.vault.ui.lock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.AppLogger
import com.xpx.vault.data.crypto.BackupKeyManager
import com.xpx.vault.data.crypto.PasswordHasher
import com.xpx.vault.data.db.PhotoVaultDatabase
import com.xpx.vault.data.db.entity.SecuritySettingEntity
import com.xpx.vault.ui.backup.AutoBackupScheduler
import com.xpx.vault.ui.backup.BackupSecretsStore
import com.xpx.vault.ui.backup.BackupTriggerReason
import com.xpx.vault.ui.backup.LocalBackupMvpService
import com.xpx.vault.ui.setup.FirstLaunchRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PIN_LENGTH = 6
private const val LOCK_TYPE_PIN = "PIN"
private const val TAG = "LockViewModel"

@HiltViewModel
class LockViewModel @Inject constructor(
    private val db: PhotoVaultDatabase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val dao = db.securitySettingDao()
    private val backupKeyManager = BackupKeyManager(appContext)

    private val _state = MutableStateFlow(LockUiState(loading = true))
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (FirstLaunchRouter.detect(appContext, db)) {
                FirstLaunchRouter.Branch.Unlock -> {
                    val setting = dao.getById()
                    _state.value = if (setting != null) toUnlockState(setting) else freshSetupState()
                }
                FirstLaunchRouter.Branch.Fresh -> {
                    _state.value = freshSetupState()
                }
                FirstLaunchRouter.Branch.RestoreLogin -> {
                    _state.value = LockUiState(
                        stage = LockStage.RESTORE_LOGIN,
                        loading = false,
                        isSetup = false,
                        title = "欢迎回来",
                        subtitle = "检测到你的备份，请输入 AI Vault 密码",
                        stepLabel = null,
                    )
                }
            }
        }
    }

    private fun freshSetupState(): LockUiState = LockUiState(
        stage = LockStage.SETUP_ENTER,
        loading = false,
        title = "设置 PIN 码",
        subtitle = "请设置一个 6 位 PIN 码用于快速解锁",
        stepLabel = "1 / 2",
    )

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
            } else if (s.stage == LockStage.RESTORE_LOGIN) {
                attemptRestoreLogin(next)
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
            // UI 立即跳转；Argon2id 派生放后台，备份尽早触发一次。
            onSaved()
            launchRefreshBackupKey(rawValue, triggerAutoBackup = true, force = true)
        }
    }

    private fun verifyUnlockPin(pin: String) {
        viewModelScope.launch {
            val setting = dao.getById() ?: return@launch
            val hashed = PasswordHasher.sha256HexOfUtf8(pin)
            if (setting.pinHashHex == hashed) {
                dao.upsert(setting.copy(failCount = 0))
                // PIN 校验通过立即解锁 UI；Argon2id 派生放后台，已有缓存则直接跳过。
                _state.value = _state.value.copy(unlockSuccess = true, enteredPin = "", error = null)
                launchRefreshBackupKey(pin, triggerAutoBackup = false, force = false)
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

    /**
     * 后台异步刷新 BackupKey，避免阻塞 UI。
     * @param force true 时忽略缓存强制重新派生（用于 Setup / 改密码）。
     * @param triggerAutoBackup 派生成功后是否迷一次 PASSWORD_CHANGED 自动备份。
     */
    private fun launchRefreshBackupKey(pin: String, triggerAutoBackup: Boolean, force: Boolean) {
        viewModelScope.launch {
            runCatching {
                // 已有有效缓存且非强刷：不再运行 Argon2id。
                if (!force && BackupSecretsStore.hasCached(appContext)) {
                    return@runCatching
                }
                withContext(Dispatchers.IO) {
                    val params = backupKeyManager.getOrCreateKdfParams()
                    val chars = pin.toCharArray()
                    val material = try {
                        backupKeyManager.deriveKey(chars, params)
                    } finally {
                        chars.fill(0.toChar())
                    }
                    BackupSecretsStore.cache(appContext, material.key)
                }
                if (triggerAutoBackup) {
                    AutoBackupScheduler.runOnceNow(appContext, BackupTriggerReason.PASSWORD_CHANGED)
                }
            }.onFailure {
                AppLogger.e(TAG, "refreshBackupKey failed: ${it.message}", it)
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

    /**
     * RESTORE_LOGIN 分支：以输入 PIN 尝试解密外部 backup.dat。
     * - 成功：把该 PIN 写入 SecuritySetting（等于本机 PIN），并缓存 BackupKey，解锁成功。
     * - 失败：累计次数，♥3 暴露「放弃备份」入口。
     */
    private fun attemptRestoreLogin(pin: String) {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                LocalBackupMvpService.restoreFromAutoPackage(appContext, pin.toCharArray())
            }
            if (result.success) {
                // 1) 用该 PIN 注册为本机 PIN
                val hashed = PasswordHasher.sha256HexOfUtf8(pin)
                dao.upsert(
                    SecuritySettingEntity(
                        lockType = LOCK_TYPE_PIN,
                        pinHashHex = hashed,
                        biometricEnabled = false,
                        failCount = 0,
                    ),
                )
                // 2) 缓存 BackupKey
                launchRefreshBackupKey(pin, triggerAutoBackup = false, force = true)
                _state.value = _state.value.copy(
                    loading = false,
                    unlockSuccess = true,
                    enteredPin = "",
                    error = null,
                    restoreFailCount = 0,
                )
            } else {
                val nextFail = s.restoreFailCount + 1
                _state.value = _state.value.copy(
                    loading = false,
                    enteredPin = "",
                    error = result.message,
                    restoreFailCount = nextFail,
                    showAbandonBackupEntry = nextFail >= 3,
                )
            }
        }
    }

    /** 放弃外部备份，回到正常的 SETUP_ENTER 新建相册流程。 */
    fun abandonBackupAndCreateFresh() {
        // 不删除外部 backup.dat（防反悔）；下次设置 PIN 后的自动备份会 FULL 覆盖。
        _state.value = freshSetupState()
    }
}

enum class LockStage {
    SETUP_ENTER,
    SETUP_CONFIRM,
    SETUP_CONFIRM_ERROR,
    UNLOCK,
    RESTORE_LOGIN,
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
    val restoreFailCount: Int = 0,
    val showAbandonBackupEntry: Boolean = false,
)
