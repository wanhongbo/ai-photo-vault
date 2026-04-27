package com.xpx.vault.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xpx.vault.ui.feedback.rememberThrottledClick
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

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
    val latestOnClick by rememberUpdatedState(onClick)
    val throttledOnClick = rememberThrottledClick { latestOnClick() }
    val (containerColor, contentColor) = when {
        !enabled || loading -> UiColors.Button.disabledContainer to UiColors.Button.disabledContent
        variant == AppButtonVariant.PRIMARY -> UiColors.Button.primaryContainer to UiColors.Button.primaryContent
        variant == AppButtonVariant.SECONDARY -> UiColors.Button.secondaryContainer to UiColors.Button.secondaryContent
        else -> UiColors.Button.dangerContainer to UiColors.Button.dangerContent
    }
    Button(
        onClick = throttledOnClick,
        enabled = enabled && !loading,
        modifier = modifier.height(UiSize.buttonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = UiColors.Button.disabledContainer,
            disabledContentColor = UiColors.Button.disabledContent,
        ),
        shape = RoundedCornerShape(999.dp),
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
