package com.photovault.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DialogBg = Color(0xFF101722)
private val DialogTitle = Color(0xFFEAF1FF)
private val DialogBody = Color(0xFF97A8C0)

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
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DialogBg, RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = title,
                    color = DialogTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    color = DialogBody,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    dismissText?.let {
                        AppButton(
                            text = it,
                            onClick = { onDismiss?.invoke() },
                            variant = AppButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AppButton(
                        text = confirmText,
                        onClick = onConfirm,
                        variant = confirmVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {},
    )
}
