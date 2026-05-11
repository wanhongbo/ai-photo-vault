package com.xpx.vault.ui.lock

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppDialog
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

// 生物识别自动重拉冷却期：用户取消后 4 秒内不再自动拉起，避免 ON_RESUME 立即重复弹窗
private const val BIOMETRIC_AUTO_RETRY_COOLDOWN_MS = 4_000L

@Composable
fun LockScreen(
    onUnlockSuccess: () -> Unit,
    onQuickCapture: () -> Unit = {},
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findFragmentActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val biometricAuthenticator =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val biometricAvailability = remember(context) {
        resolveBiometricAvailability(context, biometricAuthenticator)
    }
    val biometricAvailable = biometricAvailability.isAvailable
    var biometricInFlight by remember { mutableStateOf(false) }
    var lastBiometricDismissedAt by remember { mutableStateOf(0L) }
    var showAbandonBackupDialog by remember { mutableStateOf(false) }
    val stageState = rememberUpdatedState(state.stage)
    val biometricEnabledState = rememberUpdatedState(state.biometricEnabled)
    val dismissedAtState = rememberUpdatedState(lastBiometricDismissedAt)
    val inFlightState = rememberUpdatedState(biometricInFlight)

    fun canAutoPromptBiometric(): Boolean {
        if (inFlightState.value) return false
        if (stageState.value != LockStage.UNLOCK) return false
        if (!biometricEnabledState.value) return false
        if (!biometricAvailable) return false
        val elapsed = System.currentTimeMillis() - dismissedAtState.value
        return elapsed >= BIOMETRIC_AUTO_RETRY_COOLDOWN_MS
    }

    fun launchBiometricPrompt(userInitiated: Boolean = true) {
        if (biometricInFlight) return
        if (!biometricAvailable) {
            if (userInitiated) {
                viewModel.onBiometricUnlockFailed(biometricAvailability.unavailableMessage)
            }
            return
        }
        val activity = hostActivity ?: run {
            if (userInitiated) {
                viewModel.onBiometricUnlockFailed(context.getString(R.string.lock_biometric_cannot_start))
            }
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                biometricInFlight = false
                viewModel.onBiometricUnlockSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                biometricInFlight = false
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    // 用户主动取消：记录时间戳，避免下次 ON_RESUME 立即再弹
                    lastBiometricDismissedAt = System.currentTimeMillis()
                } else {
                    viewModel.onBiometricUnlockFailed(
                        context.getString(R.string.lock_biometric_failed_with_reason, errString),
                    )
                }
            }

            override fun onAuthenticationFailed() {
                viewModel.onBiometricUnlockFailed(context.getString(R.string.lock_biometric_failed_use_pin))
            }
        }
        biometricInFlight = true
        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.lock_biometric_prompt_title))
            .setSubtitle(context.getString(R.string.lock_biometric_prompt_subtitle))
            .setAllowedAuthenticators(biometricAuthenticator)
            .build()
        prompt.authenticate(promptInfo)
    }

    // 首次 stage 切入 UNLOCK 时自动拉起（关心冷启动、PIN 设置完成后等异步加载场景）
    LaunchedEffect(state.stage, state.biometricEnabled, biometricAvailable) {
        if (canAutoPromptBiometric()) {
            launchBiometricPrompt(userInitiated = false)
        }
    }

    // App 每次从后台回到前台时自动拉起，取消后 4 秒内不重复弹
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && canAutoPromptBiometric()) {
                launchBiometricPrompt(userInitiated = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (state.unlockSuccess) {
        viewModel.consumeUnlockEvent()
        onUnlockSuccess()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = UiColors.Lock.bg) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.lock_loading), color = UiColors.Lock.textSub)
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
                state.stepLabel?.let { step ->
                    Text(
                        text = stringResource(R.string.lock_screen_step_prefix, step),
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
                    Text(
                        text = it,
                        color = UiColors.Lock.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 8.dp, end = 8.dp),
                    )
                }

                NumberPad(
                    modifier = Modifier.padding(top = 20.dp),
                    showBiometric = !state.isSetup && biometricAvailable,
                    onNumber = viewModel::onNumber,
                    onDelete = viewModel::deleteLast,
                    onBiometric = { launchBiometricPrompt() },
                    onQuickCapture = onQuickCapture,
                )

                if (state.stage == LockStage.SETUP_CONFIRM_ERROR) {
                    val resetInteraction = rememberFeedbackInteractionSource()
                    AppButton(
                        text = stringResource(R.string.lock_setup_reset_password),
                        onClick = viewModel::resetSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                            .height(52.dp)
                            .pressFeedback(resetInteraction),
                        variant = AppButtonVariant.DANGER,
                    )
                } else if (state.stage == LockStage.RESTORE_LOGIN && state.showAbandonBackupEntry) {
                    val abandonInteraction = rememberFeedbackInteractionSource()
                    AppButton(
                        text = stringResource(R.string.lock_abandon_button),
                        onClick = { showAbandonBackupDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                            .height(52.dp)
                            .pressFeedback(abandonInteraction),
                        variant = AppButtonVariant.DANGER,
                    )
                }
            }
        }
    }

    AppDialog(
        show = showAbandonBackupDialog,
        title = stringResource(R.string.lock_abandon_backup_title),
        message = stringResource(R.string.lock_abandon_backup_message),
        confirmText = stringResource(R.string.lock_abandon_confirm),
        dismissText = stringResource(R.string.lock_abandon_retry),
        onConfirm = {
            showAbandonBackupDialog = false
            viewModel.abandonBackupAndCreateFresh()
        },
        onDismiss = { showAbandonBackupDialog = false },
    )

    AppDialog(
        show = state.success && state.biometricPromptAfterSetup && biometricAvailable,
        title = stringResource(R.string.lock_biometric_setup_title),
        message = stringResource(R.string.lock_biometric_setup_message),
        confirmText = stringResource(R.string.lock_biometric_setup_confirm),
        dismissText = stringResource(R.string.lock_biometric_setup_later),
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
private fun LockSuccessContent(onContinue: () -> Unit) {
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
            stringResource(R.string.lock_success_title),
            color = UiColors.Lock.textMain,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            stringResource(R.string.lock_success_subtitle),
            color = UiColors.Lock.textSub,
            modifier = Modifier.padding(top = 10.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp)
                .border(1.dp, UiColors.Lock.keypadStroke, RoundedCornerShape(UiRadius.hintCard))
                .background(UiColors.Lock.hintPanel, RoundedCornerShape(UiRadius.hintCard))
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.lock_success_notice_title),
                color = UiColors.Lock.textMain,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = UiColors.Lock.keypadStroke)
            Text(stringResource(R.string.lock_success_notice_1), color = UiColors.Lock.textSub)
            Text(
                stringResource(R.string.lock_success_notice_2),
                color = UiColors.Lock.textSub,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.lock_success_notice_3),
                color = UiColors.Lock.textSub,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val continueInteraction = rememberFeedbackInteractionSource()
        AppButton(
            text = stringResource(R.string.lock_success_start),
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
    onQuickCapture: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
            KeypadKey(
                label = "",
                extraHighlight = true,
                iconRes = R.drawable.ic_home_nav_camera,
                contentDescription = stringResource(R.string.home_nav_camera),
                onClick = onQuickCapture,
            )
            KeypadKey(label = "0", extraHighlight = true, onClick = { onNumber(0) })
            KeypadKey(label = "⌫", extraHighlight = true, onClick = onDelete)
        }
        if (showBiometric) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    text = stringResource(R.string.lock_biometric_retry),
                    color = UiColors.Lock.brandBlue,
                    modifier = Modifier.throttledClickable(onClick = onBiometric).padding(top = 2.dp),
                )
            }
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
            BiometricAvailability(false, context.getString(R.string.lock_biometric_system_not_enrolled))
        }
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            BiometricAvailability(false, context.getString(R.string.lock_biometric_hw_unavailable))
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            BiometricAvailability(false, context.getString(R.string.lock_biometric_no_hardware))
        }
        else -> BiometricAvailability(false, context.getString(R.string.lock_biometric_system_generic))
    }
}

@Composable
private fun KeypadKey(
    label: String,
    extraHighlight: Boolean = false,
    onClick: () -> Unit,
    iconRes: Int? = null,
    contentDescription: String? = null,
) {
    val interactionSource = rememberFeedbackInteractionSource()
    Box(
        modifier = Modifier
            .size(86.dp)
            .clip(CircleShape)
            .background(UiColors.Lock.keypadSurface, CircleShape)
            .border(
                width = if (extraHighlight) 1.5.dp else 1.dp,
                color = if (extraHighlight) UiColors.Lock.brandBlue else UiColors.Lock.keypadStroke,
                shape = CircleShape,
            )
            .pressFeedback(interactionSource = interactionSource)
            .throttledClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = UiColors.Lock.textMain,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Text(label, color = UiColors.Lock.textMain, fontSize = 34.sp)
        }
    }
}
