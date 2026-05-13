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
import androidx.compose.ui.unit.dp
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

/**
 * 通用的"详细信息"弹窗：头部标题 + 若干键值对行 + 底部单按钮。
 *
 * 视觉与 [AppDialog] 保持一致（同色板/同圆角/同边框），只是正文改为多行 Row 排版。
 */
@Composable
fun MediaInfoDialog(
    show: Boolean,
    title: String,
    items: List<Pair<String, String>>,
    confirmText: String,
    onDismiss: () -> Unit,
) {
    if (!show) return
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
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UiSize.dialogBodyTopGap),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(UiSize.dialogButtonSpacing),
                        ) {
                            Text(
                                text = label,
                                color = UiColors.Dialog.body.copy(alpha = 0.7f),
                                fontSize = UiTextSize.dialogBody,
                                modifier = Modifier.weight(0.35f),
                            )
                            Text(
                                text = value,
                                color = UiColors.Dialog.body,
                                fontSize = UiTextSize.dialogBody,
                                modifier = Modifier.weight(0.65f),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UiSize.dialogButtonTopGap),
                ) {
                    AppButton(
                        text = confirmText,
                        onClick = onDismiss,
                        variant = AppButtonVariant.PRIMARY,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(UiSize.dialogButtonHeight),
                    )
                }
            }
        },
        confirmButton = {},
    )
}
