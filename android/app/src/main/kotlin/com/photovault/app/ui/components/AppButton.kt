package com.photovault.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PrimaryContainer = Color(0xFF4A9EFF)
private val PrimaryContent = Color.White
private val SecondaryContainer = Color(0xFF1A202C)
private val SecondaryContent = Color(0xFFEAF1FF)
private val DangerContainer = Color(0xFF2A1820)
private val DangerContent = Color(0xFFFF6B8F)
private val DisabledContainer = Color(0xFF2A3240)
private val DisabledContent = Color(0xFF7788A1)

enum class AppButtonVariant {
    PRIMARY,
    SECONDARY,
    DANGER,
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val (containerColor, contentColor) = when {
        !enabled || loading -> DisabledContainer to DisabledContent
        variant == AppButtonVariant.PRIMARY -> PrimaryContainer to PrimaryContent
        variant == AppButtonVariant.SECONDARY -> SecondaryContainer to SecondaryContent
        else -> DangerContainer to DangerContent
    }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = DisabledContainer,
            disabledContentColor = DisabledContent,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Text(text = text, fontSize = 18.sp)
            }
        }
    }
}
