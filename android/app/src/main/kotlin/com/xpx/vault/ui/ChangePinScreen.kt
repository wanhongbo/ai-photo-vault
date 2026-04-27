package com.xpx.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppDialog
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.data.crypto.PasswordHasher
import com.xpx.vault.data.db.PhotoVaultDatabase
import com.xpx.vault.data.db.entity.SecuritySettingEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val CHANGE_PIN_LENGTH = 6

@Composable
fun ChangePinScreen(
    onBack: () -> Unit,
    viewModel: ChangePinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    var showPinPlainText by remember { mutableStateOf(false) }

    AppDialog(
        show = state.showSuccessDialog,
        title = stringResource(R.string.settings_pin_success_title),
        message = stringResource(R.string.settings_pin_success_desc),
        confirmText = stringResource(R.string.settings_pin_success_action),
        onConfirm = {
            viewModel.dismissSuccessDialog()
            onBack()
        },
    )

    AppDialog(
        show = state.fatalError != null,
        title = stringResource(R.string.settings_pin_error_title),
        message = state.fatalError ?: "",
        confirmText = stringResource(R.string.settings_pin_error_action),
        onConfirm = { viewModel.dismissFatalError() },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.settingsScreenHorizontalPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppTopBar(title = stringResource(R.string.settings_pin_title), onBack = onBack)
        Text(
            text = stringResource(R.string.settings_pin_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeSubtitle,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
                .padding(UiSize.homeCardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_pin_step, state.stepIndex, 3),
                color = UiColors.Home.navItemActive,
                fontSize = UiTextSize.settingsRowDesc,
            )
            Text(
                text = state.stepTitleRes?.let { stringResource(it) } ?: "",
                color = UiColors.Home.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = UiTextSize.homeEmptyTitle,
            )
            Text(
                text = state.stepDescRes?.let { stringResource(it) } ?: "",
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.homeEmptyBody,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(UiColors.Home.emptyCardBg, RoundedCornerShape(UiRadius.settingsRow))
                    .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.settingsRow))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = state.currentPinInput,
                    onValueChange = viewModel::onPinInputChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (showPinPlainText) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = TextStyle(
                        color = UiColors.Home.title,
                        fontSize = UiTextSize.homeEmptyBody,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (state.currentPinInput.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_pin_input_hint),
                                color = UiColors.Home.subtitle,
                                fontSize = UiTextSize.homeEmptyBody,
                            )
                        }
                        innerTextField()
                    },
                )
            }
            AppButton(
                text = if (showPinPlainText) {
                    stringResource(R.string.settings_pin_hide_input)
                } else {
                    stringResource(R.string.settings_pin_show_input)
                },
                onClick = { showPinPlainText = !showPinPlainText },
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = UiColors.Lock.error,
                    fontSize = UiTextSize.settingsRowDesc,
                )
            }
        }
        AppButton(
            text = stringResource(
                when (state.step) {
                    ChangePinStep.VERIFY_OLD -> R.string.settings_pin_action_next
                    ChangePinStep.ENTER_NEW -> R.string.settings_pin_action_next
                    ChangePinStep.CONFIRM_NEW -> R.string.settings_pin_action_confirm
                },
            ),
            onClick = {
                focusManager.clearFocus()
                viewModel.submitCurrentStep()
            },
            enabled = !state.loading,
            loading = state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@HiltViewModel
class ChangePinViewModel @Inject constructor(
    db: PhotoVaultDatabase,
) : ViewModel() {
    private val dao = db.securitySettingDao()
    private val _state = MutableStateFlow(ChangePinUiState())
    val state: StateFlow<ChangePinUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val setting = dao.getById()
            if (setting?.pinHashHex.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    fatalError = "未检测到已设置的 PIN，请先在解锁页完成 PIN 设置。",
                )
            } else {
                _state.value = _state.value.copy(existingSetting = setting)
            }
        }
    }

    fun onPinInputChange(input: String) {
        val filtered = input.filter { it.isDigit() }.take(CHANGE_PIN_LENGTH)
        _state.value = _state.value.copy(currentPinInput = filtered, errorMessage = null)
    }

    fun submitCurrentStep() {
        val s = _state.value
        if (s.loading) return
        if (s.currentPinInput.length != CHANGE_PIN_LENGTH) {
            _state.value = s.copy(errorMessage = "请输入 6 位 PIN")
            return
        }
        when (s.step) {
            ChangePinStep.VERIFY_OLD -> verifyOldPin(s.currentPinInput)
            ChangePinStep.ENTER_NEW -> captureNewPin(s.currentPinInput)
            ChangePinStep.CONFIRM_NEW -> confirmNewPin(s.currentPinInput)
        }
    }

    fun dismissSuccessDialog() {
        _state.value = _state.value.copy(showSuccessDialog = false)
    }

    fun dismissFatalError() {
        _state.value = _state.value.copy(fatalError = null)
    }

    private fun verifyOldPin(pin: String) {
        val setting = _state.value.existingSetting ?: return
        val oldHash = PasswordHasher.sha256HexOfUtf8(pin)
        if (setting.pinHashHex != oldHash) {
            _state.value = _state.value.copy(
                currentPinInput = "",
                errorMessage = "原 PIN 验证失败，请重试。",
            )
            return
        }
        _state.value = _state.value.copy(
            step = ChangePinStep.ENTER_NEW,
            currentPinInput = "",
            errorMessage = null,
        )
    }

    private fun captureNewPin(newPin: String) {
        val oldHash = _state.value.existingSetting?.pinHashHex
        if (oldHash == PasswordHasher.sha256HexOfUtf8(newPin)) {
            _state.value = _state.value.copy(errorMessage = "新 PIN 不能与旧 PIN 相同。")
            return
        }
        _state.value = _state.value.copy(
            pendingNewPin = newPin,
            currentPinInput = "",
            step = ChangePinStep.CONFIRM_NEW,
            errorMessage = null,
        )
    }

    private fun confirmNewPin(confirmPin: String) {
        val s = _state.value
        if (s.pendingNewPin != confirmPin) {
            _state.value = s.copy(
                step = ChangePinStep.ENTER_NEW,
                pendingNewPin = null,
                currentPinInput = "",
                errorMessage = "两次新 PIN 不一致，请重新输入。",
            )
            return
        }
        val setting = s.existingSetting ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, errorMessage = null)
            runCatching {
                dao.upsert(
                    setting.copy(
                        pinHashHex = PasswordHasher.sha256HexOfUtf8(confirmPin),
                        failCount = 0,
                    ),
                )
            }.onSuccess {
                _state.value = _state.value.copy(
                    loading = false,
                    currentPinInput = "",
                    pendingNewPin = null,
                    showSuccessDialog = true,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    fatalError = "PIN 修改失败，请稍后重试。",
                )
            }
        }
    }
}

enum class ChangePinStep {
    VERIFY_OLD,
    ENTER_NEW,
    CONFIRM_NEW,
}

data class ChangePinUiState(
    val loading: Boolean = false,
    val step: ChangePinStep = ChangePinStep.VERIFY_OLD,
    val existingSetting: SecuritySettingEntity? = null,
    val currentPinInput: String = "",
    val pendingNewPin: String? = null,
    val showSuccessDialog: Boolean = false,
    val fatalError: String? = null,
    val errorMessage: String? = null,
) {
    val stepIndex: Int
        get() = when (step) {
            ChangePinStep.VERIFY_OLD -> 1
            ChangePinStep.ENTER_NEW -> 2
            ChangePinStep.CONFIRM_NEW -> 3
        }

    val stepTitleRes: Int?
        get() = when (step) {
            ChangePinStep.VERIFY_OLD -> R.string.settings_pin_step_verify_title
            ChangePinStep.ENTER_NEW -> R.string.settings_pin_step_new_title
            ChangePinStep.CONFIRM_NEW -> R.string.settings_pin_step_confirm_title
        }

    val stepDescRes: Int?
        get() = when (step) {
            ChangePinStep.VERIFY_OLD -> R.string.settings_pin_step_verify_desc
            ChangePinStep.ENTER_NEW -> R.string.settings_pin_step_new_desc
            ChangePinStep.CONFIRM_NEW -> R.string.settings_pin_step_confirm_desc
        }
}
