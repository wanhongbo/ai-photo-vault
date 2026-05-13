package com.xpx.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

/**
 * 统一的文本输入弹窗，用于新建相册、重命名等场景。
 *
 * 视觉规范与 [AppDialog] 保持一致：
 * - 容器：`UiColors.Dialog.bg` + `UiRadius.dialog` 圆角 + 1dp 边框
 * - 内边距：`UiSize.dialogPadding`
 * - 按钮高度：统一 `UiSize.dialogButtonHeight`，确保并排按钮等高
 * - 宽度上限：`UiSize.dialogMaxWidth`
 *
 * @param show 是否显示
 * @param title 弹窗标题（居中展示）
 * @param value 输入框当前值
 * @param onValueChange 输入框值变更回调
 * @param placeholder 输入框占位文本
 * @param confirmText 确认按钮文案
 * @param onConfirm 确认回调（输入框非空时才可点击）
 * @param dismissText 取消按钮文案
 * @param onDismiss 取消/关闭回调
 * @param confirmVariant 确认按钮样式，默认 PRIMARY
 * @param singleLine 输入框是否单行，默认 true
 * @param keyboardType 键盘类型，默认 text
 */
@Composable
fun AppInputDialog(
    show: Boolean,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    confirmVariant: AppButtonVariant = AppButtonVariant.PRIMARY,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    if (!show) return

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        text = {
            Column(
                modifier = Modifier
                    .widthIn(max = UiSize.dialogMaxWidth)
                    .fillMaxWidth()
                    .background(UiColors.Dialog.bg, RoundedCornerShape(UiRadius.dialog))
                    .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.dialog))
                    .padding(UiSize.dialogPadding),
            ) {
                Text(
                    text = title,
                    color = UiColors.Dialog.title,
                    fontSize = UiTextSize.dialogTitle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UiSize.dialogBodyTopGap)
                        .height(52.dp)
                        .clip(RoundedCornerShape(UiRadius.homeAlbumCard))
                        .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeAlbumCard))
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = UiColors.Dialog.title,
                        fontSize = UiTextSize.homeEmptyBody,
                    ),
                    placeholder = {
                        Text(
                            text = placeholder,
                            color = UiColors.Home.subtitle,
                            fontSize = UiTextSize.homeEmptyBody,
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = UiColors.Home.emptyCardBg,
                        unfocusedContainerColor = UiColors.Home.emptyCardBg,
                        disabledContainerColor = UiColors.Home.emptyCardBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = UiColors.Home.navItemActive,
                        focusedTextColor = UiColors.Dialog.title,
                        unfocusedTextColor = UiColors.Dialog.title,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (value.isNotBlank()) onConfirm() },
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UiSize.dialogButtonTopGap),
                    horizontalArrangement = Arrangement.spacedBy(UiSize.dialogButtonSpacing),
                ) {
                    AppButton(
                        text = dismissText,
                        onClick = onDismiss,
                        variant = AppButtonVariant.SECONDARY,
                        modifier = Modifier
                            .weight(1f)
                            .height(UiSize.dialogButtonHeight),
                    )
                    AppButton(
                        text = confirmText,
                        onClick = onConfirm,
                        variant = confirmVariant,
                        enabled = value.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(UiSize.dialogButtonHeight),
                    )
                }
            }
        },
        confirmButton = {},
    )
}
