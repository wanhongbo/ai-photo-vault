package com.xpx.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiTextSize

/**
 * 6 位 PIN 输入对话框：
 * - 使用不可见输入框接收数字键盘输入，以 6 个圆点展示当前输入进度。
 * - 满 6 位时会自动回调 [onConfirm]，并在 busy 状态下显示进度指示。
 * - 外层切换 [show] 为 false 会清空内部输入。
 */
@Composable
fun PinInputDialog(
    show: Boolean,
    title: String,
    subtitle: String,
    confirmText: String = "确认",
    dismissText: String = "取消",
    errorMessage: String? = null,
    busy: Boolean = false,
    pinLength: Int = 6,
    onConfirm: (pin: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    var pin by remember(show) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        containerColor = Color.Transparent,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UiColors.Dialog.bg, RoundedCornerShape(UiRadius.dialog))
                    .padding(20.dp),
            ) {
                Text(
                    text = title,
                    color = UiColors.Dialog.title,
                    fontSize = UiTextSize.dialogTitle,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    color = UiColors.Dialog.body,
                    fontSize = UiTextSize.dialogBody,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 10.dp),
                )

                // 隐藏输入框 + 显式 Dots
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicTextField(
                        value = pin,
                        onValueChange = { v ->
                            if (busy) return@BasicTextField
                            val filtered = v.filter { it.isDigit() }.take(pinLength)
                            pin = filtered
                            if (filtered.length == pinLength) {
                                onConfirm(filtered)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                        modifier = Modifier
                            .size(width = 1.dp, height = 1.dp)
                            .focusRequester(focusRequester),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(pinLength) { idx ->
                            val active = idx < pin.length
                            val isError = errorMessage != null
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .border(
                                        2.dp,
                                        if (isError) UiColors.Lock.error else UiColors.Lock.brandBlue,
                                        CircleShape,
                                    )
                                    .background(
                                        color = if (active) {
                                            if (isError) UiColors.Lock.error else UiColors.Lock.brandBlue
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }

                if (busy) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = UiColors.Lock.brandBlue,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "正在处理…",
                            color = UiColors.Dialog.body,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = UiColors.Lock.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppButton(
                        text = dismissText,
                        onClick = onDismiss,
                        variant = AppButtonVariant.SECONDARY,
                        enabled = !busy,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    )
                    AppButton(
                        text = confirmText,
                        onClick = { if (pin.length == pinLength) onConfirm(pin) },
                        variant = AppButtonVariant.PRIMARY,
                        enabled = !busy && pin.length == pinLength,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    )
                }
            }
        },
        confirmButton = {},
    )
}
