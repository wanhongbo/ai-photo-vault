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
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize

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
        !enabled || loading -> UiColors.Button.disabledContainer to UiColors.Button.disabledContent
        variant == AppButtonVariant.PRIMARY -> UiColors.Button.primaryContainer to UiColors.Button.primaryContent
        variant == AppButtonVariant.SECONDARY -> UiColors.Button.secondaryContainer to UiColors.Button.secondaryContent
        else -> UiColors.Button.dangerContainer to UiColors.Button.dangerContent
    }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(UiSize.buttonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = UiColors.Button.disabledContainer,
            disabledContentColor = UiColors.Button.disabledContent,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(UiSize.loadingIndicator),
                    strokeWidth = UiSize.loadingIndicator / 10,
                    color = contentColor,
                )
            } else {
                Text(text = text, fontSize = UiTextSize.button)
            }
        }
    }
}
