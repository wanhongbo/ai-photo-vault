package com.photovault.app.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppButtonVariant
import com.photovault.app.ui.components.AppDialog
import com.photovault.app.ui.feedback.pressFeedback
import com.photovault.app.ui.feedback.rememberFeedbackInteractionSource
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

@Composable
fun LockScreen(
    onUnlockSuccess: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findFragmentActivity() }
    val biometricAuthenticator =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val biometricAvailability = remember(context) {
        resolveBiometricAvailability(context, biometricAuthenticator)
    }
    val biometricAvailable = biometricAvailability.isAvailable
    var biometricAutoPromptConsumed by remember { mutableStateOf(false) }

    fun launchBiometricPrompt() {
        if (!biometricAvailable) {
            viewModel.onBiometricUnlockFailed(biometricAvailability.unavailableMessage)
            return
        }
        val activity = hostActivity ?: run {
            viewModel.onBiometricUnlockFailed("当前页面无法启动生物识别认证，请使用 PIN 解锁")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.onBiometricUnlockSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    viewModel.onBiometricUnlockFailed("生物识别失败：$errString")
                }
            }

            override fun onAuthenticationFailed() {
                viewModel.onBiometricUnlockFailed("生物识别未通过，请重试或改用 PIN 解锁")
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("生物识别解锁")
            .setSubtitle("验证后可快速进入私密相册")
            .setAllowedAuthenticators(biometricAuthenticator)
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(state.stage) {
        if (state.stage != LockStage.UNLOCK) {
            biometricAutoPromptConsumed = false
        }
    }

    LaunchedEffect(state.stage, state.biometricEnabled, biometricAvailable) {
        if (state.stage == LockStage.UNLOCK &&
            state.biometricEnabled &&
            biometricAvailable &&
            !biometricAutoPromptConsumed
        ) {
            biometricAutoPromptConsumed = true
            launchBiometricPrompt()
        }
    }
    if (state.unlockSuccess) {
        viewModel.consumeUnlockEvent()
        onUnlockSuccess()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = UiColors.Lock.bg) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中...", color = UiColors.Lock.textSub)
            }
        } else if (state.success) {
            LockSuccessContent(onContinue = onUnlockSuccess)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                state.stepLabel?.let {
                    Text(
                        text = "安全设置  $it",
                        color = UiColors.Lock.textSub,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                }
                Text(
                    text = state.title,
                    color = if (state.stage == LockStage.SETUP_CONFIRM_ERROR) UiColors.Lock.error else UiColors.Lock.textMain,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.subtitle,
                    color = UiColors.Lock.textSub,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                PinDots(
                    length = state.enteredPin.length,
                    error = state.stage == LockStage.SETUP_CONFIRM_ERROR || state.error != null,
                    modifier = Modifier.padding(top = 20.dp),
                )

                state.helper?.let {
                    Text(text = it, color = UiColors.Lock.success, modifier = Modifier.padding(top = 12.dp))
                }
                state.error?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .border(1.dp, UiColors.Lock.error, RoundedCornerShape(UiRadius.errorBanner))
                            .background(UiColors.Lock.errorBg, RoundedCornerShape(UiRadius.errorBanner))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(text = it, color = UiColors.Lock.error)
                    }
                }

                NumberPad(
                    modifier = Modifier.padding(top = 20.dp),
                    showBiometric = !state.isSetup && biometricAvailable,
                    onNumber = viewModel::onNumber,
                    onDelete = viewModel::deleteLast,
                    onBiometric = { launchBiometricPrompt() },
                )

                if (state.stage == LockStage.SETUP_CONFIRM_ERROR) {
                    val resetInteraction = rememberFeedbackInteractionSource()
                    AppButton(
                        text = "重新设置 PIN 码",
                        onClick = viewModel::resetSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                            .height(52.dp)
                            .pressFeedback(resetInteraction),
                        variant = AppButtonVariant.DANGER,
                    )
                }
            }
        }
    }

    AppDialog(
        show = state.success && state.biometricPromptAfterSetup && biometricAvailable,
        title = "开启生物识别解锁",
        message = "开启后可通过指纹或面容快速解锁；失败时仍可使用 PIN。",
        confirmText = "立即开启",
        dismissText = "暂不开启",
        onConfirm = {
            viewModel.setBiometricEnabled(true)
            launchBiometricPrompt()
        },
        onDismiss = {
            viewModel.dismissBiometricSetupPrompt()
            viewModel.onBiometricUnlockSuccess()
        },
    )
}

@Composable
private fun LockSuccessContent(
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(UiColors.Lock.successHalo, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = UiColors.Lock.success, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "PIN 码设置成功",
            color = UiColors.Lock.textMain,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text("您的 PIN 码已安全加密存储在本设备", color = UiColors.Lock.textSub, modifier = Modifier.padding(top = 10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp)
                .border(1.dp, UiColors.Lock.keypadStroke, RoundedCornerShape(UiRadius.hintCard))
                .background(UiColors.Lock.hintPanel, RoundedCornerShape(UiRadius.hintCard))
                .padding(16.dp),
        ) {
            Text("PIN 安全须知", color = UiColors.Lock.textMain, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = UiColors.Lock.keypadStroke)
            Text("• PIN 码仅存储在您的设备本地，不会上传至云端", color = UiColors.Lock.textSub)
            Text("• 忘记 PIN 码时，可通过主密码重置", color = UiColors.Lock.textSub, modifier = Modifier.padding(top = 8.dp))
            Text("• 连续 5 次错误后账户将临时锁定", color = UiColors.Lock.textSub, modifier = Modifier.padding(top = 8.dp))
        }
        val continueInteraction = rememberFeedbackInteractionSource()
        AppButton(
            text = "开始使用 VaultSafe",
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(56.dp)
                .pressFeedback(continueInteraction),
            variant = AppButtonVariant.PRIMARY,
        )
    }
}

@Composable
private fun PinDots(
    length: Int,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(6) { idx ->
            val active = idx < length
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(2.dp, if (error) UiColors.Lock.error else UiColors.Lock.brandBlue, CircleShape)
                    .background(
                        color = if (active) {
                            if (error) UiColors.Lock.error else UiColors.Lock.brandBlue
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun NumberPad(
    modifier: Modifier = Modifier,
    showBiometric: Boolean,
    onNumber: (Int) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { n ->
                    KeypadKey(
                        label = n.toString(),
                        extraHighlight = true,
                        onClick = { onNumber(n) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showBiometric) {
                KeypadKey(label = "🆔", extraHighlight = true, onClick = onBiometric)
            } else {
                Box(modifier = Modifier.size(86.dp))
            }
            KeypadKey(label = "0", extraHighlight = true, onClick = { onNumber(0) })
            KeypadKey(label = "⌫", extraHighlight = true, onClick = onDelete)
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private data class BiometricAvailability(
    val isAvailable: Boolean,
    val unavailableMessage: String,
)

private fun resolveBiometricAvailability(
    context: Context,
    authenticators: Int,
): BiometricAvailability {
    return when (BiometricManager.from(context).canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability(true, "")
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            BiometricAvailability(false, "系统未录入生物识别，请先在系统设置中录入后再试")
        }
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            BiometricAvailability(false, "当前设备生物识别硬件暂不可用，请稍后重试")
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            BiometricAvailability(false, "当前设备不支持生物识别，请使用 PIN 解锁")
        }
        else -> BiometricAvailability(false, "当前系统不支持生物识别，请使用 PIN 解锁")
    }
}

@Composable
private fun KeypadKey(
    label: String,
    extraHighlight: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = rememberFeedbackInteractionSource()
    Box(
        modifier = Modifier
            .size(86.dp)
            .pressFeedback(
                interactionSource = interactionSource,
                extraHighlight = extraHighlight,
            )
            .background(UiColors.Lock.keypadSurface, CircleShape)
            .border(
                width = if (extraHighlight) 1.5.dp else 1.dp,
                color = if (extraHighlight) UiColors.Lock.brandBlue else UiColors.Lock.keypadStroke,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = UiColors.Lock.textMain, fontSize = 34.sp)
    }
}
