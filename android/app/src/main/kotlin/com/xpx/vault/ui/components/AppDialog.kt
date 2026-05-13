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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

/**
 * 项目统一的确认/提示弹窗。
 *
 * 视觉规范：
 * - 容器：`UiColors.Dialog.bg` + `UiRadius.dialog` 圆角 + 1dp 边框
 * - 内边距：`UiSize.dialogPadding`
 * - 按钮高度：统一 `UiSize.dialogButtonHeight`，确保并排按钮等高
 * - 宽度上限：`UiSize.dialogMaxWidth`，比平台默认约大 10%
 */
@Composable
fun AppDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmVariant: AppButtonVariant = AppButtonVariant.PRIMARY,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
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
                )
                Text(
                    text = message,
                    color = UiColors.Dialog.body,
                    fontSize = UiTextSize.dialogBody,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = UiSize.dialogBodyTopGap),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UiSize.dialogButtonTopGap),
                    horizontalArrangement = Arrangement.spacedBy(UiSize.dialogButtonSpacing),
                ) {
                    dismissText?.let {
                        AppButton(
                            text = it,
                            onClick = { onDismiss?.invoke() },
                            variant = AppButtonVariant.SECONDARY,
                            modifier = Modifier
                                .weight(1f)
                                .height(UiSize.dialogButtonHeight),
                        )
                    }
                    AppButton(
                        text = confirmText,
                        onClick = onConfirm,
                        variant = confirmVariant,
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
